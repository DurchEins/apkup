package com.apkup.ui.screen

import android.app.Activity.RESULT_CANCELED
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.util.Consumer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.apkup.data.ui.Screen
import com.apkup.ui.component.BadgeText
import com.apkup.ui.theme.AppTheme
import com.apkup.util.Badger
import com.apkup.util.InstallLog
import com.apkup.util.SnackBar
import com.apkup.util.Themer
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.apkup.BuildConfig
import com.apkup.prefs.Prefs
import com.apkup.ui.component.ChangelogBottomSheet
import com.apkup.ui.component.InstallPermissionDialog
import com.apkup.util.SessionInstaller
import com.apkup.viewmodel.AppsViewModel
import com.apkup.viewmodel.MainViewModel
import com.apkup.viewmodel.SettingsViewModel
import com.apkup.viewmodel.UpdatesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import kotlin.coroutines.CoroutineContext


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
	mainViewModel: MainViewModel = koinViewModel(),
	isColdStart: Boolean = true
) {
	val context = LocalContext.current
	val prefs = koinInject<Prefs>()
	val sessionInstaller = koinInject<SessionInstaller>()

	// ViewModels
	val appsViewModel: AppsViewModel = koinViewModel()
	val updatesViewModel: UpdatesViewModel = koinViewModel()
	val settingsViewModel: SettingsViewModel = koinViewModel()

	val showPermissionDialog by updatesViewModel.showInstallPermissionDialog.collectAsStateWithLifecycle()

	// Navigation
	val navController = rememberNavController()

	// Pull to refresh
	val isRefreshing = mainViewModel.isRefreshing.collectAsStateWithLifecycle()
	val pullToRefreshState = rememberPullToRefreshState()
	val isUpdatesScreen = !shouldShowDefaultPullToRefreshIndicator(
		navController.currentBackStackEntryAsState().value?.destination?.route
	)
	val currentVersionCode = BuildConfig.VERSION_CODE.toLong()
	var showChangelogSheet by remember { mutableStateOf(false) }

	LaunchedEffect(Unit) {
		mainViewModel.initializeOnStart(appsViewModel, updatesViewModel, isColdStart)
		val lastSeen = prefs.lastSeenVersionCode.get()
		if (lastSeen == 0L) {
			prefs.lastSeenVersionCode.put(currentVersionCode)
		} else if (currentVersionCode > lastSeen) {
			showChangelogSheet = true
		}
	}

	LaunchedEffect(isColdStart) {
		if (isColdStart && !prefs.hasSeenInstallPermissionPrompt.get()) {
			if (!sessionInstaller.isShizukuReady() && !sessionInstaller.canRequestPackageInstalls()) {
				updatesViewModel.triggerInstallPermissionPrompt()
				prefs.hasSeenInstallPermissionPrompt.put(true)
			}
		}
	}

	// Used to launch the install intent and get dismissal result
	val installLog = koinInject<InstallLog>()
	val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
		if (it.resultCode == RESULT_CANCELED) {
			installLog.cancelCurrentInstall()
		}
	}

	// Check intent when cold starting from notification
	CheckNotificationIntent(mainViewModel, updatesViewModel, navController, launcher)

	// Check notification intent when hot starting
	IntentListener(mainViewModel, updatesViewModel, navController, launcher)

	// Theme
	val theme = koinInject<Themer>().flow().collectAsStateWithLifecycle().value

	// SnackBar
	val snackBarHostState = handleSnackBar()

	AppTheme(theme) {
		Scaffold(
			containerColor = MaterialTheme.colorScheme.background,
			snackbarHost = { SnackbarHost(snackBarHostState) },
			bottomBar = { BottomBar(mainViewModel, updatesViewModel, navController) }
		) { padding ->
			PullToRefreshBox(
				isRefreshing = isRefreshing.value,
				onRefresh = { mainViewModel.refresh(appsViewModel, updatesViewModel) },
				state = pullToRefreshState,
				indicator = {
					if (!isUpdatesScreen) {
						PullToRefreshDefaults.Indicator(
							modifier = Modifier.align(Alignment.TopCenter),
							isRefreshing = isRefreshing.value,
							state = pullToRefreshState
						)
					}
				}
			) {
				NavHost(navController, padding, mainViewModel, appsViewModel, updatesViewModel, settingsViewModel)
			}
		}
	}

	if (showPermissionDialog) {
		InstallPermissionDialog(
			onDismiss = { updatesViewModel.dismissInstallPermissionDialog() },
			onOpenSettings = {
				updatesViewModel.dismissInstallPermissionDialog()
				runCatching {
					val intent = Intent(
						Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
						Uri.parse("package:${context.packageName}")
					).apply {
						addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
					}
					context.startActivity(intent)
				}
			}
		)
	}

	if (showChangelogSheet) {
		ChangelogBottomSheet(
			onDismiss = {
				showChangelogSheet = false
				prefs.lastSeenVersionCode.put(currentVersionCode)
			}
		)
	}
}

internal fun shouldShowDefaultPullToRefreshIndicator(route: String?): Boolean = route != Screen.Updates.route

@Composable
fun handleSnackBar(): SnackbarHostState {
	val snackBarHostState = remember { SnackbarHostState() }
	koinInject<SnackBar>().flow().CollectAsEffect(Dispatchers.IO) {
		snackBarHostState.showSnackbar(it)
	}
	return snackBarHostState
}

@Composable
fun <T> Flow<T>.CollectAsEffect(
	context: CoroutineContext = Dispatchers.IO,
	block: suspend (T) -> Unit
) = LaunchedEffect(Unit) {
	onEach(block).flowOn(context).launchIn(this)
}

@Composable
fun IntentListener(
	mainViewModel: MainViewModel,
	updatesViewModel: UpdatesViewModel,
	navController: NavController,
	launcher: ManagedActivityResultLauncher<Intent, ActivityResult>
) {
	val activity = LocalActivity.current as? ComponentActivity ?: return
	DisposableEffect(Unit) {
		val listener = Consumer<Intent> {
			mainViewModel.processIntent(it, launcher, updatesViewModel, navController)
		}
		activity.addOnNewIntentListener(listener)
		onDispose { activity.removeOnNewIntentListener(listener) }
	}
}

@Composable
fun CheckNotificationIntent(
	mainViewModel: MainViewModel,
	updatesViewModel: UpdatesViewModel,
	navController: NavController,
	launcher: ManagedActivityResultLauncher<Intent, ActivityResult>
) {
	val activity = LocalActivity.current as? ComponentActivity ?: return
	LaunchedEffect(Unit) {
		mainViewModel.processIntent(activity.intent, launcher, updatesViewModel, navController)
	}
}

@Composable
fun BottomBar(mainViewModel: MainViewModel, updatesViewModel: UpdatesViewModel, navController: NavController) = NavigationBar(
	containerColor = MaterialTheme.colorScheme.surfaceContainer,
	tonalElevation = 0.dp
) {
	val badges = koinInject<Badger>().flow().collectAsStateWithLifecycle().value
	val updatesState = updatesViewModel.state().collectAsStateWithLifecycle().value
	val isChecking = updatesState.isChecking
	mainViewModel.screens.forEach { screen ->
		val state = navController.currentBackStackEntryAsState().value
		val selected = state?.destination?.route == screen.route
		val isLiveChecking = screen == Screen.Updates && isChecking
		BottomBarItem(mainViewModel, navController, screen, selected, badges[screen.route].orEmpty(), isLiveChecking)
	}
}

@Composable
fun RowScope.BottomBarItem(
	mainViewModel: MainViewModel,
    navController: NavController,
    screen: Screen,
    selected: Boolean,
    badge: String,
    isLiveChecking: Boolean = false
) = NavigationBarItem(
	icon = {
		BadgedBox(
			badge = {
				if (isLiveChecking) {
					Badge(
						containerColor = MaterialTheme.colorScheme.primaryContainer,
						contentColor = MaterialTheme.colorScheme.primary
					) {
						CircularProgressIndicator(
							modifier = Modifier
								.size(10.dp)
								.padding(1.dp),
							strokeWidth = 1.6.dp,
							color = MaterialTheme.colorScheme.primary
						)
					}
				} else {
					BadgeText(badge)
				}
			}
		) {
			Icon(if (selected) screen.iconSelected else screen.icon, contentDescription = null)
		}
   	},
	label = {
		Text(
			stringResource(screen.resourceId),
			maxLines = 1,
			overflow = TextOverflow.Ellipsis
		)
	},
	selected = selected,
	colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
		selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
		selectedTextColor = MaterialTheme.colorScheme.onSurface,
		indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
		unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
		unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
	),
	onClick = { mainViewModel.navigateTo(navController, screen.route) }
)

@Composable
fun NavHost(
	navController: NavHostController,
	padding: PaddingValues,
	mainViewModel: MainViewModel,
	appsViewModel: AppsViewModel,
	updatesViewModel: UpdatesViewModel,
	settingsViewModel: SettingsViewModel
) = NavHost(
	navController = navController,
	startDestination = mainViewModel.getLastRoute(),
	modifier = Modifier.padding(padding)
) {
	composable(Screen.Apps.route) { AppsScreen(appsViewModel) }
	composable(Screen.Updates.route) { UpdatesScreen(updatesViewModel) }
	composable(Screen.Settings.route) { SettingsScreen(settingsViewModel, appsViewModel, updatesViewModel) }
}
