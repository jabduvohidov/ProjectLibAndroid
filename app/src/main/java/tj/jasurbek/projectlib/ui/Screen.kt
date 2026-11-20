package tj.jasurbek.projectlib.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tj.jasurbek.projectlib.App
import tj.jasurbek.projectlib.data.SyncState
import tj.jasurbek.projectlib.ui.common.GlassIconButton
import tj.jasurbek.projectlib.ui.common.SyncIndicator
import tj.jasurbek.projectlib.ui.common.UserAvatar
import tj.jasurbek.projectlib.ui.designsystem.DS
import tj.jasurbek.projectlib.ui.designsystem.DSSpace
import tj.jasurbek.projectlib.ui.designsystem.glass.dsGlass
import tj.jasurbek.projectlib.ui.designsystem.glass.dsGlassPress
import tj.jasurbek.projectlib.ui.designsystem.glass.dsGlassSource

/** What a screen needs from the app shell: navigation callbacks. */
data class Nav(
    val back: () -> Unit,
    val project: (id: String) -> Unit,
    val run: (projectId: String, linkId: String) -> Unit,
    val projects: () -> Unit,
    val settings: () -> Unit,
    val profile: () -> Unit = {},
)

/**
 * Shell → screens: room taken by the floating tab bar, the scroll hook that minimizes it, and the slot where a
 * screen puts its create action ("+" next to the tab bar). [actionSlot] null = no tab bar: screens keep a FAB.
 */
class ShellInfo(
    val bottomBar: Dp,
    val minimizeOnScroll: NestedScrollConnection?,
    val actionSlot: androidx.compose.runtime.MutableState<tj.jasurbek.projectlib.ui.common.ShellAction?>? = null,
)
val LocalShell = staticCompositionLocalOf { ShellInfo(0.dp, null) }

/** Height of the bar under the status bar (glass buttons are 48 + 4 around). */
private val BarHeight = 56.dp

/**
 * Screen in the design system's Liquid-Glass shell (references/layouts.md, Android glass mode — same as iOS 27):
 *  - no solid app bar: round glass buttons (back, actions, sync, profile) float over the content, which dissolves
 *    into the canvas under them (scroll-edge fade);
 *  - large title (display 34) scrolls with the content; the small title fades into the bar once it passes under it;
 *  - the content is what the glass refracts (dsGlassSource); cards and lists stay solid;
 *  - [fab]: the screen's create action — a tinted glass FAB above the floating tab bar.
 */
@Composable
fun AppScreen(
    title: String,
    nav: Nav,
    subtitle: String? = null,
    showBack: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    fab: @Composable () -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    val app = App.instance
    val status by app.repo.status.collectAsStateWithLifecycle()
    val auth by app.auth.state.collectAsStateWithLifecycle()
    val shell = LocalShell.current
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val collapsed by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 40 }
    }
    val edge by animateFloatAsState(if (listState.canScrollBackward) 1f else 0f, tween(180), label = "edge")
    val smallTitle by animateFloatAsState(if (collapsed) 1f else 0f, tween(180), label = "smallTitle")
    val c = DS.colors

    Box(Modifier.fillMaxSize().background(c.bg)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().dsGlassSource().background(c.bg)
                .then(if (shell.minimizeOnScroll != null) Modifier.nestedScroll(shell.minimizeOnScroll) else Modifier),
            contentPadding = PaddingValues(
                start = DSSpace.lg, end = DSSpace.lg,
                top = top + BarHeight, bottom = bottom + shell.bottomBar + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(DSSpace.xl),
        ) {
            item(key = "__title") {
                Column(Modifier.padding(top = DSSpace.xs), verticalArrangement = Arrangement.spacedBy(DSSpace.xs)) {
                    Text(title, style = MaterialTheme.typography.displaySmall, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() })
                    if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                }
            }
            content()
        }

        // Top: scroll-edge fade + glass buttons + the small title (no solid bar).
        Box(Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
            tj.jasurbek.projectlib.ui.common.GlassScrollEdge(fromTop = true, Modifier.matchParentSize().alpha(edge))
            Column {
                Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                Row(
                    Modifier.fillMaxWidth().height(BarHeight).padding(horizontal = DSSpace.md),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpace.sm),
                ) {
                    if (showBack) GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Назад", nav.back)
                    // Small title between the buttons (never under them); fades in once the large title scrolls away.
                    Text(
                        title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(start = if (showBack) 0.dp else DSSpace.xs).alpha(smallTitle).clearAndSetSemantics { },
                    )
                    actions()
                    SyncIndicator(status, compact = collapsed || showBack) {
                        if (status.state == SyncState.Unauthorized || status.state == SyncState.NotConfigured) nav.settings() else app.repo.syncNow()
                    }
                    // Signed-in user → profile (web: avatar in the header).
                    auth.me?.let { me ->
                        Box(
                            Modifier.size(48.dp).dsGlass(CircleShape).dsGlassPress().clickable(onClickLabel = "Профиль") { nav.profile() }
                                .semantics { contentDescription = "Профиль: ${me.name}" },
                            contentAlignment = Alignment.Center,
                        ) { UserAvatar(me.name, me.avatar, 36.dp) }
                    }
                }
                Spacer(Modifier.height(DSSpace.md))
            }
        }

        // The screen's create action: tinted glass FAB above the floating tab bar.
        Box(Modifier.align(Alignment.BottomEnd).padding(end = DSSpace.lg, bottom = bottom + shell.bottomBar + DSSpace.md)) { fab() }
    }
}
