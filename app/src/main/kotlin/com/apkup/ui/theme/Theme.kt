package com.apkup.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
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
import com.apkup.util.SolarScheduleCalculator
import com.apkup.util.isDark

const val THEME_SYSTEM = 0
const val THEME_DARK = 1
const val THEME_LIGHT = 2
const val THEME_ACCESSIBILITY = 3

internal val LightColors = lightColorScheme(
    primary = Color(0xFF92400E),
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

internal val DarkColors = darkColorScheme(
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

internal val LightExtendedColors = ApkUpExtendedColors(
    success = Color(0xFF1E7E45),
    onSuccess = Color.White,
    successContainer = Color(0xFFD7F5E3),
    onSuccessContainer = Color(0xFF0A361C),
    warning = Color(0xFF92400E),
    onWarning = Color.White,
    warningContainer = Color(0xFFFFE4CC),
    onWarningContainer = Color(0xFF451D00),
    info = Color(0xFF1D6296),
    onInfo = Color.White,
    infoContainer = Color(0xFFD6ECFF),
    onInfoContainer = Color(0xFF092A45)
)

internal val DarkExtendedColors = ApkUpExtendedColors(
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
 * WCAG 2.2 AAA (>= 7.0:1) high-contrast light palette.
 */
internal val A11yLightColors = lightColorScheme(
    primary = Color(0xFF6B2F00),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFDE68A),
    onPrimaryContainer = Color(0xFF291000),
    secondary = Color(0xFF3F3426),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE4D3),
    onSecondaryContainer = Color(0xFF1C140A),
    tertiary = Color(0xFF0F4C3A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD1FAE5),
    onTertiaryContainer = Color(0xFF04261C),
    error = Color(0xFF8C0009),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF2D0001),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF0C0A09),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEBE6DD),
    onSurface = Color(0xFF0C0A09),
    onSurfaceVariant = Color(0xFF2E2A27),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F5F0),
    surfaceContainer = Color(0xFFF2EFE9),
    surfaceContainerHigh = Color(0xFFEBE6DD),
    surfaceContainerHighest = Color(0xFFE3DDD2),
    outline = Color(0xFF3F3A34),
    outlineVariant = Color(0xFF57524B)
)

/**
 * WCAG 2.2 AAA (>= 7.0:1) high-contrast dark palette.
 */
internal val A11yDarkColors = darkColorScheme(
    primary = Color(0xFFFCD34D),
    onPrimary = Color(0xFF1F0E00),
    primaryContainer = Color(0xFF3D2000),
    onPrimaryContainer = Color(0xFFFEF08A),
    secondary = Color(0xFFE7D3B5),
    onSecondary = Color(0xFF1E1407),
    secondaryContainer = Color(0xFF2E2110),
    onSecondaryContainer = Color(0xFFFAF0E1),
    tertiary = Color(0xFF86EFAC),
    onTertiary = Color(0xFF022419),
    tertiaryContainer = Color(0xFF063E2E),
    onTertiaryContainer = Color(0xFFECFDF5),
    error = Color(0xFFFFC2BC),
    onError = Color(0xFF3B0002),
    errorContainer = Color(0xFF5C0006),
    onErrorContainer = Color(0xFFFFEDEA),
    background = Color(0xFF090A0D),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF090A0D),
    surfaceVariant = Color(0xFF1E222B),
    onSurface = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFFD8DCE3),
    surfaceContainerLowest = Color(0xFF050608),
    surfaceContainerLow = Color(0xFF101217),
    surfaceContainer = Color(0xFF161920),
    surfaceContainerHigh = Color(0xFF1E222B),
    surfaceContainerHighest = Color(0xFF262B36),
    outline = Color(0xFFB8C0CC),
    outlineVariant = Color(0xFF8B95A5)
)

internal val A11yLightExtendedColors = ApkUpExtendedColors(
    success = Color(0xFF0D542B),
    onSuccess = Color.White,
    successContainer = Color(0xFFD1FAE5),
    onSuccessContainer = Color(0xFF032110),
    warning = Color(0xFF6B2F00),
    onWarning = Color.White,
    warningContainer = Color(0xFFFDE68A),
    onWarningContainer = Color(0xFF291000),
    info = Color(0xFF0C436B),
    onInfo = Color.White,
    infoContainer = Color(0xFFD6ECFF),
    onInfoContainer = Color(0xFF041D30)
)

internal val A11yDarkExtendedColors = ApkUpExtendedColors(
    success = Color(0xFF86EFAC),
    onSuccess = Color(0xFF022419),
    successContainer = Color(0xFF063E2E),
    onSuccessContainer = Color(0xFFECFDF5),
    warning = Color(0xFFFCD34D),
    onWarning = Color(0xFF1F0E00),
    warningContainer = Color(0xFF3D2000),
    onWarningContainer = Color(0xFFFEF08A),
    info = Color(0xFF93C5FD),
    onInfo = Color(0xFF041E36),
    infoContainer = Color(0xFF0A3256),
    onInfoContainer = Color(0xFFEFF6FF)
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
private val LocalApkUpHighContrast = staticCompositionLocalOf { false }
private val LocalApkUpAccessibleUi = staticCompositionLocalOf { false }

object ApkUpTheme {
    val colors: ApkUpExtendedColors
        @Composable @ReadOnlyComposable get() = LocalApkUpExtendedColors.current

    val motion: ApkUpMotionScheme
        @Composable @ReadOnlyComposable get() = LocalApkUpMotionScheme.current

    val isReducedMotion: Boolean
        @Composable @ReadOnlyComposable get() = LocalApkUpReducedMotion.current

    val isHighContrast: Boolean
        @Composable @ReadOnlyComposable get() = LocalApkUpHighContrast.current

    val isAccessibleUi: Boolean
        @Composable @ReadOnlyComposable get() = LocalApkUpAccessibleUi.current
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

internal val AccessibleTypography = AppTypography.copy(
    titleMedium = AppTypography.titleMedium.copy(fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = AppTypography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = AppTypography.bodySmall.copy(fontSize = 13.5.sp, lineHeight = 19.sp),
    labelLarge = AppTypography.labelLarge.copy(fontSize = 15.sp, lineHeight = 21.sp),
    labelMedium = AppTypography.labelMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = AppTypography.labelSmall.copy(fontSize = 13.sp, lineHeight = 17.sp)
)

@Composable
fun AppTheme(
    darkTheme: Boolean,
    dynamicColor: Boolean = false,
    highContrast: Boolean = false,
    accessibleUi: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val effectiveDynamicColor = dynamicColor && !highContrast
    val colorScheme = when {
        highContrast -> if (darkTheme) A11yDarkColors else A11yLightColors
        effectiveDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    val extendedColors = when {
        highContrast && darkTheme -> A11yDarkExtendedColors
        highContrast -> A11yLightExtendedColors
        darkTheme -> DarkExtendedColors
        else -> LightExtendedColors
    }
    val isReducedMotion = accessibleUi || remember(context) { isSystemReducedMotion(context) }
    val motionScheme = if (isReducedMotion) ReducedApkUpMotionScheme else ExpressiveApkUpMotionScheme

    val animatedColorScheme = if (isReducedMotion) {
        colorScheme
    } else {
        val spec = tween<Color>(durationMillis = 260)
        colorScheme.copy(
            primary = animateColorAsState(colorScheme.primary, spec, label = "ThemePrimary").value,
            onPrimary = animateColorAsState(colorScheme.onPrimary, spec, label = "ThemeOnPrimary").value,
            primaryContainer = animateColorAsState(colorScheme.primaryContainer, spec, label = "ThemePrimaryContainer").value,
            onPrimaryContainer = animateColorAsState(colorScheme.onPrimaryContainer, spec, label = "ThemeOnPrimaryContainer").value,
            secondaryContainer = animateColorAsState(colorScheme.secondaryContainer, spec, label = "ThemeSecondaryContainer").value,
            onSecondaryContainer = animateColorAsState(colorScheme.onSecondaryContainer, spec, label = "ThemeOnSecondaryContainer").value,
            background = animateColorAsState(colorScheme.background, spec, label = "ThemeBackground").value,
            onBackground = animateColorAsState(colorScheme.onBackground, spec, label = "ThemeOnBackground").value,
            surface = animateColorAsState(colorScheme.surface, spec, label = "ThemeSurface").value,
            onSurface = animateColorAsState(colorScheme.onSurface, spec, label = "ThemeOnSurface").value,
            surfaceVariant = animateColorAsState(colorScheme.surfaceVariant, spec, label = "ThemeSurfaceVariant").value,
            onSurfaceVariant = animateColorAsState(colorScheme.onSurfaceVariant, spec, label = "ThemeOnSurfaceVariant").value,
            surfaceContainerLowest = animateColorAsState(colorScheme.surfaceContainerLowest, spec, label = "ThemeSCLL").value,
            surfaceContainerLow = animateColorAsState(colorScheme.surfaceContainerLow, spec, label = "ThemeSCL").value,
            surfaceContainer = animateColorAsState(colorScheme.surfaceContainer, spec, label = "ThemeSC").value,
            surfaceContainerHigh = animateColorAsState(colorScheme.surfaceContainerHigh, spec, label = "ThemeSCH").value,
            surfaceContainerHighest = animateColorAsState(colorScheme.surfaceContainerHighest, spec, label = "ThemeSCHH").value,
            outline = animateColorAsState(colorScheme.outline, spec, label = "ThemeOutline").value,
            outlineVariant = animateColorAsState(colorScheme.outlineVariant, spec, label = "ThemeOutlineVariant").value
        )
    }

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
        LocalApkUpReducedMotion provides isReducedMotion,
        LocalApkUpHighContrast provides highContrast,
        LocalApkUpAccessibleUi provides accessibleUi
    ) {
        MaterialTheme(
            colorScheme = animatedColorScheme,
            shapes = AppShapes,
            typography = if (accessibleUi) AccessibleTypography else AppTypography,
            content = content
        )
    }
}

fun ColorScheme.statusBarColor() = surfaceColorAtElevation(3.dp)

fun isDarkTheme(
    theme: Int,
    a11ySubMode: Int = SolarScheduleCalculator.SUBMODE_AUTO_SUN,
    customSunriseMinutes: Int = SolarScheduleCalculator.UNSET_MINUTES,
    customSunsetMinutes: Int = SolarScheduleCalculator.UNSET_MINUTES,
    epochMillis: Long = System.currentTimeMillis()
): Boolean = when (theme) {
    THEME_DARK -> true
    THEME_LIGHT -> false
    THEME_ACCESSIBILITY -> SolarScheduleCalculator.isDarkAt(
        subMode = a11ySubMode,
        customSunriseMinutes = customSunriseMinutes,
        customSunsetMinutes = customSunsetMinutes,
        epochMillis = epochMillis
    )
    else -> isDark()
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
