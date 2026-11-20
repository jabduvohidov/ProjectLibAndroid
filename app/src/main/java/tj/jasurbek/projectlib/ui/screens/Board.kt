package tj.jasurbek.projectlib.ui.screens

import android.content.ClipData
import android.content.ClipDescription
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tj.jasurbek.projectlib.App
import tj.jasurbek.projectlib.data.Idea
import tj.jasurbek.projectlib.data.DEFAULT_STATUSES
import tj.jasurbek.projectlib.data.Project
import tj.jasurbek.projectlib.data.StatusDef
import tj.jasurbek.projectlib.data.of
import tj.jasurbek.projectlib.ui.common.LocalToast
import tj.jasurbek.projectlib.ui.designsystem.DSTone
import tj.jasurbek.projectlib.data.Priority
import tj.jasurbek.projectlib.data.byIdeaOrder
import tj.jasurbek.projectlib.data.dueState
import tj.jasurbek.projectlib.ui.common.DueBadge
import tj.jasurbek.projectlib.ui.common.StatusDot
import tj.jasurbek.projectlib.ui.designsystem.DS
import tj.jasurbek.projectlib.ui.designsystem.DSBadge
import tj.jasurbek.projectlib.ui.designsystem.DSSpace

data class BoardItem(
    val projectId: String, val idea: Idea, val projectName: String? = null, val readOnly: Boolean = false,
    /** The idea's project columns. */
    val columns: List<StatusDef> = DEFAULT_STATUSES,
)

data class BoardCol(val key: String, val name: String, val tone: DSTone)

/** Which columns the board shows and how cards map to them (port of projectBoard / mergedBoard in Board.tsx). */
class BoardSpec(
    val columns: List<BoardCol>,
    val keyOf: (BoardItem) -> String,
    /** Status id of the item's project for a column, or null if that project has no such column. */
    val statusFor: (BoardItem, String) -> String?,
)

/** One project's board: its own columns, keyed by status id. */
fun projectBoard(p: Project) = BoardSpec(
    p.columns.map { BoardCol(it.id, it.name, it.dsTone) },
    keyOf = { it.columns.of(it.idea.status).id },
    statusFor = { _, key -> key },
)

private fun nameKey(name: String) = name.trim().lowercase()

/** Several projects: columns merged by name, ordered by progress percent; a card only goes to a column its project has. */
fun mergedBoard(projects: List<Project>): BoardSpec {
    val seen = LinkedHashMap<String, Pair<StatusDef, Int>>()
    var seq = 0
    projects.forEach { p -> p.columns.forEach { s -> seen.getOrPut(nameKey(s.name)) { s to seq++ } } }
    val cols = seen.values.sortedWith(compareBy({ if (it.first.excluded == true) 101 else it.first.percent }, { it.second }))
        .map { (s, _) -> BoardCol(nameKey(s.name), s.name, s.dsTone) }
    return BoardSpec(
        cols,
        keyOf = { nameKey(it.columns.of(it.idea.status).name) },
        statusFor = { item, key -> item.columns.firstOrNull { nameKey(it.name) == key }?.id },
    )
}

private const val MIME = "application/x-projectlib-idea"

/**
 * Kanban board: the project's columns (or several projects' columns merged by name), scrolling sideways. Long-press a card and drag it to another
 * column or between cards; dropping saves {status, order} — synced like any edit (port of Board.tsx).
 * Screen readers get "Переместить в …" actions on every card instead of dragging.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IdeaBoard(items: List<BoardItem>, spec: BoardSpec, onEdit: (BoardItem) -> Unit, onAdd: ((String) -> Unit)? = null) {
    val repo = App.instance.repo
    val toast = LocalToast.current
    val byId = items.associateBy { it.idea.id }
    val keys = spec.columns.map { it.key }.toSet()
    val columns = spec.columns.associate { c ->
        c.key to items.filter { val k = spec.keyOf(it); k == c.key || (k !in keys && c == spec.columns.first()) }
            .sortedWith(compareBy(byIdeaOrder) { it.idea })
    }
    // Card positions in root coordinates, to find where between the cards a drop lands.
    val cardBounds = remember { mutableStateMapOf<String, ClosedFloatingPointRange<Float>>() }

    fun move(id: String, to: String, dropY: Float?) {
        val item = byId[id] ?: return
        if (item.readOnly) return
        val status = spec.statusFor(item, to) ?: run {
            toast("В проекте «${item.projectName ?: "этой идеи"}» нет колонки «${spec.columns.firstOrNull { it.key == to }?.name ?: ""}»")
            return
        }
        val target = columns.getValue(to).filter { it.idea.id != id }
        val index = if (dropY == null) target.size
        else target.indexOfFirst { t -> cardBounds[t.idea.id]?.let { dropY < (it.start + it.endInclusive) / 2 } ?: false }.let { if (it < 0) target.size else it }
        val order = between(target.getOrNull(index - 1)?.idea?.sortOrder, target.getOrNull(index)?.idea?.sortOrder)
        repo.updateIdea(item.projectId, id) { it.copy(status = status, order = order) }
    }

    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(DSSpace.md),
    ) {
        spec.columns.forEach { col ->
            BoardColumn(
                col, spec.columns, columns.getValue(col.key), cardBounds, onEdit, onAdd,
                onDrop = { id, y -> move(id, col.key, y) },
                onMove = { id, to -> move(id, to, null) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BoardColumn(
    status: BoardCol,
    all: List<BoardCol>,
    cards: List<BoardItem>,
    cardBounds: MutableMap<String, ClosedFloatingPointRange<Float>>,
    onEdit: (BoardItem) -> Unit,
    onAdd: ((String) -> Unit)?,
    onDrop: (id: String, y: Float) -> Unit,
    onMove: (id: String, to: String) -> Unit,
) {
    var over by remember { mutableStateOf(false) }
    val target = remember(status.key) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) { over = true }
            override fun onExited(event: DragAndDropEvent) { over = false }
            override fun onEnded(event: DragAndDropEvent) { over = false }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                over = false
                val e = event.toAndroidDragEvent()
                val id = e.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString() ?: return false
                onDrop(id, e.y)
                return true
            }
        }
    }
    val shape = MaterialTheme.shapes.large
    Column(
        Modifier.width(272.dp).heightIn(min = 160.dp).clip(shape)
            .background(if (over) DS.colors.accentSubtle else DS.colors.fill.copy(alpha = 0.5f))
            .border(1.dp, if (over) DS.colors.accent else DS.colors.border, shape)
            .dragAndDropTarget(shouldStartDragAndDrop = { it.mimeTypes().contains(MIME) }, target = target)
            .padding(DSSpace.sm),
        verticalArrangement = Arrangement.spacedBy(DSSpace.sm),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = DSSpace.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpace.sm)) {
            StatusDot(status.tone)
            Text(status.name, style = MaterialTheme.typography.labelLarge, color = DS.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${cards.size}", style = MaterialTheme.typography.labelLarge, color = DS.colors.textTertiary, modifier = Modifier.weight(1f))
            if (onAdd != null) {
                tj.jasurbek.projectlib.ui.common.ContentIconButton(Icons.Rounded.Add, "Добавить идею в «${status.name}»") { onAdd(status.key) }
            } else Box(Modifier.size(36.dp))
        }
        if (cards.isEmpty()) {
            Text(
                "Перетащите сюда", style = MaterialTheme.typography.bodySmall, color = DS.colors.textTertiary,
                modifier = Modifier.fillMaxWidth().padding(vertical = DSSpace.xl), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        cards.forEach { item ->
            BoardCard(item, all.filter { it.key != status.key }, onClick = { onEdit(item) }, onMove = { to -> onMove(item.idea.id, to) },
                modifier = Modifier.onGloballyPositioned { c -> val b = c.boundsInRoot(); cardBounds[item.idea.id] = b.top..b.bottom })
        }
    }
}

@Suppress("DEPRECATION")
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun BoardCard(item: BoardItem, others: List<BoardCol>, onClick: () -> Unit, onMove: (String) -> Unit, modifier: Modifier = Modifier) {
    val idea = item.idea
    val haptics = LocalHapticFeedback.current
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier.fillMaxWidth().clip(shape).background(DS.colors.surface).border(1.dp, DS.colors.border, shape)
            // One gesture detector for both: tap opens the idea, long press starts dragging it.
            // (A separate .clickable consumes the press and the long press never fires.)
            // View-only projects: the card can't be opened for editing or moved.
            .then(if (item.readOnly) Modifier else Modifier.dragAndDropSource(block = {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        startTransfer(DragAndDropTransferData(ClipData(ClipDescription(idea.title, arrayOf(MIME)), ClipData.Item(idea.id))))
                    },
                )
            }))
            .semantics {
                if (item.readOnly) return@semantics
                role = Role.Button
                onClick(label = "Изменить") { onClick(); true }
                customActions = others.map { s ->
                    CustomAccessibilityAction("Переместить в «${s.name}»") { onMove(s.key); true }
                }
            }
            .padding(DSSpace.md),
        verticalArrangement = Arrangement.spacedBy(DSSpace.xs),
    ) {
        if (item.projectName != null) Text(item.projectName, style = MaterialTheme.typography.labelMedium, color = DS.colors.accentText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(idea.title, style = MaterialTheme.typography.bodyMedium, color = DS.colors.textPrimary, maxLines = 4, overflow = TextOverflow.Ellipsis)
        val atts = idea.attachments?.size ?: 0
        if (idea.dueState(item.columns) != null || idea.priority != Priority.Medium || atts > 0) {
            FlowRow(Modifier.padding(top = DSSpace.xs), horizontalArrangement = Arrangement.spacedBy(DSSpace.xs), verticalArrangement = Arrangement.spacedBy(DSSpace.xs),
                itemVerticalAlignment = Alignment.CenterVertically) {
                DueBadge(idea, item.columns)
                if (idea.priority != Priority.Medium) DSBadge(idea.priority.label, idea.priority.tone)
                if (atts > 0) Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AttachFile, "Вложения", tint = DS.colors.textTertiary, modifier = Modifier.size(14.dp))
                    Text("$atts", style = MaterialTheme.typography.labelMedium, color = DS.colors.textTertiary)
                }
            }
        }
    }
}

