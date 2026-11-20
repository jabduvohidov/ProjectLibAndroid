package tj.jasurbek.projectlib.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import tj.jasurbek.projectlib.ui.common.GlassDropdown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tj.jasurbek.projectlib.App
import tj.jasurbek.projectlib.data.DueState
import tj.jasurbek.projectlib.data.Idea
import tj.jasurbek.projectlib.data.IdeaLayout
import tj.jasurbek.projectlib.data.IdeaSort
import tj.jasurbek.projectlib.data.DEFAULT_STATUSES
import tj.jasurbek.projectlib.data.StatusDef
import tj.jasurbek.projectlib.data.isOpenIn
import tj.jasurbek.projectlib.data.of
import tj.jasurbek.projectlib.data.Priority
import tj.jasurbek.projectlib.data.byIdeaOrder
import tj.jasurbek.projectlib.data.dueState
import tj.jasurbek.projectlib.data.todayISO
import tj.jasurbek.projectlib.ui.common.ChipRow
import tj.jasurbek.projectlib.ui.common.CountChip
import tj.jasurbek.projectlib.ui.common.DueBadge
import tj.jasurbek.projectlib.ui.common.IdeaStatusPicker
import tj.jasurbek.projectlib.ui.common.PersonChip
import tj.jasurbek.projectlib.ui.designsystem.DS
import tj.jasurbek.projectlib.ui.designsystem.DSBadge
import tj.jasurbek.projectlib.ui.designsystem.DSSegmentedControl
import tj.jasurbek.projectlib.ui.designsystem.DSSpace
import tj.jasurbek.projectlib.ui.forms.AttachmentThumbs

/* ---------------- filter & sort (port of src/lib/ideaView.ts) ---------------- */

/** "open" | "all" | "overdue" | a status. Kept as a string so it survives configuration changes. */
@JvmInline
value class IdeaFilter(val key: String) {
    companion object {
        val Open = IdeaFilter("open")
        val All = IdeaFilter("all")
        val Overdue = IdeaFilter("overdue")
        /** A column, by name — so it works across projects with their own columns (web: statusFilter). */
        fun ofName(name: String) = IdeaFilter("s:" + name.trim().lowercase())
    }
    fun matches(i: Idea, columns: List<StatusDef> = DEFAULT_STATUSES, today: String = todayISO()): Boolean = when (key) {
        "all" -> true
        "open" -> i.isOpenIn(columns)
        "overdue" -> i.dueState(columns, today) == DueState.Overdue
        else -> ofName(columns.of(i.status).name).key == key
    }
}

private fun byDue(columns: List<StatusDef>) = Comparator<Idea> { a, b ->
    // Open ideas with a deadline first (earliest first), then everything else in manual order.
    val da = if (a.isOpenIn(columns) && !a.dueDate.isNullOrEmpty()) a.dueDate else "9999"
    val db = if (b.isOpenIn(columns) && !b.dueDate.isNullOrEmpty()) b.dueDate else "9999"
    val c = da.compareTo(db)
    if (c != 0) c else byIdeaOrder.compare(a, b)
}

fun ideaSorter(s: IdeaSort, columns: List<StatusDef> = DEFAULT_STATUSES): Comparator<Idea> = when (s) {
    IdeaSort.Due -> byDue(columns)
    IdeaSort.New -> compareByDescending { it.createdAt }
    IdeaSort.Order -> byIdeaOrder
}

/** Position between two neighbours (null = column edge). */
fun between(prev: Double?, next: Double?): Double = when {
    prev == null && next == null -> -System.currentTimeMillis().toDouble()
    prev == null -> next!! - 1
    next == null -> prev + 1
    else -> (prev + next) / 2
}

/* ---------------- toolbar ---------------- */

/** List/board switch, sort (list) and filter chips (list) shared by the project screen and the Ideas screen. */
@Composable
fun IdeasToolbar(
    /** Ideas with their project's columns. */
    ideas: List<Pair<Idea, List<StatusDef>>>,
    layout: IdeaLayout, onLayout: (IdeaLayout) -> Unit,
    sort: IdeaSort, onSort: (IdeaSort) -> Unit,
    filter: IdeaFilter, onFilter: (IdeaFilter) -> Unit,
    allStatusChips: Boolean = false,
) {
    val today = todayISO()
    fun count(f: IdeaFilter) = ideas.count { (i, cols) -> f.matches(i, cols, today) }
    // Column chips by name, in column order, merged across projects.
    val columnChips = ideas.map { it.second }.distinct().flatten().distinctBy { IdeaFilter.ofName(it.name).key }
    val overdue = count(IdeaFilter.Overdue)
    Column(verticalArrangement = Arrangement.spacedBy(DSSpace.md)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            DSSegmentedControl(listOf(IdeaLayout.List to "Список", IdeaLayout.Board to "Доска"), layout, onLayout, Modifier.widthIn(max = 220.dp))
            if (layout == IdeaLayout.List) {
                var open by remember { mutableStateOf(false) }
                Box {
                    tj.jasurbek.projectlib.ui.designsystem.DSButton(
                        sort.label, { open = true }, variant = tj.jasurbek.projectlib.ui.designsystem.DSButtonVariant.Ghost,
                        size = tj.jasurbek.projectlib.ui.designsystem.DSButtonSize.Sm, leadingIcon = Icons.AutoMirrored.Rounded.Sort,
                    )
                    GlassDropdown(expanded = open, onDismissRequest = { open = false }, shape = MaterialTheme.shapes.medium) {
                        IdeaSort.entries.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.label) },
                                trailingIcon = if (s == sort) ({ Icon(Icons.Rounded.Check, null, tint = DS.colors.accentText) }) else null,
                                onClick = { open = false; onSort(s) },
                            )
                        }
                    }
                }
            }
        }
        if (layout == IdeaLayout.List) {
            ChipRow {
                CountChip("Открытые", filter == IdeaFilter.Open) { onFilter(IdeaFilter.Open) }
                CountChip("Все", filter == IdeaFilter.All) { onFilter(IdeaFilter.All) }
                if (overdue > 0) CountChip("Просрочено", filter == IdeaFilter.Overdue, overdue, danger = true) { onFilter(IdeaFilter.Overdue) }
                columnChips.filter { allStatusChips || count(IdeaFilter.ofName(it.name)) > 0 }.forEach { s ->
                    val f = IdeaFilter.ofName(s.name)
                    CountChip(s.label, filter == f, count(f)) { onFilter(f) }
                }
            }
        } else if (overdue > 0) {
            Text("Просрочено: $overdue", style = MaterialTheme.typography.bodySmall, color = DS.colors.danger)
        }
    }
}

/* ---------------- row ---------------- */

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IdeaRow(
    projectId: String, idea: Idea, onEdit: () -> Unit, onDelete: () -> Unit,
    /** The project's columns. */
    columns: List<StatusDef> = DEFAULT_STATUSES,
    /** View-only project: no editing controls. */
    readOnly: Boolean = false,
    /** Shared project: show who changed the idea last. */
    showAuthor: Boolean = false,
) {
    val closed = !idea.isOpenIn(columns)
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().then(if (readOnly) Modifier else Modifier.clickable(onClick = onEdit))
            .padding(start = DSSpace.lg, end = if (readOnly) DSSpace.lg else 0.dp, top = DSSpace.md, bottom = DSSpace.md),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DSSpace.xs)) {
            Text(
                idea.title, style = MaterialTheme.typography.bodyLarge,
                color = if (closed) DS.colors.textSecondary else DS.colors.textPrimary,
                textDecoration = if (closed) TextDecoration.LineThrough else null,
            )
            if (idea.description.isNotEmpty()) {
                Text(idea.description, style = MaterialTheme.typography.bodySmall, color = DS.colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (!idea.attachments.isNullOrEmpty()) Box(Modifier.padding(top = DSSpace.xs)) { AttachmentThumbs(idea.attachments, max = 4) }
            FlowRow(
                Modifier.padding(top = DSSpace.xs),
                horizontalArrangement = Arrangement.spacedBy(DSSpace.xs), verticalArrangement = Arrangement.spacedBy(DSSpace.xs),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                val col = columns.of(idea.status)
                if (readOnly) DSBadge(col.label, col.dsTone, dot = true)
                else IdeaStatusPicker(columns, idea.status) { s -> App.instance.repo.updateIdea(projectId, idea.id) { it.copy(status = s) } }
                DueBadge(idea, columns)
                if (idea.priority != Priority.Medium) DSBadge("${idea.priority.label} приоритет", idea.priority.tone)
                if (showAuthor) {
                    val changed = idea.updatedBy != null && idea.updatedBy != idea.createdBy
                    PersonChip(idea.updatedBy ?: idea.createdBy, if (changed) "изменил(а)" else "добавил(а)")
                }
            }
        }
        if (!readOnly) Box {
            tj.jasurbek.projectlib.ui.common.ContentIconButton(Icons.Rounded.MoreVert, "Действия с идеей", DS.colors.textTertiary) { menu = true }
            GlassDropdown(expanded = menu, onDismissRequest = { menu = false }, shape = MaterialTheme.shapes.medium) {
                DropdownMenuItem(text = { Text("Изменить") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; onEdit() })
                DropdownMenuItem(
                    text = { Text("Удалить", color = DS.colors.danger) }, leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = DS.colors.danger) },
                    onClick = { menu = false; onDelete() },
                )
            }
        }
    }
}
