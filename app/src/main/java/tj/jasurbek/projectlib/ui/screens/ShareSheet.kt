package tj.jasurbek.projectlib.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import tj.jasurbek.projectlib.App
import tj.jasurbek.projectlib.data.HttpFailure
import tj.jasurbek.projectlib.data.Project
import tj.jasurbek.projectlib.data.ShareAccess
import tj.jasurbek.projectlib.ui.common.GroupDivider
import tj.jasurbek.projectlib.ui.common.ListGroup
import tj.jasurbek.projectlib.ui.common.LocalToast
import tj.jasurbek.projectlib.ui.common.UserAvatar
import tj.jasurbek.projectlib.ui.designsystem.DS
import tj.jasurbek.projectlib.ui.designsystem.DSButton
import tj.jasurbek.projectlib.ui.designsystem.DSEmptyState
import tj.jasurbek.projectlib.ui.designsystem.DSSpace

/** Owner / admin chooses who else sees or edits a project. Needs a connection (access is decided by the server). */
@Composable
fun ShareDialog(project: Project, onClose: () -> Unit) {
    val app = App.instance
    val auth by app.auth.state.collectAsStateWithLifecycle()
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { app.auth.refreshPeople() }

    val others = auth.people.filter { it.id != auth.me?.id && it.id != project.ownerId }
    fun levelOf(userId: String) = project.shares?.find { it.userId == userId }?.access

    AlertDialog(
        onDismissRequest = onClose, shape = MaterialTheme.shapes.extraLarge, containerColor = DS.colors.surface,
        title = { Text("Доступ к «${project.name}»", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(DSSpace.md)) {
                Text("Другие люди увидят проект со всеми идеями и ссылками. Читатели всегда только смотрят, даже с доступом «Редактирование». Администраторы и так видят все проекты.",
                    style = MaterialTheme.typography.bodySmall, color = DS.colors.textSecondary)
                if (others.isEmpty()) DSEmptyState(Icons.Rounded.Group, "Пока не с кем поделиться", "Администратор может пригласить людей в разделе «Люди».")
                else ListGroup {
                    others.forEachIndexed { i, p ->
                        if (i > 0) GroupDivider()
                        var menu by remember { mutableStateOf(false) }
                        val level = levelOf(p.id)
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = DSSpace.md, end = DSSpace.xs),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpace.md)) {
                            UserAvatar(p.name, p.avatar, 32.dp)
                            Text(p.name, style = MaterialTheme.typography.bodyLarge, color = DS.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Box {
                                if (busy == p.id) CircularProgressIndicator(Modifier.padding(DSSpace.md).size(20.dp), strokeWidth = 2.dp, color = DS.colors.accent)
                                else TextButton(onClick = { menu = true }) {
                                    Text(level?.label ?: "Нет доступа", color = if (level == null) DS.colors.textSecondary else DS.colors.accentText, style = MaterialTheme.typography.labelLarge)
                                    Icon(Icons.Rounded.KeyboardArrowDown, "Доступ для ${p.name}", tint = DS.colors.textTertiary, modifier = Modifier.size(18.dp))
                                }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = MaterialTheme.shapes.medium) {
                                    (listOf<ShareAccess?>(null) + ShareAccess.entries).forEach { a ->
                                        DropdownMenuItem(
                                            text = { Text(a?.label ?: "Нет доступа") },
                                            trailingIcon = if (a == level) ({ Icon(Icons.Rounded.Check, null, tint = DS.colors.accentText) }) else null,
                                            onClick = {
                                                menu = false
                                                if (a == level) return@DropdownMenuItem
                                                busy = p.id
                                                scope.launch {
                                                    try {
                                                        app.api.setShare(project.id, p.id, a)
                                                        app.repo.syncNow() // pull the updated share list
                                                        toast(if (a == null) "Доступ закрыт" else "Доступ: ${a.label.lowercase()}")
                                                    } catch (e: HttpFailure) { toast(e.message ?: "Ошибка") } finally { busy = null }
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { DSButton("Готово", onClose) },
    )
}
