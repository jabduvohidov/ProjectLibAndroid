package tj.jasurbek.projectlib.ui.forms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tj.jasurbek.projectlib.App
import tj.jasurbek.projectlib.data.ColumnTemplate
import tj.jasurbek.projectlib.data.MAX_STATUSES
import tj.jasurbek.projectlib.data.Project
import tj.jasurbek.projectlib.data.STANDARD_TEMPLATE
import tj.jasurbek.projectlib.data.StatusDef
import tj.jasurbek.projectlib.data.StatusTone
import tj.jasurbek.projectlib.data.adoptTemplate
import tj.jasurbek.projectlib.data.columnKey
import tj.jasurbek.projectlib.data.nearestStatus
import tj.jasurbek.projectlib.data.newColumnId
import tj.jasurbek.projectlib.data.of
import tj.jasurbek.projectlib.data.uniqueColumnName
import tj.jasurbek.projectlib.ui.common.LocalToast
import tj.jasurbek.projectlib.ui.common.toneColor
import tj.jasurbek.projectlib.ui.designsystem.DS
import tj.jasurbek.projectlib.ui.designsystem.DSBanner
import tj.jasurbek.projectlib.ui.designsystem.DSButton
import tj.jasurbek.projectlib.ui.designsystem.DSButtonSize
import tj.jasurbek.projectlib.ui.designsystem.DSButtonVariant
import tj.jasurbek.projectlib.ui.designsystem.DSSpace
import tj.jasurbek.projectlib.ui.designsystem.DSTone

private val TONE_LABEL = mapOf(
    StatusTone.Neutral to "Серый", StatusTone.Info to "Бирюзовый", StatusTone.Success to "Зелёный",
    StatusTone.Warning to "Оранжевый", StatusTone.Danger to "Красный",
)

/** A column while editing: the percent stays text so the field can be empty mid-typing. */
private data class ColDraft(val id: String, val name: String, val tone: StatusTone, val percent: String, val excluded: Boolean) {
    fun toDef() = StatusDef(id, name.trim(), tone, percent.toIntOrNull() ?: 0, if (excluded) true else null)
}

private fun StatusDef.toDraft() = ColDraft(id, name, tone, percent.toString(), excluded == true)

/**
 * Board columns of a project (port of web ColumnsDialog): name, colour, progress percent, «Вне прогресса», order;
 * add / delete (ideas of a deleted column move to a column you pick); save as / apply a template.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColumnsSheet(project: Project, onDismiss: () -> Unit) {
    val app = App.instance
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    val original = remember { project.columns }
    val draft = remember { mutableStateListOf(*original.map { it.toDraft() }.toTypedArray()) }
    val moveTo = remember { mutableStateMapOf<String, String>() }
    val errors = remember { mutableStateMapOf<String, String>() }
    var templates by remember { mutableStateOf<List<ColumnTemplate>?>(null) }
    var templateError by remember { mutableStateOf<String?>(null) }
    var picked by remember { mutableStateOf(STANDARD_TEMPLATE.id) }
    var saveName by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(reload) {
        try { templates = app.api.templates(); templateError = null }
        catch (_: Exception) { templates = emptyList(); templateError = "Шаблоны недоступны без связи с сервером" }
    }

    val counts = project.ideas.groupingBy { original.of(it.status).id }.eachCount()
    val defs = draft.map { it.toDef() }
    // Columns that disappear but still hold ideas → where those ideas go.
    val removed = original.filter { o -> draft.none { it.id == o.id } && (counts[o.id] ?: 0) > 0 }
    fun targetFor(old: StatusDef): String? =
        moveTo[old.id]?.takeIf { t -> draft.any { it.id == t } } ?: nearestStatus(old, defs)?.id

    fun set(i: Int, f: (ColDraft) -> ColDraft) { draft[i] = f(draft[i]) }
    fun move(i: Int, dir: Int) {
        val j = i + dir
        if (j !in draft.indices) return
        val a = draft[i]; draft[i] = draft[j]; draft[j] = a
    }

    fun validate(): Boolean {
        errors.clear()
        val seen = mutableSetOf<String>()
        for (s in draft) {
            val n = s.name.trim()
            val p = s.percent.toIntOrNull()
            when {
                n.isEmpty() -> errors[s.id] = "Введите название"
                columnKey(n) in seen -> errors[s.id] = "Такое название уже есть"
                p == null || p !in 0..100 -> errors[s.id] = "Процент — от 0 до 100"
            }
            seen += columnKey(n)
        }
        return errors.isEmpty() && draft.isNotEmpty()
    }

    val all = listOf(STANDARD_TEMPLATE) + (templates ?: emptyList())
    val pickedTemplate = all.firstOrNull { it.id == picked }

    FormSheet("Колонки и проценты", "Сохранить", onDismiss, onSubmit = {
        if (!validate()) return@FormSheet false
        val cols = draft.map { it.toDef() }
        val targets = removed.mapNotNull { old -> targetFor(old)?.let { old.id to it } }.toMap()
        val moved = app.repo.saveColumns(project.id, cols, targets)
        toast(if (moved > 0) "Колонки сохранены, перенесено идей: $moved" else "Колонки сохранены")
        true
    }) {
        Text(
            "Процент колонки — сколько идея в ней даёт к «Прогрессу идей». Прогресс проекта — средний процент его идей. " +
                "Колонки «вне прогресса» (например, «Отменено») не учитываются.",
            style = MaterialTheme.typography.bodyMedium, color = DS.colors.textSecondary,
        )

        Column(verticalArrangement = Arrangement.spacedBy(DSSpace.sm)) {
            draft.forEachIndexed { i, s ->
                val c = DS.colors
                Column(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(c.surface)
                        .border(1.dp, c.border, MaterialTheme.shapes.medium).padding(DSSpace.sm),
                    verticalArrangement = Arrangement.spacedBy(DSSpace.xs),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpace.xs)) {
                        Column {
                            SmallIcon(Icons.Rounded.ArrowUpward, "Выше: ${s.name}", enabled = i > 0) { move(i, -1) }
                            SmallIcon(Icons.Rounded.ArrowDownward, "Ниже: ${s.name}", enabled = i < draft.lastIndex) { move(i, 1) }
                        }
                        OutlinedTextField(
                            value = s.name, onValueChange = { v -> set(i) { it.copy(name = v.take(40)) } }, singleLine = true,
                            modifier = Modifier.weight(1f), isError = s.id in errors, shape = MaterialTheme.shapes.medium,
                            textStyle = MaterialTheme.typography.bodyLarge, colors = fieldColors(),
                        )
                        OutlinedTextField(
                            value = s.percent, onValueChange = { v -> set(i) { it.copy(percent = v.filter(Char::isDigit).take(3)) } },
                            singleLine = true, enabled = !s.excluded, modifier = Modifier.width(96.dp).alpha(if (s.excluded) 0.4f else 1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = MaterialTheme.shapes.medium,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.End), colors = fieldColors(),
                            suffix = { Text("%", color = c.textSecondary) },
                        )
                        IconButton(onClick = { draft.removeAt(i) }, enabled = draft.size > 1) {
                            Icon(Icons.Rounded.Delete, "Удалить колонку «${s.name}»",
                                tint = if (draft.size > 1) c.textTertiary else c.textTertiary.copy(alpha = 0.3f))
                        }
                    }
                    FlowRow(
                        Modifier.padding(start = 26.dp), verticalArrangement = Arrangement.spacedBy(DSSpace.xxs),
                        horizontalArrangement = Arrangement.spacedBy(DSSpace.md), itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(DSSpace.xxs)) {
                            StatusTone.entries.forEach { t ->
                                val on = s.tone == t
                                Box(
                                    Modifier.size(32.dp).clip(CircleShape)
                                        .background(if (on) c.fill else androidx.compose.ui.graphics.Color.Transparent)
                                        .then(if (on) Modifier.border(1.dp, c.borderStrong, CircleShape) else Modifier)
                                        .clickable(role = Role.RadioButton, onClickLabel = TONE_LABEL[t]) { set(i) { it.copy(tone = t) } },
                                    contentAlignment = Alignment.Center,
                                ) { Box(Modifier.size(14.dp).clip(CircleShape).background(toneColor(t.ds))) }
                            }
                        }
                        Row(
                            Modifier.clip(MaterialTheme.shapes.small).clickable { set(i) { it.copy(excluded = !it.excluded) } }.padding(end = DSSpace.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(s.excluded, { v -> set(i) { it.copy(excluded = v) } },
                                colors = CheckboxDefaults.colors(checkedColor = c.accent, uncheckedColor = c.borderStrong))
                            Text("Вне прогресса", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                        }
                        val n = counts[s.id] ?: 0
                        if (n > 0) Text("идей: $n", style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
                        errors[s.id]?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.danger) }
                    }
                }
            }
        }
        DSButton(
            "Добавить колонку", { draft += ColDraft(newColumnId(), uniqueColumnName(defs, "Новая колонка"), StatusTone.Neutral, "0", false) },
            variant = DSButtonVariant.Outline, size = DSButtonSize.Sm, leadingIcon = Icons.Rounded.Add, enabled = draft.size < MAX_STATUSES,
        )

        if (removed.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(DS.colors.warningSubtle).padding(DSSpace.md),
                verticalArrangement = Arrangement.spacedBy(DSSpace.sm),
            ) {
                Text("Куда перенести идеи из удалённых колонок", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = DS.colors.textPrimary)
                removed.forEach { old ->
                    val cur = targetFor(old)
                    SelectField(
                        "«${old.name}» (${counts[old.id]})", draft.firstOrNull { it.id == cur } ?: draft.first(), draft.toList(),
                        { it.name.ifBlank { "Без названия" } }, { moveTo[old.id] = it.id },
                    )
                }
            }
        }

        // Templates
        Column(verticalArrangement = Arrangement.spacedBy(DSSpace.sm)) {
            androidx.compose.material3.HorizontalDivider(color = DS.colors.border)
            Text("Шаблоны колонок", style = MaterialTheme.typography.titleSmall, color = DS.colors.textSecondary,
                modifier = Modifier.padding(top = DSSpace.sm))
            templateError?.let { DSBanner(it, tone = DSTone.Info) }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(DSSpace.sm)) {
                SelectField(
                    "Шаблон", pickedTemplate ?: STANDARD_TEMPLATE, all, { "${it.name} · ${it.statuses.size} кол." }, { picked = it.id },
                    modifier = Modifier.weight(1f),
                )
                DSButton("Применить", {
                    val t = pickedTemplate ?: return@DSButton
                    val next = adoptTemplate(draft.map { it.toDef() }, t.statuses, ::newColumnId)
                    draft.clear(); draft.addAll(next.map { it.toDraft() }); errors.clear()
                    toast("Колонки из шаблона «${t.name}» — проверьте и сохраните")
                }, variant = DSButtonVariant.Outline)
            }
            pickedTemplate?.let { t ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t.statuses.joinToString(" · ") { "${it.name} ${if (it.excluded == true) "(вне)" else "${it.percent}%"}" },
                        style = MaterialTheme.typography.bodySmall, color = DS.colors.textTertiary, modifier = Modifier.weight(1f))
                    if (t.canDelete) IconButton(onClick = {
                        scope.launch {
                            try { app.api.deleteTemplate(t.id); toast("Шаблон «${t.name}» удалён"); picked = STANDARD_TEMPLATE.id; reload++ }
                            catch (e: Exception) { toast(e.message ?: "Ошибка") }
                        }
                    }) { Icon(Icons.Rounded.Delete, "Удалить шаблон «${t.name}»", tint = DS.colors.danger) }
                }
            }
            val name = saveName
            if (name == null) {
                DSButton("Сохранить эти колонки как шаблон", { saveName = project.name }, variant = DSButtonVariant.Ghost, size = DSButtonSize.Sm,
                    leadingIcon = Icons.Rounded.BookmarkAdd, enabled = templateError == null)
            } else {
                FormField("Название шаблона", name, { saveName = it.take(60) })
                Row(horizontalArrangement = Arrangement.spacedBy(DSSpace.sm)) {
                    DSButton("Сохранить шаблон", {
                        if (!validate() || name.isBlank()) return@DSButton
                        scope.launch {
                            try {
                                app.api.createTemplate(name.trim(), draft.map { it.toDef() })
                                toast("Шаблон «${name.trim()}» сохранён"); saveName = null; reload++
                            } catch (e: Exception) { toast(e.message ?: "Ошибка") }
                        }
                    }, variant = DSButtonVariant.Outline, enabled = name.isNotBlank())
                    DSButton("Отмена", { saveName = null }, variant = DSButtonVariant.Ghost)
                }
            }
        }
    }
}

@Composable
private fun SmallIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(22.dp).clip(MaterialTheme.shapes.small).clickable(enabled = enabled, onClick = onClick).heightIn(min = 22.dp),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = DS.colors.textTertiary.copy(alpha = if (enabled) 1f else 0.3f), modifier = Modifier.size(14.dp)) }
}
