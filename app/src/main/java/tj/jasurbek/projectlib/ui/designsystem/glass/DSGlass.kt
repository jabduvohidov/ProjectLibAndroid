// Universal Design System — optional Liquid-Glass mode for Android (Jetpack Compose).
//
// Gives Android the same floating glass navigation layer as iOS 27 / web: a glass capsule navigation bar with a
// selection "lens", glass icon buttons over content, a tinted glass FAB, and a scroll-edge fade under the top bar.
// Content (cards, lists, forms) stays solid — glass is ONLY for the navigation/control layer.
//
// Rendering: Haze 2.x `hazeGlass` (blur + refraction + rim light on capable devices, simplified fallback elsewhere).
// Gradle (keep all Haze artifacts on the same version; written against Haze 2.1.0):
//   implementation("dev.chrisbanes.haze:haze:2.1.0")
//   implementation("dev.chrisbanes.haze:haze-glass:2.1.0")
//
// Glass is switched ON only when the device can afford it (see rememberDSGlassAvailability):
//   Android 12+ (API 31), not a low-RAM device, battery saver off, system blur not disabled
//   (WindowManager.isCrossWindowBlurEnabled — false when the user turned blur off / "reduce blur", or in power saving).
// Otherwise every component here falls back to the solid Material look of DSComponents.kt automatically.
//
// NOTE: API names follow the Haze 2.1 documentation (hazeSource / hazeGlass / GlassStyle / HazeInput /
// LocalGlassAccessibilitySettings). If an import does not resolve in your Haze version, check its migration guide.
package tj.jasurbek.projectlib.ui.designsystem.glass

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.pointerInput
import tj.jasurbek.projectlib.ui.designsystem.DS
import tj.jasurbek.projectlib.ui.designsystem.DSMotion
import tj.jasurbek.projectlib.ui.designsystem.DSSize
import tj.jasurbek.projectlib.ui.designsystem.DSSpace
import dev.chrisbanes.haze.*
import dev.chrisbanes.haze.glass.*

// ------------------------------------------------------------------ capability check

/** Why glass is on or off — handy for a debug screen or analytics. */
data class DSGlassAvailability(val enabled: Boolean, val reason: String)

/**
 * Decides whether this device should render glass. Re-evaluated when the composition restarts
 * (e.g. after returning from settings). Pass `userOverride` to honour an in-app "Glass effects" switch.
 */
@Composable
fun rememberDSGlassAvailability(userOverride: Boolean? = null): DSGlassAvailability {
    val context = LocalContext.current
    return remember(context, userOverride) { glassAvailability(context, userOverride) }
}

fun glassAvailability(context: Context, userOverride: Boolean? = null): DSGlassAvailability {
    if (userOverride == false) return DSGlassAvailability(false, "turned off in app settings")
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return DSGlassAvailability(false, "Android 11 or older")
    val am = context.getSystemService(ActivityManager::class.java)
    if (am?.isLowRamDevice == true) return DSGlassAvailability(false, "low-RAM device")
    val pm = context.getSystemService(PowerManager::class.java)
    if (pm?.isPowerSaveMode == true) return DSGlassAvailability(false, "battery saver on")
    val wm = context.getSystemService(WindowManager::class.java)
    if (wm != null && !wm.isCrossWindowBlurEnabled) return DSGlassAvailability(false, "system blur disabled")
    return DSGlassAvailability(true, "supported")
}

// ------------------------------------------------------------------ glass scope

/** Shared state for one screen: the HazeState that content draws into, and whether glass is active. */
@Stable
class DSGlassScope(val hazeState: HazeState, val enabled: Boolean)

val LocalDSGlass = staticCompositionLocalOf<DSGlassScope?> { null }

/**
 * Wrap a screen (usually the whole Scaffold) once. Inside it:
 *  - put `Modifier.dsGlassSource()` on the scrolling content (what the glass sees through),
 *  - use DSGlassNavigationBar / DSGlassIconButton / DSGlassFab / Modifier.dsGlass on the floating controls.
 * Maps Android accessibility (high-contrast text) to Haze's glass accessibility settings.
 */
@Composable
fun DSGlassProvider(
    availability: DSGlassAvailability = rememberDSGlassAvailability(),
    highContrast: Boolean = rememberHighContrastText(),
    content: @Composable () -> Unit,
) {
    val hazeState = rememberHazeState()
    val scope = remember(hazeState, availability.enabled) { DSGlassScope(hazeState, availability.enabled) }
    CompositionLocalProvider(
        LocalDSGlass provides scope,
        LocalGlassAccessibilitySettings provides GlassAccessibilitySettings(
            reduceTransparency = highContrast,
            increaseContrast = highContrast,
            showBorders = highContrast,
        ),
        content = content,
    )
}

/** Android's "High contrast text" (Settings → Accessibility). Treated like iOS Increase Contrast. */
@Composable
fun rememberHighContrastText(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            android.provider.Settings.Secure.getInt(context.contentResolver, "high_text_contrast_enabled", 0) == 1
        }.getOrDefault(false)
    }
}

/** Marks content the glass layer refracts. Apply to the scrolling content container (not to the glass controls). */
fun Modifier.dsGlassSource(): Modifier = composed {
    val g = LocalDSGlass.current
    if (g != null && g.enabled) this.hazeSource(g.hazeState) else this
}

// ------------------------------------------------------------------ styles

private enum class GlassKind { Regular, Prominent }

@Composable
private fun glassStyle(kind: GlassKind, shape: Shape): GlassStyle {
    val c = DS.colors
    val dark = c.bg.luminanceApprox() < 0.5f
    return remember(kind, shape, dark, c.accent) {
        when (kind) {
            GlassKind.Regular -> GlassStyle.regular.then {
                shape(shape as? androidx.compose.foundation.shape.RoundedCornerShape ?: CircleShape)
                backgroundColor(c.bg)
                tint(if (dark) Color(0xFF283230).copy(alpha = 0.35f) else Color.White.copy(alpha = 0.22f))
                specularIntensity(0.7f)
                // The spec's 14px blur behind the glass.
                optics { copy(blurRadius = OpticalSizeValue.Fixed(40.dp), depth = OpticalSizeValue.Fixed(0.2f)) }
            }
            GlassKind.Prominent -> GlassStyle.regular.then {
                shape(shape as? androidx.compose.foundation.shape.RoundedCornerShape ?: CircleShape)
                tint(c.accent.copy(alpha = 0.85f))   // the accent seen through glass
                specularIntensity(0.8f)
            }
        }
    }
}

private fun Color.luminanceApprox(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

/**
 * Glass surface for a floating control ([strong] for large panels: sheets, menus). Built the way the spec
 * describes iOS 27 glass: the content behind it blurred lightly (14dp) and more saturated (Haze blur), a clear thin
 * tint (`glass`, `glassStrong` for panels, the accent for [prominent]), a top sheen, a bright specular rim on the
 * top edge and a softer one at the bottom, a darkened 0.5dp outer edge and a soft depth shadow (light mode).
 * Karavan: Haze's `hazeGlass` refracted without blurring on the test phone, so the body is Haze blur and the
 * optics are drawn here. Falls back to a solid raised surface (+ border) when glass is off, so callers never branch.
 * Content colors stay the caller's job (use DS.colors.textPrimary / textOnAccent).
 */
fun Modifier.dsGlass(shape: Shape = CircleShape, prominent: Boolean = false, strong: Boolean = false): Modifier = composed {
    val g = LocalDSGlass.current
    val c = DS.colors
    val dark = c.bg.luminanceApprox() < 0.5f
    if (g != null && g.enabled) {
        val tint = when {
            prominent -> c.accent.copy(alpha = 0.85f)
            strong -> c.glassStrong
            else -> c.glass
        }
        val style = remember(tint, c.bg) {
            HazeBlurStyle {
                blurRadius(14.dp)
                backgroundColor(c.bg)
                colorEffects(
                    listOf(
                        HazeColorEffect.colorFilter(ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.4f) })),
                        HazeColorEffect.tint(tint),
                    ),
                )
                noiseFactor(0f)
            }
        }
        this
            .then(if (dark) Modifier else Modifier.shadow(10.dp, shape, ambientColor = Color(0x14101828), spotColor = Color(0x1F101828)))
            .clip(shape)
            .hazeBlur(HazeInput.Sources(g.hazeState), style)
            .drawWithContent {
                drawContent()
                val outline = shape.createOutline(size, layoutDirection, this)
                // Top sheen.
                drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = if (dark) 0.07f else 0.18f), 0.5f to Color.Transparent))
                // Specular rim: bright on the top edge, softer at the bottom (refraction).
                drawOutline(
                    outline,
                    Brush.verticalGradient(
                        0f to c.glassHighlight, 0.35f to c.glassHighlight.copy(alpha = 0f),
                        0.75f to c.glassHighlight.copy(alpha = 0f), 1f to c.glassHighlight.copy(alpha = c.glassHighlight.alpha * 0.45f),
                    ),
                    style = Stroke(width = 1.2.dp.toPx()),
                )
                // Inner glow (thickness) and the darkened outer edge.
                drawOutline(outline, Color.White.copy(alpha = if (dark) 0.03f else 0.08f), style = Stroke(width = 6.dp.toPx()))
                drawOutline(outline, c.glassEdge, style = Stroke(width = 0.5.dp.toPx()))
            }
    } else {
        this.clip(shape)
            .background(if (prominent) c.accent else c.surfaceRaised)
            .then(if (prominent) Modifier else Modifier.border(1.dp, c.border, shape))
    }
}

// ------------------------------------------------------------------ glass in content (ProjectLib)

/**
 * ProjectLib: the owner asked for Liquid Glass on in-content controls too (buttons, chips, segmented controls) —
 * the design system itself keeps them solid. Provided at the app root; false = the kit's solid look.
 */
val LocalDSContentGlass = staticCompositionLocalOf { false }

/** Tint of in-content glass. */
enum class DSGlassTint { Regular, Accent, AccentSoft, Danger }

/**
 * The glass material for a control that lives INSIDE the content (a button in a card). It is drawn without a
 * backdrop blur: that content is what the navigation glass blurs, so sampling it again would be glass seeing
 * itself (Haze recursion, flicker) — and behind a control in a card there is only the flat card anyway. The rest is
 * the same iOS 27 optics as [dsGlass]: a thin translucent body, top sheen, bright top rim / softer bottom rim,
 * inner glow, darkened 0.5dp edge and a soft depth shadow in light mode.
 */
fun Modifier.dsGlassLook(shape: Shape = CircleShape, tint: DSGlassTint = DSGlassTint.Regular): Modifier = composed {
    val c = DS.colors
    val dark = c.bg.luminanceApprox() < 0.5f
    val body = when (tint) {
        DSGlassTint.Regular -> if (dark) Color.White.copy(alpha = 0.08f) else c.fill.copy(alpha = 0.55f)
        DSGlassTint.Accent -> c.accent.copy(alpha = 0.9f)
        DSGlassTint.AccentSoft -> c.accentSubtle.copy(alpha = if (dark) 0.85f else 0.9f)
        DSGlassTint.Danger -> c.dangerFill.copy(alpha = 0.9f)
    }
    val tinted = tint == DSGlassTint.Accent || tint == DSGlassTint.Danger
    this
        .then(if (dark) Modifier else Modifier.shadow(if (tinted) 6.dp else 4.dp, shape, ambientColor = Color(0x14101828), spotColor = Color(0x1F101828)))
        .clip(shape)
        .background(body)
        .drawWithContent {
            drawContent()
            val outline = shape.createOutline(size, layoutDirection, this)
            drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = if (dark) 0.08f else if (tinted) 0.22f else 0.5f), 0.55f to Color.Transparent))
            drawOutline(
                outline,
                Brush.verticalGradient(
                    0f to c.glassHighlight, 0.35f to c.glassHighlight.copy(alpha = 0f),
                    0.75f to c.glassHighlight.copy(alpha = 0f), 1f to c.glassHighlight.copy(alpha = c.glassHighlight.alpha * 0.45f),
                ),
                style = Stroke(width = 1.2.dp.toPx()),
            )
            drawOutline(outline, Color.White.copy(alpha = if (dark) 0.03f else 0.1f), style = Stroke(width = 5.dp.toPx()))
            drawOutline(outline, c.glassEdge, style = Stroke(width = 0.5.dp.toPx()))
        }
}

/** Solid content buttons answer a press by shrinking to ×0.97 (design system motion; glass grows instead). */
fun Modifier.dsPressShrink(): Modifier = composed {
    val reduce = rememberReduceMotion()
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (pressed && !reduce) DSMotion.pressButtonScale else 1f,
        if (pressed) tween(100) else DSMotion.bouncy(), label = "pressShrink",
    )
    this.graphicsLayer { scaleX = scale; scaleY = scale }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false); pressed = true
                waitForUpOrCancellation(); pressed = false
            }
        }
}

/** A control's surface: glass look when content glass is on, otherwise [solid] (+ press shrink). */
@Composable
fun Modifier.dsControlSurface(shape: Shape, tint: DSGlassTint, solid: Modifier.() -> Modifier): Modifier =
    if (LocalDSContentGlass.current) this.dsGlassLook(shape, tint).dsGlassPress(maxLean = 10.dp)
    else this.solid().dsPressShrink()

// ------------------------------------------------------------------ components

data class DSGlassNavItem(val id: String, val label: String, val icon: ImageVector)

/**
 * iOS-27-style floating navigation: glass capsule inset 14dp from the edges, a lighter "lens" pill sliding under
 * the selected item (accent icon + label), optional separate round glass search button on the trailing side.
 * Place it in Scaffold.bottomBar (with Modifier.navigationBarsPadding()) or overlay it at the bottom.
 * Fallback (glass off): same geometry on a solid surface.
 */
@Composable
fun DSGlassNavigationBar(
    items: List<DSGlassNavItem>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    onSearch: (() -> Unit)? = null,
    /** From rememberMinimizeOnScroll(): shrinks to the active item while scrolling down (iOS 27). */
    minimized: Boolean = false,
) {
    val c = DS.colors
    val index = items.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = DSSpace.sm)) {
        val searchSpace = if (onSearch != null) 64.dp + DSSpace.sm else 0.dp
        val fullWidth = maxWidth - searchSpace
        val barWidth by animateDpAsState(if (minimized) 64.dp else fullWidth, dsSpring(), label = "barWidth")
        DSGlassCluster(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.width(barWidth).height(64.dp).dsGlass(CircleShape).dsGlassPress().padding(5.dp)) {
                val itemWidth = (fullWidth - 10.dp) / items.size
                val lensX by animateDpAsState(itemWidth * index, dsSpring(), label = "lens")
                val lensAlpha by animateFloatAsState(if (minimized) 0f else 1f, dsSpring(), label = "lensAlpha")
                // selection lens
                Box(Modifier.offset(x = lensX).width(itemWidth).fillMaxHeight().graphicsLayer { alpha = lensAlpha }
                    .clip(CircleShape).background(c.textPrimary.copy(alpha = 0.09f)))
                // items slide so the active one stays visible when minimized
                val shift by animateDpAsState(if (minimized) -(54.dp * index) else 0.dp, dsSpring(), label = "shift")
                // Start-aligned even when wider than the minimized capsule (requiredWidth alone would centre it and
                // push the active item out of view).
                Row(Modifier.fillMaxHeight().wrapContentWidth(Alignment.Start, unbounded = true)
                    .requiredWidth(if (minimized) 54.dp * items.size else fullWidth - 10.dp).offset(x = shift),
                    horizontalArrangement = Arrangement.Start) {
                    items.forEachIndexed { i, item ->
                        val on = i == index
                        val alpha by animateFloatAsState(if (minimized && !on) 0f else 1f, tween(DSMotion.fastMs), label = "itemAlpha")
                        Column(
                            Modifier.width(if (minimized) 54.dp else itemWidth).fillMaxHeight().graphicsLayer { this.alpha = alpha }.clip(CircleShape)
                                .clickable(enabled = !minimized || on, role = Role.Tab) { onSelect(item.id) }
                                .semantics { selected = on; contentDescription = item.label },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(item.icon, null, tint = if (on) c.accentText else c.textPrimary, modifier = Modifier.size(24.dp))
                            AnimatedVisibility(!minimized) {
                                Text(item.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (on) c.accentText else c.textPrimary, maxLines = 1)
                            }
                        }
                    }
                }
            }
            if (onSearch != null) DSGlassIconButton(Icons.Rounded.Search, "Search", onSearch, size = 64.dp)
        }
    }
}

/** Round glass icon button for the navigation layer (top-right actions, back, search). Min 48dp target. */
@Composable
fun DSGlassIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, size: Dp = DSSize.minTouchAndroid) {
    Box(
        Modifier.size(size).dsGlass(CircleShape).dsGlassPress().clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription, tint = DS.colors.textPrimary, modifier = Modifier.size(22.dp)) }
}

/** Primary floating action as tinted (prominent) glass. One per screen. */
@Composable
fun DSGlassFab(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(60.dp).dsGlass(CircleShape, prominent = true).dsGlassPress().clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription, tint = DS.colors.textOnAccent, modifier = Modifier.size(26.dp)) }
}

/**
 * Scroll-edge fade behind a transparent top bar: content dissolves into the canvas instead of hitting a solid bar.
 * Put it as the first child of the top bar's Box (behind the title and glass buttons).
 */
@Composable
fun BoxScope.DSScrollEdgeTop() {
    val bg = DS.colors.bg
    Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to bg, 0.6f to bg.copy(alpha = 0.75f), 1f to bg.copy(alpha = 0f))))
}

/** Same fade at the bottom, behind the floating navigation. */
@Composable
fun DSScrollEdgeBottom(modifier: Modifier = Modifier, height: Dp = 120.dp) {
    val bg = DS.colors.bg
    Box(modifier.fillMaxWidth().height(height).background(Brush.verticalGradient(0f to bg.copy(alpha = 0f), 0.5f to bg.copy(alpha = 0.6f), 1f to bg)))
}
