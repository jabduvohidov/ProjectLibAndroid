package tj.jasurbek.projectlib.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.SearchOff
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tj.jasurbek.projectlib.App
import tj.jasurbek.projectlib.data.Idea
import tj.jasurbek.projectlib.data.IdeaLayout
import tj.jasurbek.projectlib.ui.AppScreen
import tj.jasurbek.projectlib.ui.Nav
import tj.jasurbek.projectlib.ui.common.ConfirmDialog
import tj.jasurbek.projectlib.ui.common.GroupDivider
import tj.jasurbek.projectlib.ui.common.ListGroup
import tj.jasurbek.projectlib.ui.common.LocalToast
import tj.jasurbek.projectlib.ui.common.SearchField
import tj.jasurbek.projectlib.ui.designsystem.DS
import tj.jasurbek.projectlib.ui.designsystem.DSCard
import tj.jasurbek.projectlib.ui.designsystem.DSEmptyState
import tj.jasurbek.projectlib.ui.designsystem.DSSpace
import tj.jasurbek.projectlib.ui.forms.IdeaFormSheet

private data class Target(val projectId: String, val idea: Idea)

@Composable
fun IdeasScreen(nav: Nav) {
    val app = App.instance
    val repo = app.repo
    val projects by repo.projects.collectAsStateWithLifecycle()
    val settings by app.settings.state.collectAsStateWithLifecycle()
    val toast = LocalToast.current
    var filter by remember { mutableStateOf(IdeaFilter.Open) }
    var query by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<Target?>(null) }
    var deleting by remember { mutableStateOf<Target?>(null) }

    val all = projects.flatMap { p -> p.ideas.map { p to it } }
    val q = query.trim().lowercase()
    fun matchQuery(i: Idea) = q.isEmpty() || i.title.lowercase().contains(q) || i.description.lowercase().contains(q)
    val groups = projects
        .map { p -> p to p.ideas.filter { matchQuery(it) && filter.matches(it) }.sortedWith(ideaSorter(settings.ideaSort)) }
        .filter { it.second.isNotEmpty() }

    AppScreen(title = "Идеи", nav = nav, subtitle = "${all.size} во всех проектах") {
        if (all.isEmpty()) {
            item {
                DSCard {
                    DSEmptyState(Icons.Rounded.Lightbulb, "Идей пока нет", "Откройте проект и добавьте к нему первую идею.",
                        actionLabel = "К проектам", onAction = nav.projects)
                    Spacer(Modifier.height(DSSpace.lg))
                }
            }
            return@AppScreen
        }
        item(key = "toolbar") {
            Column(verticalArrangement = Arrangement.spacedBy(DSSpace.md)) {
                SearchField(query, { query = it }, "Поиск по идеям")
                IdeasToolbar(
                    all.map { it.second }, settings.ideaLayout, app.settings::setIdeaLayout, settings.ideaSort, app.settings::setIdeaSort,
                    filter, { filter = it }, allStatusChips = true,
                )
            }
        }
        when {
            settings.ideaLayout == IdeaLayout.Board -> item(key = "board") {
                IdeaBoard(
                    all.filter { matchQuery(it.second) }.map { (p, i) -> BoardItem(p.id, i, p.name, readOnly = !p.canEdit) },
                    onEdit = { editing = Target(it.projectId, it.idea) },
                )
            }
            groups.isEmpty() -> item(key = "none") {
                DSCard {
                    DSEmptyState(Icons.Rounded.SearchOff, "Ничего не найдено", "Попробуйте другой запрос или фильтр.",
                        actionLabel = "Сбросить", onAction = { query = ""; filter = IdeaFilter.All })
                    Spacer(Modifier.height(DSSpace.lg))
                }
            }
            else -> groups.forEach { (project, ideas) ->
                item(key = "group-${project.id}") {
                    Column(verticalArrangement = Arrangement.spacedBy(DSSpace.sm)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = DSSpace.xs), verticalAlignment = Alignment.CenterVertically) {
                            Text("${project.name} · ${ideas.size}", style = MaterialTheme.typography.labelLarge, color = DS.colors.textSecondary, modifier = Modifier.weight(1f))
                            Text("Открыть проект", style = MaterialTheme.typography.bodySmall, color = DS.colors.accentText,
                                modifier = Modifier.clickable { nav.project(project.id) }.padding(DSSpace.xs))
                        }
                        ListGroup {
                            ideas.forEachIndexed { i, idea ->
                                if (i > 0) GroupDivider()
                                IdeaRow(project.id, idea, onEdit = { editing = Target(project.id, idea) }, onDelete = { deleting = Target(project.id, idea) },
                                    readOnly = !project.canEdit, showAuthor = !project.isOwn || project.shareCount > 0)
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { t ->
        IdeaFormSheet(t.idea, onDismiss = { editing = null }, onSubmit = { repo.updateIdea(t.projectId, t.idea.id, it); toast("Идея сохранена") })
    }
    deleting?.let { t ->
        ConfirmDialog("Удалить идею?", "«${t.idea.title}» будет удалена.", "Удалить идею",
            onConfirm = { repo.deleteIdea(t.projectId, t.idea.id); toast("Идея удалена") }, onDismiss = { deleting = null })
    }
}
