package tj.jasurbek.projectlib

import tj.jasurbek.projectlib.data.SecretLock
import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import tj.jasurbek.projectlib.data.Api
import tj.jasurbek.projectlib.data.Auth
import tj.jasurbek.projectlib.data.Repository
import tj.jasurbek.projectlib.data.Settings

/** Holds the app-wide singletons: settings, server client and the offline-first repository. */
class App : Application(), SingletonImageLoader.Factory {
    lateinit var settings: Settings
        private set
    lateinit var api: Api
        private set
    lateinit var repo: Repository
        private set
    lateinit var auth: Auth
        private set
    lateinit var secretLock: SecretLock
        private set

    /** A pairing link (projectlib://pair?server=…&code=…) opened from the QR code in the web profile. */
    val pendingPair = MutableStateFlow<Pair<String, String>?>(null)

    /** "Войти с passkey" sends the user to the browser; the code that comes back from that server is expected. */
    private var browserLogin: Pair<String, Long>? = null
    fun startBrowserLogin(server: String) { browserLogin = Settings.normalizeServerUrl(server) to System.currentTimeMillis() }
    /** Was this pairing link the answer to our own browser sign-in (same server, within 10 minutes)? Used once. */
    fun takeBrowserLogin(server: String): Boolean {
        val (expected, at) = browserLogin ?: return false
        val ok = Settings.normalizeServerUrl(server) == expected && System.currentTimeMillis() - at < 10 * 60_000
        if (ok) browserLogin = null
        return ok
    }

    /** Busy / error state of a sign-in finished from a returning browser link (shown on the sign-in screen). */
    val browserSignIn = MutableStateFlow<String?>(null)

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = Settings(this)
        api = Api(settings)
        secretLock = SecretLock(this)
        repo = Repository(this, settings, api, secretLock)
        auth = Auth(settings, api, repo)
        // Who am I / roles may change on the server (admin changed my role): re-check whenever the app shows up.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { auth.refresh(); secretLock.onForeground() }
            override fun onStop(owner: LifecycleOwner) { secretLock.onBackground() }
        })
    }

    /** Attachments load through the same OkHttp client (same trust config, connection pool). */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { api.client })) }
            .build()

    companion object {
        lateinit var instance: App
            private set
    }
}
