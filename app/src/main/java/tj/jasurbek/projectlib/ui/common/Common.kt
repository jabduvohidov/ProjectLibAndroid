package tj.jasurbek.projectlib.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.widthIn
import tj.jasurbek.projectlib.ui.designsystem.glass.dsGlass
import tj.jasurbek.projectlib.ui.designsystem.glass.DSGlassTint
import tj.jasurbek.projectlib.ui.designsystem.glass.dsControlSurface
import tj.jasurbek.projectlib.ui.designsystem.glass.dsGlassPress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import tj.jasurbek.projectlib.data.DueState
import tj.jasurbek.projectlib.data.Idea
import tj.jasurbek.projectlib.data.DEFAULT_STATUSES
import tj.jasurbek.projectlib.data.StatusDef
import tj.jasurbek.projectlib.data.of
import tj.jasurbek.projectlib.data.ProjectStatus
import tj.jasurbek.projectlib.data.SyncState
import tj.jasurbek.projectlib.data.SyncStatus
import tj.jasurbek.projectlib.data.dueState
import tj.jasurbek.projectlib.ui.designsystem.DS
import tj.jasurbek.projectlib.ui.designsystem.DSBadge
import tj.jasurbek.projectlib.ui.designsystem.DSButton
import tj.jasurbek.projectlib.ui.designsystem.DSButtonVariant
import tj.jasurbek.projectlib.ui.designsystem.DSSize
import tj.jasurbek.projectlib.ui.designsystem.DSSpace
import tj.jasurbek.projectlib.ui.designsystem.DSTone
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/* ---------------- toast (snackbar) ---------------- */

class Toaster(private val host: SnackbarHostState, private val scope: CoroutineScope) {
    operator fun invoke(message: String) {
        scope.launch { host.currentSnackbarData?.dismiss(); host.showSnackbar(message) }
    }
}

val LocalToast = staticCompositionLocalOf<Toaster> { error("No Toaster") }

/* ---------------- formatting ---------------- */

private val RU = Locale.forLanguageTag("ru")

fun formatDate(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMMM yyyy", RU))

fun formatTime(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm:ss", RU))

/** "2026-11-20" → "20 нояб" (year added when it isn't this one). */
fun formatDue(iso: String): String {
    val d = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return iso
    val pattern = if (d.year == LocalDate.now().year) "d MMM" else "d MMM yyyy"
    return d.format(DateTimeFormatter.ofPattern(pattern, RU)).replace(".", "")
}

/** Russian plural: 1 изменение, 2 изменения, 5 изменений. */
fun plural(n: Int, one: String, few: String, many: String): String {
    val m10 = n % 10
    val m100 = n % 100
    return when {
        m10 == 1 && m100 != 11 -> one
        m10 in 2..4 && (m100 < 12 || m100 > 14) -> few
        else -> many
    }
}

/* ---------------- status ---------------- */

@Composable
fun toneColor(tone: DSTone): Color = when (tone) {
    DSTone.Neutral -> DS.colors.textTertiary
    DSTone.Info -> DS.colors.accent
    DSTone.Success -> DS.colors.success
    DSTone.Warning -> DS.colors.warning
    DSTone.Danger -> DS.colors.danger
}

@Composable
fun StatusDot(tone: DSTone) {
    Box(Modifier.size(8.dp).clip(CircleShape).background(toneColor(tone)))
}

@Composable
fun ProjectStatusBadge(status: ProjectStatus) = DSBadge(status.label, status.tone, dot = true)

/** Idea status badge that doubles as a one-tap status switcher. */
@Composable
fun IdeaStatusPicker(columns: List<StatusDef>, status: String, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val cur = columns.of(status)
    Box {
        Row(
            Modifier.clip(CircleShape).clickable { open = true }.semantics { contentDescription = "Статус: ${cur.label}. Изменить" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToneBadge(cur.label, cur.dsTone, dot = true, trailing = Icons.Rounded.KeyboardArrowDown)
        }
        GlassDropdown(expanded = open, onDismissRequest = { open = false }, shape = MaterialTheme.shapes.medium) {
            columns.forEach { s ->
                DropdownMenuItem(
                    text = { Text(if (s.excluded == true) "${s.label} · вне прогресса" else "${s.label} · ${s.percent}%") },
                    leadingIcon = { StatusDot(s.dsTone) },
                    trailingIcon = if (s.id == cur.id) ({ Icon(Icons.Rounded.Check, null, tint = DS.colors.accentText) }) else null,
                    onClick = { open = false; onChange(s.id) },
                )
            }
        }
    }
}

/** DSBadge with an optional leading / trailing icon (the kit badge has text + dot only). */
@Composable
fun ToneBadge(text: String, tone: DSTone, dot: Boolean = false, leading: ImageVector? = null, trailing: ImageVector? = null) {
    val c = DS.colors
    val (fg, bg) = when (tone) {
        DSTone.Neutral -> c.textSecondary to c.fill
        DSTone.Info -> c.accentText to c.accentSubtle
        DSTone.Success -> c.success to c.successSubtle
        DSTone.Warning -> c.warning to c.warningSubtle
        DSTone.Danger -> c.danger to c.dangerSubtle
    }
    Row(
        Modifier.heightIn(min = 24.dp).clip(CircleShape).background(bg).padding(horizontal = DSSpace.sm),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpace.xs),
    ) {
        if (dot) Box(Modifier.size(6.dp).clip(CircleShape).background(fg))
        if (leading != null) Icon(leading, null, tint = fg, modifier = Modifier.size(12.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg)
        if (trailing != null) Icon(trailing, null, tint = fg, modifier = Modifier.size(14.dp))
    }
}

/** Deadline chip: overdue (danger) · today (warning) · within 3 days (info) · later (neutral). Closed ideas show none. */
@Composable
fun DueBadge(idea: Idea, columns: List<StatusDef> = DEFAULT_STATUSES) {
    val state = idea.dueState(columns) ?: return
    val date = formatDue(idea.dueDate ?: return)
    val (tone, label) = when (state) {
        DueState.Overdue -> DSTone.Danger to "Просрочено · $date"
        DueState.Today -> DSTone.Warning to "Сегодня"
        DueState.Soon -> DSTone.Info to "до $date"
        DueState.Later -> DSTone.Neutral to "до $date"
    }
    Box(Modifier.semantics { contentDescription = "Срок: $label" }) { ToneBadge(label, tone, leading = Icons.Rounded.Event) }
}

@Composable
fun TagChip(tag: String) {
    Text(
        "#$tag", style = MaterialTheme.typography.labelMedium, color = DS.colors.textSecondary,
        modifier = Modifier.clip(MaterialTheme.shapes.extraSmall).background(DS.colors.fill).padding(horizontal = DSSpace.sm, vertical = 2.dp),
    )
}

/* ---------------- search ---------------- */

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val c = DS.colors
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true,
        modifier = modifier.fillMaxWidth().heightIn(min = DSSize.controlMd),
        placeholder = { Text(placeholder, color = c.textTertiary) },
        leadingIcon = { Icon(Icons.Rounded.Search, null, tint = c.textTertiary) },
        trailingIcon = if (value.isNotEmpty()) ({ IconButton(onClick = { onChange("") }) { Icon(Icons.Rounded.Cancel, "Очистить", tint = c.textTertiary) } }) else null,
        shape = CircleShape,
        textStyle = MaterialTheme.typography.bodyLarge,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = c.surface, unfocusedContainerColor = c.surface,
            focusedBorderColor = c.accent, unfocusedBorderColor = c.border, cursorColor = c.accent,
            focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary,
        ),
    )
}

/** Horizontally scrolling chip row (filters). */
@Composable
fun ChipRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(DSSpace.sm), verticalAlignment = Alignment.CenterVertically, content = content,
    )
}

/** Filter chip with an optional count (like the web chips "В процессе 3"). */
@Composable
fun CountChip(text: String, selected: Boolean, count: Int? = null, danger: Boolean = false, onClick: () -> Unit) {
    val c = DS.colors
    val fg = when { danger -> c.danger; selected -> c.accentText; else -> c.textPrimary }
    Row(
        Modifier.heightIn(min = DSSize.controlSm)
            .dsControlSurface(CircleShape, if (selected) DSGlassTint.AccentSoft else DSGlassTint.Regular) { clip(CircleShape).background(if (selected) c.accentSubtle else c.fill) }
            .clickable(onClick = onClick).padding(horizontal = DSSpace.md),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg)
        if (count != null) Text("$count", style = MaterialTheme.typography.labelLarge, color = fg.copy(alpha = 0.6f))
    }
}

/* ---------------- sync indicator ---------------- */

data class SyncInfo(val icon: ImageVector, val label: String, val tone: DSTone)

fun describeSync(s: SyncStatus): SyncInfo {
    val queued = if (s.pending > 0) " · ${s.pending} ${plural(s.pending, "изменение", "изменения", "изменений")} в очереди" else ""
    return when (s.state) {
        SyncState.Syncing -> SyncInfo(Icons.Rounded.Sync, "Синхронизация…", DSTone.Neutral)
        SyncState.Synced -> SyncInfo(Icons.Rounded.Cloud, "Синхронизировано", DSTone.Success)
        SyncState.Offline -> SyncInfo(Icons.Rounded.CloudOff, "Офлайн$queued", DSTone.Warning)
        SyncState.Unauthorized -> SyncInfo(Icons.Rounded.Key, "Войти$queued", DSTone.Danger)
        SyncState.Error -> SyncInfo(Icons.Rounded.Warning, "Ошибка синхронизации", DSTone.Danger)
        SyncState.NotConfigured -> SyncInfo(Icons.Rounded.CloudOff, "Без сервера", DSTone.Neutral)
    }
}

@Composable
fun SyncIcon(info: SyncInfo, syncing: Boolean, tint: Color, size: androidx.compose.ui.unit.Dp = 16.dp) {
    val angle = if (syncing) {
        val t = rememberInfiniteTransition(label = "spin")
        t.animateFloat(0f, -360f, infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart), label = "a").value
    } else 0f
    Icon(info.icon, null, tint = tint, modifier = Modifier.size(size).rotate(angle))
}

/**
 * Top-bar glass capsule: current sync state; tap = sync now (or open the account settings when sign-in is needed).
 * [compact]: icon only (scrolled bar, pushed screens) unless something needs attention.
 */
@Composable
fun SyncIndicator(status: SyncStatus, compact: Boolean = false, onClick: () -> Unit) {
    val info = describeSync(status)
    val color = when (info.tone) {
        DSTone.Success -> DS.colors.success
        DSTone.Warning -> DS.colors.warning
        DSTone.Danger -> DS.colors.danger
        else -> DS.colors.textSecondary
    }
    val showLabel = status.state != SyncState.Synced && !(compact && status.state == SyncState.Syncing)
    Row(
        Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp).dsGlass(CircleShape).dsGlassPress().clickable(onClick = onClick)
            .padding(horizontal = if (showLabel) DSSpace.lg else DSSpace.md)
            .semantics { contentDescription = info.label },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpace.xs, Alignment.CenterHorizontally),
    ) {
        SyncIcon(info, status.state == SyncState.Syncing, color, 20.dp)
        if (showLabel) {
            Text(info.label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = color, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.dp))
        }
    }
}

/* ---------------- dialogs & groups ---------------- */

/** Destructive confirmation (danger button + cancel). */
@Composable
fun ConfirmDialog(title: String, text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = DS.colors.surface,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(text, style = MaterialTheme.typography.bodyLarge, color = DS.colors.textSecondary) },
        confirmButton = { DSButton(confirmLabel, { onConfirm(); onDismiss() }, variant = DSButtonVariant.Destructive) },
        dismissButton = { DSButton("Отмена", onDismiss, variant = DSButtonVariant.Secondary) },
    )
}

/** A rounded list group (web: ul.divide-y rounded-lg border bg-surface). */
@Composable
fun ListGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = MaterialTheme.shapes.large
    Column(modifier.fillMaxWidth().clip(shape).background(DS.colors.surface).border(1.dp, DS.colors.border, shape)) { content() }
}

@Composable
fun GroupDivider() = HorizontalDivider(thickness = 1.dp, color = DS.colors.border)

@Composable
fun ScreenTitleSpacer() = Spacer(Modifier.width(0.dp))

val ScreenPadding = PaddingValues(horizontal = DSSpace.lg)
