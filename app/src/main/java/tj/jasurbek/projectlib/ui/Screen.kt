package tj.jasurbek.projectlib.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tj.jasurbek.projectlib.App
import tj.jasurbek.projectlib.data.SyncState
import tj.jasurbek.projectlib.ui.common.SyncIndicator
import tj.jasurbek.projectlib.ui.common.UserAvatar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import tj.jasurbek.projectlib.ui.designsystem.DS
import tj.jasurbek.projectlib.ui.designsystem.DSSpace

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
 * Screen skeleton (references/android.md): Scaffold + collapsing LargeTopAppBar on the background color,
 * sync indicator top-right, optional back arrow, optional FAB, LazyColumn of sections with 16dp sides.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = DS.colors.bg,
        topBar = {
            LargeTopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (showBack) IconButton(onClick = nav.back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") }
                },
                actions = {
                    actions()
                    SyncIndicator(status) {
                        if (status.state == SyncState.Unauthorized || status.state == SyncState.NotConfigured) nav.settings() else app.repo.syncNow()
                    }
                    // Signed-in user → profile (web: avatar in the header).
                    auth.me?.let { me ->
                        Box(Modifier.padding(end = DSSpace.sm).size(48.dp).clip(CircleShape).clickable(onClickLabel = "Профиль") { nav.profile() }
                            .semantics { contentDescription = "Профиль: ${me.name}" }, contentAlignment = Alignment.Center) {
                            UserAvatar(me.name, me.avatar, 32.dp)
                        }
                    }
                },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = DS.colors.bg, scrolledContainerColor = DS.colors.bg,
                    titleContentColor = DS.colors.textPrimary, navigationIconContentColor = DS.colors.textPrimary,
                    actionIconContentColor = DS.colors.textPrimary,
                ),
            )
        },
        floatingActionButton = fab,
    ) { padding ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = DSSpace.lg, end = DSSpace.lg,
                top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + DSSpace.s4xl + DSSpace.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(DSSpace.xl),
        ) {
            if (subtitle != null) item(key = "subtitle") {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = DS.colors.textSecondary)
            }
            content()
        }
    }
}
