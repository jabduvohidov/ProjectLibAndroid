package tj.jasurbek.projectlib.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/*
 * Offline-first sync protocol — a port of src/lib/sync-protocol.ts.
 *
 * The device keeps `base` (last server-confirmed state) + `outbox` (ops not yet acknowledged).
 * What the user sees = base with the outbox applied on top.
 * POST /api/sync { since, ops } → the server applies the ops (per-field last-writer-wins by `at`,
 * delete wins) and returns every row changed after `since` (tombstones included).
 */

val AppJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    coerceInputValues = true
    classDiscriminator = "type"
}

val PROJECT_FIELDS = listOf("name", "description", "status", "tags", "links", "statuses", "secret")
val IDEA_FIELDS = listOf("title", "description", "status", "priority", "dueDate", "order", "attachments")

@Serializable
sealed class Op {
    abstract val opId: String
    abstract val at: Long

    @Serializable @SerialName("project.put")
    data class ProjectPut(override val opId: String, override val at: Long, val project: Project) : Op()

    @Serializable @SerialName("project.patch")
    data class ProjectPatch(override val opId: String, override val at: Long, val id: String, val patch: JsonObject) : Op()

    @Serializable @SerialName("project.delete")
    data class ProjectDelete(override val opId: String, override val at: Long, val id: String) : Op()

    @Serializable @SerialName("idea.put")
    data class IdeaPut(override val opId: String, override val at: Long, val projectId: String, val idea: Idea) : Op()

    @Serializable @SerialName("idea.patch")
    data class IdeaPatch(override val opId: String, override val at: Long, val projectId: String, val id: String, val patch: JsonObject) : Op()

    @Serializable @SerialName("idea.delete")
    data class IdeaDelete(override val opId: String, override val at: Long, val projectId: String, val id: String) : Op()

    @Serializable @SerialName("replaceAll")
    data class ReplaceAll(override val opId: String, override val at: Long, val projects: List<Project>) : Op()
}

@Serializable
data class SyncRequest(val since: Long, val ops: List<Op>)

@Serializable
data class Rejected(val opId: String, val reason: String)

@Serializable
data class SyncResponse(
    val rev: Long,
    /** true → projects/ideas are the complete state: replace base instead of merging. */
    val full: Boolean = false,
    val projects: List<JsonObject> = emptyList(),
    val ideas: List<JsonObject> = emptyList(),
    val rejected: List<Rejected> = emptyList(),
)

/** Overlay a partial patch on an object (only the listed fields). */
private inline fun <reified T> T.patched(patch: JsonObject, fields: List<String>): T {
    val cur = AppJson.encodeToJsonElement(this).jsonObject
    val merged = JsonObject(cur + patch.filterKeys { it in fields })
    return AppJson.decodeFromJsonElement(merged)
}

fun Project.withPatch(patch: JsonObject) = patched(patch, PROJECT_FIELDS)
fun Idea.withPatch(patch: JsonObject) = patched(patch, IDEA_FIELDS)

/** The fields of [next] that differ from [cur] (null when nothing changed). */
inline fun <reified T> diffFields(cur: T, next: T, fields: List<String>): JsonObject? {
    val a = AppJson.encodeToJsonElement(cur).jsonObject
    val b = AppJson.encodeToJsonElement(next).jsonObject
    val out = fields.filter { it in b && a[it] != b[it] }.associateWith { b.getValue(it) }
    return if (out.isEmpty()) null else JsonObject(out)
}

/** Apply one op to a list of projects (pure). Shows pending changes immediately. */
fun applyOp(projects: List<Project>, op: Op): List<Project> {
    fun map(id: String, fn: (Project) -> Project) = projects.map { if (it.id == id) fn(it) else it }
    return when (op) {
        is Op.ProjectPut -> {
            val existing = projects.find { it.id == op.project.id }
            val next = if (op.project.ideas.isEmpty() && existing != null) op.project.copy(ideas = existing.ideas) else op.project
            if (existing != null) map(next.id) { next } else listOf(next) + projects
        }
        is Op.ProjectPatch -> map(op.id) { it.withPatch(op.patch).copy(updatedAt = maxOf(it.updatedAt, op.at)) }
        is Op.ProjectDelete -> projects.filter { it.id != op.id }
        is Op.IdeaPut -> map(op.projectId) { p ->
            val ideas = if (p.ideas.any { it.id == op.idea.id }) p.ideas.map { if (it.id == op.idea.id) op.idea else it }
            else listOf(op.idea) + p.ideas
            p.copy(updatedAt = maxOf(p.updatedAt, op.at), ideas = ideas)
        }
        is Op.IdeaPatch -> map(op.projectId) { p ->
            p.copy(
                updatedAt = maxOf(p.updatedAt, op.at),
                ideas = p.ideas.map { if (it.id == op.id) it.withPatch(op.patch).copy(updatedAt = maxOf(it.updatedAt, op.at)) else it },
            )
        }
        is Op.IdeaDelete -> map(op.projectId) { p -> p.copy(updatedAt = maxOf(p.updatedAt, op.at), ideas = p.ideas.filter { it.id != op.id }) }
        // Import / "delete all" replace only your own projects; ones shared with you stay.
        is Op.ReplaceAll -> op.projects + projects.filter { !it.isOwn }
    }
}

fun applyOps(projects: List<Project>, ops: List<Op>) = ops.fold(projects, ::applyOp)

private fun JsonObject.isDeleted() = this["deleted"]?.jsonPrimitive?.booleanOrNull == true

/** Merge a server response into the device's base state. */
fun mergeChanges(base: List<Project>, res: SyncResponse): List<Project> {
    val projects = (if (res.full) mutableListOf() else base.toMutableList())
    for (row in res.projects) {
        val id = row["id"]?.jsonPrimitive?.content ?: continue
        val i = projects.indexOfFirst { it.id == id }
        if (row.isDeleted()) { if (i >= 0) projects.removeAt(i); continue }
        val p = AppJson.decodeFromJsonElement<Project>(JsonObject(row - "ideas" - "deleted"))
        if (i >= 0) projects[i] = p.copy(ideas = projects[i].ideas) else projects.add(p)
    }
    val ideasByProject = projects.associate { it.id to it.ideas.toMutableList() }
    for (row in res.ideas) {
        val projectId = row["projectId"]?.jsonPrimitive?.content ?: continue
        val list = ideasByProject[projectId] ?: continue
        val id = row["id"]?.jsonPrimitive?.content ?: continue
        val i = list.indexOfFirst { it.id == id }
        if (row.isDeleted()) { if (i >= 0) list.removeAt(i); continue }
        val idea = AppJson.decodeFromJsonElement<Idea>(JsonObject(row - "projectId" - "deleted"))
        if (i >= 0) list[i] = idea else list.add(idea)
    }
    return projects
        .map { p -> p.copy(ideas = ideasByProject.getValue(p.id).sortedByDescending { it.createdAt }) }
        .sortedByDescending { it.createdAt }
}

