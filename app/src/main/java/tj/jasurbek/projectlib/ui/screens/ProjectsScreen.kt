package tj.jasurbek.projectlib.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tj.jasurbek.projectlib.App
import tj.jasurbek.projectlib.data.DueState
import tj.jasurbek.projectlib.data.of
import tj.jasurbek.projectlib.data.Project
import tj.jasurbek.projectlib.data.ProjectStatus
import tj.jasurbek.projectlib.data.SyncState
import tj.jasurbek.projectlib.data.dueState
import tj.jasurbek.projectlib.data.progress
import tj.jasurbek.projectlib.data.todayISO
import tj.jasurbek.projectlib.ui.AppScreen
import tj.jasurbek.projectlib.ui.Nav
import tj.jasurbek.projectlib.ui.common.ChipRow
import tj.jasurbek.projectlib.ui.common.CountChip
import tj.jasurbek.projectlib.ui.common.LocalToast
import tj.jasurbek.projectlib.ui.common.PersonChip
import tj.jasurbek.projectlib.ui.common.ProjectStatusBadge
import tj.jasurbek.projectlib.ui.common.SearchField
import tj.jasurbek.projectlib.ui.common.TagChip
import tj.jasurbek.projectlib.ui.designsystem.DS
import tj.jasurbek.projectlib.ui.designsystem.DSButton
import tj.jasurbek.projectlib.ui.designsystem.DSButtonSize
import tj.jasurbek.projectlib.ui.designsystem.DSButtonVariant
import tj.jasurbek.projectlib.ui.designsystem.DSCard
import tj.jasurbek.projectlib.ui.designsystem.DSEmptyState
import tj.jasurbek.projectlib.ui.designsystem.DSProgressBar
import tj.jasurbek.projectlib.ui.designsystem.DSSkeleton
import tj.jasurbek.projectlib.ui.designsystem.DSSpace
import tj.jasurbek.projectlib.ui.forms.ProjectFormSheet
import tj.jasurbek.projectlib.ui.launch.LaunchButton
import tj.jasurbek.projectlib.ui.launch.PlatformIcons

/**
 * The screen's one create action. With the tab bar on screen it becomes the round glass "+" next to the bar
 * (iOS 27, like the search button); without it, the design system's tinted glass FAB. [text] is what TalkBack says.
 */
@Composable
fun AccentFab(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Rounded.Add, onClick: () -> Unit) {
    val slot = tj.jasurbek.projectlib.ui.LocalShell.current.actionSlot
    if (slot == null) { tj.jasurbek.projectlib.ui.designsystem.glass.DSGlassFab(icon, text, onClick); return }
    val click = androidx.compose.runtime.rememberUpdatedState(onClick)
    androidx.compose.runtime.DisposableEffect(slot, text, icon) {
        val action = tj.jasurbek.projectlib.ui.common.ShellAction(icon, text) { click.value() }
        slot.value = action
        // During a screen transition the new screen sets its action first; the old one must not clear it.
        onDispose { if (slot.value === action) slot.value = null }
    }
}

@Composable
fun ProjectsScreen(nav: Nav) {
    val repo = App.instance.repo
    val projects by repo.shownProjects.collectAsStateWithLifecycle()
    val sync by repo.status.collectAsStateWithLifecycle()
    val toast = LocalToast.current
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf<ProjectStatus?>(null) } // null = all active
    var creating by rememberSaveable { mutableStateOf(false) }
    var whose by rememberSaveable { mutableStateOf("all") } // all | mine | shared
    val auth by App.instance.auth.state.collectAsStateWithLifecycle()
    val canCreate = auth.canWrite
    val sharedCount = projects.count { !it.isOwn }

    val q = query.trim().lowercase()
    val visible = projects.filter { p ->
        (if (filter == null) p.status != ProjectStatus.Archived else p.status == filter) &&
            (whose == "all" || (whose == "mine") == p.isOwn) &&
            (q.isEmpty() || (listOf(p.name, p.description) + p.tags).any { it.lowercase().contains(q) })
    }

    AppScreen(
        title = "Проекты", nav = nav, subtitle = "${projects.size} в библиотеке",
        fab = { if (canCreate) AccentFab("Новый проект") { creating = true } },
        actions = { if (canCreate) tj.jasurbek.projectlib.ui.common.SecretLockAction() },
    ) {
        when {
            projects.isEmpty() && !sync.everSynced && sync.state == SyncState.Syncing -> items(3) { SkeletonCard() }
            projects.isEmpty() -> item {
                DSCard {
                    DSEmptyState(Icons.Rounded.CreateNewFolder, "Пока нет проектов",
                        if (canCreate) "Добавьте первый проект — потом к нему можно будет прикреплять идеи и ссылки." else "Здесь появятся проекты, которыми с вами поделятся.",
                        actionLabel = if (canCreate) "Добавить проект" else null, onAction = { creating = true })
                    if (canCreate) {
                        Spacer(Modifier.height(DSSpace.sm))
                        DSButton("Загрузить примеры", { repo.resetToDemo(); toast("Демо-данные загружены") },
                            Modifier.align(Alignment.CenterHorizontally), variant = DSButtonVariant.Ghost, size = DSButtonSize.Sm)
                    }
                    Spacer(Modifier.height(DSSpace.lg))
                }
            }
            else -> {
                item(key = "filters") {
                    Column(verticalArrangement = Arrangement.spacedBy(DSSpace.md)) {
                        SearchField(query, { query = it }, "Поиск по названию, описанию, тегам")
                        ChipRow {
                            if (sharedCount > 0) {
                                CountChip("Мои", whose == "mine") { whose = if (whose == "mine") "all" else "mine" }
                                CountChip(if (auth.isAdmin) "Других людей" else "Со мной поделились", whose == "shared", sharedCount) {
                                    whose = if (whose == "shared") "all" else "shared"
                                }
                                Box(Modifier.width(1.dp).height(24.dp).background(DS.colors.border))
                            }
                            CountChip("Все активные", filter == null) { filter = null }
                            ProjectStatus.entries.forEach { s ->
                                CountChip(s.label, filter == s, projects.count { it.status == s }) { filter = s }
                            }
                        }
                    }
                }
                if (visible.isEmpty()) item {
                    DSCard {
                        DSEmptyState(Icons.Rounded.SearchOff, "Ничего не найдено", "Попробуйте другой запрос или фильтр.",
                            actionLabel = "Сбросить", onAction = { query = ""; filter = null; whose = "all" })
                        Spacer(Modifier.height(DSSpace.lg))
                    }
                } else items(visible, key = { it.id }) { p -> ProjectCard(p) { nav.project(p.id) } }
            }
        }
    }

    if (creating) {
        ProjectFormSheet(null, onDismiss = { creating = false }) { input ->
            val p = repo.createProject(input)
            toast("Проект «${p.name}» создан")
            nav.project(p.id)
        }
    }
}

@Composable
private fun SkeletonCard() {
    DSCard {
        Column(verticalArrangement = Arrangement.spacedBy(DSSpace.md)) {
            DSSkeleton(Modifier.fillMaxWidth(0.66f).height(22.dp))
            DSSkeleton(Modifier.fillMaxWidth().height(16.dp))
            DSSkeleton(Modifier.fillMaxWidth(0.8f).height(16.dp))
            DSSkeleton(Modifier.fillMaxWidth().height(4.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProjectCard(p: Project, onOpen: () -> Unit) {
    val prog = p.progress()
    val columns = p.columns
    // "In progress": ideas in a started column (0 % < percent < 100 %) that counts toward progress.
    val active = p.ideas.count { val s = columns.of(it.status); s.excluded != true && s.percent in 1..99 }
    val today = todayISO()
    val overdue = p.ideas.count { it.dueState(columns, today) == DueState.Overdue }
    DSCard(onClick = onOpen) {
        Column(verticalArrangement = Arrangement.spacedBy(DSSpace.md)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(DSSpace.md)) {
                if (p.secret == true) Icon(Icons.Rounded.Lock, "Секретный", tint = DS.colors.textTertiary, modifier = Modifier.padding(top = 3.dp).size(16.dp))
                Text(p.name, style = MaterialTheme.typography.titleMedium, color = DS.colors.textPrimary, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                ProjectStatusBadge(p.status)
            }
            // Shared projects: whose it is / how many people see yours (web: under the title).
            if (!p.isOwn || p.shareCount > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpace.sm)) {
                    if (!p.isOwn) PersonChip(p.ownerId, "от")
                    else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpace.xs)) {
                        Icon(Icons.Rounded.Group, null, tint = DS.colors.textTertiary, modifier = Modifier.size(14.dp))
                        Text("Общий · ${p.shareCount}", style = MaterialTheme.typography.labelMedium, color = DS.colors.textTertiary)
                    }
                    if (!p.canEdit) Text("· только просмотр", style = MaterialTheme.typography.labelMedium, color = DS.colors.textTertiary)
                }
            }
            if (p.description.isNotEmpty()) {
                Text(p.description, style = MaterialTheme.typography.bodyMedium, color = DS.colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (p.tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(DSSpace.xs), verticalArrangement = Arrangement.spacedBy(DSSpace.xs)) { p.tags.forEach { TagChip(it) } }
            }
            Column(verticalArrangement = Arrangement.spacedBy(DSSpace.sm)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Идеи: ${prog.done}/${prog.total} выполнено", style = MaterialTheme.typography.bodySmall, color = DS.colors.textSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(DSSpace.sm)) {
                        if (overdue > 0) Text("$overdue просрочено", style = MaterialTheme.typography.bodySmall, color = DS.colors.danger)
                        if (active > 0) Text("$active в процессе", style = MaterialTheme.typography.bodySmall, color = DS.colors.warning)
                    }
                }
                DSProgressBar(prog.percent / 100f)
            }
            if (p.launchLinks.isNotEmpty()) {
                HorizontalDivider(color = DS.colors.border)
                Row(Modifier.fillMaxWidth().padding(top = 0.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    PlatformIcons(p)
                    LaunchButton(p, compact = true)
                }
            }
        }
    }
}
