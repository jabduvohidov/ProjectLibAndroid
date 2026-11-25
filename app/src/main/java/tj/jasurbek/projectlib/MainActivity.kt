package tj.jasurbek.projectlib

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import tj.jasurbek.projectlib.data.HttpFailure
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tj.jasurbek.projectlib.data.ThemePref
import tj.jasurbek.projectlib.ui.ProjectLibApp
import tj.jasurbek.projectlib.ui.designsystem.AppTheme

class MainActivity : ComponentActivity() {
    /** Any touch counts as activity for the secret projects' idle lock. */
    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (ev.actionMasked == android.view.MotionEvent.ACTION_DOWN) (application as App).secretLock.touch()
        return super.dispatchTouchEvent(ev)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handlePairLink(intent)
    }

    /** projectlib://pair?server=https://project.lib&code=ABCD-EFGH → prefill the sign-in by code. */
    private fun handlePairLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "projectlib" || uri.host != "pair") return
        val server = uri.getQueryParameter("server").orEmpty()
        val code = uri.getQueryParameter("code").orEmpty()
        intent.data = null // a rotation must not apply it twice
        if (code.isEmpty()) return
        val app = application as App
        if (!app.takeBrowserLogin(server)) {
            // Not started from this app (a QR code, a link from someone): ask before signing in.
            app.pendingPair.value = server to code
            return
        }
        // Coming back from our own "Войти с passkey": finish the sign-in right away.
        app.browserSignIn.value = "Вход…"
        lifecycleScope.launch {
            try {
                val me = app.auth.signInWithCode(server, code)
                app.browserSignIn.value = null
                Toast.makeText(this@MainActivity, "Вы вошли как ${me.name}", Toast.LENGTH_SHORT).show()
            } catch (e: HttpFailure) {
                app.browserSignIn.value = null
                app.pendingPair.value = server to code // show the form with the code so it can be retried
                Toast.makeText(this@MainActivity, e.message ?: "Не удалось войти", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as App
        handlePairLink(intent)
        setContent {
            val settings by app.settings.state.collectAsStateWithLifecycle()
            val dark = when (settings.theme) {
                ThemePref.System -> isSystemInDarkTheme()
                ThemePref.Light -> false
                ThemePref.Dark -> true
            }
            // Status / navigation bar icons follow the app theme, not only the system one.
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            AppTheme(darkTheme = dark) { ProjectLibApp(app) }
        }
    }
}
