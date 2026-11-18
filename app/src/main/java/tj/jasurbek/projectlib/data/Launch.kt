package tj.jasurbek.projectlib.data

/*
 * How a link can be launched from an Android phone — a port of src/lib/launch.ts with the device fixed to Android.
 * Local commands run through the agent on the Mac, which a phone can't reach, so they get no option here.
 */

enum class LaunchMode(val button: String) {
    Embed("Запустить"),
    Browser("Открыть"),
    Deeplink("Открыть приложение"),
    Qr("QR-код"),
}

data class LaunchOption(val link: Link, val mode: LaunchMode, val hint: String, val score: Int)

/** All ways to launch one link on this phone, best first. */
fun linkOptions(link: Link): List<LaunchOption> {
    val platform = link.effectivePlatform
    val here = platform.opensHere
    val out = mutableListOf<LaunchOption>()
    when (link.kind) {
        LinkKind.Web -> {
            out += LaunchOption(link, LaunchMode.Embed, "Внутри ProjectLib", 80)
            out += LaunchOption(link, LaunchMode.Browser, "В браузере", 75)
        }
        LinkKind.Deeplink -> {
            if (here) out += LaunchOption(link, LaunchMode.Deeplink, "Открыть в установленном приложении", 100)
            else out += LaunchOption(link, LaunchMode.Qr, "Для ${platform.label} — QR-код или ссылка", 20)
        }
        LinkKind.Store, LinkKind.Download -> {
            if (!here && platform.isMobile) out += LaunchOption(link, LaunchMode.Qr, "QR-код для ${platform.label}", 30)
            out += LaunchOption(link, LaunchMode.Browser, if (here) "Открыть страницу установки" else "Для ${platform.label}", if (here) 60 else 12)
        }
        else -> Unit
    }
    return out.sortedByDescending { it.score }
}

/** All launch options of a project, best first. */
fun projectOptions(p: Project): List<LaunchOption> = p.launchLinks.flatMap(::linkOptions).sortedByDescending { it.score }

/** Where to send someone whose deep link did not open: explicit fallback, else an install link for the same platform. */
fun fallbackFor(p: Project, link: Link): String? {
    link.fallbackUrl?.takeIf { it.isNotBlank() }?.let { return it }
    val platform = link.effectivePlatform
    return p.links.find { (it.kind == LinkKind.Store || it.kind == LinkKind.Download) && it.effectivePlatform == platform }?.url
}

/** Guess the platform from well-known store / download URLs. */
fun guessPlatform(url: String): Platform? {
    val u = url.lowercase()
    return when {
        Regex("apps\\.apple\\.com/.*/app/|testflight\\.apple\\.com|itms-apps:|\\.ipa(\\?|$)").containsMatchIn(u) -> Platform.Ios
        Regex("play\\.google\\.com|market:|\\.apk(\\?|$)|\\.aab(\\?|$)").containsMatchIn(u) -> Platform.Android
        Regex("apps\\.apple\\.com/.*mac|macappstore:|\\.dmg(\\?|$)|\\.pkg(\\?|$)").containsMatchIn(u) -> Platform.Macos
        Regex("microsoft\\.com/store|ms-windows-store:|\\.exe(\\?|$)|\\.msi(\\?|$)|\\.msix(\\?|$)").containsMatchIn(u) -> Platform.Windows
        Regex("\\.appimage(\\?|$)|\\.deb(\\?|$)|\\.rpm(\\?|$)|snapcraft\\.io|flathub\\.org").containsMatchIn(u) -> Platform.Linux
        else -> null
    }
}

/** "example.com" → "https://example.com". */
fun normalizeUrl(url: String): String {
    val u = url.trim()
    if (u.isEmpty()) return u
    return if (Regex("^[a-z][a-z0-9+.-]*:", RegexOption.IGNORE_CASE).containsMatchIn(u)) u else "https://$u"
}

private val BLOCKED = Regex("^(javascript|data|vbscript|file|blob):", RegexOption.IGNORE_CASE)
fun isHttpUrl(u: String) = runCatching { java.net.URI(u).let { (it.scheme == "http" || it.scheme == "https") && !it.host.isNullOrEmpty() } }.getOrDefault(false)
fun isAnyUrl(u: String) = runCatching { java.net.URI(u).scheme != null && !BLOCKED.containsMatchIn(u) }.getOrDefault(false)
