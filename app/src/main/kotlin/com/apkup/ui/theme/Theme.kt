package com.apkup.ui.theme

import android.app.Activity
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
	primary = Color(0xFF3446A8),
	onPrimary = Color.White,
	primaryContainer = Color(0xFFDFE2FF),
	onPrimaryContainer = Color(0xFF151C5A),
	secondary = Color(0xFF006B5A),
	onSecondary = Color.White,
	secondaryContainer = Color(0xFFA6F2D8),
	onSecondaryContainer = Color(0xFF002117),
	tertiary = Color(0xFFA83A28),
	onTertiary = Color.White,
	tertiaryContainer = Color(0xFFFFD9D0),
	onTertiaryContainer = Color(0xFF410E06),
	error = Color(0xFFBA1A1A),
	errorContainer = Color(0xFFFFDAD6),
	onErrorContainer = Color(0xFF410002),
	background = Color(0xFFF7F7FC),
	surface = Color(0xFFF7F7FC),
	surfaceVariant = Color(0xFFE4E5F0),
	onSurface = Color(0xFF191A24),
	onSurfaceVariant = Color(0xFF454652),
	surfaceContainerLowest = Color(0xFFFFFFFF),
	surfaceContainerLow = Color(0xFFF1F0F8),
	surfaceContainer = Color(0xFFEBEAF3),
	surfaceContainerHigh = Color(0xFFE5E4EE),
	surfaceContainerHighest = Color(0xFFDFDEE8),
	outline = Color(0xFF757682),
	outlineVariant = Color(0xFFC5C6D2)
)

private val DarkColors = darkColorScheme(
	primary = Color(0xFFBBC3FF),
	onPrimary = Color(0xFF1B286E),
	primaryContainer = Color(0xFF34479C),
	onPrimaryContainer = Color(0xFFE0E2FF),
	secondary = Color(0xFF6CD9BD),
	onSecondary = Color(0xFF00382D),
	secondaryContainer = Color(0xFF005143),
	onSecondaryContainer = Color(0xFF8BF1D4),
	tertiary = Color(0xFFFFB4A1),
	onTertiary = Color(0xFF611800),
	tertiaryContainer = Color(0xFF842B13),
	onTertiaryContainer = Color(0xFFFFDAD1),
	error = Color(0xFFFFB4AB),
	errorContainer = Color(0xFF93000A),
	onErrorContainer = Color(0xFFFFDAD6),
	background = Color(0xFF11121B),
	surface = Color(0xFF11121B),
	surfaceVariant = Color(0xFF454652),
	onSurface = Color(0xFFE3E2EC),
	onSurfaceVariant = Color(0xFFC5C6D2),
	surfaceContainerLowest = Color(0xFF0B0C14),
	surfaceContainerLow = Color(0xFF191A24),
	surfaceContainer = Color(0xFF1D1E28),
	surfaceContainerHigh = Color(0xFF272833),
	surfaceContainerHighest = Color(0xFF32333E),
	outline = Color(0xFF8F909C),
	outlineVariant = Color(0xFF454652)
)

private val AppShapes = Shapes(
	extraSmall = RoundedCornerShape(10.dp),
	small = RoundedCornerShape(16.dp),
	medium = RoundedCornerShape(20.dp),
	large = RoundedCornerShape(24.dp),
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
	dynamicColor: Boolean = true,
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
			val activity = view.context as Activity
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
