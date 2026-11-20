package tj.jasurbek.projectlib.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tj.jasurbek.projectlib.App
import tj.jasurbek.projectlib.data.HttpFailure
import tj.jasurbek.projectlib.data.SyncState
import tj.jasurbek.projectlib.data.ThemePref
import tj.jasurbek.projectlib.ui.AppScreen
import tj.jasurbek.projectlib.ui.Nav
import tj.jasurbek.projectlib.ui.common.ConfirmDialog
import tj.jasurbek.projectlib.ui.common.GroupDivider
import tj.jasurbek.projectlib.ui.common.ListGroup
import tj.jasurbek.projectlib.ui.common.LocalToast
import tj.jasurbek.projectlib.ui.common.SyncIcon
import tj.jasurbek.projectlib.ui.common.describeSync
import tj.jasurbek.projectlib.ui.common.formatTime
import tj.jasurbek.projectlib.ui.common.UserAvatar
import tj.jasurbek.projectlib.ui.designsystem.DS
import tj.jasurbek.projectlib.ui.designsystem.DSBanner
import tj.jasurbek.projectlib.ui.designsystem.DSButton
import tj.jasurbek.projectlib.ui.designsystem.DSButtonSize
import tj.jasurbek.projectlib.ui.designsystem.DSButtonVariant
import tj.jasurbek.projectlib.ui.designsystem.DSCard
import tj.jasurbek.projectlib.ui.designsystem.DSListRow
import tj.jasurbek.projectlib.ui.designsystem.DSSectionHeader
import tj.jasurbek.projectlib.ui.designsystem.DSSegmentedControl
import tj.jasurbek.projectlib.ui.designsystem.DSSpace
import tj.jasurbek.projectlib.ui.designsystem.DSTone
import tj.jasurbek.projectlib.ui.forms.FormField
import java.time.LocalDate

@Composable
fun SettingsScreen(nav: Nav) {
    val app = App.instance
    val repo = app.repo
    val settings by app.settings.state.collectAsStateWithLifecycle()
    val projects by repo.projects.collectAsStateWithLifecycle()
    val sync by repo.status.collectAsStateWithLifecycle()
    val toast = LocalToast.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf<String?>(null) } // "clear" | "demo" | "signout"
    var signingIn by remember { mutableStateOf(false) }
    val auth by app.auth.state.collectAsStateWithLifecycle()

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val json = repo.exportJson()
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } } }
                .onSuccess { toast("Файл с данными сохранён") }
                .onFailure { toast("Не удалось сохранить файл") }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val text = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } } ?: ""
                toast("Импортировано проектов: ${repo.importJson(text)}")
            } catch (e: Exception) {
                toast(e.message ?: "Не удалось прочитать файл")
            }
        }
    }

    val ideas = projects.sumOf { it.ideas.size }
    val info = describeSync(sync)

    AppScreen(title = "Настройки", nav = nav) {
        item(key = "theme") {
            Section("Оформление") {
                DSCard {
                    DSSegmentedControl(
                        listOf(ThemePref.Light to "Светлая", ThemePref.Dark to "Тёмная", ThemePref.System to "Системная"),
                        settings.theme, app.settings::setTheme,
                    )
                    Spacer(Modifier.height(DSSpace.lg))
                    // Liquid Glass on/off; on devices that can't afford it, the kit keeps it off and says why.
                    val device = tj.jasurbek.projectlib.ui.designsystem.glass.rememberDSGlassAvailability()
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpace.md)) {
                        Column(Modifier.weight(1f)) {
                            Text("Эффекты стекла", style = MaterialTheme.typography.bodyLarge, color = DS.colors.textPrimary)
                            Text(
                                when {
                                    !device.enabled -> "Недоступно на этом устройстве: ${glassReason(device.reason)}"
                                    settings.glass -> "Панели, кнопки и меню — Liquid Glass"
                                    else -> "Сплошные панели — быстрее и экономнее"
                                },
                                style = MaterialTheme.typography.bodySmall, color = DS.colors.textSecondary,
                            )
                        }
                        androidx.compose.material3.Switch(
                            checked = settings.glass && device.enabled, enabled = device.enabled, onCheckedChange = app.settings::setGlass,
                            colors = androidx.compose.material3.SwitchDefaults.colors(
                                checkedTrackColor = DS.colors.accent, checkedThumbColor = DS.colors.textOnAccent,
                                uncheckedTrackColor = DS.colors.fill, uncheckedThumbColor = DS.colors.surfaceRaised, uncheckedBorderColor = DS.colors.borderStrong,
                            ),
                        )
                    }
                    Spacer(Modifier.height(DSSpace.md))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpace.md)) {
                        Column(Modifier.weight(1f)) {
                            Text("Стеклянные кнопки в контенте", style = MaterialTheme.typography.bodyLarge, color = DS.colors.textPrimary)
                            Text("Кнопки, чипы и переключатели внутри карточек. Дизайн-система оставляет их сплошными.",
                                style = MaterialTheme.typography.bodySmall, color = DS.colors.textSecondary)
                        }
                        androidx.compose.material3.Switch(
                            checked = settings.contentGlass && settings.glass && device.enabled, enabled = settings.glass && device.enabled,
                            onCheckedChange = app.settings::setContentGlass,
                            colors = androidx.compose.material3.SwitchDefaults.colors(
                                checkedTrackColor = DS.colors.accent, checkedThumbColor = DS.colors.textOnAccent,
                                uncheckedTrackColor = DS.colors.fill, uncheckedThumbColor = DS.colors.surfaceRaised, uncheckedBorderColor = DS.colors.borderStrong,
                            ),
                        )
                    }
                }
            }
        }

        item(key = "sync") {
            Section("Синхронизация", "Изменения сохраняются на телефоне сразу и отправляются на сервер, как только он доступен.") {
                DSCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpace.md)) {
                        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                            SyncIcon(info, sync.state == SyncState.Syncing, DS.colors.accentText, 20.dp)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(info.label, style = MaterialTheme.typography.bodyLarge, color = DS.colors.textPrimary)
                            Text(
                                (sync.lastSyncedAt?.let { "Последняя синхронизация: ${formatTime(it)}" } ?: "Ещё не синхронизировалось") +
                                    (if (sync.pending > 0) " · в очереди: ${sync.pending}" else ""),
                                style = MaterialTheme.typography.bodySmall, color = DS.colors.textSecondary,
                            )
                            if (sync.message != null && sync.state != SyncState.Synced) {
                                Text(sync.message!!, style = MaterialTheme.typography.bodySmall, color = DS.colors.textTertiary)
                            }
                        }
                    }
                    Spacer(Modifier.height(DSSpace.md))
                    DSButton("Синхронизировать", { repo.syncNow() }, variant = DSButtonVariant.Secondary, size = DSButtonSize.Sm,
                        leadingIcon = Icons.Rounded.Sync, enabled = sync.state != SyncState.NotConfigured)
                }
            }
        }

        item(key = "server") {
            Section("Аккаунт и сервер", "Тот же сервер и аккаунт, что в web-версии. Вход — passkey через браузер или кодом из профиля на сайте.") {
                if (sync.state == SyncState.Unauthorized) {
                    DSBanner("Нужно войти", tone = DSTone.Danger, message = "Сервер не принял вход (сеанс завершён или токен сменился). Изменения сохраняются на телефоне и отправятся после входа.")
                    Spacer(Modifier.height(DSSpace.sm))
                }
                val me = auth.me
                when {
                    signingIn || settings.serverUrl.isEmpty() || (me == null && !app.api.signedIn) -> Column(verticalArrangement = Arrangement.spacedBy(DSSpace.sm)) {
                        CodeSignInForm(
                            initialServer = settings.serverUrl,
                            onCancel = if (app.api.signedIn) ({ signingIn = false }) else null,
                            onDone = { signingIn = false },
                        )
                        TokenSignInToggle(initialUrl = settings.serverUrl, onSaved = { signingIn = false; toast("Подключено") })
                    }
                    else -> ListGroup {
                        if (me != null) {
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = nav.profile).padding(horizontal = DSSpace.lg, vertical = DSSpace.sm),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpace.md),
                            ) {
                                UserAvatar(me.name, me.avatar, 40.dp)
                                Column(Modifier.weight(1f)) {
                                    Text(me.name, style = MaterialTheme.typography.bodyLarge, color = DS.colors.textPrimary, maxLines = 1)
                                    Text("${me.role.label} · ${if (settings.session.isNotEmpty()) "вход по коду" else "вход по токену сервера"}",
                                        style = MaterialTheme.typography.bodySmall, color = DS.colors.textSecondary, maxLines = 1)
                                }
                                Icon(Icons.Rounded.ChevronRight, null, tint = DS.colors.textTertiary)
                            }
                            GroupDivider()
                        }
                        DSListRow(settings.serverUrl, icon = Icons.Rounded.Dns,
                            subtitle = if (me == null) "Вход по токену сервера" else "Сменить аккаунт или сервер",
                            onClick = { signingIn = true }, chevron = true)
                        GroupDivider()
                        DSListRow("Выйти", icon = Icons.AutoMirrored.Rounded.Logout, destructive = true,
                            subtitle = "Удалит копию данных с телефона", onClick = { confirm = "signout" })
                    }
                }
            }
        }

        item(key = "data") {
            Section("Данные", "${projects.size} проектов, $ideas идей. Экспорт включает и проекты, которыми с вами поделились; импорт и удаление касаются только ваших.") {
                ListGroup {
                    DSListRow("Экспорт в JSON", icon = Icons.Rounded.FileDownload, subtitle = "Сохранить резервную копию", chevron = true,
                        onClick = { exporter.launch("projectlib-${LocalDate.now()}.json") })
                    if (auth.canWrite) {
                        GroupDivider()
                        DSListRow("Импорт из JSON", icon = Icons.Rounded.FileUpload, subtitle = "Заменит ваши проекты на всех устройствах", chevron = true,
                            onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) })
                        GroupDivider()
                        DSListRow("Загрузить демо-данные", icon = Icons.Rounded.RestartAlt, iconTone = DSTone.Neutral,
                            subtitle = "Заменит ваши проекты примерами", chevron = true, onClick = { confirm = "demo" })
                    }
                }
            }
        }

        if (auth.canWrite) item(key = "danger") {
            ListGroup { DSListRow(if (auth.me != null) "Удалить все мои проекты" else "Удалить все данные", icon = Icons.Rounded.Delete, destructive = true, onClick = { confirm = "clear" }) }
        }

        item(key = "about") {
            Text("ProjectLib для Android · ${app.packageManager.getPackageInfo(app.packageName, 0).versionName}",
                style = MaterialTheme.typography.bodySmall, color = DS.colors.textTertiary)
        }
    }

    when (confirm) {
        "clear" -> ConfirmDialog(
            if (auth.me != null) "Удалить все мои проекты?" else "Удалить все данные?",
            if (auth.me != null) "Ваши проекты и их идеи будут удалены на всех устройствах (и у тех, с кем вы ими поделились). Чужие проекты останутся. Сначала сделайте экспорт, если нужна копия."
            else "Все проекты и идеи будут удалены на всех устройствах после синхронизации. Сначала сделайте экспорт, если нужна копия.",
            "Удалить все данные", onConfirm = { repo.clearAll(); toast("Данные удалены") }, onDismiss = { confirm = null },
        )
        "demo" -> ConfirmDialog(
            "Загрузить демо-данные?", "Текущие проекты и идеи будут заменены примерами.", "Заменить",
            onConfirm = { repo.resetToDemo(); toast("Демо-данные загружены") }, onDismiss = { confirm = null },
        )
        "signout" -> ConfirmDialog(
            "Выйти?",
            if (sync.pending > 0) "На телефоне ${sync.pending} неотправленных изменений — после выхода они пропадут. Данные на сервере останутся."
            else "Копия данных будет удалена с телефона. Данные на сервере останутся.",
            "Выйти", onConfirm = {
                scope.launch { app.auth.signOut(); toast("Вы вышли") }
            }, onDismiss = { confirm = null },
        )
    }
}

@Composable
private fun Section(title: String, description: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(DSSpace.sm)) {
        DSSectionHeader(title)
        if (description != null) Text(description, style = MaterialTheme.typography.bodySmall, color = DS.colors.textTertiary)
        content()
    }
}

/**
 * Server address + token with a check against GET /api/auth/status before saving.
 * [onSaved] gets true when the server address changed (then the local copy belongs to another server).
 */
@Composable
fun ServerForm(initialUrl: String, initialToken: String, onCancel: (() -> Unit)?, onSaved: (changedServer: Boolean) -> Unit) {
    val app = App.instance
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(initialUrl) }
    var token by remember { mutableStateOf(initialToken) }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    DSCard {
        Column(verticalArrangement = Arrangement.spacedBy(DSSpace.lg)) {
            FormField("Адрес сервера", url, { url = it; error = null }, "https://project.lib",
                hint = "Для эмулятора и сервера на этом Mac: http://10.0.2.2:3001",
                keyboard = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false))
            Column(verticalArrangement = Arrangement.spacedBy(DSSpace.sm)) {
                Text("Токен", style = MaterialTheme.typography.labelLarge, color = DS.colors.textSecondary)
                androidx.compose.material3.OutlinedTextField(
                    value = token, onValueChange = { token = it; error = null }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    shape = MaterialTheme.shapes.medium, colors = tj.jasurbek.projectlib.ui.forms.fieldColors(),
                    placeholder = { Text("PROJECTLIB_TOKEN", color = DS.colors.textTertiary) },
                )
                Text("Выводится при запуске сервера (npm run start:lan) и лежит в data/lan.env.",
                    style = MaterialTheme.typography.bodySmall, color = DS.colors.textTertiary)
            }
            error?.let { tj.jasurbek.projectlib.ui.forms.ErrorText(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(DSSpace.sm)) {
                if (onCancel != null) DSButton("Отмена", onCancel, Modifier.weight(1f), variant = DSButtonVariant.Secondary)
                DSButton("Подключить", {
                    checking = true
                    error = null
                    scope.launch {
                        try {
                            val s = app.api.checkServer(url, token)
                            if (s.authRequired && !s.authenticated) {
                                error = if (token.isBlank()) "Сервер требует токен" else "Сервер не принял токен"
                            } else {
                                val changed = tj.jasurbek.projectlib.data.Settings.normalizeServerUrl(url) != initialUrl && initialUrl.isNotEmpty()
                                app.settings.setServer(url, token)
                                onSaved(changed)
                            }
                        } catch (e: HttpFailure) {
                            error = if (e.status == 503) "Сервер недоступен. Проверьте адрес, сеть и сертификат (для project.lib нужен CA в настройках Android)." else e.message
                        } finally {
                            checking = false
                        }
                    }
                }, Modifier.weight(1f), loading = checking, enabled = url.isNotBlank())
            }
        }
    }
}

private fun glassReason(reason: String) = when (reason) {
    "Android 11 or older" -> "нужен Android 12 или новее"
    "low-RAM device" -> "мало оперативной памяти"
    "battery saver on" -> "включена экономия заряда"
    "system blur disabled" -> "размытие отключено в системе"
    else -> reason
}
