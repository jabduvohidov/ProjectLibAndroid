package tj.jasurbek.projectlib.data

import kotlinx.serialization.Serializable
import java.util.Locale
import kotlin.math.abs

/*
 * Column templates — port of src/lib/templates.ts. Templates live on the server (GET/POST/DELETE
 * /api/status-templates), shared by everyone; they hold only a named set of columns, no project data.
 */

@Serializable
data class ColumnTemplate(
    val id: String,
    val name: String,
    val statuses: List<StatusDef>,
    val createdBy: String? = null,
    val createdAt: Long = 0,
    val canDelete: Boolean = false,
)

/** Built-in "template": the standard columns. */
val STANDARD_TEMPLATE = ColumnTemplate("standard", "Стандартные колонки", DEFAULT_STATUSES)

const val MAX_STATUSES = 20

private val RU = Locale.forLanguageTag("ru")
fun columnKey(name: String) = name.trim().lowercase(RU)

/** Where ideas of a removed column go: same name → that column; otherwise the closest percent (excluded → excluded). */
fun nearestStatus(old: StatusDef, next: List<StatusDef>): StatusDef? {
    if (next.isEmpty()) return null
    next.firstOrNull { columnKey(it.name) == columnKey(old.name) }?.let { return it }
    if (old.excluded == true) next.firstOrNull { it.excluded == true }?.let { return it }
    val pool = next.filter { it.excluded != true }.ifEmpty { next }
    return pool.minByOrNull { abs(it.percent - old.percent) }
}

/**
 * Applying a template: columns with the same name keep the project's existing id (ideas stay put),
 * new ones get fresh ids so they never clash with ids still used by ideas.
 */
fun adoptTemplate(current: List<StatusDef>, template: List<StatusDef>, newId: () -> String): List<StatusDef> {
    val taken = mutableSetOf<String>()
    return template.map { t ->
        val match = current.firstOrNull { columnKey(it.name) == columnKey(t.name) && it.id !in taken }
        val id = match?.id ?: if (current.any { it.id == t.id } || t.id in taken) newId() else t.id
        taken += id
        t.copy(id = id)
    }
}

/** Unchanged standard columns are stored as "no own columns" (an empty list), like the web app does. */
fun isDefaultColumns(s: List<StatusDef>) = s.size == DEFAULT_STATUSES.size && s.indices.all { i ->
    val c = s[i]; val d = DEFAULT_STATUSES[i]
    c.id == d.id && c.name == d.name && c.tone == d.tone && c.percent == d.percent && (c.excluded == true) == (d.excluded == true)
}

fun newColumnId() = "c" + uid().take(10)

fun uniqueColumnName(list: List<StatusDef>, base: String): String {
    val names = list.map { columnKey(it.name) }.toSet()
    if (columnKey(base) !in names) return base
    var i = 2
    while (columnKey("$base $i") in names) i++
    return "$base $i"
}
