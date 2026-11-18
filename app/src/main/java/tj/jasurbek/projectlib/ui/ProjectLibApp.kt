package tj.jasurbek.projectlib.ui

import android.net.Uri
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
            SetupScreen()
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

        LaunchProvider(openEmbedded = nav.run) {
            Scaffold(
                containerColor = DS.colors.bg,
                // Screens handle their own insets; this Scaffold only reserves room for the bottom bar.
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    // The embedded runner takes the whole screen.
                    if (!route.startsWith("run")) {
                        NavigationBar(containerColor = DS.colors.surface) {
                            tabs(auth.isAdmin).forEach { tab ->
                                NavigationBarItem(
                                    selected = activeTab == tab.route,
                                    onClick = { navController.switchTab(tab.route) },
                                    icon = { Icon(tab.icon, null) }, label = { Text(tab.label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = DS.colors.accentText, selectedTextColor = DS.colors.accentText,
                                        indicatorColor = DS.colors.accentSubtle,
                                        unselectedIconColor = DS.colors.textTertiary, unselectedTextColor = DS.colors.textTertiary,
                                    ),
                                )
                            }
                        }
                    }
                },
            ) { padding ->
                NavHost(
                    navController, startDestination = "projects",
                    modifier = Modifier.padding(bottom = padding.calculateBottomPadding()).consumeWindowInsets(padding),
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
                // A pairing QR code was opened while the app is already set up: sign in (or switch account) with it.
                pair?.let { (server, code) ->
                    AlertDialog(
                        onDismissRequest = { app.pendingPair.value = null },
                        shape = MaterialTheme.shapes.extraLarge, containerColor = DS.colors.bg,
                        title = { Text("Войти по коду", style = MaterialTheme.typography.titleLarge) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(DSSpace.sm)) {
                                if (auth.me != null) Text("Сейчас вы вошли как ${auth.me!!.name}. Вход другим пользователем удалит копию данных с телефона.",
                                    style = MaterialTheme.typography.bodySmall, color = DS.colors.textSecondary)
                                CodeSignInForm(server.ifEmpty { app.settings.current.serverUrl }, code,
                                    onCancel = { app.pendingPair.value = null }, onDone = { app.pendingPair.value = null })
                            }
                        },
                        confirmButton = {},
                    )
                }
            }
        }
    }
}
