package tj.jasurbek.projectlib.ui.forms

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.SubcomposeAsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tj.jasurbek.projectlib.App
import tj.jasurbek.projectlib.data.Attachment
import tj.jasurbek.projectlib.ui.designsystem.DS
import tj.jasurbek.projectlib.ui.designsystem.DSButton
import tj.jasurbek.projectlib.ui.designsystem.DSButtonSize
import tj.jasurbek.projectlib.ui.designsystem.DSButtonVariant
import tj.jasurbek.projectlib.ui.designsystem.DSSpace

private const val MAX_UPLOAD = 10L * 1024 * 1024
private const val MAX_FILES = 20
private val TYPES = setOf("image/png", "image/jpeg", "image/gif", "image/webp")

fun formatSize(n: Long) = if (n < 1024 * 1024) "${maxOf(1, Math.round(n / 1024.0))} КБ" else String.format("%.1f МБ", n / 1024.0 / 1024.0)

class PickedFile(val name: String, val type: String, val bytes: ByteArray)

fun readPickedImage(context: Context, uri: Uri): PickedFile {
    val cr = context.contentResolver
    val name = cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    } ?: "image"
    val type = cr.getType(uri) ?: "application/octet-stream"
    if (type !in TYPES) throw IllegalArgumentException("«$name»: можно PNG, JPEG, GIF или WebP")
    val bytes = cr.openInputStream(uri)?.use { input ->
        val out = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(chunk)
            if (n < 0) break
            out.write(chunk, 0, n)
            if (out.size() > MAX_UPLOAD) throw IllegalArgumentException("«$name» больше 10 МБ")
        }
        out.toByteArray()
    } ?: throw IllegalArgumentException("Не удалось прочитать «$name»")
    return PickedFile(name, type, bytes)
}

/** Image of an attachment from the server (cached by Coil, so once seen it shows offline too). */
@Composable
fun AttachmentImage(a: Attachment, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val url = App.instance.api.attachmentUrl(a.id)
    SubcomposeAsyncImage(
        model = url, contentDescription = a.name, contentScale = contentScale, modifier = modifier,
        loading = { Box(Modifier.fillMaxSize().background(DS.colors.fill)) },
        error = {
            Box(Modifier.fillMaxSize().background(DS.colors.fill), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.BrokenImage, null, tint = DS.colors.textTertiary)
            }
        },
    )
}

/** Row of small thumbnails (idea row / card); tap opens the viewer. */
@Composable
fun AttachmentThumbs(items: List<Attachment>, max: Int = 4, size: Dp = 56.dp) {
    var viewing by remember { mutableStateOf<Int?>(null) }
    Row(horizontalArrangement = Arrangement.spacedBy(DSSpace.xs), verticalAlignment = Alignment.CenterVertically) {
        items.take(max).forEachIndexed { i, a ->
            AttachmentImage(
                a, Modifier.size(size).clip(MaterialTheme.shapes.small).border(1.dp, DS.colors.border, MaterialTheme.shapes.small).clickable { viewing = i },
            )
        }
        if (items.size > max) Text("+${items.size - max}", style = MaterialTheme.typography.bodySmall, color = DS.colors.textSecondary)
    }
    viewing?.let { AttachmentViewer(items, it, onClose = { viewing = null }) }
}

/** Full-screen viewer with previous / next by tapping the image sides. */
@Composable
fun AttachmentViewer(items: List<Attachment>, start: Int, onClose: () -> Unit) {
    var index by remember { mutableIntStateOf(start.coerceIn(0, items.lastIndex)) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AttachmentImage(items[index], Modifier.fillMaxSize().clickable { index = (index + 1) % items.size }, ContentScale.Fit)
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(DSSpace.sm), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(horizontal = DSSpace.sm)) {
                    Text(items[index].name, style = MaterialTheme.typography.bodyMedium, color = Color.White, maxLines = 1)
                    if (items.size > 1) Text("${index + 1} из ${items.size}", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                }
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Закрыть", tint = Color.White) }
            }
        }
    }
}

/** Attachments in the idea form: thumbnails with remove buttons + "add images" (photo picker → upload). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AttachmentEditor(value: List<Attachment>, onChange: (List<Attachment>) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uploading by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var viewing by remember { mutableStateOf<Int?>(null) }
    // Uploads finish asynchronously: always append to the latest list.
    val current = remember { mutableStateOf(value) }
    current.value = value

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_FILES)) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        error = null
        val room = MAX_FILES - current.value.size
        if (uris.size > room) error = "Не больше $MAX_FILES вложений на идею"
        uris.take(room).forEach { uri ->
            uploading++
            scope.launch {
                try {
                    val file = withContext(Dispatchers.IO) { readPickedImage(context, uri) }
                    val a = App.instance.repo.upload(file.bytes, file.type, file.name)
                    if (current.value.none { it.id == a.id }) onChange(current.value + a)
                } catch (e: Exception) {
                    error = e.message ?: "Не удалось загрузить"
                } finally {
                    uploading--
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(DSSpace.sm)) {
        Text("Вложения", style = MaterialTheme.typography.labelLarge, color = DS.colors.textSecondary)
        if (value.isNotEmpty() || uploading > 0) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(DSSpace.sm), verticalArrangement = Arrangement.spacedBy(DSSpace.sm)) {
                value.forEachIndexed { i, a ->
                    Box {
                        AttachmentImage(a, Modifier.size(80.dp).clip(MaterialTheme.shapes.medium).border(1.dp, DS.colors.border, MaterialTheme.shapes.medium).clickable { viewing = i })
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f))
                                .clickable { onChange(value.filter { it.id != a.id }) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.Close, "Убрать «${a.name}»", tint = Color.White, modifier = Modifier.size(16.dp)) }
                    }
                }
                repeat(uploading) {
                    Box(Modifier.size(80.dp).clip(MaterialTheme.shapes.medium).background(DS.colors.fill), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = DS.colors.accent)
                    }
                }
            }
        }
        DSButton(
            "Добавить изображения",
            { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            variant = DSButtonVariant.Outline, size = DSButtonSize.Sm, leadingIcon = Icons.Rounded.AddPhotoAlternate,
            enabled = value.size < MAX_FILES,
        )
        error?.let { ErrorText(it) } ?: Text(
            "PNG, JPEG, GIF или WebP до 10 МБ. Загружаются на сервер, поэтому нужна связь.",
            style = MaterialTheme.typography.bodySmall, color = DS.colors.textTertiary,
        )
    }
    viewing?.let { AttachmentViewer(value, it, onClose = { viewing = null }) }
}
