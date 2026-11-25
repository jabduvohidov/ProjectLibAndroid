package tj.jasurbek.projectlib.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer

/**
 * Who is signed in, and the people directory (names / avatars for sharing and authorship) — a port of the
 * web src/lib/auth.ts. The last known status is cached so the app knows the user while offline.
 * If a DIFFERENT user signs in on this phone, the previous user's local copy is wiped first.
 */
class Auth(private val settings: Settings, private val api: Api, private val repo: Repository) {
    data class State(val status: AuthStatus?, val people: List<Person>) {
        val me get() = status?.user
        val isAdmin get() = me?.role == Role.Admin
        /** Viewers only read what was shared with them. */
        val canWrite get() = me?.role != Role.Viewer
        fun person(id: String?) = id?.let { i -> people.find { it.id == i } }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(State(readCache("status", AuthStatus.serializer()), readCache("people", ListSerializer(Person.serializer())) ?: emptyList()))
    val state: StateFlow<State> = _state.asStateFlow()
    val me get() = _state.value.me

    init { repo.currentUserId = me?.id }

    private fun <T> readCache(key: String, s: kotlinx.serialization.KSerializer<T>): T? =
        settings.cache(key)?.let { runCatching { AppJson.decodeFromString(s, it) }.getOrNull() }
    private fun <T> writeCache(key: String, s: kotlinx.serialization.KSerializer<T>, v: T?) =
        settings.putCache(key, v?.let { AppJson.encodeToString(s, it) })

    /** A different account on this phone → forget the previous one's offline copy before syncing. */
    private fun onUser(user: Me?) {
        val previous = settings.cache("userId")
        if (user != null) settings.putCache("userId", user.id)
        repo.currentUserId = user?.id
        if (user != null && previous != null && previous != user.id) {
            repo.clearLocalData()
            repo.reconnect() // full download for the new account
        }
    }

    /** Fetch the sign-in status; offline → keep the cached one. */
    fun refresh() = scope.launch {
        if (!api.configured) return@launch
        try {
            val s = api.status()
            writeCache("status", AuthStatus.serializer(), s)
            _state.value = _state.value.copy(status = s)
            onUser(s.user)
            if (s.user != null) refreshPeople()
        } catch (_: HttpFailure) { /* offline: keep cache */ }
    }

    suspend fun refreshPeople() {
        try {
            val people = api.directory()
            writeCache("people", ListSerializer(Person.serializer()), people)
            _state.value = _state.value.copy(people = people)
        } catch (_: HttpFailure) { /* offline / not signed in */ }
    }

    /** Sign in with a one-time code from the web profile (Профиль → Подключить телефон). */
    suspend fun signInWithCode(serverUrl: String, code: String): Me {
        val paired = api.redeemCode(serverUrl, code)
        val serverChanged = Settings.normalizeServerUrl(serverUrl) != settings.current.serverUrl
        if (serverChanged && settings.current.serverUrl.isNotEmpty()) { repo.clearLocalData(); settings.putCache("userId", null) }
        settings.setSession(serverUrl, paired.session)
        val status = AuthStatus(authRequired = true, authenticated = true, via = "session", user = paired.user)
        writeCache("status", AuthStatus.serializer(), status)
        _state.value = _state.value.copy(status = status)
        onUser(paired.user)
        repo.reconnect()
        // In the background: the sign-in screen disappears right after this returns (its scope is cancelled).
        scope.launch { refreshPeople() }
        return paired.user
    }

    /** After the token / server was saved by the server form. */
    fun afterTokenSignIn(serverChanged: Boolean) {
        if (serverChanged) { repo.clearLocalData(); settings.putCache("userId", null) }
        repo.reconnect()
        refresh()
    }

    suspend fun updateProfile(name: String? = null, email: String? = null, avatar: String? = null, clearAvatar: Boolean = false) {
        val me = api.updateMe(name, email, avatar, clearAvatar)
        val status = (_state.value.status ?: AuthStatus(authRequired = true, authenticated = true)).copy(user = me)
        writeCache("status", AuthStatus.serializer(), status)
        _state.value = _state.value.copy(status = status)
        refreshPeople()
    }

    /** Sign out: end the server session if reachable and remove this phone's copy of the data. */
    suspend fun signOut() {
        if (settings.current.session.isNotEmpty()) runCatching { api.logout() }
        settings.clearCredentials()
        tj.jasurbek.projectlib.App.instance.secretLock.lock()
        repo.clearLocalData()
        listOf("userId", "people", "status").forEach { settings.putCache(it, null) }
        repo.currentUserId = null
        _state.value = State(null, emptyList())
    }
}

/** "сейчас в сети", "5 мин назад", … (web: UsersPage `ago`). */
fun agoLabel(t: Long?, now: Long = System.currentTimeMillis()): String {
    if (t == null || t == 0L) return "ещё не входил"
    val m = Math.round((now - t) / 60000.0)
    if (m < 2) return "сейчас в сети"
    if (m < 60) return "$m мин назад"
    val h = Math.round(m / 60.0)
    if (h < 24) return "$h ч назад"
    return java.time.Instant.ofEpochMilli(t).atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.forLanguageTag("ru")))
}

/** "Chrome · Mac" / "Android" from a session's user agent (web: ProfilePage `deviceOf`). */
fun deviceOf(ua: String): String {
    if (ua.startsWith("ProjectLib-App")) return "Приложение · Android"
    val os = when {
        "iPhone" in ua -> "iPhone"; "iPad" in ua -> "iPad"; "Android" in ua -> "Android"; "Mac OS X" in ua -> "Mac"
        "Windows" in ua -> "Windows"; "Linux" in ua -> "Linux"; else -> "Устройство"
    }
    val browser = when { "Edg/" in ua -> "Edge"; "Chrome/" in ua -> "Chrome"; "Firefox/" in ua -> "Firefox"; "Safari/" in ua -> "Safari"; else -> "" }
    return if (browser.isEmpty()) os else "$browser · $os"
}
