package tj.jasurbek.projectlib.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import tj.jasurbek.projectlib.ui.designsystem.glass.dsGlass
import tj.jasurbek.projectlib.ui.designsystem.glass.dsGlassPress
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tj.jasurbek.projectlib.App
import tj.jasurbek.projectlib.ui.designsystem.DS
import tj.jasurbek.projectlib.ui.designsystem.DSBanner
import tj.jasurbek.projectlib.ui.designsystem.DSEmptyState
import tj.jasurbek.projectlib.ui.designsystem.DSSpace
import tj.jasurbek.projectlib.ui.designsystem.DSTone
import tj.jasurbek.projectlib.ui.launch.LocalLauncher

/**
 * Runs a project's web version inside ProjectLib (web: iframe; here a WebView, which — unlike an iframe —
 * isn't blocked by X-Frame-Options). Toolbar: back, address, reload, open in the browser.
 * System back goes back inside the page first, then leaves.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RunScreen(projectId: String, linkId: String, onBack: () -> Unit) {
    val projects by App.instance.repo.shownProjects.collectAsStateWithLifecycle()
    val project = projects.find { it.id == projectId }
    val link = project?.links?.find { it.id == linkId }
    val launcher = LocalLauncher.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var canGoBack by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = canGoBack) { webView?.goBack() }

    Column(Modifier.fillMaxSize().background(DS.colors.bg).statusBarsPadding().navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = DSSpace.sm, vertical = DSSpace.sm),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpace.sm),
        ) {
            ToolbarButton(Icons.AutoMirrored.Rounded.ArrowBack, "Назад к проекту", onClick = onBack)
            Column(
                Modifier.weight(1f).heightIn(min = 48.dp).dsGlass(CircleShape)
                    .padding(horizontal = DSSpace.lg, vertical = DSSpace.xs),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(project?.name ?: "Ссылка", style = MaterialTheme.typography.labelLarge, color = DS.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (link != null) Text(webView?.url ?: link.url, style = MaterialTheme.typography.labelMedium, color = DS.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            ToolbarButton(Icons.Rounded.Refresh, "Обновить") { error = null; webView?.reload() }
            ToolbarButton(Icons.AutoMirrored.Rounded.OpenInNew, "Открыть в браузере", prominent = true) { link?.let { launcher.open(webView?.url ?: it.url) } }
        }
        if (progress in 0.01f..0.99f) {
            LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth().height(2.dp), color = DS.colors.accent, trackColor = DS.colors.bg, drawStopIndicator = {})
        } else Spacer(Modifier.height(2.dp))

        if (project == null || link == null) {
            DSEmptyState(Icons.Rounded.Language, "Ссылка не найдена", "Возможно, её удалили из проекта.", actionLabel = "Назад", onAction = onBack)
            return@Column
        }

        error?.let {
            DSBanner("Страница не загрузилась", Modifier.padding(DSSpace.sm), tone = DSTone.Danger, message = it,
                actionLabel = "Открыть в браузере", onAction = { launcher.open(link.url) })
        }

        Box(Modifier.fillMaxSize().padding(horizontal = DSSpace.sm).padding(bottom = DSSpace.sm).clip(MaterialTheme.shapes.large)
            .border(1.dp, DS.colors.border, MaterialTheme.shapes.large)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) { error = null }
                            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) { canGoBack = view.canGoBack() }
                            override fun onReceivedError(view: WebView, request: WebResourceRequest, err: WebResourceError) {
                                if (request.isForMainFrame) error = err.description?.toString()
                            }
                            // Non-web links inside the page (myapp://, intent://, tel:) go to the system.
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val scheme = request.url.scheme ?: return false
                                if (scheme == "http" || scheme == "https") return false
                                launcher.open(request.url.toString())
                                return true
                            }
                        }
                        webChromeClient = object : android.webkit.WebChromeClient() {
                            override fun onProgressChanged(view: WebView, newProgress: Int) { progress = newProgress / 100f }
                        }
                        loadUrl(link.url)
                        webView = this
                    }
                },
                onRelease = { it.destroy() },
            )
        }
    }
}

@Composable
private fun ToolbarButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, prominent: Boolean = false, onClick: () -> Unit) {
    // Round glass buttons of the navigation layer; prominent = accent-tinted glass ("open in browser").
    if (!prominent) { tj.jasurbek.projectlib.ui.common.GlassIconButton(icon, label, onClick); return }
    Box(
        Modifier.size(48.dp).dsGlass(CircleShape, prominent = true).dsGlassPress()
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = DS.colors.textOnAccent, modifier = Modifier.size(22.dp)) }
}

