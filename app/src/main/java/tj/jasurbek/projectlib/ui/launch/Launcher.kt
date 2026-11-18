package tj.jasurbek.projectlib.ui.launch

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import tj.jasurbek.projectlib.data.LaunchMode
import tj.jasurbek.projectlib.data.LaunchOption
import tj.jasurbek.projectlib.data.Link
import tj.jasurbek.projectlib.data.LinkKind
import tj.jasurbek.projectlib.data.Platform
import tj.jasurbek.projectlib.data.Project
import tj.jasurbek.projectlib.data.fallbackFor
import tj.jasurbek.projectlib.data.linkOptions
import tj.jasurbek.projectlib.data.projectOptions
import tj.jasurbek.projectlib.ui.common.LocalToast
import tj.jasurbek.projectlib.ui.designsystem.DS
import tj.jasurbek.projectlib.ui.designsystem.DSButton
import tj.jasurbek.projectlib.ui.designsystem.DSButtonSize
import tj.jasurbek.projectlib.ui.designsystem.DSButtonVariant
import tj.jasurbek.projectlib.ui.designsystem.DSSpace

val MODE_ICON: Map<LaunchMode, ImageVector> = mapOf(
    LaunchMode.Embed to Icons.Rounded.PlayArrow,
    LaunchMode.Browser to Icons.AutoMirrored.Rounded.OpenInNew,
    LaunchMode.Deeplink to Icons.Rounded.Apps,
    LaunchMode.Qr to Icons.Rounded.QrCode2,
)

fun platformIcon(p: Platform): ImageVector = when (p) {
    Platform.Any, Platform.Web -> Icons.Rounded.Language
    Platform.Ios -> Icons.Rounded.PhoneIphone
    Platform.Android -> Icons.Rounded.PhoneAndroid
    Platform.Macos, Platform.Windows -> Icons.Rounded.Laptop
    Platform.Linux -> Icons.Rounded.Terminal
}

fun linkIcon(l: Link): ImageVector = when (l.kind) {
    LinkKind.Repo -> Icons.Rounded.Code
    LinkKind.Docs -> Icons.AutoMirrored.Rounded.MenuBook
    LinkKind.Other -> Icons.Rounded.Link
    LinkKind.Store -> Icons.Rounded.Storefront
    LinkKind.Download -> Icons.Rounded.Download
    LinkKind.Local -> Icons.Rounded.Terminal
    else -> platformIcon(l.effectivePlatform)
}

/** Opens a URL with whatever app handles it (browser, Play Store…). Returns false when nothing can. */
fun openExternal(context: Context, url: String): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}

/**
 * Opens a deep link in the app it belongs to. For https app links the browser is not accepted
 * (FLAG_ACTIVITY_REQUIRE_NON_BROWSER) — otherwise "the app isn't installed" could never be detected.
 */
fun openDeepLink(context: Context, url: String): Boolean = try {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (url.startsWith("http", ignoreCase = true)) intent.addFlags(Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER)
    context.startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}

/** Runs a launch option; owns the QR / "app didn't open" dialogs. Provided once at the app root. */
class LaunchController(
    private val context: Context,
    private val openEmbedded: (projectId: String, linkId: String) -> Unit,
    private val showDialog: (LaunchDialog?) -> Unit,
    private val toast: (String) -> Unit,
) {
    fun launch(project: Project, o: LaunchOption) {
        val link = o.link
        when (o.mode) {
            LaunchMode.Embed -> openEmbedded(project.id, link.id)
            LaunchMode.Browser -> if (!openExternal(context, link.url)) toast("Нет приложения, которое откроет эту ссылку")
            LaunchMode.Qr -> showDialog(LaunchDialog.Qr(link.label, link.url, link.effectivePlatform))
            LaunchMode.Deeplink -> if (!openDeepLink(context, link.url)) showDialog(LaunchDialog.NotOpened(link, fallbackFor(project, link)))
        }
    }

    fun open(url: String) { if (!openExternal(context, url)) toast("Нет приложения, которое откроет эту ссылку") }
}

sealed interface LaunchDialog {
    data class Qr(val title: String, val url: String, val platform: Platform) : LaunchDialog
    data class NotOpened(val link: Link, val fallback: String?) : LaunchDialog
}

val LocalLauncher = staticCompositionLocalOf<LaunchController> { error("No LaunchController") }

@Composable
fun LaunchProvider(openEmbedded: (String, String) -> Unit, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val toast = LocalToast.current
    var dialog by remember { mutableStateOf<LaunchDialog?>(null) }
    val controller = remember(context) { LaunchController(context, openEmbedded, { dialog = it }, { toast(it) }) }
    CompositionLocalProvider(LocalLauncher provides controller) {
        content()
        when (val d = dialog) {
            is LaunchDialog.Qr -> QrDialog(d, onClose = { dialog = null })
            is LaunchDialog.NotOpened -> AlertDialog(
                onDismissRequest = { dialog = null },
                shape = MaterialTheme.shapes.extraLarge,
                containerColor = DS.colors.surface,
                title = { Text("Приложение не открылось?", style = MaterialTheme.typography.titleLarge) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(DSSpace.sm)) {
                        Text(
                            if (d.fallback != null) "Похоже, приложение не установлено на этом телефоне. Установите его и попробуйте ещё раз."
                            else "Похоже, приложение не установлено. Добавьте в проект ссылку на установку (магазин, TestFlight или загрузка), чтобы она появилась здесь.",
                            style = MaterialTheme.typography.bodyLarge, color = DS.colors.textSecondary,
                        )
                        Text(d.link.url, style = MaterialTheme.typography.bodySmall, color = DS.colors.textTertiary)
                    }
                },
                confirmButton = {
                    if (d.fallback != null) DSButton("Установить", { controller.open(d.fallback); dialog = null }, leadingIcon = Icons.Rounded.Download)
                },
                dismissButton = { DSButton("Закрыть", { dialog = null }, variant = DSButtonVariant.Secondary) },
            )
            null -> Unit
        }
    }
}

@Composable
private fun QrDialog(d: LaunchDialog.Qr, onClose: () -> Unit) {
    val context = LocalContext.current
    val toast = LocalToast.current
    val bitmap = remember(d.url) { qrBitmap(d.url, 720) }
    AlertDialog(
        onDismissRequest = onClose,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = DS.colors.surface,
        title = { Text(d.title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(DSSpace.md)) {
                Text(
                    "Наведите камеру ${if (d.platform.isMobile) "устройства ${d.platform.label}" else "другого устройства"} на код.",
                    style = MaterialTheme.typography.bodyMedium, color = DS.colors.textSecondary, textAlign = TextAlign.Center,
                )
                // QR codes are always dark on white so any camera reads them (also in dark mode).
                Box(Modifier.clip(MaterialTheme.shapes.medium).background(Color.White).padding(DSSpace.md)) {
                    Image(bitmap.asImageBitmap(), "QR-код: ${d.url}", Modifier.size(220.dp))
                }
                Text(d.url, style = MaterialTheme.typography.bodySmall, color = DS.colors.textTertiary, textAlign = TextAlign.Center)
            }
        },
        confirmButton = {
            DSButton("Скопировать", {
                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(d.title, d.url))
                toast("Ссылка скопирована")
            }, variant = DSButtonVariant.Secondary, leadingIcon = Icons.Rounded.ContentCopy)
        },
        dismissButton = { DSButton("Закрыть", onClose, variant = DSButtonVariant.Ghost) },
    )
}

fun qrBitmap(text: String, size: Int): Bitmap {
    val matrix = QRCodeWriter().encode(
        text, BarcodeFormat.QR_CODE, size, size,
        mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.CHARACTER_SET to "UTF-8"),
    )
    val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}

/**
 * The launch button: best option for this phone as the main action + a menu with the others.
 * [link] = one link only (link row); otherwise all launchable links of the project (project card).
 */
@Composable
fun LaunchButton(project: Project, link: Link? = null, compact: Boolean = false) {
    val launcher = LocalLauncher.current
    val options = if (link != null) linkOptions(link) else projectOptions(project)
    if (options.isEmpty()) {
        if (link?.kind == LinkKind.Local) Text("Только на Mac", style = MaterialTheme.typography.bodySmall, color = DS.colors.textTertiary)
        return
    }
    val best = options.first()
    var menu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpace.xs)) {
        DSButton(
            best.mode.button, { launcher.launch(project, best) },
            variant = if (compact) DSButtonVariant.Secondary else DSButtonVariant.Outline,
            size = DSButtonSize.Sm, leadingIcon = MODE_ICON.getValue(best.mode),
        )
        if (options.size > 1) {
            Box {
                androidx.compose.material3.IconButton(onClick = { menu = true }) {
                    Icon(Icons.Rounded.KeyboardArrowDown, "Другие способы запуска", tint = DS.colors.accentText)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = MaterialTheme.shapes.medium) {
                    options.forEach { o ->
                        DropdownMenuItem(
                            leadingIcon = { Icon(MODE_ICON.getValue(o.mode), null, tint = DS.colors.accentText) },
                            text = {
                                Column {
                                    Text(if (link == null) "${o.link.label} · ${o.mode.button}" else o.mode.button, style = MaterialTheme.typography.bodyMedium)
                                    Text(o.hint, style = MaterialTheme.typography.bodySmall, color = DS.colors.textSecondary)
                                }
                            },
                            onClick = { menu = false; launcher.launch(project, o) },
                        )
                    }
                }
            }
        }
    }
}

/** Small platform icons of a project's launchable links (project card footer). */
@Composable
fun PlatformIcons(project: Project) {
    val platforms = project.launchLinks.map { it.effectivePlatform }.map { if (it == Platform.Any) Platform.Web else it }.distinct()
    Row(horizontalArrangement = Arrangement.spacedBy(DSSpace.xs)) {
        platforms.forEach { Icon(platformIcon(it), it.label, tint = DS.colors.textTertiary, modifier = Modifier.size(18.dp)) }
    }
}

