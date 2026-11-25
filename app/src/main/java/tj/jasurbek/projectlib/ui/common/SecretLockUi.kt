package tj.jasurbek.projectlib.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import tj.jasurbek.projectlib.App
import tj.jasurbek.projectlib.ui.designsystem.DS
import tj.jasurbek.projectlib.ui.designsystem.DSButton
import tj.jasurbek.projectlib.ui.designsystem.DSButtonVariant
import tj.jasurbek.projectlib.ui.designsystem.DSSpace
import tj.jasurbek.projectlib.ui.forms.ErrorText
import tj.jasurbek.projectlib.ui.forms.FormSheet
import tj.jasurbek.projectlib.ui.forms.fieldColors

/** Lock icon of the projects screen (web: "Секретные" button): opens the unlock sheet, or hides secret projects again. */
@Composable
fun SecretLockAction() {
    val lock = App.instance.secretLock
    val unlocked by lock.unlocked.collectAsState()
    val toast = LocalToast.current
    var open by remember { mutableStateOf(false) }
    if (unlocked) ContentIconButton(Icons.Rounded.LockOpen, "Скрыть секретные проекты") { lock.lock(); toast("Секретные проекты скрыты") }
    else ContentIconButton(Icons.Rounded.Lock, "Секретные проекты") { open = true }
    if (open) UnlockSheet { open = false }
}

@Composable
private fun UnlockSheet(change: Boolean = false, onDismiss: () -> Unit) {
    val lock = App.instance.secretLock
    val context = LocalContext.current
    val toast = LocalToast.current
    val setup = remember { change || !lock.hasPassword }
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val unlocked by lock.unlocked.collectAsState()
    if (unlocked && !change) { onDismiss(); return } // the fingerprint prompt succeeded

    FormSheet(
        title = if (change) "Новый пароль" else if (setup) "Пароль секретных проектов" else "Секретные проекты",
        submitLabel = if (setup) "Сохранить" else "Открыть",
        onDismiss = onDismiss,
        onSubmit = submit@{
            // shortcut: PBKDF2 runs on the main thread (~0.2–0.5 s); move to a coroutine if it ever feels slow.
            if (setup) {
                error = when {
                    password.length < 4 -> "Не короче 4 символов"
                    password != repeat -> "Пароли не совпадают"
                    else -> null
                }
                if (error != null) return@submit false
                lock.setPassword(password)
            } else if (!lock.unlockWithPassword(password)) {
                error = "Неверный пароль"
                return@submit false
            }
            toast(if (change) "Пароль изменён" else "Секретные проекты открыты на 15 минут")
            true
        },
    ) {
        Text(
            if (setup) "Пароль хранится только на этом телефоне. Секретные проекты видны, пока открыты, и скрываются через 15 минут без действий или когда вы уходите из приложения."
            else "Введите пароль этого телефона или подтвердите отпечатком.",
            style = MaterialTheme.typography.bodyMedium, color = DS.colors.textSecondary,
        )
        PasswordField(if (setup) "Новый пароль" else "Пароль", password) { password = it; error = null }
        if (setup) PasswordField("Повторите пароль", repeat) { repeat = it; error = null }
        error?.let { ErrorText(it) }
        val activity = context.findActivity()
        if (!setup && activity != null && lock.canUseFingerprint(context)) {
            DSButton("Отпечаток", { lock.unlockWithFingerprint(activity) { error = it } }, Modifier.fillMaxWidth(),
                variant = DSButtonVariant.Secondary, leadingIcon = Icons.Rounded.Fingerprint)
        }
    }
}

@Composable
private fun PasswordField(label: String, value: String, onChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DSSpace.sm)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = DS.colors.textSecondary)
        OutlinedTextField(
            value = value, onValueChange = onChange, singleLine = true, modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            shape = MaterialTheme.shapes.medium, colors = fieldColors(),
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Settings → "Секретные проекты" row: set or change this phone's password (changing needs the projects unlocked). */
@Composable
fun SecretPasswordRow() {
    val lock = App.instance.secretLock
    val unlocked by lock.unlocked.collectAsState()
    var sheet by remember { mutableStateOf<String?>(null) } // "unlock" | "change"
    val has = lock.hasPassword || unlocked
    when {
        !has -> tj.jasurbek.projectlib.ui.designsystem.DSListRow("Задать пароль", icon = Icons.Rounded.Key, subtitle = "Пароль ещё не задан",
            chevron = true, onClick = { sheet = "unlock" })
        unlocked -> tj.jasurbek.projectlib.ui.designsystem.DSListRow("Сменить пароль", icon = Icons.Rounded.Key, subtitle = "Секретные проекты сейчас открыты",
            chevron = true, onClick = { sheet = "change" })
        else -> tj.jasurbek.projectlib.ui.designsystem.DSListRow("Сменить пароль", icon = Icons.Rounded.Lock,
            iconTone = tj.jasurbek.projectlib.ui.designsystem.DSTone.Neutral,
            subtitle = "Сначала откройте секретные проекты старым паролем или отпечатком", chevron = true, onClick = { sheet = "unlock" })
    }
    sheet?.let { s -> UnlockSheet(change = s == "change") { sheet = null } }
}
