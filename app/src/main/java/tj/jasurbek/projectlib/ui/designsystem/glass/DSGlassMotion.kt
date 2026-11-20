// Universal Design System — iOS-27-style motion for the Android glass layer.
// Same spring physics as iOS (DS.Motion.spring / .bouncy) and web (--easing-spring-*): DSMotion.spring() / DSMotion.bouncy().
//
//  • Modifier.dsGlassPress()          glass grows under the finger (bouncy) + highlight blooms from the touch point
//  • rememberMinimizeOnScroll()       tab bar shrinks to the active item while scrolling down, restores on scroll up
//  • DSGlassCluster                   nearby glass shapes merge / separate fluidly (Haze hazeGlassContainer)
//  • DSGlassMorphMenu                 glass FAB springs open into a glass menu; items materialize one after another
//  • DSMorphFabToSheet                container transform: the FAB grows into a glass sheet and shrinks back
//  • rememberReduceMotion()           Android "Remove animations" → movement replaced by short fades
package tj.jasurbek.projectlib.ui.designsystem.glass

import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import tj.jasurbek.projectlib.ui.designsystem.DS
import tj.jasurbek.projectlib.ui.designsystem.DSMotion
import tj.jasurbek.projectlib.ui.designsystem.DSSpace
import dev.chrisbanes.haze.glass.hazeGlassContainer

// ------------------------------------------------------------------ reduce motion

/** true when Developer options / Accessibility "Remove animations" set the animator scale to 0. */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
            .getOrDefault(false)
    }
}

/** Spring that respects reduce motion (snap when reduced). */
@Composable
fun <T> dsSpring(bouncy: Boolean = false): AnimationSpec<T> =
    if (rememberReduceMotion()) snap<T>() else if (bouncy) DSMotion.bouncy<T>() else DSMotion.spring<T>()

// ------------------------------------------------------------------ press response

/**
 * iOS-27 glass touch response: scale up to DSMotion.pressGlassScale with the bouncy spring and a soft highlight
 * blooming from the finger. While the finger drags, the glass follows it elastically — it leans toward the finger
 * (up to [maxLean]) and stretches along the drag — and springs back (bouncy) on release, so it feels liquid.
 * Doesn't consume events — combine with clickable(). Apply AFTER dsGlass().
 */
fun Modifier.dsGlassPress(maxLean: Dp = 16.dp, glowShape: androidx.compose.ui.graphics.Shape = CircleShape): Modifier = composed {
    val reduce = rememberReduceMotion()
    var pressed by remember { mutableStateOf(false) }
    var touch by remember { mutableStateOf(Offset.Unspecified) }
    val lean = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val scope = rememberCoroutineScope()
    val scale by animateFloatAsState(
        if (pressed && !reduce) DSMotion.pressGlassScale else 1f,
        if (pressed) tween<Float>(140, easing = FastOutSlowInEasing) else DSMotion.bouncy<Float>(), label = "glassScale",
    )
    val glow by animateFloatAsState(if (pressed) 1f else 0f, tween(if (pressed) 80 else 260), label = "glassGlow")
    val highlight = Color.White.copy(alpha = if (DS.colors.bg.red < 0.5f) 0.22f else 0.55f)
    val density = LocalDensity.current
    val maxPx = with(density) { maxLean.toPx() }
    this
        .graphicsLayer {
            val l = lean.value
            translationX = l.x
            translationY = l.y
            // Stretch along the drag, keep the area: wider one way, a little narrower the other.
            // ProjectLib: stronger, closer to iOS 27 (the glass visibly pulls after the finger, then springs back).
            val sx = 1f + (kotlin.math.abs(l.x) * 1.6f - kotlin.math.abs(l.y) * 0.6f) / (size.width.coerceAtLeast(1f))
            val sy = 1f + (kotlin.math.abs(l.y) * 1.6f - kotlin.math.abs(l.x) * 0.6f) / (size.height.coerceAtLeast(1f))
            scaleX = scale * sx
            scaleY = scale * sy
        }
        .pointerInput(reduce, maxPx) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                touch = down.position; pressed = true
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    touch = change.position
                    if (!reduce) {
                        val d = change.position - down.position
                        // Rubber band: follows quickly at first, then resists.
                        fun band(v: Float) = maxPx * kotlin.math.tanh(v / (maxPx * 3f))
                        scope.launch { lean.snapTo(Offset(band(d.x), band(d.y))) }
                    }
                }
                pressed = false
                scope.launch { lean.animateTo(Offset.Zero, DSMotion.bouncy()) }
            }
        }
        .drawWithContent {
            drawContent()
            if (glow > 0f) {
                val c = if (touch.isSpecified) touch else center
                // Clipped to the control's shape (ProjectLib: some glass bodies aren't clipped, e.g. the tab bar).
                val outline = glowShape.createOutline(size, layoutDirection, this)
                drawOutline(outline, Brush.radialGradient(listOf(highlight.copy(alpha = highlight.alpha * glow), Color.Transparent), center = c, radius = size.maxDimension * 0.65f))
            }
        }
}

/**
 * Materialize (iOS 27): new glass appears from ×0.6 with the bouncy spring while it fades in; [delayMs] staggers
 * a group (35 ms apart). Reduce motion → a short fade.
 */
fun Modifier.dsMaterialize(delayMs: Int = 0): Modifier = composed {
    val reduce = rememberReduceMotion()
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (delayMs > 0) kotlinx.coroutines.delay(delayMs.toLong())
        progress.animateTo(1f, if (reduce) tween(120) else DSMotion.bouncy())
    }
    graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        if (!reduce) { val sc = 0.6f + 0.4f * p; scaleX = sc; scaleY = sc }
    }
}

// ------------------------------------------------------------------ minimize on scroll

@Stable
class DSMinimizeState {
    var minimized by mutableStateOf(false)
        internal set
    /** Attach to the scrolling container: Modifier.nestedScroll(state.connection). */
    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y < -6f) minimized = true      // finger moves up → content scrolls down
            else if (available.y > 6f) minimized = false
            return Offset.Zero
        }
    }    /** Back to the full bar (e.g. after switching tabs: the new screen starts at the top). */
    fun restore() { minimized = false }
}

@Composable
fun rememberMinimizeOnScroll(): DSMinimizeState = remember { DSMinimizeState() }

// ------------------------------------------------------------------ cluster (merge / separate)

/**
 * Row of glass controls that merge and separate fluidly when they move close (≤ 16dp), like iOS GlassEffectContainer.
 * Children use Modifier.dsGlass(); with glass off it is a plain Row.
 */
@Composable
fun DSGlassCluster(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(DSSpace.sm),
    verticalAlignment: Alignment.Vertical = Alignment.Bottom,
    content: @Composable RowScope.() -> Unit,
) {
    val g = LocalDSGlass.current
    val container = if (g != null && g.enabled) Modifier.hazeGlassContainer(mergeDistance = 16.dp) else Modifier
    Row(modifier.then(container), horizontalArrangement = horizontalArrangement, verticalAlignment = verticalAlignment, content = content)
}

// ------------------------------------------------------------------ FAB → menu

data class DSGlassAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/** Prominent glass FAB that springs open into a glass menu (bouncy materialize, 35ms stagger). Tap outside / + to close. */
@Composable
fun DSGlassMorphMenu(actions: List<DSGlassAction>, modifier: Modifier = Modifier, contentDescription: String = "Actions") {
    var open by rememberSaveable { mutableStateOf(false) }
    val reduce = rememberReduceMotion()
    val shape = RoundedCornerShape(30.dp)
    val rotation by animateFloatAsState(if (open) 45f else 0f, dsSpring(), label = "plus")
    Box(
        modifier
            .dsGlass(shape, prominent = !open)
            .animateContentSize(if (reduce) snap<IntSize>() else DSMotion.spring<IntSize>())
            .dsGlassPress(),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Column(horizontalAlignment = Alignment.End) {
            if (open) {
                Column(Modifier.width(220.dp).padding(DSSpace.xs)) {
                    actions.forEachIndexed { i, a ->
                        var shown by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) { kotlinx.coroutines.delay(40L + i * 35L); shown = true }
                        AnimatedVisibility(
                            visible = shown || reduce,
                            enter = if (reduce) fadeIn(tween(120)) else fadeIn(tween(160)) + scaleIn(DSMotion.bouncy<Float>(), initialScale = 0.6f),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(CircleShape)
                                    .clickable(role = Role.Button) { open = false; a.onClick() }
                                    .padding(horizontal = DSSpace.md),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(DSSpace.md),
                            ) {
                                Icon(a.icon, null, tint = DS.colors.accentText, modifier = Modifier.size(20.dp))
                                Text(a.label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = DS.colors.textPrimary)
                            }
                        }
                    }
                }
            }
            Box(
                Modifier.size(60.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = contentDescription) { open = !open },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Add, if (open) "Close" else contentDescription,
                    tint = if (open) DS.colors.textPrimary else DS.colors.textOnAccent,
                    modifier = Modifier.size(26.dp).graphicsLayer { rotationZ = rotation })
            }
        }
    }
}

// ------------------------------------------------------------------ FAB → sheet (container transform)

/**
 * The FAB grows into a glass bottom sheet with the standard spring and shrinks back into it on close
 * (iOS zoom/morph transition). Place inside a full-screen Box; it aligns itself to the bottom end.
 * `sheet` receives a close() lambda. Reduce motion → crossfade.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun BoxScope.DSMorphFabToSheet(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    sheet: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val reduce = rememberReduceMotion()
    val scrimAlpha by animateFloatAsState(if (open) 1f else 0f, tween(DSMotion.baseMs), label = "scrim")
    if (scrimAlpha > 0f) {
        Box(Modifier.matchParentSize().graphicsLayer { alpha = scrimAlpha }.background(DS.colors.overlay)
            .clickable(indication = null, interactionSource = null) { open = false })
    }
    SharedTransitionLayout(modifier.align(Alignment.BottomEnd)) {
        AnimatedContent(
            targetState = open,
            transitionSpec = { fadeIn(tween(120)) togetherWith fadeOut(tween(120)) },
            contentAlignment = Alignment.BottomEnd,
            label = "fabSheet",
        ) { isOpen ->
            val bounds = if (reduce) Modifier else Modifier.sharedBounds(
                rememberSharedContentState("ds-fab-sheet"), this@AnimatedContent,
                boundsTransform = BoundsTransform { _, _ -> DSMotion.spring<androidx.compose.ui.geometry.Rect>() },
                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            )
            if (!isOpen) {
                Box(
                    Modifier.padding(end = DSSpace.lg, bottom = 96.dp).then(bounds).size(60.dp)
                        .dsGlass(CircleShape, prominent = true).dsGlassPress()
                        .clickable(role = Role.Button, onClickLabel = contentDescription) { open = true },
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, contentDescription, tint = DS.colors.textOnAccent, modifier = Modifier.size(26.dp)) }
            } else {
                Column(
                    Modifier.padding(DSSpace.sm).navigationBarsPadding().then(bounds).fillMaxWidth()
                        .dsGlass(RoundedCornerShape(28.dp)).padding(DSSpace.lg),
                ) {
                    Box(Modifier.align(Alignment.CenterHorizontally).size(36.dp, 5.dp).clip(CircleShape).background(DS.colors.borderStrong))
                    Spacer(Modifier.height(DSSpace.md))
                    sheet { open = false }
                }
            }
        }
    }
}
