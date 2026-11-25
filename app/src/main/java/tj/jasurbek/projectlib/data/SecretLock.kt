package tj.jasurbek.projectlib.data

import android.app.Activity
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Secret projects lock, this device only (web: src/lib/secretLock.ts). Secret projects sync like any
 * other own project; the app keeps them out of every list until unlocked with this device's password or fingerprint.
 * Locks again after 15 minutes without activity, after a minute in the background, on sign-out, or by hand.
 */
class SecretLock(context: Context) {
    private val prefs = context.getSharedPreferences("secret-lock", Context.MODE_PRIVATE)
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()
    private var lastActive = 0L
    private var leftAt = 0L

    val hasPassword get() = prefs.contains("hash")

    fun lock() { _unlocked.value = false }
    private fun unlock() { lastActive = System.currentTimeMillis(); _unlocked.value = true }

    /** Any interaction: keeps it open, or locks if the idle time already ran out. */
    fun touch() {
        if (!_unlocked.value) return
        val now = System.currentTimeMillis()
        if (now - lastActive > IDLE_MS) lock() else lastActive = now
    }
    fun onBackground() { leftAt = System.currentTimeMillis() }
    fun onForeground() {
        if (leftAt > 0 && System.currentTimeMillis() - leftAt > AWAY_MS) lock() else touch()
        leftAt = 0
    }

    fun setPassword(password: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString("salt", b64(salt)).putString("hash", b64(derive(password, salt))).apply()
        unlock()
    }

    fun unlockWithPassword(password: String): Boolean {
        val salt = prefs.getString("salt", null) ?: return false
        val hash = prefs.getString("hash", null) ?: return false
        val ok = MessageDigest.isEqual(derive(password, Base64.decode(salt, Base64.NO_WRAP)), Base64.decode(hash, Base64.NO_WRAP))
        if (ok) unlock()
        return ok
    }

    fun canUseFingerprint(context: Context): Boolean = runCatching {
        context.getSystemService(BiometricManager::class.java)?.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }.getOrDefault(false) // never crash the unlock sheet over the fingerprint check

    /** System fingerprint / face prompt; [onError] gets the reason unless the user just cancelled. */
    fun unlockWithFingerprint(activity: Activity, onError: (String) -> Unit) {
        val prompt = BiometricPrompt.Builder(activity)
            .setTitle("Секретные проекты")
            .setSubtitle("Подтвердите отпечатком")
            .setNegativeButton("Отмена", activity.mainExecutor) { _, _ -> }
            .build()
        prompt.authenticate(CancellationSignal(), activity.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = unlock()
            override fun onAuthenticationError(code: Int, message: CharSequence) {
                if (code != BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED && code != BiometricPrompt.BIOMETRIC_ERROR_CANCELED) onError(message.toString())
            }
        })
    }

    companion object {
        const val IDLE_MS = 15 * 60_000L
        const val AWAY_MS = 60_000L
        private const val ITERATIONS = 120_000
        private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
        private fun derive(password: String, salt: ByteArray): ByteArray =
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256)).encoded

        /** Secret projects are left out while locked. */
        fun visible(projects: List<Project>, unlocked: Boolean) =
            if (unlocked || projects.none { it.secret == true }) projects else projects.filter { it.secret != true }
    }
}
