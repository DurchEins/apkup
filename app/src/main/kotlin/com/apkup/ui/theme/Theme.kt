package com.apkup.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
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
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.apkup.util.isDark

private val LightColors = lightColorScheme(
	primary = Color(0xFF181A1F),
	onPrimary = Color.White,
	primaryContainer = Color(0xFFE4E7EB),
	onPrimaryContainer = Color(0xFF0F1115),
	secondary = Color(0xFF272A30),
	onSecondary = Color.White,
	secondaryContainer = Color(0xFFEAECEF),
	onSecondaryContainer = Color(0xFF14161A),
	tertiary = Color(0xFF373B44),
	onTertiary = Color.White,
	tertiaryContainer = Color(0xFFEDEFF2),
	onTertiaryContainer = Color(0xFF1F2227),
	error = Color(0xFFBA1A1A),
	errorContainer = Color(0xFFFFDAD6),
	onErrorContainer = Color(0xFF410002),
	background = Color(0xFFF8F9FA),
	surface = Color(0xFFF8F9FA),
	surfaceVariant = Color(0xFFE9ECEF),
	onSurface = Color(0xFF181A1F),
	onSurfaceVariant = Color(0xFF595F69),
	surfaceContainerLowest = Color(0xFFFFFFFF),
	surfaceContainerLow = Color(0xFFFFFFFF),
	surfaceContainer = Color(0xFFF1F3F5),
	surfaceContainerHigh = Color(0xFFE9ECEF),
	surfaceContainerHighest = Color(0xFFDEE2E6),
	outline = Color(0xFF8C939E),
	outlineVariant = Color(0xFFE0E2E6)
)

private val DarkColors = darkColorScheme(
	primary = Color(0xFFF59E0B),
	onPrimary = Color(0xFF451A03),
	primaryContainer = Color(0xFF4D2800),
	onPrimaryContainer = Color(0xFFFDE68A),
	secondary = Color(0xFFD97706),
	onSecondary = Color(0xFF451A03),
	secondaryContainer = Color(0xFF3B2104),
	onSecondaryContainer = Color(0xFFFDE68A),
	tertiary = Color(0xFFFBBF24),
	onTertiary = Color(0xFF451A03),
	tertiaryContainer = Color(0xFF4A3402),
	onTertiaryContainer = Color(0xFFFEF3C7),
	error = Color(0xFFFFB4AB),
	errorContainer = Color(0xFF93000A),
	onErrorContainer = Color(0xFFFFDAD6),
	background = Color(0xFF121316),
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

private val AppShapes = Shapes(
	extraSmall = RoundedCornerShape(8.dp),
	small = RoundedCornerShape(12.dp),
	medium = RoundedCornerShape(18.dp),
	large = RoundedCornerShape(22.dp),
	extraLarge = RoundedCornerShape(28.dp)
)

private val DefaultTypography = Typography()
private val AppTypography = DefaultTypography.copy(
	displaySmall = DefaultTypography.displaySmall.copy(fontWeight = FontWeight.Bold),
	headlineLarge = DefaultTypography.headlineLarge.copy(fontWeight = FontWeight.Bold),
	headlineMedium = DefaultTypography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
	headlineSmall = DefaultTypography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
	titleLarge = DefaultTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
	titleMedium = DefaultTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
	labelLarge = DefaultTypography.labelLarge.copy(fontWeight = FontWeight.Medium),
	labelMedium = DefaultTypography.labelMedium.copy(fontWeight = FontWeight.Medium)
)

@Composable
fun AppTheme(
	darkTheme: Boolean,
	dynamicColor: Boolean = false,
	content: @Composable () -> Unit
) {
	val colorScheme = when {
		dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
			val context = LocalContext.current
			if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
		}
		darkTheme -> DarkColors
		else -> LightColors
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

	MaterialTheme(
		colorScheme = colorScheme,
		shapes = AppShapes,
		typography = AppTypography,
		content = content
	)
}

fun ColorScheme.statusBarColor() = surfaceColorAtElevation(3.dp)

fun isDarkTheme(theme: Int): Boolean {
	if (theme == 1) return true
	if (theme == 2) return false
	return isDark()
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
	is Activity -> this
	is ContextWrapper -> baseContext.findActivity()
	else -> null
}