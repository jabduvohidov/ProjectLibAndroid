package tj.jasurbek.projectlib.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VerifiedUser
import tj.jasurbek.projectlib.ui.common.GlassAlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import tj.jasurbek.projectlib.ui.common.GlassDropdown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import tj.jasurbek.projectlib.App
import tj.jasurbek.projectlib.data.AdminUser
import tj.jasurbek.projectlib.data.HttpFailure
import tj.jasurbek.projectlib.data.Invite
import tj.jasurbek.projectlib.data.Role
import tj.jasurbek.projectlib.data.agoLabel
import tj.jasurbek.projectlib.ui.AppScreen
import tj.jasurbek.projectlib.ui.Nav
import tj.jasurbek.projectlib.ui.common.ConfirmDialog
import tj.jasurbek.projectlib.ui.common.GroupDivider
import tj.jasurbek.projectlib.ui.common.ListGroup
import tj.jasurbek.projectlib.ui.common.LocalToast
import tj.jasurbek.projectlib.ui.common.UserAvatar
import tj.jasurbek.projectlib.ui.designsystem.DS
import tj.jasurbek.projectlib.ui.designsystem.DSBadge
import tj.jasurbek.projectlib.ui.designsystem.DSBanner
import tj.jasurbek.projectlib.ui.designsystem.DSButton
import tj.jasurbek.projectlib.ui.designsystem.DSButtonSize
import tj.jasurbek.projectlib.ui.designsystem.DSButtonVariant
import tj.jasurbek.projectlib.ui.designsystem.DSChip
import tj.jasurbek.projectlib.ui.designsystem.DSSectionHeader
import tj.jasurbek.projectlib.ui.designsystem.DSSkeleton
import tj.jasurbek.projectlib.ui.designsystem.DSSpace
import tj.jasurbek.projectlib.ui.designsystem.DSTextField
import tj.jasurbek.projectlib.ui.designsystem.DSTone
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun fmtShort(t: Long) = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("ru")))

/** Admin: people, roles, invites — port of the web UsersPage. Needs a connection (all of it lives on the server). */
@Composable
fun UsersScreen(nav: Nav) {
    val app = App.instance
    val auth by app.auth.state.collectAsStateWithLifecycle()
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    var users by remember { mutableStateOf<List<AdminUser>?>(null) }
    var invites by remember { mutableStateOf<List<Invite>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var inviting by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<AdminUser?>(null) }

    LaunchedEffect(reload) {
        try { users = app.api.users(); invites = app.api.invites(); error = null }
        catch (e: HttpFailure) { error = e.message; if (users == null) users = emptyList() }
    }
    fun act(ok: String, block: suspend () -> Unit) = scope.launch {
        try { block(); toast(ok); reload++; app.auth.refreshPeople() } catch (e: HttpFailure) { toast(e.message ?: "Ошибка") }
    }

    AppScreen(
        "Люди", nav, subtitle = users?.let { "${it.size} в ProjectLib" },
        fab = { if (auth.isAdmin) AccentFab("Пригласить", Icons.Rounded.PersonAdd) { inviting = true } },
    ) {
        if (auth.me != null && !auth.isAdmin) { item { DSBanner("Только для администратора", tone = DSTone.Warning) }; return@AppScreen }
        error?.let { item(key = "error") { DSBanner("Не удалось загрузить", tone = DSTone.Danger, message = it, actionLabel = "Повторить", onAction = { reload++ }) } }

        item(key = "users") {
            Column(verticalArrangement = Arrangement.spacedBy(DSSpace.sm)) {
                ListGroup {
                    val list = users
                    if (list == null) Column(Modifier.padding(DSSpace.lg), verticalArrangement = Arrangement.spacedBy(DSSpace.md)) {
                        repeat(2) { DSSkeleton(Modifier.fillMaxWidth().height(40.dp)) }
                    } else list.forEachIndexed { i, u ->
                        if (i > 0) GroupDivider()
                        UserRow(
                            u, self = u.id == auth.me?.id,
                            onRole = { r -> act("Роль изменена") { app.api.setRole(u.id, r) } },
                            onDisable = { off -> act(if (off) "Пользователь отключён" else "Пользователь включён") { app.api.setDisabled(u.id, off) } },
                            onDelete = { toDelete = u },
                        )
                    }
                }
                Row(Modifier.padding(horizontal = DSSpace.xs), horizontalArrangement = Arrangement.spacedBy(DSSpace.xs)) {
                    Icon(Icons.Rounded.VerifiedUser, null, tint = DS.colors.textTertiary, modifier = Modifier.size(14.dp).padding(top = 2.dp))
                    Text("Администраторы видят и могут менять все проекты всех пользователей. Участники и читатели видят только свои проекты и те, которыми с ними поделились.",
                        style = MaterialTheme.typography.bodySmall, color = DS.colors.textTertiary)
                }
            }
        }

        item(key = "invites") {
            Column(verticalArrangement = Arrangement.spacedBy(DSSpace.sm)) {
                DSSectionHeader("Активные приглашения")
                Text("Одноразовые ссылки, действуют 7 дней. Человек открывает ссылку на сайте и создаёт passkey, а телефон потом подключает кодом из профиля.",
                    style = MaterialTheme.typography.bodySmall, color = DS.colors.textTertiary)
                if (invites.isEmpty()) Text("Нет активных приглашений.", style = MaterialTheme.typography.bodyMedium, color = DS.colors.textSecondary, modifier = Modifier.padding(horizontal = DSSpace.xs))
                else ListGroup {
                    invites.forEachIndexed { i, inv ->
                        if (i > 0) GroupDivider()
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = DSSpace.lg, end = DSSpace.sm, top = DSSpace.sm, bottom = DSSpace.sm),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpace.md)) {
                            Icon(Icons.Rounded.Link, null, tint = DS.colors.textTertiary)
                            Column(Modifier.weight(1f)) {
                                Text("${inv.name.ifEmpty { "Без имени" }} · ${inv.role.label}", style = MaterialTheme.typography.bodyLarge, color = DS.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("до ${fmtShort(inv.expiresAt)} · создал ${inv.createdBy}", style = MaterialTheme.typography.bodySmall, color = DS.colors.textSecondary)
                            }
                            DSButton("Отозвать", { act("Приглашение отозвано") { app.api.revokeInvite(inv.id) } },
                                variant = DSButtonVariant.Ghost, size = DSButtonSize.Sm, leadingIcon = Icons.Rounded.Close)
                        }
                    }
                }
            }
        }
    }

    if (inviting) InviteDialog(onClose = { inviting = false; reload++ })
    toDelete?.let { u ->
        ConfirmDialog(
            "Удалить ${u.name}?", "Аккаунт, ключи и сеансы будут удалены. Его проекты (${u.projects}) перейдут к вам — с теми же доступами для других людей.",
            "Удалить пользователя",
            onConfirm = { act("Пользователь удалён") { app.api.deleteUser(u.id); app.repo.syncNow() } }, onDismiss = { toDelete = null },
        )
    }
}

@Composable
private fun UserRow(u: AdminUser, self: Boolean, onRole: (Role) -> Unit, onDisable: (Boolean) -> Unit, onDelete: () -> Unit) {
    var roleMenu by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).alpha(if (u.disabled) 0.6f else 1f).padding(start = DSSpace.lg, end = DSSpace.xs, top = DSSpace.sm, bottom = DSSpace.sm),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpace.md),
    ) {
        UserAvatar(u.name, u.avatar, 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(if (self) "${u.name} · вы" else u.name, style = MaterialTheme.typography.bodyLarge, color = DS.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                (if (u.email.isNotEmpty()) "${u.email} · " else "") + (if (u.disabled) "отключён" else agoLabel(u.lastSeenAt)) + " · проектов ${u.projects} · ключей ${u.passkeys}",
                style = MaterialTheme.typography.bodySmall, color = DS.colors.textSecondary, maxLines = 2,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(DSSpace.xs), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    TextButton(onClick = { roleMenu = true }) {
                        Text(u.role.label, color = DS.colors.accentText, style = MaterialTheme.typography.labelLarge)
                        Icon(Icons.Rounded.KeyboardArrowDown, "Роль: ${u.role.label}. Изменить", tint = DS.colors.accentText, modifier = Modifier.size(18.dp))
                    }
                    GlassDropdown(expanded = roleMenu, onDismissRequest = { roleMenu = false }, shape = MaterialTheme.shapes.medium) {
                        Role.entries.forEach { r ->
                            DropdownMenuItem(
                                text = { Column { Text(r.label); Text(r.hint, style = MaterialTheme.typography.bodySmall, color = DS.colors.textSecondary) } },
                                trailingIcon = if (r == u.role) ({ Icon(Icons.Rounded.Check, null, tint = DS.colors.accentText) }) else null,
                                onClick = { roleMenu = false; if (r != u.role) onRole(r) },
                            )
                        }
                    }
                }
                if (u.disabled) DSBadge("Отключён", DSTone.Danger)
            }
        }
        if (!self) Box {
            tj.jasurbek.projectlib.ui.common.ContentIconButton(Icons.Rounded.MoreVert, "Действия: ${u.name}", DS.colors.textTertiary) { menu = true }
            GlassDropdown(expanded = menu, onDismissRequest = { menu = false }, shape = MaterialTheme.shapes.medium) {
                if (u.disabled) DropdownMenuItem(text = { Text("Включить") }, leadingIcon = { Icon(Icons.Rounded.Check, null) }, onClick = { menu = false; onDisable(false) })
                else DropdownMenuItem(text = { Text("Отключить") }, leadingIcon = { Icon(Icons.Rounded.Block, null) }, onClick = { menu = false; onDisable(true) })
                HorizontalDivider(color = DS.colors.border)
                DropdownMenuItem(text = { Text("Удалить", color = DS.colors.danger) }, leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = DS.colors.danger) },
                    onClick = { menu = false; onDelete() })
            }
        }
    }
}

@Composable
private fun InviteDialog(onClose: () -> Unit) {
    val app = App.instance
    val context = LocalContext.current
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    var role by remember { mutableStateOf(Role.Member) }
    var name by remember { mutableStateOf("") }
    var link by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    GlassAlertDialog(
        onDismissRequest = onClose, shape = MaterialTheme.shapes.extraLarge, containerColor = DS.colors.surface,
        title = { Text("Пригласить в ProjectLib", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(DSSpace.md)) {
                if (link == null) {
                    Text("Человек откроет ссылку на сайте и сразу создаст свой passkey.", style = MaterialTheme.typography.bodyMedium, color = DS.colors.textSecondary)
                    Text("Роль", style = MaterialTheme.typography.labelLarge, color = DS.colors.textSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(DSSpace.xs)) {
                        Role.entries.forEach { r -> DSChip(r.label, selected = role == r, soft = true, onClick = { role = r }) }
                    }
                    Text(role.hint, style = MaterialTheme.typography.bodySmall, color = DS.colors.textTertiary)
                    DSTextField("Для кого", name, { name = it.take(60) }, placeholder = "Например, Борис", hint = "Необязательно — чтобы узнать приглашение в списке")
                } else {
                    Text("Отправьте ссылку человеку. Она одноразовая и действует 7 дней.", style = MaterialTheme.typography.bodyMedium, color = DS.colors.textSecondary)
                    Text(link!!, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = DS.colors.textPrimary,
                        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(DS.colors.fill).padding(DSSpace.md))
                    Row(horizontalArrangement = Arrangement.spacedBy(DSSpace.sm)) {
                        DSButton("Скопировать", {
                            context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Приглашение", link))
                            toast("Ссылка скопирована")
                        }, variant = DSButtonVariant.Outline, size = DSButtonSize.Sm, leadingIcon = Icons.Rounded.ContentCopy)
                        DSButton("Отправить", {
                            context.startActivity(Intent.createChooser(
                                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link).putExtra(Intent.EXTRA_SUBJECT, "Приглашение в ProjectLib"),
                                "Отправить приглашение",
                            ))
                        }, variant = DSButtonVariant.Outline, size = DSButtonSize.Sm, leadingIcon = Icons.Rounded.Share)
                    }
                    Text("Ссылка показывается один раз. Потеряли — отзовите и создайте новую.", style = MaterialTheme.typography.bodySmall, color = DS.colors.textTertiary)
                }
            }
        },
        confirmButton = {
            if (link == null) DSButton("Создать ссылку", {
                busy = true
                scope.launch {
                    try { link = app.api.inviteUrl(app.api.createInvite(role, name.trim()).token) }
                    catch (e: HttpFailure) { toast(e.message ?: "Ошибка") } finally { busy = false }
                }
            }, loading = busy)
            else DSButton("Готово", onClose)
        },
        dismissButton = { if (link == null) DSButton("Отмена", onClose, variant = DSButtonVariant.Secondary) },
    )
}
