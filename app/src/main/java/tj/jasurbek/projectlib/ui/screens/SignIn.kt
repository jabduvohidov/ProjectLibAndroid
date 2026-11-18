package tj.jasurbek.projectlib.ui.screens

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tj.jasurbek.projectlib.data.Settings
import tj.jasurbek.projectlib.ui.designsystem.DSButtonSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.launch
import tj.jasurbek.projectlib.App
import tj.jasurbek.projectlib.data.HttpFailure
import tj.jasurbek.projectlib.ui.common.LocalToast
import tj.jasurbek.projectlib.ui.designsystem.DS
import tj.jasurbek.projectlib.ui.designsystem.DSButton
import tj.jasurbek.projectlib.ui.designsystem.DSButtonVariant
import tj.jasurbek.projectlib.ui.designsystem.DSCard
import tj.jasurbek.projectlib.ui.designsystem.DSSpace
import tj.jasurbek.projectlib.ui.forms.ErrorText
import tj.jasurbek.projectlib.ui.forms.FormField

/** The ProjectLib server on the home network (DEPLOY-LAN.md); prefilled when nothing else is known. */
const val DEFAULT_SERVER = "https://project.lib"

/**
 * Sign-in. Main way: "Войти с passkey" opens the server's #/app-login page in the phone's browser (a Custom Tab),
 * where passkeys work; after the passkey the page hands a one-time code back via projectlib://pair and the app
 * signs in by itself (MainActivity). A native app can't call passkeys on the private project.lib domain directly:
 * Android checks the app ↔ domain link through Google's servers, which can't reach it.
 * Fallback: the code typed by hand (web: Профиль → Подключить телефон), e.g. for a phone without your passkey.
 */
@Composable
fun CodeSignInForm(initialServer: String, initialCode: String = "", onCancel: (() -> Unit)? = null, onDone: () -> Unit = {}) {
    val app = App.instance
    val toast = LocalToast.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember(initialServer) { mutableStateOf(initialServer.ifBlank { DEFAULT_SERVER }) }
    var code by remember(initialCode) { mutableStateOf(initialCode) }
    var showCode by remember(initialCode) { mutableStateOf(initialCode.isNotEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val browserBusy by app.browserSignIn.collectAsStateWithLifecycle()

    fun explain(e: HttpFailure) =
        if (e.status == 503) "Сервер недоступен. Проверьте адрес, сеть и сертификат (для project.lib нужен CA в настройках Android)." else e.message

    DSCard {
        Column(verticalArrangement = Arrangement.spacedBy(DSSpace.lg)) {
            FormField("Адрес сервера", url, { url = it; error = null }, DEFAULT_SERVER,
                hint = "Для эмулятора и сервера на этом Mac: http://10.0.2.2:3001",
                keyboard = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false))
            error?.let { ErrorText(it) }
            if (!showCode) {
                DSButton("Войти с passkey", {
                    error = null
                    val server = Settings.normalizeServerUrl(url)
                    busy = true
                    scope.launch {
                        try {
                            app.api.checkServer(server, "") // fail here, not in the browser, if the address is wrong
                            app.startBrowserLogin(server)
                            CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, Uri.parse("$server/#/app-login"))
                        } catch (e: HttpFailure) { error = explain(e) } finally { busy = false }
                    }
                }, Modifier.fillMaxWidth(), size = DSButtonSize.Lg, leadingIcon = Icons.Rounded.Fingerprint,
                    loading = busy || browserBusy != null, enabled = url.isNotBlank())
                Text("Откроется браузер: подтвердите вход отпечатком или PIN и нажмите «Открыть ProjectLib».",
                    style = MaterialTheme.typography.bodySmall, color = DS.colors.textTertiary)
                Row(horizontalArrangement = Arrangement.spacedBy(DSSpace.sm)) {
                    if (onCancel != null) DSButton("Отмена", onCancel, Modifier.weight(1f), variant = DSButtonVariant.Secondary)
                    DSButton("Ввести код", { showCode = true; error = null }, Modifier.weight(1f), variant = DSButtonVariant.Secondary)
                }
            } else {
                FormField("Код из профиля", code, { code = it.uppercase().take(9); error = null }, "ABCD-EFGH",
                    hint = "На сайте: Профиль → «Подключить телефон». Код действует 10 минут.",
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Ascii, capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
                    mono = true)
                Row(horizontalArrangement = Arrangement.spacedBy(DSSpace.sm)) {
                    DSButton(if (initialCode.isEmpty()) "Назад" else "Отмена", {
                        if (initialCode.isEmpty()) showCode = false else onCancel?.invoke()
                    }, Modifier.weight(1f), variant = DSButtonVariant.Secondary)
                    DSButton("Войти", {
                        busy = true; error = null
                        scope.launch {
                            try {
                                val me = app.auth.signInWithCode(url, code)
                                app.pendingPair.value = null // a QR link that brought us here is used up
                                toast("Вы вошли как ${me.name}")
                                onDone()
                            } catch (e: HttpFailure) { error = explain(e) } finally { busy = false }
                        }
                    }, Modifier.weight(1f), loading = busy, enabled = url.isNotBlank() && code.count { it.isLetterOrDigit() } == 8)
                }
            }
        }
    }
}

/** "Вход по токену сервера" — only the admin knows PROJECTLIB_TOKEN; shown behind a toggle. */
@Composable
fun TokenSignInToggle(initialUrl: String, onSaved: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    if (!open) {
        DSButton("Войти по токену сервера (администратор)", { open = true }, variant = DSButtonVariant.Ghost)
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(DSSpace.sm)) {
            Text("Токен действует от имени первого администратора.", style = MaterialTheme.typography.bodySmall, color = DS.colors.textTertiary)
            ServerForm(initialUrl = initialUrl, initialToken = "", onCancel = { open = false }, onSaved = { changed ->
                App.instance.auth.afterTokenSignIn(changed)
                open = false
                onSaved()
            })
        }
    }
}
