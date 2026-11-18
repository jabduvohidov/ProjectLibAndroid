// Generated from tokens.json — do not edit by hand.
package tj.jasurbek.projectlib.ui.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF0E8577),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE3F7F3),
    onPrimaryContainer = Color(0xFF0B6E64),
    secondary = Color(0xFF4A5754),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8EEEC),
    onSecondaryContainer = Color(0xFF0B1513),
    background = Color(0xFFF2F5F4),
    onBackground = Color(0xFF0B1513),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0B1513),
    surfaceVariant = Color(0xFFE8EEEC),
    onSurfaceVariant = Color(0xFF4A5754),
    surfaceContainerLowest = Color(0xFFF2F5F4),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFE8EEEC),
    outline = Color(0xFFC4CFCC),
    outlineVariant = Color(0xFFDDE4E2),
    error = Color(0xFFB42318),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEF3F2),
    onErrorContainer = Color(0xFFB42318),
    scrim = Color(0x660B1513),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF2DD4BF),
    onPrimary = Color(0xFF03241F),
    primaryContainer = Color(0xFF0B2A26),
    onPrimaryContainer = Color(0xFF5EEAD4),
    secondary = Color(0xFFA5B1AE),
    onSecondary = Color(0xFF121816),
    secondaryContainer = Color(0xFF1F2826),
    onSecondaryContainer = Color(0xFFF2F7F5),
    background = Color(0xFF050807),
    onBackground = Color(0xFFF2F7F5),
    surface = Color(0xFF121816),
    onSurface = Color(0xFFF2F7F5),
    surfaceVariant = Color(0xFF1F2826),
    onSurfaceVariant = Color(0xFFA5B1AE),
    surfaceContainerLowest = Color(0xFF050807),
    surfaceContainerLow = Color(0xFF121816),
    surfaceContainer = Color(0xFF121816),
    surfaceContainerHigh = Color(0xFF1A2220),
    surfaceContainerHighest = Color(0xFF1F2826),
    outline = Color(0xFF36423F),
    outlineVariant = Color(0xFF26302E),
    error = Color(0xFFF97066),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF3D0F0B),
    onErrorContainer = Color(0xFFF97066),
    scrim = Color(0x99000000),
)

/** Full token set (Material's ColorScheme lacks success/warning/etc). Access via DS.colors. */
@Immutable
data class DSColors(
    val bg: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val fill: Color,
    val border: Color,
    val borderStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textOnAccent: Color,
    val textOnDanger: Color,
    val accent: Color,
    val accentHover: Color,
    val accentSubtle: Color,
    val accentText: Color,
    val focusRing: Color,
    val success: Color,
    val successSubtle: Color,
    val warning: Color,
    val warningSubtle: Color,
    val danger: Color,
    val dangerFill: Color,
    val dangerSubtle: Color,
    val overlay: Color,
    val glass: Color,
    val glassStrong: Color,
    val glassHighlight: Color,
    val glassEdge: Color,
)

val LightDSColors = DSColors(
    bg = Color(0xFFF2F5F4),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFFFFFFF),
    fill = Color(0xFFE8EEEC),
    border = Color(0xFFDDE4E2),
    borderStrong = Color(0xFFC4CFCC),
    textPrimary = Color(0xFF0B1513),
    textSecondary = Color(0xFF4A5754),
    textTertiary = Color(0xFF5F6D6A),
    textOnAccent = Color(0xFFFFFFFF),
    textOnDanger = Color(0xFFFFFFFF),
    accent = Color(0xFF0E8577),
    accentHover = Color(0xFF0F766E),
    accentSubtle = Color(0xFFE3F7F3),
    accentText = Color(0xFF0B6E64),
    focusRing = Color(0xFF14B8A6),
    success = Color(0xFF15803D),
    successSubtle = Color(0xFFECFDF3),
    warning = Color(0xFFB54708),
    warningSubtle = Color(0xFFFFFAEB),
    danger = Color(0xFFB42318),
    dangerFill = Color(0xFFD92D20),
    dangerSubtle = Color(0xFFFEF3F2),
    overlay = Color(0x660B1513),
    glass = Color(0x73FFFFFF),
    glassStrong = Color(0xC7FFFFFF),
    glassHighlight = Color(0xE6FFFFFF),
    glassEdge = Color(0x1F0B1513),
)

val DarkDSColors = DSColors(
    bg = Color(0xFF050807),
    surface = Color(0xFF121816),
    surfaceRaised = Color(0xFF1A2220),
    fill = Color(0xFF1F2826),
    border = Color(0xFF26302E),
    borderStrong = Color(0xFF36423F),
    textPrimary = Color(0xFFF2F7F5),
    textSecondary = Color(0xFFA5B1AE),
    textTertiary = Color(0xFF808C89),
    textOnAccent = Color(0xFF03241F),
    textOnDanger = Color(0xFFFFFFFF),
    accent = Color(0xFF2DD4BF),
    accentHover = Color(0xFF5EEAD4),
    accentSubtle = Color(0xFF0B2A26),
    accentText = Color(0xFF5EEAD4),
    focusRing = Color(0xFF2DD4BF),
    success = Color(0xFF4ADE80),
    successSubtle = Color(0xFF06281A),
    warning = Color(0xFFFDB022),
    warningSubtle = Color(0xFF3B1D04),
    danger = Color(0xFFF97066),
    dangerFill = Color(0xFFD92D20),
    dangerSubtle = Color(0xFF3D0F0B),
    overlay = Color(0x99000000),
    glass = Color(0x73283230),
    glassStrong = Color(0xC71A2220),
    glassHighlight = Color(0x40FFFFFF),
    glassEdge = Color(0x80000000),
)

val LocalDSColors = staticCompositionLocalOf { LightDSColors }

val DSTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (0).sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal, letterSpacing = (0).sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal, letterSpacing = (0).sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = (0).sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal, letterSpacing = (0).sp),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = (0.2).sp)
)

val DSShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

object DSSpace {
    val s0 = 0.dp
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val s2xl = 32.dp
    val s3xl = 48.dp
    val s4xl = 64.dp
}

object DSSize {
    val controlSm = 32.dp
    val controlMd = 44.dp
    val controlLg = 52.dp
    val controlCompact = 28.dp
    val minTouchApple = 44.dp
    val minTouchAndroid = 48.dp
    val iconSm = 16.dp
    val iconMd = 20.dp
    val iconLg = 24.dp
    val avatarSm = 32.dp
    val avatarMd = 40.dp
    val avatarLg = 56.dp
    val listRow = 56.dp
    val listRowCompact = 36.dp
    val navBarWeb = 64.dp
    val sidebarWeb = 248.dp
    val sidebarMac = 220.dp
    val containerMax = 1200.dp
    val readingMax = 680.dp
}

object DSMotion {
    const val fastMs = 120
    const val baseMs = 200
    const val slowMs = 320
    /** cubic-bezier(0.2, 0, 0, 1) */
    val standardEasing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f)
    // Springs converted from Apple's (response, dampingFraction): stiffness = (2π / response)², dampingRatio = dampingFraction.
    const val springStiffness = 322.3f
    const val springDamping = 0.85f
    const val bouncyStiffness = 246.7f
    const val bouncyDamping = 0.68f
    const val pressGlassScale = 1.06f
    const val pressButtonScale = 0.97f
    /** Same spring as iOS DS.Motion.spring and web --easing-spring-standard. */
    fun <T> spring(visibilityThreshold: T? = null) =
        androidx.compose.animation.core.spring(dampingRatio = springDamping, stiffness = springStiffness, visibilityThreshold = visibilityThreshold)
    /** Livelier spring for glass touch response and materializing. */
    fun <T> bouncy(visibilityThreshold: T? = null) =
        androidx.compose.animation.core.spring(dampingRatio = bouncyDamping, stiffness = bouncyStiffness, visibilityThreshold = visibilityThreshold)
}

object DS {
    val colors: DSColors
        @Composable @ReadOnlyComposable get() = LocalDSColors.current
}

/** Do NOT enable dynamic color — the brand accent must stay teal. */
@Composable
fun AppTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDSColors provides if (darkTheme) DarkDSColors else LightDSColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = DSTypography,
            shapes = DSShapes,
            content = content,
        )
    }
}
