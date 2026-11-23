package tj.jasurbek.projectlib.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemePref { System, Light, Dark }
enum class IdeaLayout { List, Board }
enum class IdeaSort(val label: String) { Order("Свой порядок"), Due("По сроку"), New("Сначала новые") }

/** Device-local preferences (never synced): server, token, theme and remembered UI choices. */
class Settings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("projectlib", Context.MODE_PRIVATE)

    data class State(
        /** e.g. https://project.lib — "" = not configured yet. */
        val serverUrl: String,
        /** Server token (PROJECTLIB_TOKEN): acts as the first admin. */
        val token: String,
        /** Session id from a phone pairing code (sent as the pl_session cookie): acts as that user. */
        val session: String,
        /** The user finished (or skipped) the first-run server setup. */
        val onboarded: Boolean,
        val theme: ThemePref,
        val ideaLayout: IdeaLayout,
        val ideaSort: IdeaSort,
        /** Liquid Glass on the navigation layer (it still turns itself off on devices that can't afford it). */
        val glass: Boolean,
        /** ProjectLib extra: glass also on buttons / chips inside the content (the design system keeps them solid). */
        val contentGlass: Boolean,
        /** Ideas screen: projects picked in the filter (empty = all), remembered on this device. */
        val ideaProjects: List<String>,
        /** Board: empty columns collapse into the "Скрытые колонки" list (web: projectlib.board.hideEmpty). */
        val hideEmptyColumns: Boolean,
    )

    private val _state = MutableStateFlow(read())
    val state: StateFlow<State> = _state.asStateFlow()
    val current get() = _state.value

    private fun read() = State(
        serverUrl = prefs.getString("serverUrl", "") ?: "",
        token = prefs.getString("token", "") ?: "",
        session = prefs.getString("session", "") ?: "",
        onboarded = prefs.getBoolean("onboarded", false),
        theme = enumOr(prefs.getString("theme", null), ThemePref.System),
        ideaLayout = enumOr(prefs.getString("ideaLayout", null), IdeaLayout.List),
        ideaSort = enumOr(prefs.getString("ideaSort", null), IdeaSort.Order),
        glass = prefs.getBoolean("glass", true),
        contentGlass = prefs.getBoolean("contentGlass", true),
        ideaProjects = prefs.getString("ideaProjects", "")!!.split(',').filter { it.isNotEmpty() },
        hideEmptyColumns = prefs.getBoolean("hideEmptyColumns", false),
    )

    private inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: fallback

    private fun edit(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
        _state.value = read()
    }

    fun setServer(url: String, token: String) = edit {
        putString("serverUrl", normalizeServerUrl(url)); putString("token", token.trim()); putString("session", ""); putBoolean("onboarded", true)
    }
    fun setToken(token: String) = edit { putString("token", token.trim()) }
    /** Signed in with a pairing code: the session replaces the token (it would act as the admin instead). */
    fun setSession(url: String, session: String) = edit {
        putString("serverUrl", normalizeServerUrl(url)); putString("session", session); putString("token", ""); putBoolean("onboarded", true)
    }
    /** Sign-out: forget every credential (keep the server address). */
    fun clearCredentials() = edit { putString("token", ""); putString("session", "") }

    /** Small JSON blobs cached between launches (signed-in user, people directory). */
    fun cache(key: String): String? = prefs.getString("cache.$key", null)
    fun putCache(key: String, value: String?) { prefs.edit().apply { if (value == null) remove("cache.$key") else putString("cache.$key", value) }.apply() }
    fun skipSetup() = edit { putBoolean("onboarded", true) }
    fun setTheme(t: ThemePref) = edit { putString("theme", t.name) }
    fun setGlass(on: Boolean) = edit { putBoolean("glass", on) }
    fun setContentGlass(on: Boolean) = edit { putBoolean("contentGlass", on) }
    fun setIdeaLayout(l: IdeaLayout) = edit { putString("ideaLayout", l.name) }
    fun setIdeaSort(s: IdeaSort) = edit { putString("ideaSort", s.name) }
    fun setIdeaProjects(ids: List<String>) = edit { putString("ideaProjects", ids.joinToString(",")) }
    fun setHideEmptyColumns(on: Boolean) = edit { putBoolean("hideEmptyColumns", on) }

    companion object {
        /** "project.lib" → "https://project.lib"; trailing slashes removed. */
        fun normalizeServerUrl(url: String): String {
            val u = url.trim().trimEnd('/')
            if (u.isEmpty()) return u
            return if (Regex("^[a-z][a-z0-9+.-]*://", RegexOption.IGNORE_CASE).containsMatchIn(u)) u else "https://$u"
        }
    }
}
