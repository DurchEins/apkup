package com.apkup.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.apkup.util.isDark

private val LightColors = lightColorScheme(
    primary = Color(0xFFB45309),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFEF3C7),
    onPrimaryContainer = Color(0xFF451A03),
    secondary = Color(0xFF6E5D46),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3EAD8),
    onSecondaryContainer = Color(0xFF261A0A),
    tertiary = Color(0xFF2D6A5B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC8EAE0),
    onTertiaryContainer = Color(0xFF0A2E25),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF4F1EA),
    onBackground = Color(0xFF1C1917),
    surface = Color(0xFFF4F1EA),
    surfaceVariant = Color(0xFFE7E2D8),
    onSurface = Color(0xFF1C1917),
    onSurfaceVariant = Color(0xFF57534E),
    surfaceContainerLowest = Color(0xFFFAF8F4),
    surfaceContainerLow = Color(0xFFF8F5EE),
    surfaceContainer = Color(0xFFEEEAE1),
    surfaceContainerHigh = Color(0xFFE7E2D8),
    surfaceContainerHighest = Color(0xFFDFD9CD),
    outline = Color(0xFF7D766D),
    outlineVariant = Color(0xFFD6D0C4)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF59E0B),
    onPrimary = Color(0xFF451A03),
    primaryContainer = Color(0xFF4D2800),
    onPrimaryContainer = Color(0xFFFDE68A),
    secondary = Color(0xFFD9B98B),
    onSecondary = Color(0xFF3A2810),
    secondaryContainer = Color(0xFF3B2A14),
    onSecondaryContainer = Color(0xFFF5E6D0),
    tertiary = Color(0xFF6EE7B7),
    onTertiary = Color(0xFF022C22),
    tertiaryContainer = Color(0xFF064E3B),
    onTertiaryContainer = Color(0xFFD1FAE5),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121316),
    onBackground = Color(0xFFF3F4F6),
    surface = Color(0xFF121316),
    surfaceVariant = Color(0xFF282B33),
    onSurface = Color(0xFFF3F4F6),
    onSurfaceVariant = Color(0xFF9CA3AF),
    surfaceContainerLowest = Color(0xFF0C0D0F),
    surfaceContainerLow = Color(0xFF181A1F),
    surfaceContainer = Color(0xFF20232A),
    surfaceContainerHigh = Color(0xFF2A2E37),
    surfaceContainerHighest = Color(0xFF353A45),
    outline = Color(0xFF6B7280),
    outlineVariant = Color(0xFF2A2E37)
)

@Immutable
data class ApkUpExtendedColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val info: Color,
    val onInfo: Color,
    val infoContainer: Color,
    val onInfoContainer: Color
)

private val LightExtendedColors = ApkUpExtendedColors(
    success = Color(0xFF1E7E45),
    onSuccess = Color.White,
    successContainer = Color(0xFFD7F5E3),
    onSuccessContainer = Color(0xFF0A361C),
    warning = Color(0xFFC25900),
    onWarning = Color.White,
    warningContainer = Color(0xFFFFE4CC),
    onWarningContainer = Color(0xFF451D00),
    info = Color(0xFF1D6296),
    onInfo = Color.White,
    infoContainer = Color(0xFFD6ECFF),
    onInfoContainer = Color(0xFF092A45)
)

private val DarkExtendedColors = ApkUpExtendedColors(
    success = Color(0xFF6EE7A0),
    onSuccess = Color(0xFF052E16),
    successContainer = Color(0xFF134E2A),
    onSuccessContainer = Color(0xFFD7F5E3),
    warning = Color(0xFFFBBF24),
    onWarning = Color(0xFF422006),
    warningContainer = Color(0xFF543308),
    onWarningContainer = Color(0xFFFEF3C7),
    info = Color(0xFF7DD3FC),
    onInfo = Color(0xFF082F49),
    infoContainer = Color(0xFF0C4A6E),
    onInfoContainer = Color(0xFFE0F2FE)
)

/**
 * Motion scheme aligned with Material 3 Expressive spring tokens,
 * providing an automatic reduced-motion fallback when system animations are disabled.
 */
@Immutable
interface ApkUpMotionScheme {
    fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T>
    fun <T> fastSpatialSpec(): FiniteAnimationSpec<T>
    fun <T> slowSpatialSpec(): FiniteAnimationSpec<T>
    fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T>
    fun <T> fastEffectsSpec(): FiniteAnimationSpec<T>
    fun <T> slowEffectsSpec(): FiniteAnimationSpec<T>
}

private object ExpressiveApkUpMotionScheme : ApkUpMotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.8f, stiffness = 380f)

    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.6f, stiffness = 800f)

    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.8f, stiffness = 200f)

    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 1.0f, stiffness = 1600f)

    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 1.0f, stiffness = 3800f)

    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 1.0f, stiffness = 800f)
}

private object ReducedApkUpMotionScheme : ApkUpMotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = tween(durationMillis = 80)
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = tween(durationMillis = 120)
}

private val LocalApkUpExtendedColors = staticCompositionLocalOf { LightExtendedColors }
private val LocalApkUpMotionScheme = staticCompositionLocalOf<ApkUpMotionScheme> { ExpressiveApkUpMotionScheme }
private val LocalApkUpReducedMotion = staticCompositionLocalOf { false }

object ApkUpTheme {
    val colors: ApkUpExtendedColors
        @Composable @ReadOnlyComposable get() = LocalApkUpExtendedColors.current

    val motion: ApkUpMotionScheme
        @Composable @ReadOnlyComposable get() = LocalApkUpMotionScheme.current

    val isReducedMotion: Boolean
        @Composable @ReadOnlyComposable get() = LocalApkUpReducedMotion.current
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private val DefaultTypography = Typography()
private val AppTypography = DefaultTypography.copy(
    displaySmall = DefaultTypography.displaySmall.copy(
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp
    ),
    headlineLarge = DefaultTypography.headlineLarge.copy(
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.25).sp
    ),
    headlineMedium = DefaultTypography.headlineMedium.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.15).sp
    ),
    headlineSmall = DefaultTypography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = DefaultTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = DefaultTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = DefaultTypography.titleSmall.copy(fontWeight = FontWeight.Medium),
    labelLarge = DefaultTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = DefaultTypography.labelMedium.copy(fontWeight = FontWeight.Medium),
    labelSmall = DefaultTypography.labelSmall.copy(fontWeight = FontWeight.Medium)
)

@Composable
fun AppTheme(
    darkTheme: Boolean,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors
    val isReducedMotion = remember(context) { isSystemReducedMotion(context) }
    val motionScheme = if (isReducedMotion) ReducedApkUpMotionScheme else ExpressiveApkUpMotionScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context.findActivity() ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(activity.window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalApkUpExtendedColors provides extendedColors,
        LocalApkUpMotionScheme provides motionScheme,
        LocalApkUpReducedMotion provides isReducedMotion
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = AppShapes,
            typography = AppTypography,
            content = content
        )
    }
}

fun ColorScheme.statusBarColor() = surfaceColorAtElevation(3.dp)

fun isDarkTheme(theme: Int): Boolean {
    if (theme == 1) return true
    if (theme == 2) return false
    return isDark()
}

private fun isSystemReducedMotion(context: Context): Boolean = runCatching {
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    ) == 0f
}.getOrDefault(false)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
