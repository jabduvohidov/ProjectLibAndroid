package tj.jasurbek.projectlib.ui.forms

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import tj.jasurbek.projectlib.data.Link
import tj.jasurbek.projectlib.data.LinkKind
import tj.jasurbek.projectlib.data.Platform
import tj.jasurbek.projectlib.data.Project
import tj.jasurbek.projectlib.data.ProjectStatus
import tj.jasurbek.projectlib.data.Repository.ProjectInput
import tj.jasurbek.projectlib.data.guessPlatform
import tj.jasurbek.projectlib.data.isAnyUrl
import tj.jasurbek.projectlib.data.isHttpUrl
import tj.jasurbek.projectlib.data.normalizeUrl
import tj.jasurbek.projectlib.data.uid
import tj.jasurbek.projectlib.ui.designsystem.DS
import tj.jasurbek.projectlib.ui.designsystem.DSButton
import tj.jasurbek.projectlib.ui.designsystem.DSButtonSize
import tj.jasurbek.projectlib.ui.designsystem.DSButtonVariant
import tj.jasurbek.projectlib.ui.designsystem.DSChip
import tj.jasurbek.projectlib.ui.designsystem.DSSpace
import tj.jasurbek.projectlib.ui.designsystem.DSTextField

/* ---------------- link draft + validation (port of LinkEditor.tsx) ---------------- */

data class LinkDraft(
    val id: String, val label: String = "", val url: String = "", val kind: LinkKind,
    val platform: Platform? = null, val fallbackUrl: String = "", val command: String = "", val cwd: String = "",
    /** true once the user picked the platform by hand (stop guessing it from the URL). */
    val platformTouched: Boolean = false,
)

data class LinkErrors(val url: String? = null, val fallbackUrl: String? = null, val command: String? = null) {
    val any get() = url != null || fallbackUrl != null || command != null
}

fun Link.toDraft() = LinkDraft(id, label, url, kind, platform, fallbackUrl ?: "", command ?: "", cwd ?: "", platform != null)

sealed interface Finalized {
    data class Ok(val link: Link) : Finalized
    data class Invalid(val errors: LinkErrors) : Finalized
    data object Empty : Finalized
}

fun finalizeLink(d: LinkDraft): Finalized {
    val label = d.label.trim().ifEmpty { d.kind.label }
    if (d.kind == LinkKind.Local) {
        if (d.command.isBlank() && d.url.isBlank() && d.label.isBlank()) return Finalized.Empty
        val url = if (d.url.isNotBlank()) normalizeUrl(d.url) else ""
        val errors = LinkErrors(
            command = if (d.command.isBlank()) "Укажите команду" else null,
            url = if (url.isNotEmpty() && !isHttpUrl(url)) "Адрес вида http://localhost:3000" else null,
        )
        if (errors.any) return Finalized.Invalid(errors)
        return Finalized.Ok(Link(d.id, label, url, d.kind, d.platform, command = d.command.trim(), cwd = d.cwd.trim()))
    }
    if (d.url.isBlank() && d.label.isBlank()) return Finalized.Empty
    // Deep links keep their own scheme (myapp://); everything else defaults to https://.
    val url = if (d.kind == LinkKind.Deeplink && Regex("^[a-z][a-z0-9+.-]*:", RegexOption.IGNORE_CASE).containsMatchIn(d.url.trim())) d.url.trim() else normalizeUrl(d.url)
    val urlOk = if (d.kind == LinkKind.Deeplink) isAnyUrl(url) else isHttpUrl(url)
    val fallback = if (d.fallbackUrl.isNotBlank()) normalizeUrl(d.fallbackUrl) else ""
    val errors = LinkErrors(
        url = if (!urlOk) (if (d.kind == LinkKind.Deeplink) "Например myapp://screen или https://app.example.com/open" else "Нужен адрес вида https://example.com") else null,
        fallbackUrl = if (fallback.isNotEmpty() && !isAnyUrl(fallback)) "Некорректная ссылка" else null,
    )
    if (errors.any) return Finalized.Invalid(errors)
    return Finalized.Ok(
        Link(
            d.id, label, url, d.kind,
            platform = if (d.kind.hasPlatform) d.platform else null,
            fallbackUrl = if (d.kind == LinkKind.Deeplink && fallback.isNotEmpty()) fallback else null,
        )
    )
}

private data class UrlField(val label: String, val placeholder: String, val hint: String? = null)

private fun urlField(kind: LinkKind) = when (kind) {
    LinkKind.Web -> UrlField("Адрес", "https://app.example.com")
    LinkKind.Deeplink -> UrlField("Диплинк", "myapp://projects", "Схема приложения или universal / app link")
    LinkKind.Store -> UrlField("Ссылка в магазине", "play.google.com/… · testflight.apple.com/…")
    LinkKind.Download -> UrlField("Ссылка на скачивание", "https://github.com/…/releases/latest")
    LinkKind.Repo -> UrlField("Адрес", "https://github.com/…")
    else -> UrlField("Адрес", "https://…")
}

private data class Template(val label: String, val command: String, val url: String? = null, val platform: Platform? = null)

/** Ready-made local commands (same as the web form). */
private val TEMPLATES = listOf(
    Template("npm run dev", "npm run dev", "http://localhost:5173", Platform.Web),
    Template("Expo", "npx expo start", "http://localhost:8081", Platform.Ios),
    Template("iOS-симулятор", "open -a Simulator && xcrun simctl bootstatus booted -b && xcrun simctl launch booted com.example.app", platform = Platform.Ios),
    Template("Android-эмулятор", "adb wait-for-device && adb shell monkey -p com.example.app -c android.intent.category.LAUNCHER 1", platform = Platform.Android),
    Template("macOS-приложение", "open -a \"MyApp\"", platform = Platform.Macos),
)

private val URL_KEYBOARD = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false, capitalization = KeyboardCapitalization.None)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LinkEditor(draft: LinkDraft, errors: LinkErrors?, onChange: (LinkDraft) -> Unit, onRemove: () -> Unit) {
    val shape = MaterialTheme.shapes.medium
    Column(
        Modifier.fillMaxWidth().clip(shape).border(1.dp, DS.colors.border, shape).padding(DSSpace.md),
        verticalArrangement = Arrangement.spacedBy(DSSpace.md),
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(DSSpace.sm)) {
            SelectField(
                "Тип", draft.kind, LinkKind.entries, { it.label },
                { kind -> onChange(draft.copy(kind = kind, platform = if (kind == LinkKind.Local && !draft.platformTouched) null else draft.platform)) },
                Modifier.weight(1f),
            )
            FilledTonalIconButton(
                onClick = onRemove, modifier = Modifier.size(52.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = DS.colors.dangerSubtle, contentColor = DS.colors.danger),
            ) { Icon(Icons.Rounded.Delete, "Удалить ссылку") }
        }
        DSTextField("Название", draft.label, { onChange(draft.copy(label = it)) }, placeholder = draft.kind.label)

        if (draft.kind.hasPlatform || draft.kind == LinkKind.Local) {
            SelectField(
                if (draft.kind == LinkKind.Local) "Что запускается" else "Платформа", draft.platform ?: Platform.Any, Platform.entries, { it.label },
                { onChange(draft.copy(platform = if (it == Platform.Any) null else it, platformTouched = true)) },
            )
        }

        if (draft.kind == LinkKind.Local) {
            Text("Шаблоны", style = MaterialTheme.typography.bodySmall, color = DS.colors.textTertiary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(DSSpace.xs), verticalArrangement = Arrangement.spacedBy(DSSpace.xs)) {
                TEMPLATES.forEach { t ->
                    DSChip(t.label, selected = false, soft = true, onClick = {
                        onChange(
                            draft.copy(
                                command = t.command,
                                url = if (t.url != null && draft.url.isBlank()) t.url else draft.url,
                                platform = if (t.platform != null && !draft.platformTouched) t.platform else draft.platform,
                                label = draft.label.ifBlank { t.label },
                            )
                        )
                    })
                }
            }
            TextArea(
                "Команда", draft.command, { onChange(draft.copy(command = it)) }, placeholder = "npm run dev", mono = true, maxLength = 2000,
                error = errors?.command, hint = "Выполняется агентом на Mac в вашей оболочке (zsh). С телефона команда не запускается — только хранится в проекте.",
            )
            FormField("Папка проекта", draft.cwd, { onChange(draft.copy(cwd = it)) }, "~/Projects/my-app", keyboard = URL_KEYBOARD, mono = true)
            FormField(
                "Открыть после запуска", draft.url, { onChange(draft.copy(url = it)) }, "http://localhost:3000",
                hint = "Необязательно: ждём, пока порт откроется", error = errors?.url, keyboard = URL_KEYBOARD,
            )
        } else {
            val f = urlField(draft.kind)
            FormField(
                f.label, draft.url,
                { url -> onChange(draft.copy(url = url, platform = if (!draft.platformTouched) guessPlatform(url) ?: draft.platform else draft.platform)) },
                f.placeholder, hint = f.hint, error = errors?.url, keyboard = URL_KEYBOARD,
            )
            if (draft.kind == LinkKind.Deeplink) {
                FormField(
                    "Если приложение не установлено", draft.fallbackUrl, { onChange(draft.copy(fallbackUrl = it)) }, "https://play.google.com/…",
                    hint = "Необязательно: иначе возьмём ссылку «Магазин / Загрузка» той же платформы", error = errors?.fallbackUrl, keyboard = URL_KEYBOARD,
                )
            }
        }
    }
}

/* ---------------- project form ---------------- */

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProjectFormSheet(initial: Project?, onDismiss: () -> Unit, onSubmit: (ProjectInput) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var status by remember { mutableStateOf(initial?.status ?: ProjectStatus.Idea) }
    var tags by remember { mutableStateOf(initial?.tags?.joinToString(", ") ?: "") }
    val links = remember { mutableStateListOf<LinkDraft>().apply { initial?.links?.map { it.toDraft() }?.let(::addAll) } }
    var nameError by remember { mutableStateOf<String?>(null) }
    val linkErrors = remember { mutableStateMapOf<String, LinkErrors>() }
    var secret by remember { mutableStateOf(initial?.secret == true) }
    val unlocked by tj.jasurbek.projectlib.App.instance.secretLock.unlocked.collectAsState()
    // Owner only; a shared project must stop being shared first; turning it on needs the lock open (else it just vanishes).
    val own = initial == null || initial.isOwn
    val shared = (initial?.shares?.size ?: 0) > 0

    FormSheet(
        title = if (initial != null) "Изменить проект" else "Новый проект",
        submitLabel = if (initial != null) "Сохранить" else "Создать",
        onDismiss = onDismiss,
        onSubmit = submit@{
            linkErrors.clear()
            val cleaned = mutableListOf<Link>()
            links.forEach { d ->
                when (val r = finalizeLink(d)) {
                    is Finalized.Ok -> cleaned += r.link
                    is Finalized.Invalid -> linkErrors[d.id] = r.errors
                    Finalized.Empty -> Unit
                }
            }
            nameError = if (name.isBlank()) "Введите название" else null
            if (nameError != null || linkErrors.isNotEmpty()) return@submit false
            onSubmit(ProjectInput(name.trim(), description.trim(), status, tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct(), cleaned,
                secret = if (own && secret != (initial?.secret == true)) secret else null))
            true
        },
    ) {
        DSTextField("Название", name, { name = it; nameError = null }, placeholder = "Например, Трекер привычек", error = nameError)
        TextArea("Описание", description, { description = it }, placeholder = "Коротко: что это и зачем", maxLength = 500)
        Column(verticalArrangement = Arrangement.spacedBy(DSSpace.sm)) {
            Text("Статус", style = MaterialTheme.typography.labelLarge, color = DS.colors.textSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(DSSpace.xs), verticalArrangement = Arrangement.spacedBy(DSSpace.xs)) {
                ProjectStatus.entries.forEach { s -> DSChip(s.label, selected = status == s, soft = true, onClick = { status = s }) }
            }
        }
        DSTextField("Теги", tags, { tags = it }, placeholder = "react, mobile", hint = "Через запятую")
        if (own) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpace.md)) {
            Column(Modifier.weight(1f)) {
                Text("Секретный проект", style = MaterialTheme.typography.bodyLarge, color = DS.colors.textPrimary)
                Text(
                    when {
                        shared -> "Сначала закройте общий доступ — секретным проектом нельзя делиться."
                        !unlocked && !secret -> "Чтобы включить, откройте секретные проекты (замок на экране «Проекты»)."
                        else -> "Виден только вам и только после пароля или отпечатка на устройстве. Администраторы его не видят."
                    },
                    style = MaterialTheme.typography.bodySmall, color = DS.colors.textSecondary,
                )
            }
            androidx.compose.material3.Switch(
                checked = secret, onCheckedChange = { secret = it }, enabled = !shared && (unlocked || secret),
                colors = androidx.compose.material3.SwitchDefaults.colors(
                    checkedTrackColor = DS.colors.accent, checkedThumbColor = DS.colors.textOnAccent,
                    uncheckedTrackColor = DS.colors.fill, uncheckedThumbColor = DS.colors.surfaceRaised, uncheckedBorderColor = DS.colors.borderStrong,
                ),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(DSSpace.md)) {
            Text("Приложения и ссылки", style = MaterialTheme.typography.labelLarge, color = DS.colors.textSecondary)
            if (links.isEmpty()) {
                Text(
                    "Web-версия, диплинк в мобильное или десктопное приложение, магазин или TestFlight, локальная команда для Mac — всё это можно запускать прямо отсюда.",
                    style = MaterialTheme.typography.bodySmall, color = DS.colors.textTertiary,
                )
            }
            links.forEachIndexed { i, d ->
                LinkEditor(d, linkErrors[d.id], onChange = { links[i] = it; linkErrors.remove(d.id) }, onRemove = { links.removeAt(i); linkErrors.remove(d.id) })
            }
            DSButton(
                "Добавить ссылку",
                { links += LinkDraft(id = uid(), kind = if (links.any { it.kind == LinkKind.Web }) LinkKind.Deeplink else LinkKind.Web) },
                variant = DSButtonVariant.Outline, size = DSButtonSize.Sm, leadingIcon = Icons.Rounded.Add,
            )
        }
    }
}

