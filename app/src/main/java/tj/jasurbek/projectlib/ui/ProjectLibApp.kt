package tj.jasurbek.projectlib.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import tj.jasurbek.projectlib.ui.common.GlassAlertDialog
import tj.jasurbek.projectlib.ui.common.GlassMenuHost
import tj.jasurbek.projectlib.ui.common.GlassMenuLayer
import tj.jasurbek.projectlib.ui.common.GlassToastHost
import tj.jasurbek.projectlib.ui.common.GlassTabBar
import tj.jasurbek.projectlib.ui.common.ShellAction
import tj.jasurbek.projectlib.ui.common.TabItem
import androidx.compose.runtime.mutableStateOf
import tj.jasurbek.projectlib.ui.common.LocalGlassMenuHost
import tj.jasurbek.projectlib.ui.common.NoGlassMenuHost
import tj.jasurbek.projectlib.ui.designsystem.glass.DSGlassNavItem
import tj.jasurbek.projectlib.ui.designsystem.glass.LocalDSContentGlass
import tj.jasurbek.projectlib.ui.common.glassOn
import tj.jasurbek.projectlib.ui.designsystem.glass.DSGlassNavigationBar
import tj.jasurbek.projectlib.ui.designsystem.glass.DSGlassProvider
import tj.jasurbek.projectlib.ui.designsystem.glass.DSScrollEdgeBottom
import tj.jasurbek.projectlib.ui.designsystem.glass.rememberDSGlassAvailability
import tj.jasurbek.projectlib.ui.designsystem.glass.rememberMinimizeOnScroll
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import tj.jasurbek.projectlib.App
import tj.jasurbek.projectlib.ui.common.LocalToast
import tj.jasurbek.projectlib.ui.common.Toaster
import tj.jasurbek.projectlib.ui.designsystem.DS
import tj.jasurbek.projectlib.ui.launch.LaunchProvider
import tj.jasurbek.projectlib.ui.screens.IdeasScreen
import tj.jasurbek.projectlib.ui.screens.ProjectScreen
import tj.jasurbek.projectlib.ui.screens.ProjectsScreen
import tj.jasurbek.projectlib.ui.screens.RunScreen
import tj.jasurbek.projectlib.ui.screens.SettingsScreen
import tj.jasurbek.projectlib.ui.screens.SetupScreen
import tj.jasurbek.projectlib.ui.screens.ProfileScreen
import tj.jasurbek.projectlib.ui.screens.UsersScreen
import tj.jasurbek.projectlib.ui.screens.CodeSignInForm
import tj.jasurbek.projectlib.data.SyncState
import tj.jasurbek.projectlib.ui.designsystem.DSSpace
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private fun tabs(admin: Boolean) = listOfNotNull(
    Tab("projects", "Проекты", Icons.Rounded.Folder),
    Tab("ideas", "Идеи", Icons.Rounded.Lightbulb),
    if (admin) Tab("users", "Люди", Icons.Rounded.Group) else null,
    Tab("settings", "Настройки", Icons.Rounded.Settings),
)

/** Room the floating glass tab bar takes above the system navigation (64 capsule + 8 + 8). */
private val TabBarSpace = 80.dp

private fun NavHostController.switchTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun ProjectLibApp(app: App) {
    val settings by app.settings.state.collectAsStateWithLifecycle()
    val projects by app.repo.projects.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val toaster = remember { Toaster(snackbar, scope) }
    val navController = rememberNavController()

    val auth by app.auth.state.collectAsStateWithLifecycle()
    val sync by app.repo.status.collectAsStateWithLifecycle()
    val pair by app.pendingPair.collectAsStateWithLifecycle()

    // Rejected changes etc. from the sync engine.
    LaunchedEffect(Unit) { app.repo.notices.collect { toaster(it) } }
    // The server refused our session (ended on another device, user disabled) → refresh what we know about sign-in.
    LaunchedEffect(sync.state) { if (sync.state == SyncState.Unauthorized) app.auth.refresh() }

    CompositionLocalProvider(LocalToast provides toaster) {
        // First run with nothing on the phone: connect to the server first (web: sign-in comes first without local data).
        // Signed out with nothing on the phone: there is nothing to show, so sign-in comes first (as on the web).
        val signedOut = settings.serverUrl.isNotEmpty() && settings.session.isEmpty() && settings.token.isEmpty() && auth.status?.authRequired != false
        if ((!settings.onboarded || signedOut) && projects.isEmpty() && sync.pending == 0) {
            DSGlassProvider(rememberDSGlassAvailability(userOverride = if (settings.glass) null else false)) {
                CompositionLocalProvider(LocalDSContentGlass provides (settings.contentGlass && glassOn)) { SetupScreen() }
            }
            return@CompositionLocalProvider
        }

        val nav = remember(navController) {
            Nav(
                back = { if (!navController.popBackStack()) navController.switchTab("projects") },
                project = { id -> navController.navigate("project/${Uri.encode(id)}") { launchSingleTop = true } },
                run = { p, l -> navController.navigate("run/${Uri.encode(p)}/${Uri.encode(l)}") },
                projects = { navController.switchTab("projects") },
                settings = { navController.switchTab("settings") },
                profile = { navController.navigate("profile") { launchSingleTop = true } },
            )
        }
        val entry by navController.currentBackStackEntryAsState()
        val route = entry?.destination?.route ?: "projects"
        val activeTab = when {
            route.startsWith("project") || route.startsWith("run") -> "projects"
            route == "profile" -> "settings"
            else -> route
        }

        val minimize = rememberMinimizeOnScroll()
        val actionSlot = remember { mutableStateOf<ShellAction?>(null) }
        val menus = remember { GlassMenuHost() }
        val showTabs = !route.startsWith("run")
        // Liquid Glass shell (universal-design-system, Android glass mode): one glass scope for the window; content
        // under it is solid, the navigation layer (tab bar, top buttons, FAB, menus, toasts) is glass.
        DSGlassProvider(rememberDSGlassAvailability(userOverride = if (settings.glass) null else false)) {
          CompositionLocalProvider(
              LocalDSContentGlass provides (settings.contentGlass && glassOn),
              LocalGlassMenuHost provides menus,
              LocalShell provides ShellInfo(if (showTabs) TabBarSpace else 0.dp, if (showTabs) minimize.connection else null, if (showTabs) actionSlot else null),
          ) {
            LaunchProvider(openEmbedded = nav.run) {
                Box(Modifier.fillMaxSize().background(DS.colors.bg)) {
                    NavHost(
                        navController, startDestination = "projects", modifier = Modifier.fillMaxSize(),
                        enterTransition = { fadeIn() }, exitTransition = { fadeOut() },
                    ) {
                        composable("projects") { ProjectsScreen(nav) }
                        composable("ideas") { IdeasScreen(nav) }
                        composable("settings") { SettingsScreen(nav) }
                        composable("profile") { ProfileScreen(nav) }
                        composable("users") { UsersScreen(nav) }
                        composable("project/{id}") { e -> ProjectScreen(e.arguments?.getString("id").orEmpty(), nav) }
                        composable("run/{id}/{linkId}") { e ->
                            RunScreen(e.arguments?.getString("id").orEmpty(), e.arguments?.getString("linkId").orEmpty(), onBack = nav.back)
                        }
                    }

                    if (showTabs) {
                        // Content dissolves under the floating capsule instead of hitting a solid bar.
                        tj.jasurbek.projectlib.ui.common.GlassScrollEdge(fromTop = false,
                            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                                .height(TabBarSpace + 48.dp + androidx.compose.foundation.layout.WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
                        GlassTabBar(
                            tabs(auth.isAdmin).map { TabItem(it.route, it.label, it.icon) },
                            selectedId = activeTab,
                            // Same tab on a pushed screen (a project) → back to its list, like iOS; the open list itself → nothing.
                            onSelect = { minimize.restore(); if (route != it) navController.switchTab(it) },
                            minimized = minimize.minimized,
                            onRestore = minimize::restore,
                            action = actionSlot.value,
                            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
                        )
                    }
                    GlassToastHost(
                        snackbar,
                        Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = if (showTabs) TabBarSpace + DSSpace.sm else DSSpace.lg),
                    )
                    GlassMenuLayer(menus)
                }

                // A pairing QR code was opened while the app is already set up: sign in (or switch account) with it.
                pair?.let { (server, code) ->
                    GlassAlertDialog(
                        onDismissRequest = { app.pendingPair.value = null },
                        title = { Text("Войти по коду", style = MaterialTheme.typography.titleLarge) },
                        text = {
                            NoGlassMenuHost {
                                Column(verticalArrangement = Arrangement.spacedBy(DSSpace.sm)) {
                                    if (auth.me != null) Text("Сейчас вы вошли как ${auth.me!!.name}. Вход другим пользователем удалит копию данных с телефона.",
                                        style = MaterialTheme.typography.bodySmall, color = DS.colors.textSecondary)
                                    CodeSignInForm(server.ifEmpty { app.settings.current.serverUrl }, code,
                                        onCancel = { app.pendingPair.value = null }, onDone = { app.pendingPair.value = null })
                                }
                            }
                        },
                        confirmButton = {},
                    )
                }
            }
          }
        }
    }
}
