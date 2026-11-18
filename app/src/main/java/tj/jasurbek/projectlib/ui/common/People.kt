package tj.jasurbek.projectlib.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import tj.jasurbek.projectlib.App
import tj.jasurbek.projectlib.ui.designsystem.DS
import tj.jasurbek.projectlib.ui.designsystem.DSAvatar
import tj.jasurbek.projectlib.ui.designsystem.DSSpace

/** Profile photo from the server (an upload id), or initials (kit DSAvatar; scaled down for chips). */
@Composable
fun UserAvatar(name: String, avatar: String?, size: Dp = 32.dp, modifier: Modifier = Modifier) {
    val url = avatar?.let { App.instance.api.attachmentUrl(it) }
    Box(modifier.size(size).clip(CircleShape)) {
        if (url == null) Initials(name, size)
        else SubcomposeAsyncImage(
            model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size),
            loading = { Initials(name, size) }, error = { Initials(name, size) },
        )
    }
}

@Composable
private fun Initials(name: String, size: Dp) {
    if (size >= 32.dp) return DSAvatar(name, size)
    val initials = name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
    Box(Modifier.size(size).background(DS.colors.accentSubtle), contentAlignment = Alignment.Center) {
        Text(initials, color = DS.colors.accentText, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Tiny avatar + name of a user id from the people directory (e.g. "от Анна", "изменил(а) Борис"). */
@Composable
fun PersonChip(userId: String?, prefix: String? = null) {
    val auth by App.instance.auth.state.collectAsStateWithLifecycle()
    val p = auth.person(userId) ?: return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpace.xs)) {
        UserAvatar(p.name, p.avatar, 18.dp)
        Text(if (prefix != null) "$prefix ${p.name}" else p.name, style = MaterialTheme.typography.labelMedium, color = DS.colors.textTertiary, maxLines = 1)
    }
}
