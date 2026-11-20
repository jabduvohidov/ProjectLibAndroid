package tj.jasurbek.projectlib.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Smartphone
import tj.jasurbek.projectlib.ui.common.GlassAlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tj.jasurbek.projectlib.App
import tj.jasurbek.projectlib.data.DeviceCode
import tj.jasurbek.projectlib.data.DeviceSession
import tj.jasurbek.projectlib.data.HttpFailure
import tj.jasurbek.projectlib.data.Passkey
import tj.jasurbek.projectlib.data.Role
import tj.jasurbek.projectlib.data.deviceOf
import tj.jasurbek.projectlib.ui.AppScreen
import tj.jasurbek.projectlib.ui.Nav
import tj.jasurbek.projectlib.ui.common.ConfirmDialog
import tj.jasurbek.projectlib.ui.common.GroupDivider
import tj.jasurbek.projectlib.ui.common.ListGroup
import tj.jasurbek.projectlib.ui.common.LocalToast
import tj.jasurbek.projectlib.ui.common.UserAvatar
import tj.jasurbek.projectlib.ui.common.formatDate
import tj.jasurbek.projectlib.ui.designsystem.DS
import tj.jasurbek.projectlib.ui.designsystem.DSBadge
import tj.jasurbek.projectlib.ui.designsystem.DSBanner
import tj.jasurbek.projectlib.ui.designsystem.DSButton
import tj.jasurbek.projectlib.ui.designsystem.DSButtonSize
import tj.jasurbek.projectlib.ui.designsystem.DSButtonVariant
import tj.jasurbek.projectlib.ui.designsystem.DSCard
import tj.jasurbek.projectlib.ui.designsystem.DSIconBox
import tj.jasurbek.projectlib.ui.designsystem.DSSectionHeader
import tj.jasurbek.projectlib.ui.designsystem.DSSkeleton
import tj.jasurbek.projectlib.ui.designsystem.DSSpace
import tj.jasurbek.projectlib.ui.designsystem.DSTextField
import tj.jasurbek.projectlib.ui.designsystem.DSTone
import tj.jasurbek.projectlib.ui.forms.FormField
import tj.jasurbek.projectlib.ui.forms.readPickedImage
import tj.jasurbek.projectlib.ui.launch.qrBitmap
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val RU = Locale.forLanguageTag("ru")
private fun fmtTime(t: Long) = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM, HH:mm", RU))

/** Profile — port of the web ProfilePage: card, phone pairing, devices, passkeys, sign-out. */
@Composable
fun ProfileScreen(nav: Nav) {
    val app = App.instance
    val auth by app.auth.state.collectAsStateWithLifecycle()
    val me = auth.me

    AppScreen("Профиль", nav, showBack = true) {
        if (me == null) {
            item {
                DSBanner(
                    if (auth.status?.bootstrap == true) "Пользователей пока нет" else "Вы не вошли",
                    message = if (auth.status?.bootstrap == true) "Создайте первого пользователя (passkey) в web-версии — он станет администратором." else "Профиль доступен после входа.",
                    actionLabel = "К настройкам", onAction = nav.settings,
                )
            }
            return@AppScreen
        }
        item(key = "card-${me.id}") { ProfileCard() }
        item(key = "phone") { PhoneSection() }
        item(key = "sessions") { SessionsSection() }
        item(key = "passkeys") { PasskeysSection() }
        item(key = "signout") { SignOutSection(nav) }
    }
}

@Composable
private fun ProfileCard() {
    val app = App.instance
    val toast = LocalToast.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth by app.auth.state.collectAsStateWithLifecycle()
    val me = auth.me ?: return
    var name by remember(me.id) { mutableStateOf(me.name) }
    var email by remember(me.id) { mutableStateOf(me.email) }
    var saving by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val dirty = name.trim() != me.name || email.trim() != me.email

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        uploading = true
        scope.launch {
            try {
                val f = withContext(Dispatchers.IO) { readPickedImage(context, uri) }
                val a = app.repo.upload(f.bytes, f.type, f.name)
                app.auth.updateProfile(avatar = a.id)
                toast("Фото обновлено")
            } catch (e: Exception) { toast(e.message ?: "Не удалось загрузить фото") } finally { uploading = false }
        }
    }

    DSCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpace.lg)) {
            Box {
                UserAvatar(me.name, me.avatar, 72.dp)
                Box(
                    Modifier.align(Alignment.BottomEnd).size(30.dp).clip(CircleShape).background(DS.colors.surfaceRaised)
                        .border(1.dp, DS.colors.border, CircleShape).clickable(enabled = !uploading, onClickLabel = "Сменить фото") {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.CameraAlt, "Сменить фото", tint = DS.colors.textSecondary, modifier = Modifier.size(16.dp)) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DSSpace.xs)) {
                Text(me.name, style = MaterialTheme.typography.titleLarge, color = DS.colors.textPrimary, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpace.sm)) {
                    DSBadge(me.role.label, if (me.role == Role.Admin) DSTone.Info else DSTone.Neutral)
                    if (me.createdAt > 0) Text("с ${formatDate(me.createdAt)}", style = MaterialTheme.typography.bodySmall, color = DS.colors.textTertiary)
                }
                Text(me.role.hint, style = MaterialTheme.typography.bodySmall, color = DS.colors.textSecondary)
            }
        }
        if (me.avatar != null) {
            DSButton("Убрать фото", {
                scope.launch { runCatching { app.auth.updateProfile(clearAvatar = true) }.onSuccess { toast("Фото удалено") }.onFailure { toast(it.message ?: "Ошибка") } }
            }, Modifier.padding(top = DSSpace.sm), variant = DSButtonVariant.Ghost, size = DSButtonSize.Sm)
        }
        Spacer(Modifier.height(DSSpace.xl))
        Column(verticalArrangement = Arrangement.spacedBy(DSSpace.lg)) {
            DSTextField("Имя", name, { name = it.take(60); error = null }, error = error)
            FormField("Email", email, { email = it }, "Необязательно", hint = "Видно только администратору",
                keyboard = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false))
            DSButton("Сохранить", {
                if (name.isBlank()) { error = "Введите имя"; return@DSButton }
                saving = true
                scope.launch {
                    try { app.auth.updateProfile(name = name.trim(), email = email.trim()); toast("Профиль сохранён") }
                    catch (e: HttpFailure) { toast(e.message ?: "Ошибка") } finally { saving = false }
                }
            }, Modifier.align(Alignment.End), loading = saving, enabled = dirty)
        }
    }
}

/** Pair another phone: a one-time code + QR (the same as on the web profile). */
@Composable
private fun PhoneSection() {
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(DSSpace.sm)) {
        DSSectionHeader("Приложение на телефоне")
        Text("Другой телефон входит в ваш аккаунт по одноразовому коду — passkey в приложениях на домене project.lib не работают.",
            style = MaterialTheme.typography.bodySmall, color = DS.colors.textTertiary)
        DSCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpace.md)) {
                DSIconBox(Icons.Rounded.Smartphone, size = 36.dp)
                Text("Код действует 10 минут и подходит один раз.", style = MaterialTheme.typography.bodyMedium, color = DS.colors.textSecondary, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(DSSpace.md))
            DSButton("Подключить телефон", { open = true }, variant = DSButtonVariant.Outline, size = DSButtonSize.Sm)
        }
    }
    if (open) PairCodeDialog(onClose = { open = false })
}

@Composable
private fun PairCodeDialog(onClose: () -> Unit) {
    val app = App.instance
    val context = LocalContext.current
    val toast = LocalToast.current
    var code by remember { mutableStateOf<DeviceCode?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(attempt) {
        code = null; error = null
        try { code = app.api.createDeviceCode() } catch (e: HttpFailure) { error = e.message }
    }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    val server = app.api.baseUrl()?.toString()?.trimEnd('/') ?: ""
    val link = code?.let { "projectlib://pair?server=${android.net.Uri.encode(server)}&code=${android.net.Uri.encode(it.code)}" }
    val qr = remember(link) { link?.let { qrBitmap(it, 600) } }
    val left = code?.let { maxOf(0L, (it.expiresAt - now) / 1000) } ?: 0L
    val expired = code != null && left == 0L

    GlassAlertDialog(
        onDismissRequest = onClose, shape = MaterialTheme.shapes.extraLarge, containerColor = DS.colors.surface,
        title = { Text("Подключить телефон", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(DSSpace.md)) {
                Text("На другом телефоне откройте ProjectLib и наведите камеру на код или введите его на экране входа.",
                    style = MaterialTheme.typography.bodyMedium, color = DS.colors.textSecondary, textAlign = TextAlign.Center)
                if (error != null) DSBanner("Не удалось создать код", tone = DSTone.Danger, message = error)
                else {
                    // QR codes stay dark-on-white in both themes so any camera reads them.
                    Box(Modifier.clip(MaterialTheme.shapes.medium).background(Color.White).padding(DSSpace.md)) {
                        if (qr != null) Image(qr.asImageBitmap(), "QR-код для входа", Modifier.size(180.dp))
                        else DSSkeleton(Modifier.size(180.dp))
                    }
                    Text(code?.code ?: "····-····", fontFamily = FontFamily.Monospace, fontSize = 28.sp, letterSpacing = 3.sp,
                        color = if (expired) DS.colors.textTertiary else DS.colors.textPrimary)
                    Text(
                        when { expired -> "Код истёк — создайте новый"; code != null -> "Действует ещё ${left / 60}:${"%02d".format(left % 60)}"; else -> "Создаю код…" },
                        style = MaterialTheme.typography.bodySmall, color = DS.colors.textSecondary,
                    )
                    Text("Адрес сервера: $server", style = MaterialTheme.typography.bodySmall, color = DS.colors.textTertiary, textAlign = TextAlign.Center)
                }
            }
        },
        confirmButton = { DSButton("Готово", onClose) },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(DSSpace.xs)) {
                if (code != null && !expired) DSButton("Копировать", {
                    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Код ProjectLib", code!!.code))
                    toast("Код скопирован")
                }, variant = DSButtonVariant.Ghost, size = DSButtonSize.Sm)
                DSButton("Новый", { attempt++ }, variant = DSButtonVariant.Secondary, size = DSButtonSize.Sm, leadingIcon = Icons.Rounded.Refresh)
            }
        },
    )
}

@Composable
private fun SessionsSection() {
    val app = App.instance
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    var sessions by remember { mutableStateOf<List<DeviceSession>?>(null) }
    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(reload) { sessions = runCatching { app.api.sessions() }.getOrElse { emptyList() } }
    val list = sessions
    if (list != null && list.isEmpty()) return // token sign-in has no sessions
    val others = list?.count { !it.current } ?: 0

    Column(verticalArrangement = Arrangement.spacedBy(DSSpace.sm)) {
        DSSectionHeader("Устройства")
        Text("Где выполнен вход в ваш аккаунт.", style = MaterialTheme.typography.bodySmall, color = DS.colors.textTertiary)
        ListGroup {
            if (list == null) Box(Modifier.padding(DSSpace.lg)) { DSSkeleton(Modifier.fillMaxWidth(0.5f).height(20.dp)) }
            else list.forEachIndexed { i, s ->
                if (i > 0) GroupDivider()
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = DSSpace.lg, vertical = DSSpace.sm),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpace.md)) {
                    Icon(if ("ProjectLib-App" in s.userAgent) Icons.Rounded.PhoneAndroid else Icons.Rounded.Devices, null, tint = DS.colors.textTertiary)
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpace.sm)) {
                            Text(deviceOf(s.userAgent), style = MaterialTheme.typography.bodyLarge, color = DS.colors.textPrimary, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                            if (s.current) DSBadge("Этот телефон", DSTone.Success)
                        }
                        Text("Активность: ${fmtTime(s.lastSeenAt)} · вход ${formatDate(s.createdAt)}", style = MaterialTheme.typography.bodySmall, color = DS.colors.textSecondary)
                    }
                    if (!s.current) DSButton("Выйти", {
                        scope.launch { runCatching { app.api.endSession(s.id) }.onSuccess { toast("Сеанс завершён"); reload++ }.onFailure { toast(it.message ?: "Ошибка") } }
                    }, variant = DSButtonVariant.Ghost, size = DSButtonSize.Sm)
                }
            }
            if (others > 0 && list?.any { it.current } == true) {
                GroupDivider()
                Box(Modifier.padding(DSSpace.md)) {
                    DSButton("Выйти на всех других устройствах", {
                        scope.launch { runCatching { app.api.endOtherSessions() }.onSuccess { toast("Вы вышли на всех других устройствах"); reload++ }.onFailure { toast(it.message ?: "Ошибка") } }
                    }, variant = DSButtonVariant.DestructiveSoft, size = DSButtonSize.Sm, leadingIcon = Icons.Rounded.PersonOff)
                }
            }
        }
    }
}

@Composable
private fun PasskeysSection() {
    val app = App.instance
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    var keys by remember { mutableStateOf<List<Passkey>?>(null) }
    var reload by remember { mutableStateOf(0) }
    var toDelete by remember { mutableStateOf<Passkey?>(null) }
    LaunchedEffect(reload) { keys = runCatching { app.api.passkeys() }.getOrElse { emptyList() } }

    Column(verticalArrangement = Arrangement.spacedBy(DSSpace.sm)) {
        DSSectionHeader("Ключи входа (passkey)")
        Text("Нужны для входа на сайте. Добавить новый ключ можно в web-версии.", style = MaterialTheme.typography.bodySmall, color = DS.colors.textTertiary)
        ListGroup {
            val list = keys
            when {
                list == null -> Box(Modifier.padding(DSSpace.lg)) { DSSkeleton(Modifier.fillMaxWidth(0.5f).height(20.dp)) }
                list.isEmpty() -> Text("Ключей нет.", style = MaterialTheme.typography.bodyMedium, color = DS.colors.textSecondary, modifier = Modifier.padding(DSSpace.lg))
                else -> list.forEachIndexed { i, k ->
                    if (i > 0) GroupDivider()
                    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = DSSpace.lg, vertical = DSSpace.sm),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpace.md)) {
                        DSIconBox(if (k.synced) Icons.Rounded.Cloud else Icons.Rounded.Smartphone, size = 36.dp)
                        Column(Modifier.weight(1f)) {
                            Text(k.name, style = MaterialTheme.typography.bodyLarge, color = DS.colors.textPrimary, maxLines = 1)
                            Text("${if (k.synced) "Синхронизируется" else "Только на одном устройстве"} · создан ${formatDate(k.createdAt)}",
                                style = MaterialTheme.typography.bodySmall, color = DS.colors.textSecondary, maxLines = 2)
                        }
                        DSButton("Удалить", { toDelete = k }, variant = DSButtonVariant.Ghost, size = DSButtonSize.Sm)
                    }
                }
            }
        }
    }
    toDelete?.let { k ->
        ConfirmDialog("Удалить passkey?", "С ключом «${k.name}» больше нельзя будет войти; устройства, вошедшие с ним, будут разлогинены.", "Удалить passkey",
            onConfirm = { scope.launch { runCatching { app.api.deletePasskey(k.id) }.onSuccess { toast("Passkey удалён"); reload++ }.onFailure { toast(it.message ?: "Ошибка") } } },
            onDismiss = { toDelete = null })
    }
}

@Composable
private fun SignOutSection(nav: Nav) {
    val app = App.instance
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    val sync by app.repo.status.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(DSSpace.sm)) {
        DSButton("Выйти из аккаунта", { confirm = true }, variant = DSButtonVariant.DestructiveSoft, leadingIcon = Icons.AutoMirrored.Rounded.Logout)
        Text("Войти снова можно новым кодом из профиля на сайте.", style = MaterialTheme.typography.bodySmall, color = DS.colors.textTertiary)
    }
    if (confirm) ConfirmDialog(
        "Выйти на этом телефоне?",
        if (sync.pending > 0) "Копия данных будет удалена с телефона. ${sync.pending} неотправленных изменений пропадут — сначала дождитесь синхронизации."
        else "Копия данных будет удалена с телефона. На сервере и других устройствах всё останется.",
        "Выйти", onConfirm = { scope.launch { app.auth.signOut(); toast("Вы вышли"); nav.settings() } }, onDismiss = { confirm = false },
    )
}
