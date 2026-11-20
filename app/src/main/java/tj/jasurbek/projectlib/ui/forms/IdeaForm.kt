package tj.jasurbek.projectlib.ui.forms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import tj.jasurbek.projectlib.data.Attachment
import tj.jasurbek.projectlib.data.Idea
import tj.jasurbek.projectlib.data.DEFAULT_STATUSES
import tj.jasurbek.projectlib.data.StatusDef
import tj.jasurbek.projectlib.data.of
import tj.jasurbek.projectlib.data.Priority
import tj.jasurbek.projectlib.data.Repository.IdeaInput
import tj.jasurbek.projectlib.data.addDaysISO
import tj.jasurbek.projectlib.ui.common.StatusDot
import tj.jasurbek.projectlib.ui.common.formatDue
import tj.jasurbek.projectlib.ui.designsystem.DS
import tj.jasurbek.projectlib.ui.designsystem.DSButton
import tj.jasurbek.projectlib.ui.designsystem.DSButtonVariant
import tj.jasurbek.projectlib.ui.designsystem.DSChip
import tj.jasurbek.projectlib.ui.designsystem.DSSpace
import tj.jasurbek.projectlib.ui.designsystem.DSTextField
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val DUE_PRESETS = listOf("Сегодня" to 0L, "Завтра" to 1L, "Через неделю" to 7L)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun IdeaFormSheet(
    initial: Idea?,
    onDismiss: () -> Unit,
    onSubmit: (IdeaInput) -> Unit,
    /** Status for a new idea (e.g. the kanban column it was added from). null = first column. */
    defaultStatus: String? = null,
    /** The project's columns. */
    columns: List<StatusDef> = DEFAULT_STATUSES,
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var status by remember { mutableStateOf(initial?.let { columns.of(it.status).id } ?: defaultStatus ?: columns.first().id) }
    var priority by remember { mutableStateOf(initial?.priority ?: Priority.Medium) }
    var dueDate by remember { mutableStateOf(initial?.dueDate ?: "") }
    var attachments by remember { mutableStateOf<List<Attachment>>(initial?.attachments ?: emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }

    FormSheet(
        title = if (initial != null) "Изменить идею" else "Новая идея",
        submitLabel = if (initial != null) "Сохранить" else "Добавить",
        onDismiss = onDismiss,
        onSubmit = {
            if (title.isBlank()) { error = "Опишите идею в двух словах"; false }
            else { onSubmit(IdeaInput(title.trim(), description.trim(), status, priority, dueDate, attachments)); true }
        },
    ) {
        DSTextField("Идея", title, { title = it; error = null }, placeholder = "Например, тёмная тема", error = error)
        TextArea("Подробности", description, { description = it }, placeholder = "Необязательно", maxLength = 1000)
        SelectField(
            "Статус", columns.of(status), columns,
            { if (it.excluded == true) "${it.label} · вне прогресса" else "${it.label} · ${it.percent}%" },
            { status = it.id }, leading = { StatusDot(it.dsTone) },
        )
        Column(verticalArrangement = Arrangement.spacedBy(DSSpace.sm)) {
            Text("Приоритет", style = MaterialTheme.typography.labelLarge, color = DS.colors.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(DSSpace.xs)) {
                Priority.entries.forEach { p -> DSChip(p.label, selected = priority == p, soft = true, onClick = { priority = p }) }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(DSSpace.sm)) {
            Text("Срок", style = MaterialTheme.typography.labelLarge, color = DS.colors.textSecondary)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(MaterialTheme.shapes.medium).background(DS.colors.surface)
                    .border(1.dp, DS.colors.borderStrong, MaterialTheme.shapes.medium).clickable { picking = true }.padding(horizontal = DSSpace.lg),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpace.sm),
            ) {
                Icon(Icons.Rounded.Event, null, tint = DS.colors.textTertiary)
                Text(
                    if (dueDate.isEmpty()) "Без срока" else formatDue(dueDate),
                    style = MaterialTheme.typography.bodyLarge, color = if (dueDate.isEmpty()) DS.colors.textTertiary else DS.colors.textPrimary,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(DSSpace.xs), verticalArrangement = Arrangement.spacedBy(DSSpace.xs)) {
                DUE_PRESETS.forEach { (label, days) ->
                    val d = addDaysISO(days)
                    DSChip(label, selected = dueDate == d, soft = true, onClick = { dueDate = d })
                }
                if (dueDate.isNotEmpty()) DSChip("Без срока", selected = false, onClick = { dueDate = "" })
            }
        }
        AttachmentEditor(attachments) { attachments = it }
    }

    if (picking) {
        val initialMillis = runCatching { LocalDate.parse(dueDate).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                DSButton("Готово", {
                    state.selectedDateMillis?.let { dueDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
                    picking = false
                }, variant = DSButtonVariant.Ghost)
            },
            dismissButton = { DSButton("Отмена", { picking = false }, variant = DSButtonVariant.Ghost) },
            shape = MaterialTheme.shapes.extraLarge,
            colors = DatePickerDefaults.colors(containerColor = DS.colors.surface),
        ) {
            DatePicker(state, colors = DatePickerDefaults.colors(containerColor = DS.colors.surface))
        }
    }
}
