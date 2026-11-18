package tj.jasurbek.projectlib.ui.forms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tj.jasurbek.projectlib.ui.designsystem.DS
import tj.jasurbek.projectlib.ui.designsystem.DSButton
import tj.jasurbek.projectlib.ui.designsystem.DSButtonVariant
import tj.jasurbek.projectlib.ui.designsystem.DSSize
import tj.jasurbek.projectlib.ui.designsystem.DSSpace

/**
 * A form in a bottom sheet (web: Dialog → bottom sheet on mobile): title, scrolling body,
 * Cancel + one accent submit button. [onSubmit] returns true when the form is valid and the sheet may close.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormSheet(
    title: String,
    submitLabel: String,
    onDismiss: () -> Unit,
    onSubmit: () -> Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun close() = scope.launch { state.hide() }.invokeOnCompletion { onDismiss() }
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = state,
        containerColor = DS.colors.bg, shape = MaterialTheme.shapes.extraLarge.let { androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp) },
    ) {
        Column(Modifier.fillMaxWidth().imePadding()) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = DS.colors.textPrimary, modifier = Modifier.padding(horizontal = DSSpace.xl))
            Spacer(Modifier.size(DSSpace.lg))
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = DSSpace.xl),
                verticalArrangement = Arrangement.spacedBy(DSSpace.lg), content = content,
            )
            HorizontalDivider(Modifier.padding(top = DSSpace.lg), color = DS.colors.border)
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = DSSpace.xl, vertical = DSSpace.md),
                horizontalArrangement = Arrangement.spacedBy(DSSpace.sm),
            ) {
                DSButton("Отмена", { close() }, Modifier.weight(1f), variant = DSButtonVariant.Secondary)
                DSButton(submitLabel, { if (onSubmit()) close() }, Modifier.weight(1f))
            }
        }
    }
}

/** Multi-line text input with a label above (web: Textarea). */
@Composable
fun TextArea(label: String, value: String, onChange: (String) -> Unit, placeholder: String = "", maxLength: Int = 5000, hint: String? = null, error: String? = null, mono: Boolean = false) {
    val c = DS.colors
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DSSpace.sm)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
        OutlinedTextField(
            value = value, onValueChange = { onChange(it.take(maxLength)) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
            placeholder = { Text(placeholder, color = c.textTertiary) },
            isError = error != null,
            shape = MaterialTheme.shapes.medium,
            textStyle = if (mono) MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodyLarge,
            colors = fieldColors(),
        )
        when {
            error != null -> ErrorText(error)
            hint != null -> Text(hint, style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
        }
    }
}

@Composable
fun ErrorText(error: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpace.xs)) {
        Icon(Icons.Rounded.Error, null, tint = DS.colors.danger, modifier = Modifier.size(14.dp))
        Text(error, style = MaterialTheme.typography.bodySmall, color = DS.colors.danger)
    }
}

@Composable
fun fieldColors() = DS.colors.let { c ->
    OutlinedTextFieldDefaults.colors(
        focusedContainerColor = c.surface, unfocusedContainerColor = c.surface, errorContainerColor = c.surface,
        focusedBorderColor = c.accent, unfocusedBorderColor = c.borderStrong, errorBorderColor = c.danger,
        cursorColor = c.accent, focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary,
    )
}

/** Single-line field with a label; keyboard options for URLs etc. (the kit's DSTextField + options). */
@Composable
fun FormField(
    label: String, value: String, onChange: (String) -> Unit, placeholder: String = "", hint: String? = null, error: String? = null,
    keyboard: KeyboardOptions = KeyboardOptions.Default, mono: Boolean = false,
) {
    val c = DS.colors
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DSSpace.sm)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
        OutlinedTextField(
            value = value, onValueChange = onChange, singleLine = true,
            modifier = Modifier.fillMaxWidth().heightIn(min = DSSize.controlMd),
            placeholder = { Text(placeholder, color = c.textTertiary, maxLines = 1) },
            isError = error != null, keyboardOptions = keyboard,
            shape = MaterialTheme.shapes.medium,
            textStyle = if (mono) MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodyLarge,
            colors = fieldColors(),
        )
        when {
            error != null -> ErrorText(error)
            hint != null -> Text(hint, style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
        }
    }
}

/** A select: label above, current value in a field-like box, options in a menu. */
@Composable
fun <T> SelectField(
    label: String, value: T, options: List<T>, optionLabel: (T) -> String, onSelect: (T) -> Unit,
    modifier: Modifier = Modifier, leading: (@Composable (T) -> Unit)? = null,
) {
    val c = DS.colors
    var open by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DSSpace.sm)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
        Box {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(MaterialTheme.shapes.medium).background(c.surface)
                    .border(1.dp, c.borderStrong, MaterialTheme.shapes.medium).clickable { open = true }.padding(horizontal = DSSpace.lg),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpace.sm),
            ) {
                leading?.invoke(value)
                Text(optionLabel(value), style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.UnfoldMore, null, tint = c.textTertiary)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = MaterialTheme.shapes.medium) {
                options.forEach { o ->
                    DropdownMenuItem(
                        text = { Text(optionLabel(o)) },
                        leadingIcon = leading?.let { { it(o) } },
                        trailingIcon = if (o == value) ({ Icon(Icons.Rounded.Check, null, tint = c.accentText) }) else null,
                        onClick = { open = false; onSelect(o) },
                    )
                }
            }
        }
    }
}
