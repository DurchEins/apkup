package com.apkup.ui.screen

import android.app.Activity.RESULT_CANCELED
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
import com.apkup.ui.component.SelfUpdateDialog
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
	val showChangelogSheet by mainViewModel.showChangelogSheet.collectAsStateWithLifecycle()
	val selfUpdateState by mainViewModel.selfUpdateState.collectAsStateWithLifecycle()

	LaunchedEffect(Unit) {
		mainViewModel.checkChangelogPrompt(context, currentVersionCode)
		mainViewModel.initializeOnStart(appsViewModel, updatesViewModel, isColdStart)
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
			snackbarHost = { SnackbarHost(snackBarHostState) }
		) { padding ->
			Box(
				modifier = Modifier
					.fillMaxSize()
					.padding(top = padding.calculateTopPadding())
			) {
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
					NavHost(navController, PaddingValues(0.dp), mainViewModel, appsViewModel, updatesViewModel, settingsViewModel)
				}

				FloatingCapsuleDock(
					mainViewModel = mainViewModel,
					updatesViewModel = updatesViewModel,
					navController = navController,
					modifier = Modifier
						.align(Alignment.BottomCenter)
						.navigationBarsPadding()
						.padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
				)
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
				mainViewModel.dismissChangelogSheet(currentVersionCode)
			}
		)
	}

	SelfUpdateDialog(
		state = selfUpdateState,
		onDismiss = { mainViewModel.dismissSelfUpdate() },
		onConfirmUpdate = { info -> mainViewModel.startSelfUpdate(info, context) }
	)
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
fun FloatingCapsuleDock(
	mainViewModel: MainViewModel,
	updatesViewModel: UpdatesViewModel,
	navController: NavController,
	modifier: Modifier = Modifier
) {
	val badges = koinInject<Badger>().flow().collectAsStateWithLifecycle().value
	val updatesState = updatesViewModel.state().collectAsStateWithLifecycle().value
	val isChecking = updatesState.isChecking
	val navBackStackEntry by navController.currentBackStackEntryAsState()
	val currentRoute = navBackStackEntry?.destination?.route

	Surface(
		modifier = modifier.heightIn(min = 64.dp),
		shape = CircleShape,
		color = MaterialTheme.colorScheme.surfaceContainerHigh,
		tonalElevation = 6.dp,
		shadowElevation = 8.dp,
		border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
	) {
		Row(
			modifier = Modifier
				.heightIn(min = 64.dp)
				.padding(horizontal = 12.dp, vertical = 6.dp),
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			mainViewModel.screens.forEach { screen ->
				val selected = currentRoute == screen.route
				val isLiveChecking = screen == Screen.Updates && isChecking
				CapsuleDockItem(
					screen = screen,
					selected = selected,
					badge = badges[screen.route].orEmpty(),
					isLiveChecking = isLiveChecking,
					onClick = { mainViewModel.navigateTo(navController, screen.route) }
				)
			}
		}
	}
}

internal fun formatCapsuleBadge(badge: String): String {
	val count = badge.toIntOrNull()
	return if (count != null && count > 99) "99+" else badge
}

@Composable
fun CapsuleDockItem(
	screen: Screen,
	selected: Boolean,
	badge: String,
	isLiveChecking: Boolean = false,
	onClick: () -> Unit
) {
	val containerColor by animateColorAsState(
		targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
		animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
		label = "CapsuleContainerColor"
	)
	val contentColor by animateColorAsState(
		targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
		animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
		label = "CapsuleContentColor"
	)

	Surface(
		shape = CircleShape,
		color = containerColor,
		contentColor = contentColor,
		modifier = Modifier
			.clickable(
				interactionSource = remember { MutableInteractionSource() },
				indication = null,
				onClick = onClick
			)
			.animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
	) {
		if (selected) {
			Row(
				modifier = Modifier
					.padding(horizontal = 16.dp, vertical = 12.dp),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(6.dp)
			) {
				Icon(
					imageVector = screen.iconSelected,
					contentDescription = stringResource(screen.resourceId),
					modifier = Modifier.size(20.dp)
				)
				Text(
					text = stringResource(screen.resourceId),
					style = MaterialTheme.typography.labelMedium,
					fontWeight = FontWeight.Bold,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis
				)
				if (isLiveChecking) {
					Surface(
						shape = CircleShape,
						color = MaterialTheme.colorScheme.primaryContainer,
						contentColor = MaterialTheme.colorScheme.primary
					) {
						Box(
							modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
							contentAlignment = Alignment.Center
						) {
							CircularProgressIndicator(
								modifier = Modifier.size(10.dp),
								strokeWidth = 1.6.dp,
								color = MaterialTheme.colorScheme.primary
							)
						}
					}
				} else if (badge.isNotEmpty()) {
					Surface(
						shape = CircleShape,
						color = MaterialTheme.colorScheme.primary,
						contentColor = MaterialTheme.colorScheme.onPrimary
					) {
						Text(
							text = badge,
							style = MaterialTheme.typography.labelSmall,
							fontWeight = FontWeight.Bold,
							maxLines = 1,
							modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
						)
					}
				}
			}
		} else {
			Column(
				modifier = Modifier
					.padding(horizontal = 16.dp, vertical = 4.dp),
				horizontalAlignment = Alignment.CenterHorizontally,
				verticalArrangement = Arrangement.Center
			) {
				Icon(
					imageVector = screen.icon,
					contentDescription = stringResource(screen.resourceId),
					modifier = Modifier.size(24.dp)
				)
				if (isLiveChecking) {
					Surface(
						shape = CircleShape,
						color = MaterialTheme.colorScheme.primaryContainer,
						contentColor = MaterialTheme.colorScheme.primary,
						modifier = Modifier.offset(y = (-3).dp)
					) {
						Box(
							modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
							contentAlignment = Alignment.Center
						) {
							CircularProgressIndicator(
								modifier = Modifier.size(10.dp),
								strokeWidth = 1.6.dp,
								color = MaterialTheme.colorScheme.primary
							)
						}
					}
				} else if (badge.isNotEmpty()) {
					Surface(
						shape = CircleShape,
						color = MaterialTheme.colorScheme.errorContainer,
						contentColor = MaterialTheme.colorScheme.onErrorContainer,
						modifier = Modifier.offset(y = (-3).dp)
					) {
						Text(
							text = badge,
							style = MaterialTheme.typography.labelSmall,
							fontWeight = FontWeight.Bold,
							maxLines = 1,
							modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
						)
					}
				}
			}
		}
	}
}

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
	composable(Screen.Settings.route) { SettingsScreen(settingsViewModel, appsViewModel, updatesViewModel, mainViewModel) }
}
