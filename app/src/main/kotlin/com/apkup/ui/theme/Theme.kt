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
	primary = Color(0xFF00639B),
	onPrimary = Color.White,
	primaryContainer = Color(0xFFCCE5FF),
	onPrimaryContainer = Color(0xFF001D32),
	secondary = Color(0xFF006877),
	onSecondary = Color.White,
	secondaryContainer = Color(0xFFA5EEFF),
	onSecondaryContainer = Color(0xFF001F25),
	tertiary = Color(0xFF6750A4),
	onTertiary = Color.White,
	tertiaryContainer = Color(0xFFE7DEFF),
	onTertiaryContainer = Color(0xFF210F47),
	error = Color(0xFFBA1A1A),
	errorContainer = Color(0xFFFFDAD6),
	onErrorContainer = Color(0xFF410002),
	background = Color(0xFFF8F9FE),
	surface = Color(0xFFF8F9FE),
	surfaceVariant = Color(0xFFDEE3EB),
	onSurface = Color(0xFF191C1E),
	onSurfaceVariant = Color(0xFF41474D),
	surfaceContainerLowest = Color(0xFFFFFFFF),
	surfaceContainerLow = Color(0xFFF2F4F9),
	surfaceContainer = Color(0xFFECF0F6),
	surfaceContainerHigh = Color(0xFFE6EAEE),
	surfaceContainerHighest = Color(0xFFDFE4E8),
	outline = Color(0xFF72787E),
	outlineVariant = Color(0xFFC1C7CE)
)

private val DarkColors = darkColorScheme(
	primary = Color(0xFF96CCFF),
	onPrimary = Color(0xFF003354),
	primaryContainer = Color(0xFF004B76),
	onPrimaryContainer = Color(0xFFCCE5FF),
	secondary = Color(0xFF84D2E6),
	onSecondary = Color(0xFF00363F),
	secondaryContainer = Color(0xFF004E5B),
	onSecondaryContainer = Color(0xFFA5EEFF),
	tertiary = Color(0xFFCFBCFF),
	onTertiary = Color(0xFF371E73),
	tertiaryContainer = Color(0xFF4F378B),
	onTertiaryContainer = Color(0xFFE7DEFF),
	error = Color(0xFFFFB4AB),
	errorContainer = Color(0xFF93000A),
	onErrorContainer = Color(0xFFFFDAD6),
	background = Color(0xFF0E1116),
	surface = Color(0xFF0E1116),
	surfaceVariant = Color(0xFF41474D),
	onSurface = Color(0xFFE1E2E8),
	onSurfaceVariant = Color(0xFFC1C7CE),
	surfaceContainerLowest = Color(0xFF080B0F),
	surfaceContainerLow = Color(0xFF14171E),
	surfaceContainer = Color(0xFF181C24),
	surfaceContainerHigh = Color(0xFF222731),
	surfaceContainerHighest = Color(0xFF2D323C),
	outline = Color(0xFF8B9198),
	outlineVariant = Color(0xFF41474D)
)

private val AppShapes = Shapes(
	extraSmall = RoundedCornerShape(8.dp),
	small = RoundedCornerShape(12.dp),
	medium = RoundedCornerShape(18.dp),
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
