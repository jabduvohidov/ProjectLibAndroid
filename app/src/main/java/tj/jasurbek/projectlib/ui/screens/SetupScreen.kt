package tj.jasurbek.projectlib.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import tj.jasurbek.projectlib.App
import tj.jasurbek.projectlib.ui.designsystem.DS
import tj.jasurbek.projectlib.ui.designsystem.DSButton
import tj.jasurbek.projectlib.ui.designsystem.DSButtonVariant
import tj.jasurbek.projectlib.ui.designsystem.DSSpace

/** First run: connect to the ProjectLib server (or continue with data on this phone only). */
@Composable
fun SetupScreen() {
    val app = App.instance
    Box(Modifier.fillMaxSize().background(DS.colors.bg).safeDrawingPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 480.dp).verticalScroll(rememberScrollState()).padding(DSSpace.xl),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(DSSpace.xl),
        ) {
            Box(Modifier.padding(top = DSSpace.s2xl).size(64.dp).clip(MaterialTheme.shapes.large).background(DS.colors.accent), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.LibraryBooks, null, tint = DS.colors.textOnAccent, modifier = Modifier.size(32.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(DSSpace.sm)) {
                Text("ProjectLib", style = MaterialTheme.typography.displaySmall, color = DS.colors.textPrimary)
                Text(
                    "Проекты, идеи и их приложения. Войдите в свой аккаунт ProjectLib — данные будут те же, что в web-версии.",
                    style = MaterialTheme.typography.bodyLarge, color = DS.colors.textSecondary, textAlign = TextAlign.Center,
                )
            }
            val pair by app.pendingPair.collectAsStateWithLifecycle()
            // After a sign-out the server is still known: prefill it.
            val known = app.settings.current.serverUrl
            CodeSignInForm(initialServer = pair?.first?.ifEmpty { known } ?: known, initialCode = pair?.second ?: "", onDone = { app.pendingPair.value = null })
            TokenSignInToggle(initialUrl = known, onSaved = {})
            DSButton("Продолжить без сервера", { app.settings.skipSetup(); app.repo.syncNow() }, variant = DSButtonVariant.Ghost)
        }
    }
}
