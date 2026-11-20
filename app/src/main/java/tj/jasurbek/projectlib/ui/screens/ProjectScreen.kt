package tj.jasurbek.projectlib.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import tj.jasurbek.projectlib.ui.common.GlassDropdown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tj.jasurbek.projectlib.App
import tj.jasurbek.projectlib.data.Access
import tj.jasurbek.projectlib.data.Idea
import tj.jasurbek.projectlib.data.IdeaLayout
import tj.jasurbek.projectlib.data.Link
import tj.jasurbek.projectlib.data.LinkKind
import tj.jasurbek.projectlib.data.Platform
import tj.jasurbek.projectlib.data.Project
import tj.jasurbek.projectlib.data.ProjectStatus
import tj.jasurbek.projectlib.data.progress
import tj.jasurbek.projectlib.ui.AppScreen
import tj.jasurbek.projectlib.ui.Nav
import tj.jasurbek.projectlib.ui.common.ConfirmDialog
import tj.jasurbek.projectlib.ui.common.GroupDivider
import tj.jasurbek.projectlib.ui.common.ListGroup
import tj.jasurbek.projectlib.ui.common.PersonChip
import tj.jasurbek.projectlib.ui.common.LocalToast
import tj.jasurbek.projectlib.ui.common.ProjectStatusBadge
import tj.jasurbek.projectlib.ui.common.StatusDot
import tj.jasurbek.projectlib.ui.common.TagChip
import tj.jasurbek.projectlib.ui.common.formatDate
import tj.jasurbek.projectlib.ui.designsystem.DS
import tj.jasurbek.projectlib.ui.designsystem.DSBadge
import tj.jasurbek.projectlib.ui.designsystem.DSButton
import tj.jasurbek.projectlib.ui.designsystem.DSButtonSize
import tj.jasurbek.projectlib.ui.designsystem.DSButtonVariant
import tj.jasurbek.projectlib.ui.designsystem.DSCard
import tj.jasurbek.projectlib.ui.designsystem.DSEmptyState
import tj.jasurbek.projectlib.ui.designsystem.DSIconBox
import tj.jasurbek.projectlib.ui.designsystem.DSProgressBar
import tj.jasurbek.projectlib.ui.designsystem.DSSectionHeader
import tj.jasurbek.projectlib.ui.designsystem.DSSpace
import tj.jasurbek.projectlib.ui.designsystem.DSTone
import tj.jasurbek.projectlib.ui.forms.IdeaFormSheet
import tj.jasurbek.projectlib.ui.forms.ProjectFormSheet
import tj.jasurbek.projectlib.ui.launch.LaunchButton
import tj.jasurbek.projectlib.ui.launch.LocalLauncher
import tj.jasurbek.projectlib.ui.launch.linkIcon

/** Open / edit idea dialog state: [idea] = edit, else new idea with [status]. */
data class IdeaDialog(val idea: Idea? = null, val status: String? = null, val projectId: String)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProjectScreen(id: String, nav: Nav) {
    val app = App.instance
    val repo = app.repo
    val projects by repo.projects.collectAsStateWithLifecycle()
    val settings by app.settings.state.collectAsStateWithLifecycle()
    val toast = LocalToast.current
    val project = projects.find { it.id == id }

    var editing by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var ideaDialog by remember { mutableStateOf<IdeaDialog?>(null) }
    var ideaToDelete by remember { mutableStateOf<Idea?>(null) }
    var filter by remember { mutableStateOf(IdeaFilter.Open) }
    var menu by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    val auth by app.auth.state.collectAsStateWithLifecycle()

    if (project == null) {
        AppScreen("Проект", nav, showBack = true) {
            item {
                DSCard {
                    DSEmptyState(Icons.Rounded.FolderOff, "Проект не найден", "Возможно, он был удалён.", actionLabel = "К списку проектов", onAction = nav.projects)
                    Spacer(Modifier.height(DSSpace.lg))
                }
            }
        }
        return
    }

    val prog = project.progress()
    val canEdit = project.canEdit
    val owner = project.isOwn
    val manage = project.canManage // owner or admin
    val hasMenu = canEdit || manage
    val columns = project.columns
    val ideas = project.ideas.filter { filter.matches(it, columns) }.sortedWith(ideaSorter(settings.ideaSort, columns))

    AppScreen(
        title = project.name, nav = nav, showBack = true,
        actions = {
            if (hasMenu) Box {
                tj.jasurbek.projectlib.ui.common.GlassIconButton(Icons.Rounded.MoreVert, "Действия с проектом") { menu = true }
                GlassDropdown(expanded = menu, onDismissRequest = { menu = false }, shape = MaterialTheme.shapes.medium) {
                    if (canEdit) {
                        DropdownMenuItem(text = { Text("Изменить проект") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; editing = true })
                        HorizontalDivider(color = DS.colors.border)
                        ProjectStatus.entries.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.label) }, leadingIcon = { StatusDot(s.tone) },
                                trailingIcon = if (s == project.status) ({ Icon(Icons.Rounded.Check, null, tint = DS.colors.accentText) }) else null,
                                onClick = { menu = false; repo.setProjectStatus(project.id, s) },
                            )
                        }
                    }
                    if (manage && auth.me != null) {
                        if (canEdit) HorizontalDivider(color = DS.colors.border)
                        DropdownMenuItem(text = { Text("Поделиться…") }, leadingIcon = { Icon(Icons.Rounded.Share, null) }, onClick = { menu = false; sharing = true })
                    }
                    if (manage) {
                        HorizontalDivider(color = DS.colors.border)
                        DropdownMenuItem(
                            text = { Text("Удалить проект", color = DS.colors.danger) }, leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = DS.colors.danger) },
                            onClick = { menu = false; confirmDelete = true },
                        )
                    }
                }
            }
        },
        fab = { if (canEdit) AccentFab("Новая идея") { ideaDialog = IdeaDialog(projectId = project.id) } },
    ) {
        // Hero
        item(key = "hero") {
            DSCard {
                Column(verticalArrangement = Arrangement.spacedBy(DSSpace.lg)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(DSSpace.sm), verticalArrangement = Arrangement.spacedBy(DSSpace.sm),
                        itemVerticalAlignment = Alignment.CenterVertically) {
                        ProjectStatusBadge(project.status)
                        project.tags.forEach { TagChip(it) }
                    }
                    // Whose project and what you may do with it (web: under the title).
                    if (!owner || project.shareCount > 0) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(DSSpace.sm), verticalArrangement = Arrangement.spacedBy(DSSpace.sm),
                            itemVerticalAlignment = Alignment.CenterVertically) {
                            if (!owner) PersonChip(project.ownerId, "Владелец:")
                            if (!canEdit) DSBadge("Только просмотр", DSTone.Neutral)
                            if (project.access == Access.Admin) DSBadge("Доступ администратора", DSTone.Info)
                            if (manage && project.shareCount > 0) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpace.xs)) {
                                Icon(Icons.Rounded.Group, null, tint = DS.colors.textTertiary, modifier = Modifier.size(14.dp))
                                Text("Общий доступ: ${project.shareCount}", style = MaterialTheme.typography.labelMedium, color = DS.colors.textTertiary)
                            }
                        }
                    }
                    if (project.description.isNotEmpty()) {
                        Text(project.description, style = MaterialTheme.typography.bodyLarge, color = DS.colors.textPrimary)
                    } else if (canEdit) {
                        Text("Добавить описание", style = MaterialTheme.typography.bodyLarge, color = DS.colors.accentText,
                            modifier = Modifier.clickable { editing = true })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(DSSpace.lg)) {
                        Column(Modifier.weight(1f)) {
                            Text("Прогресс идей", style = MaterialTheme.typography.bodySmall, color = DS.colors.textSecondary)
                            Text("${prog.percent}%", style = MaterialTheme.typography.headlineSmall, color = DS.colors.textPrimary)
                            Spacer(Modifier.height(DSSpace.sm))
                            DSProgressBar(prog.percent / 100f)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Выполнено / всего", style = MaterialTheme.typography.bodySmall, color = DS.colors.textSecondary)
                            Text("${prog.done} / ${prog.total}", style = MaterialTheme.typography.headlineSmall, color = DS.colors.textPrimary)
                        }
                    }
                    Column {
                        Text("Обновлён", style = MaterialTheme.typography.bodySmall, color = DS.colors.textSecondary)
                        Text(formatDate(project.updatedAt), style = MaterialTheme.typography.bodyMedium, color = DS.colors.textPrimary)
                        Text("создан ${formatDate(project.createdAt)}", style = MaterialTheme.typography.bodySmall, color = DS.colors.textTertiary)
                    }
                }
            }
        }

        // Links
        item(key = "links") {
            Column(verticalArrangement = Arrangement.spacedBy(DSSpace.sm)) {
                DSSectionHeader("Приложения и ссылки")
                if (project.links.isEmpty()) {
                    DSCard {
                        Text(if (canEdit) "Нет ссылок. Добавьте web-версию, диплинк, магазин или локальную команду — и запускайте отсюда." else "Ссылок нет.",
                            style = MaterialTheme.typography.bodyMedium, color = DS.colors.textSecondary)
                        if (canEdit) {
                            Spacer(Modifier.height(DSSpace.md))
                            DSButton("Добавить ссылку", { editing = true }, variant = DSButtonVariant.Outline, size = DSButtonSize.Sm, leadingIcon = Icons.Rounded.Add)
                        }
                    }
                } else {
                    ListGroup {
                        project.links.forEachIndexed { i, l ->
                            if (i > 0) GroupDivider()
                            LinkRow(project, l)
                        }
                    }
                }
            }
        }

        // Ideas
        item(key = "ideas-header") {
            Column(verticalArrangement = Arrangement.spacedBy(DSSpace.md)) {
                DSSectionHeader("Идеи · ${project.ideas.size}")
                if (project.ideas.isNotEmpty()) {
                    IdeasToolbar(
                        project.ideas.map { it to columns }, settings.ideaLayout, app.settings::setIdeaLayout, settings.ideaSort, app.settings::setIdeaSort,
                        filter, { filter = it },
                    )
                }
            }
        }
        when {
            project.ideas.isEmpty() -> item(key = "ideas-empty") {
                DSCard {
                    DSEmptyState(Icons.Rounded.Lightbulb, "Идей пока нет", "Записывайте всё, что хочется сделать в проекте, и отмечайте прогресс.",
                        actionLabel = if (canEdit) "Добавить идею" else null, onAction = { ideaDialog = IdeaDialog(projectId = project.id) })
                    Spacer(Modifier.height(DSSpace.lg))
                }
            }
            settings.ideaLayout == IdeaLayout.Board -> item(key = "board") {
                IdeaBoard(
                    project.ideas.map { BoardItem(project.id, it, readOnly = !canEdit, columns = columns) },
                    projectBoard(project),
                    onEdit = { ideaDialog = IdeaDialog(it.idea, projectId = project.id) },
                    onAdd = if (canEdit) ({ ideaDialog = IdeaDialog(status = it, projectId = project.id) }) else null,
                )
            }
            ideas.isEmpty() -> item(key = "ideas-none") {
                DSCard { Text("В этом фильтре идей нет.", style = MaterialTheme.typography.bodyMedium, color = DS.colors.textSecondary,
                    modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
            }
            else -> item(key = "ideas-list") {
                ListGroup {
                    ideas.forEachIndexed { i, idea ->
                        if (i > 0) GroupDivider()
                        IdeaRow(project.id, idea, onEdit = { ideaDialog = IdeaDialog(idea, projectId = project.id) }, onDelete = { ideaToDelete = idea }, columns = columns,
                            readOnly = !canEdit, showAuthor = !owner || project.shareCount > 0)
                    }
                }
            }
        }
    }

    if (sharing && manage) ShareDialog(project, onClose = { sharing = false })
    if (editing) {
        ProjectFormSheet(project, onDismiss = { editing = false }) { repo.updateProject(project.id, it); toast("Проект сохранён") }
    }
    ideaDialog?.let { d ->
        IdeaFormSheet(d.idea, onDismiss = { ideaDialog = null }, defaultStatus = d.status, columns = project.columns, onSubmit = { input ->
            if (d.idea != null) { repo.updateIdea(project.id, d.idea.id, input); toast("Идея сохранена") }
            else { repo.addIdea(project.id, input); toast("Идея добавлена") }
        })
    }
    if (confirmDelete) {
        ConfirmDialog(
            "Удалить проект?", "«${project.name}» и все его идеи (${project.ideas.size}) будут удалены без возможности восстановления.", "Удалить проект",
            onConfirm = { repo.deleteProject(project.id); toast("Проект удалён"); nav.projects() }, onDismiss = { confirmDelete = false },
        )
    }
    ideaToDelete?.let { idea ->
        ConfirmDialog("Удалить идею?", "«${idea.title}» будет удалена.", "Удалить идею",
            onConfirm = { repo.deleteIdea(project.id, idea.id); toast("Идея удалена") }, onDismiss = { ideaToDelete = null })
    }
}

private fun describe(l: Link): String {
    val p = l.effectivePlatform
    val where = if (p != Platform.Any && p != Platform.Web) " · ${p.label}" else ""
    return if (l.kind == LinkKind.Local) "${LinkKind.Local.label}$where · ${l.command ?: ""}"
    else "${l.kind.label}$where · ${l.url.removePrefix("https://").removePrefix("http://")}"
}

@Composable
private fun LinkRow(project: Project, link: Link) {
    val launcher = LocalLauncher.current
    // Text gets the full width (phones are narrow); the launch actions sit on their own line below it,
    // aligned with the text (web: the row wraps the same way below ~400px).
    Column(Modifier.fillMaxWidth().padding(horizontal = DSSpace.lg, vertical = DSSpace.md), verticalArrangement = Arrangement.spacedBy(DSSpace.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpace.md)) {
            DSIconBox(linkIcon(link), size = 36.dp)
            Column(Modifier.weight(1f)) {
                Text(link.label, style = MaterialTheme.typography.bodyLarge, color = DS.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(describe(link), style = MaterialTheme.typography.bodySmall, color = DS.colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(Modifier.padding(start = 36.dp + DSSpace.md)) {
            if (link.kind.launchable) LaunchButton(project, link)
            else DSButton("Открыть", { launcher.open(link.url) }, variant = DSButtonVariant.Ghost, size = DSButtonSize.Sm, leadingIcon = Icons.AutoMirrored.Rounded.OpenInNew)
        }
    }
}

