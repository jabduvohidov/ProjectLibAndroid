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

/**
 * An idea's status is the id of a column (StatusDef) of its project — projects can have their own columns.
 * These are the ids of the default columns (the original fixed statuses).
 */
object IdeaStatus {
    const val New = "new"
    const val Planned = "planned"
    const val InProgress = "in_progress"
    const val Paused = "paused"
    const val Done = "done"
    const val Dropped = "dropped"
}

@Serializable
enum class StatusTone(val ds: DSTone) {
    @SerialName("neutral") Neutral(DSTone.Neutral),
    @SerialName("info") Info(DSTone.Info),
    @SerialName("success") Success(DSTone.Success),
    @SerialName("warning") Warning(DSTone.Warning),
    @SerialName("danger") Danger(DSTone.Danger),
}

/**
 * A board column of a project (port of StatusDef in src/lib/types.ts). [percent] is how far an idea in this
 * column counts toward "Прогресс идей" (100 = done); [excluded] columns are left out of progress.
 */
@Serializable
data class StatusDef(
    val id: String,
    val name: String,
    val tone: StatusTone = StatusTone.Neutral,
    val percent: Int = 0,
    val excluded: Boolean? = null,
) {
    val label get() = name
    val dsTone get() = tone.ds
    /** Done (100 %) or excluded from progress — no deadline warnings, struck through. */
    val isClosed get() = excluded == true || percent >= 100
}

val DEFAULT_STATUSES = listOf(
    StatusDef(IdeaStatus.New, "Новая", StatusTone.Neutral, 0),
    StatusDef(IdeaStatus.Planned, "Запланировано", StatusTone.Info, 10),
    StatusDef(IdeaStatus.InProgress, "В процессе", StatusTone.Warning, 50),
    StatusDef(IdeaStatus.Paused, "На паузе", StatusTone.Neutral, 50),
    StatusDef(IdeaStatus.Done, "Выполнено", StatusTone.Success, 100),
    StatusDef(IdeaStatus.Dropped, "Отменено", StatusTone.Danger, 0, excluded = true),
)

/** The column of [id]; an unknown id (column deleted on another device) shows in the first column. */
fun List<StatusDef>.of(id: String): StatusDef = firstOrNull { it.id == id } ?: first()

@Serializable
enum class Priority(val label: String, val tone: DSTone, val rank: Int) {
    // Most to least urgent (menu order); rank drives board sorting. Mirrors PRIORITY in web types.ts.
    @SerialName("urgent") Urgent("Срочный", DSTone.Danger, 4),
    @SerialName("high") High("Высокий", DSTone.Warning, 3),
    @SerialName("medium") Medium("Средний", DSTone.Info, 2),
    @SerialName("low") Low("Низкий", DSTone.Neutral, 1),
    @SerialName("none") None("Без приоритета", DSTone.Neutral, 0),
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
    /** Column id in the project's [Project.columns]. */
    val status: String = IdeaStatus.New,
    val priority: Priority = Priority.None,
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
    /** Board columns. null / empty = DEFAULT_STATUSES. */
    val statuses: List<StatusDef>? = null,
) {
    val columns: List<StatusDef> get() = statuses?.takeIf { it.isNotEmpty() } ?: DEFAULT_STATUSES
    val launchLinks get() = links.filter { it.kind.launchable }
    private val rights get() = access ?: Access.Owner
    val canEdit get() = rights != Access.View
    val isOwn get() = rights == Access.Owner
    /** Owner or admin: delete the project and manage who has access. */
    val canManage get() = rights == Access.Owner || rights == Access.Admin
    val shareCount get() = shares?.size ?: 0
}

data class Progress(val done: Int, val total: Int, val percent: Int)

/** "Прогресс идей": the average column percent of the ideas (excluded columns don't count); done = 100 % columns. */
fun Project.progress(): Progress {
    val relevant = ideas.map { columns.of(it.status) }.filter { it.excluded != true }
    val done = relevant.count { it.percent >= 100 }
    val sum = relevant.sumOf { it.percent.coerceIn(0, 100) }
    return Progress(done, relevant.size, if (relevant.isEmpty()) 0 else Math.round(sum.toFloat() / relevant.size))
}

fun Idea.isOpenIn(columns: List<StatusDef>) = !columns.of(status).isClosed

val byIdeaOrder = compareBy<Idea> { it.sortOrder }
/** Board columns: most urgent first, then the manual (drag) order. */
val byPriorityThenOrder = compareByDescending<Idea> { it.priority.rank }.thenBy { it.sortOrder }

fun todayISO(): String = LocalDate.now().toString()
fun addDaysISO(days: Long): String = LocalDate.now().plusDays(days).toString()

enum class DueState { Overdue, Today, Soon, Later }

/** Deadline state of an open idea (ideas in done / excluded columns have none). */
fun Idea.dueState(columns: List<StatusDef> = DEFAULT_STATUSES, today: String = todayISO()): DueState? {
    val d = dueDate
    if (d.isNullOrEmpty() || !isOpenIn(columns)) return null
    return when {
        d < today -> DueState.Overdue
        d == today -> DueState.Today
        d <= LocalDate.parse(today).plusDays(3).toString() -> DueState.Soon
        else -> DueState.Later
    }
}
