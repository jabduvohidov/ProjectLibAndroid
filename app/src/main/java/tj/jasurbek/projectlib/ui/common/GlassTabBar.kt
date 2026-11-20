package tj.jasurbek.projectlib.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import tj.jasurbek.projectlib.ui.designsystem.DS
import tj.jasurbek.projectlib.ui.designsystem.DSMotion
import tj.jasurbek.projectlib.ui.designsystem.DSSpace
import tj.jasurbek.projectlib.ui.designsystem.glass.dsGlass
import tj.jasurbek.projectlib.ui.designsystem.glass.dsGlassLook
import tj.jasurbek.projectlib.ui.designsystem.glass.dsGlassPress
import tj.jasurbek.projectlib.ui.designsystem.glass.dsSpring
import tj.jasurbek.projectlib.ui.designsystem.glass.rememberReduceMotion
import kotlin.math.abs
import kotlin.math.roundToInt

data class TabItem(val id: String, val label: String, val icon: ImageVector)

/** The screen's create action, shown as a round glass button next to the tab bar (iOS 27: like the search button). */
@Stable
class ShellAction(val icon: ImageVector, val label: String, val onClick: () -> Unit)

private val BarHeight = 64.dp
private val Inset = 5.dp

/**
 * iOS 27 tab bar on Liquid Glass:
 *  - a glass capsule floating 16dp from the edges, the selected tab in a lighter lens;
 *  - press and slide along the bar: the lens follows the finger as a clear magnifying droplet (it grows past the
 *    bar, icons under it swell), the capsule stretches after the finger; on release the lens snaps to the nearest
 *    tab and opens it. A plain tap opens the tab under the finger;
 *  - [action] (the screen's "+") sits next to the capsule as its own round glass button, like iOS's search button;
 *  - [minimized] (scrolling down): the capsule shrinks to the active tab; tapping it restores the bar.
 */
@Composable
fun GlassTabBar(
    items: List<TabItem>,
    selectedId: String,
    onSelect: (String) -> Unit,
    minimized: Boolean,
    onRestore: () -> Unit,
    action: ShellAction?,
    modifier: Modifier = Modifier,
) {
    val c = DS.colors
    val index = items.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    val select by rememberUpdatedState(onSelect)
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = DSSpace.lg, vertical = DSSpace.sm)) {
        val actionSpace = if (action != null) BarHeight + DSSpace.sm else 0.dp
        val fullWidth = maxWidth - actionSpace
        val width by animateDpAsState(if (minimized) BarHeight else fullWidth, dsSpring(), label = "tabBarWidth")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            // The glass body is clipped to the capsule; the lens and tabs lie on top unclipped, so the lifted droplet
            // can grow past the bar like on iOS. The stretch after the finger moves all of it together.
            Box(Modifier.width(width).height(BarHeight).dsGlassPress(maxLean = 8.dp)) {
                Box(Modifier.matchParentSize().dsGlass(CircleShape))
                if (minimized) {
                    val item = items[index]
                    Box(
                        Modifier.size(BarHeight).clip(CircleShape).clickable(role = Role.Tab, onClickLabel = "Показать вкладки", onClick = onRestore)
                            .semantics { contentDescription = item.label; selected = true },
                        contentAlignment = Alignment.Center,
                    ) { Icon(item.icon, null, tint = c.accentText, modifier = Modifier.size(24.dp)) }
                } else {
                    TabCapsuleContent(items, index, fullWidth, onPick = { select(it) })
                }
            }
            if (action != null) {
                Box(
                    Modifier.size(BarHeight).dsGlass(CircleShape, prominent = true).dsGlassPress()
                        .clickable(role = Role.Button, onClickLabel = action.label, onClick = action.onClick)
                        .semantics { contentDescription = action.label },
                    contentAlignment = Alignment.Center,
                ) { Icon(action.icon, null, tint = c.textOnAccent, modifier = Modifier.size(26.dp)) }
            }
        }
    }
}

@Composable
private fun TabCapsuleContent(items: List<TabItem>, index: Int, fullWidth: androidx.compose.ui.unit.Dp, onPick: (String) -> Unit) {
    val c = DS.colors
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val reduce = rememberReduceMotion()
    val slop = LocalViewConfiguration.current.touchSlop
    val scope = rememberCoroutineScope()
    val inner = with(density) { (fullWidth - Inset * 2).toPx() }
    val itemW = inner / items.size

    // Lens position (left edge, px), whether a finger is on the bar, and how "lifted" the lens is (0 → 1).
    val lensX = remember { Animatable(index * itemW) }
    var touching by remember { mutableStateOf(false) }
    val lift by animateFloatAsState(if (touching && !reduce) 1f else 0f, if (touching) DSMotion.bouncy() else DSMotion.spring(), label = "lensLift")
    LaunchedEffect(index, itemW) { if (!touching) lensX.animateTo(index * itemW, if (reduce) androidx.compose.animation.core.snap() else DSMotion.spring()) }
    val hovered = (lensX.value / itemW).roundToInt().coerceIn(0, items.lastIndex)

    Box(
        Modifier.fillMaxHeight().fillMaxWidth().padding(Inset)
            .pointerInput(items, itemW) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    touching = true
                    var dragging = false
                    var last = hovered
                    fun lensFor(x: Float) = (x - itemW / 2).coerceIn(0f, inner - itemW)
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        if (!dragging && abs(ch.position.x - down.position.x) > slop) dragging = true
                        if (dragging) {
                            ch.consume()
                            scope.launch { lensX.snapTo(lensFor(ch.position.x)) }
                            val now = ((lensFor(ch.position.x)) / itemW).roundToInt().coerceIn(0, items.lastIndex)
                            if (now != last) { last = now; haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                        }
                    }
                    touching = false
                    // Release: snap to the nearest tab (drag) or the tab under the finger (tap), then open it.
                    val target = if (dragging) last else (down.position.x / itemW).toInt().coerceIn(0, items.lastIndex)
                    scope.launch { lensX.animateTo(target * itemW, DSMotion.spring()) }
                    onPick(items[target].id)
                }
            },
    ) {
        val lensW = with(density) { itemW.toDp() }
        // The lens: a soft pill at rest; lifted, it becomes a clear glass droplet bigger than the bar.
        Box(
            Modifier.offset { androidx.compose.ui.unit.IntOffset(lensX.value.roundToInt(), 0) }.width(lensW).fillMaxHeight()
                .graphicsLayer { scaleX = 1f + 0.18f * lift; scaleY = 1f + 0.32f * lift }
                .then(
                    if (lift > 0.02f) Modifier.dsGlassLook(CircleShape)
                    else Modifier.clip(CircleShape).background(c.textPrimary.copy(alpha = 0.09f)),
                ),
        )
        Row(Modifier.fillMaxHeight()) {
            items.forEachIndexed { i, item ->
                // Icons under the lifted lens swell, like seen through a magnifying drop.
                val center = (i + 0.5f) * itemW
                val near = (1f - abs(center - (lensX.value + itemW / 2)) / itemW).coerceIn(0f, 1f)
                val swell = 1f + 0.22f * near * lift
                val on = if (touching) i == hovered else i == index
                Column(
                    Modifier.width(lensW).fillMaxHeight().graphicsLayer { scaleX = swell; scaleY = swell }
                        .semantics {
                            role = Role.Tab; selected = i == index; contentDescription = item.label
                            onClick(label = item.label) { onPick(item.id); true }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Icon(item.icon, null, tint = if (on) c.accentText else c.textPrimary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(2.dp))
                    Text(item.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (on) c.accentText else c.textPrimary, maxLines = 1)
                }
            }
        }
    }
}
