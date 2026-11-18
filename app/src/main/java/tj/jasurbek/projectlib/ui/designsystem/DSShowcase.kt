// Living reference of the design system + the standard app shell. Preview in Android Studio (light & dark).
package tj.jasurbek.projectlib.ui.designsystem

import android.content.res.Configuration
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import java.math.BigDecimal

/** App shell: bottom NavigationBar (≤5 top-level sections), collapsing LargeTopAppBar, optional FAB. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DSAppShell() {
    val tabs = listOf("Home" to Icons.Rounded.Home, "Cards" to Icons.Rounded.CreditCard, "Transfers" to Icons.Rounded.SwapHoriz, "Profile" to Icons.Rounded.Person)
    var selected by remember { mutableIntStateOf(0) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbar = remember { SnackbarHostState() }
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            LargeTopAppBar(
                title = { Text(tabs[selected].first) },
                actions = { IconButton(onClick = {}) { Icon(Icons.Rounded.Notifications, "Notifications") }; Box(Modifier.padding(end = DSSpace.md)) { DSAvatar("Jasur A", DSSize.avatarSm) } },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.largeTopAppBarColors(containerColor = MaterialTheme.colorScheme.background, scrolledContainerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = DS.colors.surface) {
                tabs.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = i == selected, onClick = { selected = i }, icon = { Icon(icon, null) }, label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = DS.colors.accentText, selectedTextColor = DS.colors.accentText,
                            indicatorColor = DS.colors.accentSubtle, unselectedIconColor = DS.colors.textTertiary, unselectedTextColor = DS.colors.textTertiary,
                        ),
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {}, containerColor = DS.colors.accent, contentColor = DS.colors.textOnAccent, shape = MaterialTheme.shapes.large) {
                Icon(Icons.Rounded.Add, "New transfer")
            }
        },
    ) { padding -> DSShowcaseContent(padding) }
}

@Composable
fun DSShowcaseContent(padding: PaddingValues = PaddingValues()) {
    var filter by remember { mutableStateOf("all") }
    var chip by remember { mutableStateOf("all") }
    var phone by remember { mutableStateOf("") }
    var notifications by remember { mutableStateOf(true) }
    LazyColumn(
        contentPadding = PaddingValues(start = DSSpace.lg, end = DSSpace.lg, top = padding.calculateTopPadding() + DSSpace.sm, bottom = padding.calculateBottomPadding() + DSSpace.xl),
        verticalArrangement = Arrangement.spacedBy(DSSpace.xl),
    ) {
        item {
            DSCard {
                DSAmount(BigDecimal("12500"), label = "Main account", hideable = true)
                Spacer(Modifier.height(DSSpace.md))
                Row(horizontalArrangement = Arrangement.spacedBy(DSSpace.sm)) { DSBadge("Active", DSTone.Success, dot = true); DSBadge("Premium", DSTone.Info) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(DSSpace.md)) {
                DSStatCard("Income", "+8 400", Modifier.weight(1f), "+12%")
                DSStatCard("Spent", "3 120", Modifier.weight(1f), "−4%", positive = false)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(DSSpace.sm)) {
                DSSectionHeader("Recent", actionLabel = "See all", onAction = {})
                DSCard(padding = DSSpace.xxs) {
                    DSListRow("Salary", icon = Icons.Rounded.Payments, subtitle = "Alif · 09:12") {
                        Text(DSMoney.format(BigDecimal("8400"), signed = true), color = DS.colors.success, style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"))
                    }
                    DSDivider()
                    DSListRow("Taxi", icon = Icons.Rounded.LocalTaxi, iconTone = DSTone.Neutral, subtitle = "Transport · 18:05") {
                        Text(DSMoney.format(BigDecimal("-28")), color = DS.colors.textPrimary, style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"))
                    }
                    DSDivider()
                    DSListRow("Notifications", icon = Icons.Rounded.Notifications) { Switch(notifications, { notifications = it }) }
                    DSDivider()
                    DSListRow("Delete account", icon = Icons.Rounded.Delete, destructive = true, onClick = {})
                }
            }
        }
        item {
            DSCard {
                DSSegmentedControl(listOf("all" to "All", "in" to "Income", "out" to "Expenses"), filter, { filter = it })
                Spacer(Modifier.height(DSSpace.lg))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(DSSpace.sm)) {
                    listOf("all", "food", "taxi", "shopping").forEach { DSChip(it, chip == it, { chip = it }) }
                }
                Spacer(Modifier.height(DSSpace.lg)); DSProgressBar(0.64f)
                Spacer(Modifier.height(DSSpace.lg)); DSStepDots(3, 1)
            }
        }
        item {
            DSCard {
                DSTextField("Phone", phone, { phone = it }, placeholder = "+992 93 123 45 67", hint = "We'll send a code", leadingIcon = Icons.Rounded.Phone)
                Spacer(Modifier.height(DSSpace.lg))
                DSTextField("Email", "wrong@", {}, error = "Enter a valid email")
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(DSSpace.sm)) {
                DSBanner("New feature", tone = DSTone.Info, message = "Transfers by QR are now available.")
                DSBanner("Transfer failed", tone = DSTone.Danger, message = "Insufficient funds.", actionLabel = "Try again", onAction = {})
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(DSSpace.sm)) {
                DSButton("Primary", {}, size = DSButtonSize.Lg, fullWidth = true)
                DSButton("Secondary", {}, variant = DSButtonVariant.Secondary, fullWidth = true)
                Row(horizontalArrangement = Arrangement.spacedBy(DSSpace.sm)) {
                    DSButton("Outline", {}, variant = DSButtonVariant.Outline, size = DSButtonSize.Sm)
                    DSButton("Ghost", {}, variant = DSButtonVariant.Ghost, size = DSButtonSize.Sm)
                    DSButton("Loading", {}, size = DSButtonSize.Sm, loading = true)
                    DSButton("Delete", {}, variant = DSButtonVariant.DestructiveSoft, size = DSButtonSize.Sm)
                }
            }
        }
        item { DSCard(padding = DSSpace.xxs) { repeat(3) { DSSkeletonRow() } } }
        item { DSEmptyState(Icons.Rounded.Inbox, "No transactions yet", "Your payments and transfers will appear here.", actionLabel = "Make a transfer", onAction = {}) }
    }
}

@Preview(name = "Light", showBackground = true, heightDp = 1800)
@Composable private fun ShowcaseLight() = AppTheme(darkTheme = false) { DSAppShell() }

@Preview(name = "Dark", showBackground = true, heightDp = 1800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun ShowcaseDark() = AppTheme(darkTheme = true) { DSAppShell() }
