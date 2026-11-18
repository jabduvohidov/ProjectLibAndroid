package tj.jasurbek.projectlib.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.File
import java.security.SecureRandom

enum class SyncState { Syncing, Synced, Offline, Unauthorized, Error, NotConfigured }

data class SyncStatus(
    val state: SyncState,
    val pending: Int,
    /** Has this device ever completed a sync? (false → loading state instead of "empty") */
    val everSynced: Boolean,
    val lastSyncedAt: Long? = null,
    val message: String? = null,
)

private val random = SecureRandom()
private const val ID_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789"
fun uid(): String = buildString { repeat(20) { append(ID_CHARS[random.nextInt(ID_CHARS.length)]) } }

/**
 * Offline-first store — a port of the web src/lib/store.ts.
 *  - Every change is applied locally at once and appended to `outbox` (persisted in a file).
 *  - `base` is the last state confirmed by the server; the UI shows base + outbox.
 *  - The sync engine sends the outbox to POST /api/sync and merges what other devices changed.
 *    It runs on start, after edits, when the network returns, when the app comes to the foreground,
 *    when the server announces a new revision (SSE /api/events), and retries with backoff while offline.
 *
 * All state changes happen on the main thread; network and disk work run on IO.
 */
class Repository(context: Context, private val settings: Settings, val api: Api) {
    @Serializable
    private data class Persisted(val base: List<Project> = emptyList(), val outbox: List<Op> = emptyList(), val lastRev: Long = 0)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val file = File(context.filesDir, "projectlib-state.json")
    private var persisted = load()

    private val _projects = MutableStateFlow(applyOps(persisted.base, persisted.outbox))
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    private val _status = MutableStateFlow(
        SyncStatus(if (api.configured) SyncState.Syncing else SyncState.NotConfigured, persisted.outbox.size, persisted.lastRev > 0)
    )
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    /** Messages for a snackbar: rejected changes etc. */
    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val notices: SharedFlow<String> = _notices.asSharedFlow()

    fun project(id: String) = _projects.value.find { it.id == id }

    /** Signed-in user (set by [Auth]); new projects are created as theirs. */
    var currentUserId: String? = null

    /** Bumped when the local copy is wiped (sign-out, another user): answers to older requests are dropped. */
    private var generation = 0

    /* ---------------- persistence ---------------- */

    private fun load(): Persisted = runCatching {
        if (file.exists()) AppJson.decodeFromString(Persisted.serializer(), file.readText()) else Persisted()
    }.getOrElse { Persisted() } // corrupted → start clean

    private val writes = Channel<Persisted>(Channel.CONFLATED)

    init {
        scope.launch(Dispatchers.IO) {
            for (p in writes) {
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(AppJson.encodeToString(Persisted.serializer(), p))
                tmp.renameTo(file)
            }
        }
    }

    private fun commit(next: Persisted) {
        persisted = next
        _projects.value = applyOps(next.base, next.outbox)
        _status.value = _status.value.copy(pending = next.outbox.size)
        writes.trySend(next)
    }

    private fun setStatus(state: SyncState, message: String? = null, everSynced: Boolean? = null, lastSyncedAt: Long? = null) {
        val s = _status.value
        _status.value = s.copy(
            state = state, message = message, pending = persisted.outbox.size,
            everSynced = everSynced ?: s.everSynced, lastSyncedAt = lastSyncedAt ?: s.lastSyncedAt,
        )
    }

    /* ---------------- mutations (local first) ---------------- */

    private fun enqueue(vararg ops: Op) {
        commit(persisted.copy(outbox = persisted.outbox + ops))
        scheduleSync(400)
    }

    data class ProjectInput(val name: String, val description: String, val status: ProjectStatus, val tags: List<String>, val links: List<Link>)
    data class IdeaInput(
        val title: String, val description: String, val status: IdeaStatus, val priority: Priority,
        val dueDate: String, val attachments: List<Attachment>, val order: Double? = null,
    )

    fun createProject(input: ProjectInput): Project {
        val at = System.currentTimeMillis()
        val p = Project(
            uid(), input.name, input.description, input.status, input.tags, input.links, emptyList(), at, at,
            ownerId = currentUserId, access = Access.Owner,
        )
        enqueue(Op.ProjectPut(uid(), at, p))
        return p
    }

    fun updateProject(id: String, input: ProjectInput) {
        val cur = project(id) ?: return
        val next = cur.copy(name = input.name, description = input.description, status = input.status, tags = input.tags, links = input.links)
        diffFields(cur.copy(ideas = emptyList()), next.copy(ideas = emptyList()), PROJECT_FIELDS)?.let {
            enqueue(Op.ProjectPatch(uid(), System.currentTimeMillis(), id, it))
        }
    }

    fun setProjectStatus(id: String, status: ProjectStatus) {
        val cur = project(id) ?: return
        updateProject(id, ProjectInput(cur.name, cur.description, status, cur.tags, cur.links))
    }

    fun deleteProject(id: String) = enqueue(Op.ProjectDelete(uid(), System.currentTimeMillis(), id))

    /** First position in a status column (new ideas and cards dropped on top go there). */
    fun topOrder(projectId: String?, status: IdeaStatus): Double {
        val ideas = _projects.value.filter { projectId == null || it.id == projectId }.flatMap { it.ideas }.filter { it.status == status }
        return if (ideas.isEmpty()) -System.currentTimeMillis().toDouble() else ideas.minOf { it.sortOrder } - 1
    }

    fun addIdea(projectId: String, input: IdeaInput) {
        val at = System.currentTimeMillis()
        val idea = Idea(
            uid(), input.title, input.description, input.status, input.priority, input.dueDate,
            input.order ?: topOrder(projectId, input.status), input.attachments, createdAt = at, updatedAt = at,
        )
        enqueue(Op.IdeaPut(uid(), at, projectId, idea))
    }

    fun updateIdea(projectId: String, ideaId: String, transform: (Idea) -> Idea) {
        val cur = project(projectId)?.ideas?.find { it.id == ideaId } ?: return
        diffFields(cur, transform(cur), IDEA_FIELDS)?.let {
            enqueue(Op.IdeaPatch(uid(), System.currentTimeMillis(), projectId, ideaId, it))
        }
    }

    fun updateIdea(projectId: String, ideaId: String, input: IdeaInput) = updateIdea(projectId, ideaId) {
        it.copy(
            title = input.title, description = input.description, status = input.status, priority = input.priority,
            dueDate = input.dueDate, attachments = input.attachments,
        )
    }

    fun deleteIdea(projectId: String, ideaId: String) = enqueue(Op.IdeaDelete(uid(), System.currentTimeMillis(), projectId, ideaId))

    /* ---------------- data management ---------------- */

    fun exportJson(): String {
        val obj = JsonObject(
            mapOf(
                "app" to AppJson.parseToJsonElement("\"ProjectLib\""),
                "version" to AppJson.parseToJsonElement("2"),
                "exportedAt" to AppJson.parseToJsonElement("\"${java.time.Instant.now()}\""),
                "projects" to AppJson.encodeToJsonElement(ListSerializer(Project.serializer()), _projects.value),
            )
        )
        return kotlinx.serialization.json.Json { prettyPrint = true; explicitNulls = false }.encodeToString(JsonObject.serializer(), obj)
    }

    /** Replaces all data (on every device after sync). Returns the number of imported projects. */
    fun importJson(text: String): Int {
        val root = runCatching { AppJson.parseToJsonElement(text) }.getOrElse { throw IllegalArgumentException("Файл не похож на JSON") }
        val list = when (root) {
            is JsonArray -> root
            is JsonObject -> root["projects"]?.jsonArray ?: throw IllegalArgumentException("В файле нет списка проектов")
            else -> throw IllegalArgumentException("В файле нет списка проектов")
        }
        val valid = list.mapNotNull { runCatching { AppJson.decodeFromJsonElement<Project>(it) }.getOrNull() }
        if (valid.isEmpty()) throw IllegalArgumentException("Не найдено ни одного корректного проекта")
        replaceAll(valid)
        return valid.size
    }

    private fun replaceAll(projects: List<Project>) = enqueue(Op.ReplaceAll(uid(), System.currentTimeMillis(), projects))
    fun resetToDemo() = replaceAll(demoProjects())
    fun clearAll() = replaceAll(emptyList())

    /** Sign-out / server change: forget this device's copy of the data. */
    fun clearLocalData() {
        syncJob?.cancel()
        inflight = false
        generation++
        commit(Persisted())
        _status.value = SyncStatus(if (api.configured) SyncState.Unauthorized else SyncState.NotConfigured, 0, false)
    }

    /* ---------------- sync engine ---------------- */

    private var syncJob: Job? = null
    private var timer: Job? = null
    private var inflight = false
    private var again = false
    private var failures = 0
    private val backoff = listOf(2_000L, 5_000L, 10_000L, 20_000L, 30_000L)
    private var foreground = false

    private fun scheduleSync(delayMs: Long) {
        timer?.cancel()
        timer = scope.launch { delay(delayMs); syncNow() }
    }

    private fun accept(sent: List<Op>, res: SyncResponse, gen: Int) {
        if (gen != generation) return // answer for a local copy that no longer exists
        val ids = sent.map { it.opId }.toSet()
        commit(Persisted(mergeChanges(persisted.base, res), persisted.outbox.filter { it.opId !in ids }, res.rev))
        if (res.rejected.isNotEmpty()) {
            val reasons = res.rejected.map { it.reason }.distinct().joinToString("; ")
            _notices.tryEmit("Не применено изменений: ${res.rejected.size}. $reasons")
        }
    }

    fun syncNow() {
        timer?.cancel()
        if (!api.configured) { setStatus(SyncState.NotConfigured, "Сервер не указан — данные хранятся только на телефоне"); return }
        if (inflight) { again = true; return }
        inflight = true
        setStatus(SyncState.Syncing)
        syncJob = scope.launch {
            try {
                do {
                    again = false
                    val batch = persisted.outbox.take(BATCH)
                    try {
                        val gen = generation
                        accept(batch, api.sync(batch, persisted.lastRev), gen)
                    } catch (e: HttpFailure) {
                        // A batch the server refuses as invalid would block the queue forever:
                        // retry op by op and drop only the ones that are themselves invalid.
                        if (e.status != 400 || batch.isEmpty()) throw e
                        for (op in batch) {
                            val gen = generation
                            try {
                                accept(listOf(op), api.sync(listOf(op), persisted.lastRev), gen)
                            } catch (err: HttpFailure) {
                                if (err.status != 400) throw err
                                if (generation != gen) continue
                                commit(persisted.copy(outbox = persisted.outbox.filter { it.opId != op.opId }))
                                _notices.tryEmit("Изменение отклонено сервером: ${err.message}")
                            }
                        }
                    }
                } while (again || persisted.outbox.isNotEmpty())
                failures = 0
                setStatus(SyncState.Synced, everSynced = true, lastSyncedAt = System.currentTimeMillis())
            } catch (e: HttpFailure) {
                if (e.status == 401) {
                    setStatus(SyncState.Unauthorized, "Нужно войти: изменения сохраняются на телефоне и отправятся после входа")
                } else {
                    val offline = e.status in listOf(0, 502, 503, 504)
                    setStatus(if (offline) SyncState.Offline else SyncState.Error, e.message)
                    scheduleSync(backoff[minOf(failures++, backoff.lastIndex)])
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                setStatus(SyncState.Error, e.message)
                scheduleSync(backoff[minOf(failures++, backoff.lastIndex)])
            } finally {
                inflight = false
            }
        }
    }

    /** After the server address or token changed: reopen the live channel and sync. */
    fun reconnect() {
        failures = 0
        connectEvents()
        syncNow()
    }

    /* ---------------- live updates (SSE) while the app is visible ---------------- */

    private var events: EventSource? = null
    private var eventsRetry: Job? = null

    private fun connectEvents() {
        eventsRetry?.cancel()
        events?.cancel()
        events = null
        if (!foreground || !api.configured) return
        val url = api.url("api/events") // credentials come from the Api interceptor
        val request = Request.Builder().url(url).header("Accept", "text/event-stream").build()
        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (type != "rev") return
                scope.launch {
                    // Fires on every (re)connect too, so a returning server triggers a sync of queued changes.
                    if (data.toLongOrNull() != persisted.lastRev || persisted.outbox.isNotEmpty()) scheduleSync(150)
                }
            }
            override fun onClosed(eventSource: EventSource) = retry(eventSource)
            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) = retry(eventSource)
            private fun retry(es: EventSource) {
                scope.launch {
                    if (events !== es || !foreground) return@launch
                    eventsRetry = launch { delay(5_000); connectEvents() }
                }
            }
        }
        events = EventSources.createFactory(api.streamClient).newEventSource(request, listener)
    }

    init {
        // Foreground / background: sync and listen only while visible (no battery drain in the background).
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { foreground = true; failures = 0; connectEvents(); syncNow() }
            override fun onStop(owner: LifecycleOwner) { foreground = false; eventsRetry?.cancel(); events?.cancel(); events = null }
        })
        // Network comes back → send the queue right away.
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val main = Handler(Looper.getMainLooper())
        cm?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                main.post { if (foreground) { failures = 0; syncNow(); connectEvents() } }
            }
            override fun onLost(network: Network) {
                main.post { if (api.configured) setStatus(SyncState.Offline) }
            }
        })
    }

    /** Upload an image picked on the phone; returns the attachment to add to the idea. */
    suspend fun upload(bytes: ByteArray, type: String, name: String): Attachment = withContext(Dispatchers.IO) { api.upload(bytes, type, name) }

    companion object { private const val BATCH = 500 }
}
