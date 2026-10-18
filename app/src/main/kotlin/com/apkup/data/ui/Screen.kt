package com.apkup.data.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Update
import androidx.compose.ui.graphics.vector.ImageVector
import com.apkup.R

sealed class Screen(
	val route: String,
	@param:StringRes val resourceId: Int,
	val icon: ImageVector,
	val iconSelected: ImageVector
) {
	data object Apps : Screen("apps", R.string.tab_apps, Icons.Outlined.Home, Icons.Filled.Home)
	data object Updates : Screen("updates", R.string.tab_updates, Icons.Rounded.Update, Icons.Rounded.Update)
	data object Settings : Screen("settings", R.string.tab_settings, Icons.Outlined.Settings, Icons.Filled.Settings)
}

internal fun resolveStartRoute(savedRoute: String, availableRoutes: Set<String>): String =
	savedRoute.takeIf(availableRoutes::contains) ?: Screen.Updates.route
