// Universal Design System — Jetpack Compose component library (Material 3 1.3+).
// Depends only on DesignTokens.kt. Change `package` to your app's package.
// Gradle: material3 >= 1.3, material-icons-extended (Rounded icons).
package tj.jasurbek.projectlib.ui.designsystem

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

// ---------------------------------------------------------------- Buttons

enum class DSButtonVariant { Primary, Secondary, Outline, Ghost, Destructive, DestructiveSoft }
enum class DSButtonSize(val height: Dp, val hPad: Dp) { Sm(DSSize.controlSm, DSSpace.md), Md(DSSize.controlMd, DSSpace.lg), Lg(DSSize.controlLg, DSSpace.xl) }

@Composable
fun DSButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: DSButtonVariant = DSButtonVariant.Primary,
    size: DSButtonSize = DSButtonSize.Md,
    fullWidth: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    val c = DS.colors
    val (container, content) = when (variant) {
        DSButtonVariant.Primary -> c.accent to c.textOnAccent
        DSButtonVariant.Secondary -> c.fill to c.textPrimary
        DSButtonVariant.Outline -> Color.Transparent to c.textPrimary
        DSButtonVariant.Ghost -> Color.Transparent to c.accentText
        DSButtonVariant.Destructive -> c.dangerFill to c.textOnDanger
        DSButtonVariant.DestructiveSoft -> c.dangerSubtle to c.danger
    }
    val textStyle = when (size) {
        DSButtonSize.Sm -> MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
        DSButtonSize.Md -> MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
        DSButtonSize.Lg -> MaterialTheme.typography.titleMedium
    }
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.then(if (fullWidth) Modifier.fillMaxWidth() else Modifier).heightIn(min = size.height),
        shape = CircleShape, // capsule controls — same geometry as iOS/macOS 27 and web
        colors = ButtonDefaults.buttonColors(
            containerColor = container, contentColor = content,
            disabledContainerColor = container.copy(alpha = container.alpha * 0.4f), disabledContentColor = content.copy(alpha = 0.4f),
        ),
        border = if (variant == DSButtonVariant.Outline) BorderStroke(1.dp, c.borderStrong) else null,
        elevation = null,
        contentPadding = PaddingValues(horizontal = size.hPad),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Row(Modifier.alpha(if (loading) 0f else 1f), verticalAlignment = Alignment.CenterVertically) {
                if (leadingIcon != null) {
                    Icon(leadingIcon, null, Modifier.size(20.dp)); Spacer(Modifier.width(DSSpace.sm))
                }
                Text(text, style = textStyle)
            }
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = content)
        }
    }
}

// ---------------------------------------------------------------- Surfaces

@Composable
fun DSCard(modifier: Modifier = Modifier, padding: Dp = DSSpace.lg, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    // Follow the app theme (may differ from the system one), not isSystemInDarkTheme().
    val dark = DS.colors == DarkDSColors
    val shape = MaterialTheme.shapes.large
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(DS.colors.surface)
            .then(if (dark) Modifier.border(1.dp, DS.colors.border, shape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

@Composable
fun DSSectionHeader(title: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = DSSpace.xs), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = DS.colors.textSecondary, modifier = Modifier.weight(1f).semantics { heading() })
        if (actionLabel != null && onAction != null) {
            Text(actionLabel, style = MaterialTheme.typography.labelLarge, color = DS.colors.accentText,
                modifier = Modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onAction).padding(DSSpace.xs))
        }
    }
}

// ---------------------------------------------------------------- Text field

@Composable
fun DSTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    hint: String? = null,
    error: String? = null,
    leadingIcon: ImageVector? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
) {
    val c = DS.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DSSpace.sm)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
        OutlinedTextField(
            value = value, onValueChange = onValueChange, singleLine = singleLine,
            modifier = Modifier.fillMaxWidth().heightIn(min = DSSize.controlMd),
            placeholder = { Text(placeholder, color = c.textTertiary) },
            leadingIcon = leadingIcon?.let { { Icon(it, null, tint = c.textTertiary) } },
            trailingIcon = if (value.isNotEmpty()) { { IconButton(onClick = { onValueChange("") }) { Icon(Icons.Rounded.Cancel, "Clear", tint = c.textTertiary) } } } else null,
            isError = error != null,
            keyboardOptions = keyboardOptions,
            shape = MaterialTheme.shapes.medium,
            textStyle = MaterialTheme.typography.bodyLarge,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = c.surface, unfocusedContainerColor = c.surface, errorContainerColor = c.surface,
                focusedBorderColor = c.accent, unfocusedBorderColor = c.borderStrong, errorBorderColor = c.danger,
                cursorColor = c.accent, focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary,
            ),
        )
        when {
            error != null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpace.xs)) {
                Icon(Icons.Rounded.Error, null, tint = c.danger, modifier = Modifier.size(14.dp))
                Text(error, style = MaterialTheme.typography.bodySmall, color = c.danger)
            }
            hint != null -> Text(hint, style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
        }
    }
}

// ---------------------------------------------------------------- Status

enum class DSTone { Neutral, Info, Success, Warning, Danger }

private data class ToneColors(val fg: Color, val bg: Color, val icon: ImageVector)

@Composable
private fun toneColors(t: DSTone): ToneColors {
    val c = DS.colors
    return when (t) {
        DSTone.Neutral -> ToneColors(c.textSecondary, c.fill, Icons.Rounded.Info)
        DSTone.Info -> ToneColors(c.accentText, c.accentSubtle, Icons.Rounded.Info)
        DSTone.Success -> ToneColors(c.success, c.successSubtle, Icons.Rounded.CheckCircle)
        DSTone.Warning -> ToneColors(c.warning, c.warningSubtle, Icons.Rounded.Warning)
        DSTone.Danger -> ToneColors(c.danger, c.dangerSubtle, Icons.Rounded.Error)
    }
}

@Composable
fun DSBadge(text: String, tone: DSTone = DSTone.Neutral, dot: Boolean = false) {
    val t = toneColors(tone)
    Row(
        Modifier.heightIn(min = 22.dp).clip(CircleShape).background(t.bg).padding(horizontal = DSSpace.sm),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpace.xs),
    ) {
        if (dot) Box(Modifier.size(6.dp).clip(CircleShape).background(t.fg))
        Text(text, style = MaterialTheme.typography.labelMedium, color = t.fg)
    }
}

@Composable
fun DSBanner(title: String, modifier: Modifier = Modifier, tone: DSTone = DSTone.Info, message: String? = null, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    val t = toneColors(tone)
    Row(modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(t.bg).padding(DSSpace.lg), horizontalArrangement = Arrangement.spacedBy(DSSpace.md)) {
        Icon(t.icon, null, tint = t.fg, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DSSpace.xxs)) {
            Text(title, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = DS.colors.textPrimary)
            if (message != null) Text(message, style = MaterialTheme.typography.bodySmall, color = DS.colors.textSecondary)
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(DSSpace.sm))
                DSButton(actionLabel, onAction, variant = DSButtonVariant.Secondary, size = DSButtonSize.Sm)
            }
        }
    }
}

// ---------------------------------------------------------------- Selection

/** Segmented control for 2–4 options (filters). */
@Composable
fun <T> DSSegmentedControl(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val c = DS.colors
    Row(modifier.fillMaxWidth().heightIn(min = DSSize.minTouchAndroid).clip(CircleShape).background(c.fill).padding(DSSpace.xxs)) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier.weight(1f).heightIn(min = 40.dp).clip(CircleShape)
                    .background(if (on) c.surfaceRaised else Color.Transparent)
                    .then(if (on) Modifier.border(1.dp, c.border, CircleShape) else Modifier)
                    .clickable(role = Role.RadioButton) { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) { Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) c.textPrimary else c.textSecondary) }
        }
    }
}

@Composable
fun DSChip(text: String, selected: Boolean, onClick: () -> Unit, soft: Boolean = false) {
    val c = DS.colors
    val bg = if (!selected) c.fill else if (soft) c.accentSubtle else c.accent
    val fg = if (!selected) c.textPrimary else if (soft) c.accentText else c.textOnAccent
    Box(
        Modifier.heightIn(min = DSSize.controlSm).clip(CircleShape).background(bg).clickable(onClick = onClick).padding(horizontal = DSSpace.md),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.labelLarge, color = fg) }
}

@Composable
fun DSProgressBar(progress: Float, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { progress }, modifier = modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
        color = DS.colors.accent, trackColor = DS.colors.fill, gapSize = 0.dp, drawStopIndicator = {},
    )
}

@Composable
fun DSStepDots(count: Int, active: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(DSSpace.xs)) {
        repeat(count) { i ->
            val w by animateDpAsState(if (i == active) 24.dp else 8.dp, tween(DSMotion.baseMs, easing = DSMotion.standardEasing), label = "dot")
            Box(Modifier.height(8.dp).width(w).clip(CircleShape).background(if (i == active) DS.colors.accent else DS.colors.borderStrong))
        }
    }
}

// ---------------------------------------------------------------- Lists

@Composable
fun DSIconBox(icon: ImageVector, tone: DSTone = DSTone.Info, size: Dp = 40.dp) {
    val t = toneColors(tone)
    Box(Modifier.size(size).clip(MaterialTheme.shapes.small).background(t.bg), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = t.fg, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun DSListRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTone: DSTone = DSTone.Info,
    subtitle: String? = null,
    destructive: Boolean = false,
    chevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = DS.colors
    Row(
        modifier.fillMaxWidth().heightIn(min = DSSize.listRow)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = DSSpace.lg, vertical = DSSpace.sm),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpace.md),
    ) {
        if (icon != null) DSIconBox(icon, if (destructive) DSTone.Danger else iconTone)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (destructive) c.danger else c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke()
        if (chevron) Icon(Icons.Rounded.ChevronRight, null, tint = c.textTertiary)
    }
}

/** Divider inset to the row's text start (16 padding + 40 icon + 12 gap). */
@Composable
fun DSDivider(inset: Dp = DSSpace.lg + 40.dp + DSSpace.md) {
    HorizontalDivider(Modifier.padding(start = inset), thickness = 1.dp, color = DS.colors.border)
}

@Composable
fun DSAvatar(name: String, size: Dp = DSSize.avatarMd) {
    val initials = name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
    Box(Modifier.size(size).clip(CircleShape).background(DS.colors.accentSubtle), contentAlignment = Alignment.Center) {
        Text(initials, color = DS.colors.accentText, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
    }
}

// ---------------------------------------------------------------- Money

object DSMoney {
    private val fmt = DecimalFormat("#,##0.00", DecimalFormatSymbols().apply { groupingSeparator = ' '; decimalSeparator = ',' })
    /** "12 500,00"; signed → "+8 400,00" / "−28,00" (true minus sign). */
    fun format(value: BigDecimal, signed: Boolean = false): String {
        val n = fmt.format(value.abs().setScale(2, RoundingMode.HALF_UP))
        return when { value.signum() < 0 -> "−$n"; signed && value.signum() > 0 -> "+$n"; else -> n }
    }
}

@Composable
fun DSAmount(value: BigDecimal, currency: String = "TJS", label: String? = null, large: Boolean = true, signed: Boolean = false, hideable: Boolean = false) {
    var hidden by rememberSaveable { mutableStateOf(false) }
    val c = DS.colors
    val numStyle = (if (large) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleMedium).copy(fontFeatureSettings = "tnum")
    Column(verticalArrangement = Arrangement.spacedBy(DSSpace.xxs)) {
        if (label != null) Text(label, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(DSSpace.xs)) {
            Text(if (hidden) "••••" else DSMoney.format(value, signed), style = numStyle,
                color = if (signed && value.signum() > 0) c.success else c.textPrimary, modifier = Modifier.alignByBaseline())
            Text(currency, style = if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.alignByBaseline())
            if (hideable) IconButton(onClick = { hidden = !hidden }) {
                Icon(if (hidden) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff, if (hidden) "Show balance" else "Hide balance", tint = c.textTertiary)
            }
        }
    }
}

@Composable
fun DSStatCard(label: String, value: String, modifier: Modifier = Modifier, delta: String? = null, positive: Boolean = true) {
    DSCard(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = DS.colors.textSecondary)
        Spacer(Modifier.height(DSSpace.xs))
        Text(value, style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"), color = DS.colors.textPrimary)
        if (delta != null) { Spacer(Modifier.height(DSSpace.sm)); DSBadge(delta, if (positive) DSTone.Success else DSTone.Danger) }
    }
}

// ---------------------------------------------------------------- States

@Composable
fun DSEmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(top = DSSpace.s3xl, start = DSSpace.lg, end = DSSpace.lg), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 320.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(DSSpace.md)) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(DS.colors.accentSubtle), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = DS.colors.accentText, modifier = Modifier.size(28.dp))
            }
            Text(title, style = MaterialTheme.typography.titleLarge, color = DS.colors.textPrimary, textAlign = TextAlign.Center)
            Text(message, style = MaterialTheme.typography.bodyLarge, color = DS.colors.textSecondary, textAlign = TextAlign.Center)
            if (actionLabel != null && onAction != null) DSButton(actionLabel, onAction, variant = DSButtonVariant.Secondary)
        }
    }
}

@Composable
fun DSErrorState(onRetry: () -> Unit, title: String = "Something went wrong", message: String = "Check your connection and try again.") =
    DSEmptyState(Icons.Rounded.WifiOff, title, message, actionLabel = "Try again", onAction = onRetry)

/** Skeleton block; pulses unless the system disables animations. */
@Composable
fun DSSkeleton(modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.small) {
    val alpha by rememberInfiniteTransition(label = "sk").animateFloat(1f, 0.55f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "a")
    Box(modifier.clip(shape).alpha(alpha).background(DS.colors.fill))
}

@Composable
fun DSSkeletonRow() {
    Row(Modifier.fillMaxWidth().heightIn(min = DSSize.listRow).padding(horizontal = DSSpace.lg), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpace.md)) {
        DSSkeleton(Modifier.size(40.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DSSpace.xs)) {
            DSSkeleton(Modifier.fillMaxWidth(0.6f).height(14.dp)); DSSkeleton(Modifier.fillMaxWidth(0.3f).height(12.dp))
        }
        DSSkeleton(Modifier.width(60.dp).height(14.dp))
    }
}
