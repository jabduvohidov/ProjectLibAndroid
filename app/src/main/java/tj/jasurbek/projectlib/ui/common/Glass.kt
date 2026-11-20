package tj.jasurbek.projectlib.ui.common

import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import dev.chrisbanes.haze.blur.hazeBlur
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import tj.jasurbek.projectlib.ui.designsystem.DS
import tj.jasurbek.projectlib.ui.designsystem.DSMotion
import tj.jasurbek.projectlib.ui.designsystem.DSSpace
import tj.jasurbek.projectlib.ui.designsystem.glass.LocalDSGlass
import tj.jasurbek.projectlib.ui.designsystem.glass.dsGlass
import tj.jasurbek.projectlib.ui.designsystem.glass.dsGlassLook
import tj.jasurbek.projectlib.ui.designsystem.glass.dsGlassPress
import androidx.compose.foundation.clickable
import tj.jasurbek.projectlib.ui.designsystem.glass.rememberReduceMotion

/*
 * Liquid Glass for the whole navigation / control layer (universal-design-system, Android glass mode).
 * Haze glass only sees content of its own window, so:
 *  - menus are drawn in the app window (GlassMenuLayer at the root), anchored to the control that opened them;
 *  - dialogs and sheets live in separate windows: they get Android 12+ system blur behind the window
 *    (DialogGlass) and a translucent `glassStrong` panel — the same material, rendered by the system.
 * Content (cards, lists, fields) stays solid — glass never goes on content.
 */

/** Is glass rendering on right now (device can afford it and the setting is on)? */
val glassOn: Boolean
    @Composable get() = LocalDSGlass.current?.enabled == true

/** Panel color for glass sheets and dialogs: translucent over system blur, solid when glass is off. */
val glassPanelColor: Color
    @Composable get() = if (glassOn) DS.colors.glassStrong else DS.colors.surface

/** Sheet / dialog shape (28, design system `sheet` radius). */
val SheetShape: Shape = RoundedCornerShape(28.dp)

/**
 * Put inside the content of a Dialog / AlertDialog / ModalBottomSheet: blurs everything behind its window
 * (Android 12+ cross-window blur) and lightens the dim so the blur reads as glass. No-op when glass is off.
 */
@Composable
fun DialogGlass(blurRadius: androidx.compose.ui.unit.Dp = 28.dp, dim: Float = 0.18f) {
    val view = LocalView.current
    val on = glassOn
    val px = with(LocalDensity.current) { blurRadius.roundToPx() }
    DisposableEffect(view, on) {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (on && window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply { blurBehindRadius = px }
            window.setDimAmount(dim)
        }
        onDispose {}
    }
}

/** AlertDialog on glass (same slots as Material's). Confirm / dismiss buttons are the caller's DSButtons. */
@Composable
fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") shape: Shape = SheetShape,
    @Suppress("UNUSED_PARAMETER") containerColor: Color = Color.Unspecified,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = dismissButton,
        title = title,
        // Menus inside the dialog open in the dialog's own window (the app-window glass layer would be behind it).
        text = { DialogGlass(); NoGlassMenuHost { text?.invoke() } },
        shape = SheetShape,
        containerColor = glassPanelColor,
        tonalElevation = 0.dp,
        titleContentColor = DS.colors.textPrimary,
        textContentColor = DS.colors.textSecondary,
    )
}

/* ---------------------------------------------------------------- menus (in-window glass) */

@Stable
class GlassMenuHost {
    internal var menu by mutableStateOf<OpenMenu?>(null)
}

internal class OpenMenu(val anchor: Rect, val onDismiss: () -> Unit, val content: @Composable ColumnScope.() -> Unit) {
    var open by mutableStateOf(true)
}

/** Present at the app root (same window as the content); null inside dialogs / sheets → Material fallback. */
val LocalGlassMenuHost = staticCompositionLocalOf<GlassMenuHost?> { null }

/** Hides the root menu host for content shown in another window (dialogs, sheets). */
@Composable
fun NoGlassMenuHost(content: @Composable () -> Unit) = CompositionLocalProvider(LocalGlassMenuHost provides null, content = content)

/**
 * Drop-in glass replacement for Material's DropdownMenu: place it inside the trigger's Box exactly like DropdownMenu.
 * The parent Box is the anchor; while [expanded], [content] (DropdownMenuItems) shows in a glass panel that grows out
 * of the trigger (spring, iOS 27 "button becomes a menu"), drawn in the app window so the glass sees the content.
 * In dialogs / sheets (other windows, no host) it falls back to a Material menu on a raised surface.
 */
@Composable
fun GlassDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") shape: Shape = SheetShape,
    content: @Composable ColumnScope.() -> Unit,
) {
    val host = LocalGlassMenuHost.current
    if (host == null) {
        DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest, modifier = modifier, shape = MaterialTheme.shapes.large,
            containerColor = DS.colors.surfaceRaised) { content() }
        return
    }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val dismiss by rememberUpdatedState(onDismissRequest)
    // Zero-size probe: its parent is the trigger's Box (the same anchor DropdownMenu uses).
    Box(Modifier.size(0.dp).onGloballyPositioned { c -> c.parentLayoutCoordinates?.let { bounds = it.boundsInRoot() } })
    val current = remember { mutableStateOf<OpenMenu?>(null) }
    LaunchedEffect(expanded) {
        if (expanded) {
            val m = OpenMenu(bounds, { dismiss() }, content)
            current.value = m
            host.menu = m
        } else {
            current.value?.open = false // melts back; the layer removes it after the animation
            current.value = null
        }
    }
}

/** Draws the open menu over everything in the app window. Place last in the root Box. */
@Composable
fun BoxScope.GlassMenuLayer(host: GlassMenuHost) {
    val m = host.menu ?: return
    val reduce = rememberReduceMotion()
    val progress = remember(m) { Animatable(0f) }
    LaunchedEffect(m, m.open) {
        if (m.open) progress.animateTo(1f, if (reduce) tween(120) else DSMotion.spring())
        else {
            progress.animateTo(0f, if (reduce) tween(100) else tween(140))
            if (host.menu === m) host.menu = null
        }
    }
    BackHandler(enabled = m.open) { m.onDismiss() }
    // Tap outside closes (the tap is consumed, like on iOS).
    Box(Modifier.matchParentSize().pointerInput(m) { detectTapGestures { m.onDismiss() } })
    val margin = with(LocalDensity.current) { DSSpace.sm.roundToPx() }
    Layout(
        modifier = Modifier.matchParentSize(),
        content = {
            Column(
                Modifier
                    .widthIn(min = 200.dp, max = 300.dp)
                    .dsGlass(RoundedCornerShape(24.dp), strong = true)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = DSSpace.xs),
                content = m.content,
            )
        },
    ) { measurables, c ->
        val maxH = (c.maxHeight - margin * 2).coerceAtLeast(0)
        val p = measurables.first().measure(Constraints(maxWidth = c.maxWidth - margin * 2, maxHeight = maxH))
        val a = m.anchor
        // Horizontally: start at the trigger's left edge, or end at its right edge when that runs off screen.
        val alignStart = a.left + p.width <= c.maxWidth - margin
        val x = (if (alignStart) a.left.toInt() else a.right.toInt() - p.width).coerceIn(margin, (c.maxWidth - margin - p.width).coerceAtLeast(margin))
        // Vertically: open downward from the trigger's top, upward from its bottom when there's no room.
        val down = a.top + p.height <= c.maxHeight - margin
        val y = (if (down) a.top.toInt() else a.bottom.toInt() - p.height).coerceIn(margin, (c.maxHeight - margin - p.height).coerceAtLeast(margin))
        val origin = TransformOrigin(if (alignStart) 0f else 1f, if (down) 0f else 1f)
        layout(c.maxWidth, c.maxHeight) {
            // Grows out of the trigger's corner (spring) and melts back into it.
            p.placeWithLayer(x, y) {
                val t = progress.value
                scaleX = 0.6f + 0.4f * t; scaleY = 0.4f + 0.6f * t
                alpha = (t * 1.6f).coerceAtMost(1f)
                transformOrigin = origin
            }
        }
    }
}

/* ---------------------------------------------------------------- toast */

/** Snackbar as a glass capsule (design system: toasts live on the glass layer). */
@Composable
fun GlassToastHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data ->
        Row(
            Modifier.padding(horizontal = DSSpace.lg).heightIn(min = 48.dp).dsGlass(CircleShape, strong = true)
                .padding(horizontal = DSSpace.xl, vertical = DSSpace.md),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpace.sm),
        ) {
            Text(data.visuals.message, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = DS.colors.textPrimary)
        }
    }
}

/** Round glass button for the top bar (back, ⋯) — the kit's DSGlassIconButton, re-exported for screens. */
@Composable
fun GlassIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) =
    tj.jasurbek.projectlib.ui.designsystem.glass.DSGlassIconButton(icon, contentDescription, onClick)

/**
 * Small icon button inside content (⋯ in a row, ⌄ next to "Запустить", "+" in a board column): a 40dp glass circle
 * when content glass is on, otherwise the plain Material icon button. Touch target stays ≥ 48dp.
 */
@Composable
fun ContentIconButton(icon: ImageVector, contentDescription: String, tint: Color = DS.colors.textSecondary, onClick: () -> Unit) {
    if (!tj.jasurbek.projectlib.ui.designsystem.glass.LocalDSContentGlass.current) {
        androidx.compose.material3.IconButton(onClick = onClick) { Icon(icon, contentDescription, tint = tint) }
        return
    }
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(40.dp).dsGlassLook(CircleShape).dsGlassPress(maxLean = 10.dp)
                .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = contentDescription, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(20.dp)) }
    }
}

/* ---------------------------------------------------------------- scroll edges (iOS 27 scroll edge effect) */

/**
 * Behind floating controls, content doesn't hit a bar: it blurs progressively (full blur at the screen edge,
 * none toward the content) under a light veil of the canvas color. [fromTop] = the edge is at the top.
 * Glass off → the plain canvas-color fade of the kit.
 */
@Composable
fun GlassScrollEdge(fromTop: Boolean, modifier: Modifier = Modifier) {
    val g = LocalDSGlass.current
    val bg = DS.colors.bg
    val veil = if (fromTop) Brush.verticalGradient(0f to bg.copy(alpha = 0.92f), 0.55f to bg.copy(alpha = 0.7f), 1f to bg.copy(alpha = 0f))
    else Brush.verticalGradient(0f to bg.copy(alpha = 0f), 0.45f to bg.copy(alpha = 0.55f), 1f to bg.copy(alpha = 0.85f))
    if (g == null || !g.enabled) {
        val solid = if (fromTop) Brush.verticalGradient(0f to bg, 0.8f to bg, 1f to bg.copy(alpha = 0f))
        else Brush.verticalGradient(0f to bg.copy(alpha = 0f), 0.5f to bg.copy(alpha = 0.6f), 1f to bg)
        Box(modifier.background(solid))
        return
    }
    val style = remember(bg, fromTop) {
        dev.chrisbanes.haze.blur.HazeBlurStyle {
            blurRadius(22.dp)
            backgroundColor(bg)
            noiseFactor(0f)
            progressive(
                dev.chrisbanes.haze.HazeProgressive.verticalGradient(
                    startIntensity = if (fromTop) 1f else 0f, endIntensity = if (fromTop) 0f else 1f,
                ),
            )
        }
    }
    Box(modifier.hazeBlur(dev.chrisbanes.haze.HazeInput.Sources(g.hazeState), style).background(veil))
}
