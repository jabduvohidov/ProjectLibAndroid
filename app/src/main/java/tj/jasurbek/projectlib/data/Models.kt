package tj.jasurbek.projectlib.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tj.jasurbek.projectlib.ui.designsystem.DSTone
import java.time.LocalDate

/*
 * Data model — the same JSON as the web app (src/lib/types.ts) so both talk to one server.
 */

@Serializable
enum class ProjectStatus(val label: String, val tone: DSTone) {
    @SerialName("idea") Idea("Идея", DSTone.Neutral),
    @SerialName("active") Active("В разработке", DSTone.Info),
    @SerialName("live") Live("Запущен", DSTone.Success),
    @SerialName("paused") Paused("На паузе", DSTone.Warning),
    @SerialName("archived") Archived("Архив", DSTone.Neutral),
}

@Serializable
enum class IdeaStatus(val label: String, val tone: DSTone) {
    @SerialName("new") New("Новая", DSTone.Neutral),
    @SerialName("planned") Planned("Запланировано", DSTone.Info),
    @SerialName("in_progress") InProgress("В процессе", DSTone.Warning),
    @SerialName("paused") Paused("На паузе", DSTone.Neutral),
    @SerialName("done") Done("Выполнено", DSTone.Success),
    @SerialName("dropped") Dropped("Отменено", DSTone.Danger),
}

@Serializable
enum class Priority(val label: String, val tone: DSTone) {
    @SerialName("low") Low("Низкий", DSTone.Neutral),
    @SerialName("medium") Medium("Средний", DSTone.Info),
    @SerialName("high") High("Высокий", DSTone.Danger),
}

@Serializable
enum class LinkKind(val label: String) {
    @SerialName("web") Web("Web-версия"),
    @SerialName("deeplink") Deeplink("Диплинк в приложение"),
    @SerialName("store") Store("Магазин / TestFlight"),
    @SerialName("download") Download("Загрузка / релиз"),
    @SerialName("local") Local("Локальный запуск (агент)"),
    @SerialName("repo") Repo("Репозиторий"),
    @SerialName("docs") Docs("Документация"),
    @SerialName("other") Other("Другое");

    /** Kinds where the platform matters (shown in the form, used to pick how to launch). */
    val hasPlatform get() = this == Deeplink || this == Store || this == Download
    val launchable get() = this == Web || this == Deeplink || this == Store || this == Download || this == Local
}

@Serializable
enum class Platform(val label: String) {
    @SerialName("any") Any("Любая"),
    @SerialName("web") Web("Web"),
    @SerialName("ios") Ios("iOS / iPadOS"),
    @SerialName("android") Android("Android"),
    @SerialName("macos") Macos("macOS"),
    @SerialName("windows") Windows("Windows"),
    @SerialName("linux") Linux("Linux");

    val isMobile get() = this == Ios || this == Android
    /** Can a link for this platform be opened right here, on an Android phone? */
    val opensHere get() = this == Any || this == Web || this == Android
}

@Serializable
enum class Role(val label: String, val hint: String) {
    @SerialName("admin") Admin("Админ", "Видит и меняет все проекты, управляет пользователями и приглашениями"),
    @SerialName("member") Member("Участник", "Создаёт свои проекты и работает с теми, которыми поделились"),
    @SerialName("viewer") Viewer("Читатель", "Только просматривает проекты, которыми с ним поделились"),
}

/** What the current user may do with a project (server-computed). */
@Serializable
enum class Access {
    @SerialName("owner") Owner,
    @SerialName("admin") Admin,
    @SerialName("edit") Edit,
    @SerialName("view") View,
}

@Serializable
enum class ShareAccess(val label: String) {
    @SerialName("view") View("Просмотр"),
    @SerialName("edit") Edit("Редактирование"),
}

@Serializable
data class Share(val userId: String, val access: ShareAccess)

@Serializable
data class Link(
    val id: String,
    val label: String,
    /** web / deeplink / store / download: what to open. local: the address to open once the command is up. */
    val url: String = "",
    val kind: LinkKind = LinkKind.Other,
    /** Which device it is for. null = derived from kind. */
    val platform: Platform? = null,
    /** deeplink: where to send the user if the app is not installed. */
    val fallbackUrl: String? = null,
    /** local: shell command run by the agent on the Mac. */
    val command: String? = null,
    val cwd: String? = null,
) {
    val effectivePlatform: Platform
        get() = platform ?: when (kind) {
            LinkKind.Web -> Platform.Web
            LinkKind.Local -> Platform.Macos
            else -> Platform.Any
        }
}

/** An uploaded image (stored on the server by content hash: GET /api/uploads/<id>). */
@Serializable
data class Attachment(val id: String, val name: String, val type: String = "", val size: Long = 0)

@Serializable
data class Idea(
    val id: String,
    val title: String,
    val description: String = "",
    val status: IdeaStatus = IdeaStatus.New,
    val priority: Priority = Priority.Medium,
    /** Deadline as a local calendar date "YYYY-MM-DD"; null / "" = none. */
    val dueDate: String? = null,
    /** Manual position inside its status column (smaller = higher). null = by creation time. */
    val order: Double? = null,
    val attachments: List<Attachment>? = null,
    /** User ids, set by the server. */
    val createdBy: String? = null,
    val updatedBy: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val isOpen get() = status != IdeaStatus.Done && status != IdeaStatus.Dropped
    /** Sort key for manual order; ideas never dragged fall back to "newest first". */
    val sortOrder get() = order ?: -createdAt.toDouble()
}

@Serializable
data class Project(
    val id: String,
    val name: String,
    val description: String = "",
    val status: ProjectStatus = ProjectStatus.Idea,
    val tags: List<String> = emptyList(),
    val links: List<Link> = emptyList(),
    val ideas: List<Idea> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
    /** Set by the server. null = created before users existed. */
    val ownerId: String? = null,
    /** Your rights on this project. null = owner (single-user / not yet synced). */
    val access: Access? = null,
    /** Who else has access — only sent to the owner and admins. */
    val shares: List<Share>? = null,
) {
    val launchLinks get() = links.filter { it.kind.launchable }
    private val rights get() = access ?: Access.Owner
    val canEdit get() = rights != Access.View
    val isOwn get() = rights == Access.Owner
    /** Owner or admin: delete the project and manage who has access. */
    val canManage get() = rights == Access.Owner || rights == Access.Admin
    val shareCount get() = shares?.size ?: 0
}

data class Progress(val done: Int, val total: Int, val percent: Int)

/** Ideas that count toward progress (dropped ideas are excluded). */
fun Project.progress(): Progress {
    val relevant = ideas.filter { it.status != IdeaStatus.Dropped }
    val done = relevant.count { it.status == IdeaStatus.Done }
    return Progress(done, relevant.size, if (relevant.isEmpty()) 0 else Math.round(done * 100f / relevant.size))
}

val byIdeaOrder = compareBy<Idea> { it.sortOrder }

fun todayISO(): String = LocalDate.now().toString()
fun addDaysISO(days: Long): String = LocalDate.now().plusDays(days).toString()

enum class DueState { Overdue, Today, Soon, Later }

/** Deadline state of an open idea (done / dropped ideas have none). */
fun Idea.dueState(today: String = todayISO()): DueState? {
    val d = dueDate
    if (d.isNullOrEmpty() || !isOpen) return null
    return when {
        d < today -> DueState.Overdue
        d == today -> DueState.Today
        d <= LocalDate.parse(today).plusDays(3).toString() -> DueState.Soon
        else -> DueState.Later
    }
}
