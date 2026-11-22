package com.apkup.ui.screen

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.ButtonDefaults
import com.apkup.util.Badger
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import com.apkup.ui.component.rememberExpandedModalSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt
import com.apkup.BuildConfig
import com.apkup.data.fdroid.FDroidRepoConfig
import com.apkup.data.obtainium.ObtainiumImportPreview
import com.apkup.data.ui.AppsUiState
import com.apkup.R
import com.apkup.ui.theme.ApkUpTheme
import com.apkup.data.selfupdate.SelfUpdateState
import com.apkup.data.ui.AppInstalled
import com.apkup.data.ui.BackgroundCheckInterval
import com.apkup.data.ui.IgnoredVersionEntry
import com.apkup.data.ui.ShizukuSetupStatus
import com.apkup.ui.component.LoadingImageApp
import com.apkup.ui.component.SegmentedButtonSetting
import com.apkup.ui.component.SettingsSection
import com.apkup.ui.component.AutoResizeText
import com.apkup.ui.component.SwitchSetting
import com.apkup.ui.component.SplitSwitchSetting
import com.apkup.ui.component.NavigationSettingItem
import com.apkup.util.AppJournal
import com.apkup.util.DownloadStorageManager
import com.apkup.util.SessionInstaller
import com.apkup.util.openSafeHttpsUri
import com.apkup.viewmodel.AppsViewModel
import com.apkup.viewmodel.MainViewModel
import com.apkup.viewmodel.SettingsViewModel
import com.apkup.viewmodel.TokenValidationResult
import com.apkup.viewmodel.UpdatesViewModel
import com.apkup.ui.component.ChangelogBottomSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel


@Composable
fun SettingsScreen(
	viewModel: SettingsViewModel = koinViewModel(),
	appsViewModel: AppsViewModel = koinViewModel(),
	updatesViewModel: UpdatesViewModel = koinViewModel(),
	mainViewModel: MainViewModel = koinViewModel()
) = Column(
	modifier = Modifier
		.fillMaxSize()
		.background(MaterialTheme.colorScheme.background)
) {
	SettingsTopBar()
	Settings(viewModel, appsViewModel, updatesViewModel, mainViewModel)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun Settings(
	viewModel: SettingsViewModel,
	appsViewModel: AppsViewModel,
	updatesViewModel: UpdatesViewModel,
	mainViewModel: MainViewModel,
	handler: UriHandler = LocalUriHandler.current
) {
	val context = LocalContext.current
	val haptic = LocalHapticFeedback.current
	val lifecycleOwner = LocalLifecycleOwner.current
	val coroutineScope = rememberCoroutineScope()
	val listState = rememberLazyListState()
	val density = LocalDensity.current
	var highlightPermissions by remember { mutableStateOf(false) }
	var configRefreshKey by remember { mutableIntStateOf(0) }
	var hasInstallPermission by remember {
		mutableStateOf(context.packageManager.canRequestPackageInstalls())
	}

	DisposableEffect(lifecycleOwner) {
		val observer = LifecycleEventObserver { _, event ->
			if (event == Lifecycle.Event.ON_RESUME) {
				hasInstallPermission = context.packageManager.canRequestPackageInstalls()
				viewModel.refreshIgnoredVersions()
				viewModel.refreshIgnoredApps()
				viewModel.refreshShizukuStatus()
				configRefreshKey++
			}
		}
		lifecycleOwner.lifecycle.addObserver(observer)
		onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
	}
	val ignoredVersions = viewModel.ignoredVersions().collectAsStateWithLifecycle().value
	val ignoredApps = viewModel.ignoredApps().collectAsStateWithLifecycle().value
	val shizukuEnabled = viewModel.shizukuEnabled().collectAsStateWithLifecycle().value
	val rootEnabled = viewModel.rootEnabled().collectAsStateWithLifecycle().value
	val rootAvailable = viewModel.rootAvailable().collectAsStateWithLifecycle().value
	val customDownloadFolderUri = viewModel.customDownloadFolderUri().collectAsStateWithLifecycle().value
	val shizukuStatus = viewModel.shizukuSetupStatus().collectAsStateWithLifecycle().value
	val showShizukuSheet = viewModel.showShizukuSetupSheet().collectAsStateWithLifecycle().value
	val fdroidRepos = viewModel.fdroidReposFlow.collectAsStateWithLifecycle().value
	val sourcePriorityOrder = viewModel.sourcePriorityOrderFlow.collectAsStateWithLifecycle().value
	var showHiddenAppsSheet by remember { mutableStateOf(false) }
	var showIgnoredVersionsSheet by remember { mutableStateOf(false) }
	var showChangelogSheet by remember { mutableStateOf(false) }
	var showIntervalSheet by remember { mutableStateOf(false) }
	var showApkMirrorSheet by remember { mutableStateOf(false) }
	var showFDroidReposSheet by remember { mutableStateOf(false) }
	var showSourcePrioritySheet by remember { mutableStateOf(false) }
	var showSourceHealthSheet by remember { mutableStateOf(false) }
	var showPreReleaseSheet by remember { mutableStateOf(false) }
	var showUpdateWindowSheet by remember { mutableStateOf(false) }
	var showRollbackSettingsSheet by remember { mutableStateOf(false) }
	var rollbackStats by remember { mutableStateOf(0 to 0L) }
	LaunchedEffect(configRefreshKey, showRollbackSettingsSheet) {
		rollbackStats = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
			viewModel.getRollbackBackupStats()
		}
	}
	var showWifiDownloadSheet by remember { mutableStateOf(false) }
	var showGitHubTokenDialog by remember { mutableStateOf(false) }
	var showGitLabTokenDialog by remember { mutableStateOf(false) }
	var showCodebergTokenDialog by remember { mutableStateOf(false) }
	var showRootSetupSheet by rememberSaveable { mutableStateOf(false) }
	var showSupportCommunitySheet by rememberSaveable { mutableStateOf(false) }
	val openCustomTab = com.apkup.ui.component.rememberCustomTabOpener()
	var obtainiumImportPreview by remember { mutableStateOf<ObtainiumImportPreview?>(null) }
	var isAdvancedExpanded by rememberSaveable { mutableStateOf(false) }
	var includeLogcatInDiagnostics by rememberSaveable { mutableStateOf(false) }
	var isSharingDiagnosticLog by remember { mutableStateOf(false) }

	var easterEggActiveUntil by remember { mutableLongStateOf(0L) }
	val isEasterEggActive = easterEggActiveUntil > System.currentTimeMillis()

	LaunchedEffect(easterEggActiveUntil) {
		if (easterEggActiveUntil > System.currentTimeMillis()) {
			val remaining = easterEggActiveUntil - System.currentTimeMillis()
			if (remaining > 0L) {
				delay(remaining)
			}
			easterEggActiveUntil = 0L
		}
	}

	val triggerEasterEgg: () -> Unit = remember(context, haptic) {
		{
			performHeartbeatHaptic(context, haptic)
			easterEggActiveUntil = System.currentTimeMillis() + 6500L
		}
	}
	val iconTapTimestamps = remember { mutableStateListOf<Long>() }
	var exportSuccessWaveTick by remember { mutableIntStateOf(0) }

	val exportBackupLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.CreateDocument("application/json")
	) { uri ->
		if (uri != null) {
			coroutineScope.launch(Dispatchers.IO) {
				val success = runCatching {
					val json = viewModel.exportBackupJson()
					context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
						stream.write(json.toByteArray(Charsets.UTF_8))
						stream.flush()
					} ?: throw IllegalStateException("Unable to open output stream")
				}.isSuccess
				withContext(Dispatchers.Main) {
					viewModel.notifyBackupExported(success)
					if (success) exportSuccessWaveTick++
				}
			}
		}
	}

	val exportObtainiumLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.CreateDocument("application/json")
	) { uri ->
		if (uri != null) {
			coroutineScope.launch(Dispatchers.IO) {
				val success = runCatching {
					val appNames = (appsViewModel.state().value as? AppsUiState.Success)
						?.apps?.associate { it.packageName to it.name }.orEmpty()
					val json = viewModel.exportObtainiumJson(appNames)
					context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
						stream.write(json.toByteArray(Charsets.UTF_8))
						stream.flush()
					} ?: throw IllegalStateException("Unable to open output stream")
				}.isSuccess
				withContext(Dispatchers.Main) {
					viewModel.notifyObtainiumExported(success)
					if (success) exportSuccessWaveTick++
				}
			}
		}
	}

	val importObtainiumLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.OpenDocument()
	) { uri ->
		if (uri != null) {
			coroutineScope.launch(Dispatchers.IO) {
				val rawJson = readUtf8FromSafUri(context, uri)
				withContext(Dispatchers.Main) {
					if (rawJson.isNullOrBlank()) {
						viewModel.notifyObtainiumImportInvalid()
					} else {
						val installedPkgs = (appsViewModel.state().value as? AppsUiState.Success)?.apps
							?.filterNot { it.isWatchedOnly }
							?.mapTo(HashSet()) { it.packageName }
							.orEmpty()
						val preview = viewModel.parseObtainiumImportPreview(rawJson, installedPkgs)
						if (preview == null) {
							viewModel.notifyObtainiumImportInvalid()
						} else {
							obtainiumImportPreview = preview
						}
					}
				}
			}
		}
	}

	val importAppWatcherLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.OpenDocument()
	) { uri ->
		if (uri != null) {
			coroutineScope.launch(Dispatchers.IO) {
				val rawJson = readUtf8FromSafUri(context, uri).orEmpty()
				val installedPkgs = (appsViewModel.state().value as? AppsUiState.Success)?.apps
					?.filterNot { it.isWatchedOnly }
					?.mapTo(HashSet()) { it.packageName }
					.orEmpty()
				withContext(Dispatchers.Main) {
					val summary = viewModel.importAppWatcherJson(rawJson, installedPkgs)
					if (summary != null) {
						configRefreshKey++
						appsViewModel.refresh(false)
						updatesViewModel.refresh(false)
					}
				}
			}
		}
	}

	val exportDiagnosticLogLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.CreateDocument("text/plain")
	) { uri ->
		if (uri != null) {
			coroutineScope.launch(Dispatchers.IO) {
				val success = runCatching {
					val report = viewModel.buildDiagnosticLogReport(includeLogcat = includeLogcatInDiagnostics)
					context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
						stream.write(report.toByteArray(Charsets.UTF_8))
						stream.flush()
					} ?: throw IllegalStateException("Unable to open output stream")
				}.isSuccess
				withContext(Dispatchers.Main) {
					viewModel.notifyDiagnosticLogExported(success)
				}
			}
		}
	}

	val pickDownloadFolderLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.OpenDocumentTree()
	) { uri ->
		if (uri != null) {
			runCatching {
				context.contentResolver.takePersistableUriPermission(
					uri,
					Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
				)
			}
			viewModel.setCustomDownloadFolderUri(uri.toString())
			configRefreshKey++
		}
	}

	val importBackupLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.OpenDocument()
	) { uri ->
		if (uri != null) {
			coroutineScope.launch(Dispatchers.IO) {
				val rawJson = readUtf8FromSafUri(context, uri)
				withContext(Dispatchers.Main) {
					if (!rawJson.isNullOrBlank()) {
						val imported = viewModel.importBackupJson(rawJson)
						if (imported) {
							configRefreshKey++
							appsViewModel.refresh(false)
							updatesViewModel.syncIgnoredFromPrefs()
						}
					} else {
						viewModel.notifyBackupExported(false)
					}
				}
			}
		}
	}

	var overscrollOffset by remember { mutableFloatStateOf(0f) }
	var hasTriggeredOverscrollHaptic by remember { mutableStateOf(false) }

	val nestedScrollConnection = remember(context, haptic) {
		object : NestedScrollConnection {
			override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
				if (overscrollOffset < 0f && available.y > 0f) {
					val newOffset = (overscrollOffset + available.y).coerceAtMost(0f)
					val consumed = newOffset - overscrollOffset
					overscrollOffset = newOffset
					return Offset(0f, consumed)
				}
				return Offset.Zero
			}

			override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
				if (available.y < 0f && source == NestedScrollSource.UserInput) {
					val dampened = available.y * 0.42f
					val newOffset = (overscrollOffset + dampened).coerceIn(-220f, 0f)
					val delta = newOffset - overscrollOffset
					overscrollOffset = newOffset
					if (newOffset <= -120f && !hasTriggeredOverscrollHaptic) {
						hasTriggeredOverscrollHaptic = true
						performHeartbeatHaptic(context, haptic)
					}
					return Offset(0f, delta)
				}
				return Offset.Zero
			}

			override suspend fun onPreFling(available: Velocity): Velocity {
				if (overscrollOffset < 0f) {
					hasTriggeredOverscrollHaptic = false
					animate(
						initialValue = overscrollOffset,
						targetValue = 0f,
						animationSpec = spring(
							dampingRatio = Spring.DampingRatioMediumBouncy,
							stiffness = Spring.StiffnessLow
						)
					) { value, _ ->
						overscrollOffset = value
					}
				}
				return Velocity.Zero
			}

			override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
				if (overscrollOffset < 0f) {
					hasTriggeredOverscrollHaptic = false
					animate(
						initialValue = overscrollOffset,
						targetValue = 0f,
						animationSpec = spring(
							dampingRatio = Spring.DampingRatioMediumBouncy,
							stiffness = Spring.StiffnessLow
						)
					) { value, _ ->
						overscrollOffset = value
					}
				}
				return Velocity.Zero
			}
		}
	}

	val pullDistance = -overscrollOffset
	val pullProgress = (pullDistance / 130f).coerceIn(0f, 1f)

	Box(
		modifier = Modifier
			.fillMaxSize()
			.nestedScroll(nestedScrollConnection)
	) {
		val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
		val isBatteryIgnoring = remember(configRefreshKey) {
			powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
		}
		val areNotificationsEnabled = remember(configRefreshKey) {
			NotificationManagerCompat.from(context).areNotificationsEnabled()
		}

		val openBatterySettings: () -> Unit = remember(context) {
			{
				runCatching {
					val intent = Intent("android.settings.VIEW_ADVANCED_POWER_USAGE_DETAIL").apply {
						data = "package:${context.packageName}".toUri()
						addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
					}
					context.startActivity(intent)
				}.recoverCatching {
					val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
						data = "package:${context.packageName}".toUri()
						addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
					}
					context.startActivity(intent)
				}.recoverCatching {
					val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
						addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
					}
					context.startActivity(intent)
				}.recoverCatching {
					val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
						data = "package:${context.packageName}".toUri()
						addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
					}
					context.startActivity(intent)
				}.recoverCatching {
					context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
						addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
					})
				}
			}
		}

		val badger = koinInject<Badger>()
		val resources = LocalResources.current
		val activeIssues = remember(resources, isBatteryIgnoring, areNotificationsEnabled, hasInstallPermission, shizukuEnabled, shizukuStatus.isBinderRunning) {
			val list = mutableListOf<SetupWarningItem>()
			if (!hasInstallPermission) {
				list.add(
					SetupWarningItem(
						id = "install",
						title = resources.getString(R.string.warning_install_title),
						subtitle = resources.getString(R.string.warning_install_subtitle),
						shortName = resources.getString(R.string.settings_install_unknown_apps),
						actionText = resources.getString(R.string.warning_action_allow),
						onClick = {
							runCatching {
								val intent = Intent(
									Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
									"package:${context.packageName}".toUri()
								).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
								context.startActivity(intent)
							}.onFailure {
								runCatching {
									val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
										data = "package:${context.packageName}".toUri()
										addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
									}
									context.startActivity(intent)
								}
							}
						}
					)
				)
			}
			if (!areNotificationsEnabled) {
				list.add(
					SetupWarningItem(
						id = "notifications",
						title = resources.getString(R.string.warning_notifications_title),
						subtitle = resources.getString(R.string.warning_notifications_subtitle),
						shortName = resources.getString(R.string.settings_notifications),
						actionText = resources.getString(R.string.warning_action_enable),
						onClick = {
							runCatching {
								val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
									putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
									addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
								}
								context.startActivity(intent)
							}.onFailure {
								runCatching {
									val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
										data = "package:${context.packageName}".toUri()
										addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
									}
									context.startActivity(intent)
								}
							}
						}
					)
				)
			}
			if (!isBatteryIgnoring) {
				list.add(
					SetupWarningItem(
						id = "battery",
						title = resources.getString(R.string.warning_battery_title),
						subtitle = resources.getString(R.string.warning_battery_subtitle),
						shortName = resources.getString(R.string.settings_battery_optimization),
						actionText = resources.getString(R.string.warning_action_exempt),
						onClick = openBatterySettings
					)
				)
			}
			if (shizukuEnabled && !shizukuStatus.isBinderRunning) {
				list.add(
					SetupWarningItem(
						id = "shizuku",
						title = resources.getString(R.string.warning_shizuku_title),
						subtitle = resources.getString(R.string.warning_shizuku_subtitle),
						shortName = "Shizuku",
						actionText = resources.getString(R.string.warning_action_check),
						onClick = { viewModel.openShizukuSetupSheet() }
					)
				)
			}
			list
		}

		val allPermissionsGranted = isBatteryIgnoring && areNotificationsEnabled && hasInstallPermission
		var isPermissionsExpanded by rememberSaveable { mutableStateOf(!allPermissionsGranted) }
		LaunchedEffect(allPermissionsGranted) {
			isPermissionsExpanded = !allPermissionsGranted
		}

		LaunchedEffect(activeIssues.size) {
			badger.changeSettingsBadge(if (activeIssues.isNotEmpty()) "!" else "")
		}

		val scrollToPermissions: () -> Unit = {
			isAdvancedExpanded = true
			isPermissionsExpanded = true
			highlightPermissions = true
			coroutineScope.launch {
				delay(120)
				val targetIndex = if (activeIssues.isNotEmpty()) 5 else 4
				listState.animateScrollToItem(
					index = targetIndex,
					scrollOffset = with(density) { 240.dp.roundToPx() }
				)
				delay(2500)
				highlightPermissions = false
			}
		}

		LazyColumn(
			state = listState,
			modifier = Modifier
				.fillMaxSize()
				.offset { IntOffset(0, overscrollOffset.roundToInt()) },
			contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 120.dp),
			verticalArrangement = Arrangement.spacedBy(14.dp)
		) {
			// Temporäre Status-Warnkarte (nur sichtbar, wenn Einstellungen fehlen)
			if (activeIssues.isNotEmpty()) {
				item(key = "setup_active_warnings") {
					val isDark = androidx.compose.foundation.isSystemInDarkTheme()
					Surface(
						modifier = Modifier
							.fillMaxWidth()
							.clickable(onClick = scrollToPermissions),
						shape = RoundedCornerShape(16.dp),
						color = if (isDark) Color(0xFF331E05) else Color(0xFFFFF3E0),
						border = BorderStroke(1.dp, if (isDark) Color(0xFFF59E0B).copy(alpha = 0.5f) else Color(0xFFFFB74D))
					) {
						Row(
							modifier = Modifier
								.fillMaxWidth()
								.padding(horizontal = 14.dp, vertical = 12.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(12.dp)
						) {
							Box(
								modifier = Modifier
									.size(36.dp)
									.clip(RoundedCornerShape(10.dp))
									.background(if (isDark) Color(0xFF4A2B07) else Color(0xFFFFE0B2)),
								contentAlignment = Alignment.Center
							) {
								Icon(
									imageVector = Icons.Default.Warning,
									contentDescription = null,
									tint = if (isDark) Color(0xFFFBBF24) else Color(0xFFE65100),
									modifier = Modifier.size(20.dp)
								)
							}
							Column(modifier = Modifier.weight(1f)) {
								Text(
									text = if (activeIssues.size == 1) activeIssues.first().title
									       else stringResource(R.string.setup_warnings_multiple_title, activeIssues.size),
									style = MaterialTheme.typography.titleSmall,
									fontWeight = FontWeight.Bold,
									color = if (isDark) Color(0xFFFEF3C7) else Color(0xFFE65100)
								)
								Text(
									text = if (activeIssues.size == 1) activeIssues.first().subtitle
									       else activeIssues.joinToString(" • ") { it.shortName },
									style = MaterialTheme.typography.bodySmall,
									color = if (isDark) Color(0xFFD1D5DB) else Color(0xFF5D4037),
									maxLines = 1,
									overflow = TextOverflow.Ellipsis
								)
							}
							FilledTonalButton(
								onClick = {
									if (activeIssues.size > 1) {
										scrollToPermissions()
									} else {
										activeIssues.first().onClick()
									}
								},
								colors = ButtonDefaults.filledTonalButtonColors(
									containerColor = if (isDark) Color(0xFFF59E0B) else Color(0xFFE65100),
									contentColor = if (isDark) Color(0xFF451A03) else Color.White
								),
								shape = CircleShape,
								contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
								modifier = Modifier.heightIn(min = 32.dp)
							) {
								Text(
									text = if (activeIssues.size == 1) activeIssues.first().actionText else stringResource(R.string.setup_warning_action_resolve),
									style = MaterialTheme.typography.labelSmall,
									fontWeight = FontWeight.Bold
								)
							}
						}
					}
				}
			}

			// Sektion 1: Erscheinungsbild & Layout
			item(key = "section_appearance_$configRefreshKey") {
				var currentDisplayMode by remember(configRefreshKey) {
					mutableIntStateOf(viewModel.getUpdatesDisplayMode())
				}
				SettingsSection(stringResource(R.string.settings_section_appearance)) {
				SegmentedButtonSetting(
					stringResource(R.string.theme),
					listOf(
						stringResource(R.string.theme_system),
						stringResource(R.string.theme_dark),
						stringResource(R.string.theme_light)
					),
					{ viewModel.getTheme() },
					{ viewModel.setTheme(it) },
					R.drawable.ic_theme
				)
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
					SwitchSetting(
						getValue = { viewModel.getDynamicColor() },
						setValue = { viewModel.setDynamicColor(it) },
						text = stringResource(R.string.dynamic_color_title),
						subtitle = stringResource(R.string.dynamic_color_subtitle),
						icon = R.drawable.ic_theme
					)
				}
				SegmentedButtonSetting(
					stringResource(R.string.updates_display_mode),
					listOf(stringResource(R.string.updates_detail_view), stringResource(R.string.updates_compact_view)),
					{ currentDisplayMode },
					{
						currentDisplayMode = it
						viewModel.setUpdatesDisplayMode(it)
					},
					R.drawable.ic_download
				)
				UpdateDisplayModePreview(displayMode = currentDisplayMode)
			}
		}

		// Sektion 2: Update-Quellen
		item(key = "section_sources_${configRefreshKey}_${fdroidRepos.hashCode()}") {
			SettingsSection(stringResource(R.string.settings_section_sources)) {
				val directApkMirrorEnabled = remember(configRefreshKey) { viewModel.getDirectApkMirrorDownloads() }
				SwitchSetting(
					getValue = { viewModel.getUseApkMirror() },
					setValue = { enabled ->
						viewModel.setUseApkMirror(enabled)
						configRefreshKey++
					},
					text = stringResource(R.string.source_apkmirror),
					icon = R.drawable.ic_apkmirror,
					subtitle = if (viewModel.getUseApkMirror()) {
						if (directApkMirrorEnabled) {
							stringResource(R.string.settings_apkmirror_direct_active)
						} else {
							stringResource(R.string.settings_apkmirror_direct_inactive)
						}
					} else null,
					onClick = { showApkMirrorSheet = true }
				)
				SwitchSetting(
					{ viewModel.getUsePlay() },
					{ viewModel.setUsePlay(it) },
					stringResource(R.string.source_play),
					R.drawable.ic_play
				)
				val enabledFDroidCount = fdroidRepos.count { it.enabled }
				SplitSwitchSetting(
					getValue = { viewModel.getUseIzzyOnDroid() },
					setValue = { enabled ->
						viewModel.setUseIzzyOnDroid(enabled)
						configRefreshKey++
					},
					text = stringResource(R.string.source_izzyondroid),
					icon = R.drawable.ic_izzy,
					subtitle = if (viewModel.getUseIzzyOnDroid()) {
						stringResource(R.string.fdroid_repos_enabled_subtitle, enabledFDroidCount)
					} else {
						stringResource(R.string.fdroid_repos_tap_to_manage)
					},
					onClick = { showFDroidReposSheet = true }
				)
				val currentGitHubToken = remember(configRefreshKey) { viewModel.getGitHubToken() }
				SwitchSetting(
					getValue = { viewModel.getUseGitHub() },
					setValue = {
						viewModel.setUseGitHub(it)
						configRefreshKey++
					},
					text = stringResource(R.string.source_github),
					icon = R.drawable.ic_github,
					subtitle = if (currentGitHubToken.isNotEmpty()) {
						stringResource(R.string.github_token_status_set)
					} else {
						stringResource(R.string.settings_source_github_desc)
					},
					onClick = { showGitHubTokenDialog = true }
				)
				val currentGitLabToken = remember(configRefreshKey) { viewModel.getGitLabToken() }
				SwitchSetting(
					getValue = { viewModel.getUseGitLab() },
					setValue = {
						viewModel.setUseGitLab(it)
						configRefreshKey++
					},
					text = stringResource(R.string.source_gitlab),
					icon = R.drawable.ic_gitlab,
					subtitle = if (currentGitLabToken.isNotEmpty()) {
						stringResource(R.string.gitlab_token_status_set)
					} else {
						stringResource(R.string.settings_source_gitlab_desc)
					},
					onClick = { showGitLabTokenDialog = true }
				)
				val currentCodebergToken = remember(configRefreshKey) { viewModel.getCodebergToken() }
				SwitchSetting(
					getValue = { viewModel.getUseCodeberg() },
					setValue = {
						viewModel.setUseCodeberg(it)
						configRefreshKey++
					},
					text = stringResource(R.string.source_codeberg),
					icon = R.drawable.ic_codeberg,
					subtitle = if (currentCodebergToken.isNotEmpty()) {
						stringResource(R.string.codeberg_token_status_set)
					} else {
						stringResource(R.string.settings_source_codeberg_desc)
					},
					onClick = { showCodebergTokenDialog = true }
				)
				var includePreReleases by remember(configRefreshKey) {
					mutableStateOf(viewModel.getIncludePreReleasesGlobal())
				}
				val allowedChannels = remember(configRefreshKey) {
					viewModel.getAllowedPreReleaseChannels()
				}
				val preReleaseSubtitle = if (!includePreReleases) {
					stringResource(R.string.prerelease_summary_off)
				} else if (allowedChannels.isEmpty()) {
					stringResource(R.string.prerelease_summary_none)
				} else {
					listOfNotNull(
						if (com.apkup.data.repomapping.ReleaseAssetFilter.PreReleaseChannel.BETA in allowedChannels) stringResource(R.string.prerelease_channel_beta) else null,
						if (com.apkup.data.repomapping.ReleaseAssetFilter.PreReleaseChannel.RC in allowedChannels) "RC" else null,
						if (com.apkup.data.repomapping.ReleaseAssetFilter.PreReleaseChannel.ALPHA in allowedChannels) stringResource(R.string.prerelease_channel_alpha) else null,
						if (com.apkup.data.repomapping.ReleaseAssetFilter.PreReleaseChannel.NIGHTLY in allowedChannels) "Nightly" else null
					).joinToString(", ")
				}
				SwitchSetting(
					getValue = { includePreReleases },
					setValue = { enabled ->
						viewModel.setIncludePreReleasesGlobal(enabled)
						includePreReleases = enabled
						configRefreshKey++
					},
					text = stringResource(R.string.settings_include_prereleases_title),
					icon = R.drawable.ic_refresh,
					subtitle = preReleaseSubtitle,
					onClick = { showPreReleaseSheet = true }
				)
				val priorityPreview = sourcePriorityOrder.take(3).map { com.apkup.ui.component.sourcePriorityLabel(it) }.joinToString(" → ") +
					if (sourcePriorityOrder.size > 3) " → …" else ""
				Surface(
					modifier = Modifier
						.fillMaxWidth()
						.clip(RoundedCornerShape(14.dp))
						.clickable { showSourcePrioritySheet = true },
					color = Color.Transparent
				) {
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.heightIn(min = 48.dp)
							.padding(horizontal = 4.dp, vertical = 6.dp),
						verticalAlignment = Alignment.CenterVertically
					) {
						Box(
							modifier = Modifier
								.size(32.dp)
								.clip(RoundedCornerShape(10.dp))
								.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)),
							contentAlignment = Alignment.Center
						) {
							Icon(
								imageVector = Icons.Rounded.SwapVert,
								contentDescription = null,
								tint = MaterialTheme.colorScheme.primary,
								modifier = Modifier.size(18.dp)
							)
						}
						Spacer(modifier = Modifier.width(10.dp))
						Column(
							modifier = Modifier.weight(1f),
							verticalArrangement = Arrangement.spacedBy(1.dp)
						) {
							Text(
								text = stringResource(R.string.settings_source_priority_title),
								style = MaterialTheme.typography.bodyMedium,
								fontWeight = FontWeight.Medium,
								color = MaterialTheme.colorScheme.onSurface
							)
							Text(
								text = priorityPreview,
								style = MaterialTheme.typography.labelSmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis
							)
						}
						Icon(
							imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
							contentDescription = null,
							tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
							modifier = Modifier.size(20.dp)
						)
					}
				}
			}
		}

		// Sektion 3: Automatische Suche & Zeitplan
		item(key = "section_background_checks_$configRefreshKey") {
			val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
			var bgChecksEnabled by remember(configRefreshKey) {
				mutableStateOf(viewModel.getBackgroundChecksEnabled())
			}
			var windowEnabled by remember(configRefreshKey) {
				mutableStateOf(viewModel.getAutoUpdateWindowEnabled())
			}
			val windowMode = remember(configRefreshKey) {
				viewModel.getAutoUpdateWindowMode()
			}
			val windowStartMins = remember(configRefreshKey) {
				viewModel.getAutoUpdateWindowStartMinutes()
			}
			val windowEndMins = remember(configRefreshKey) {
				viewModel.getAutoUpdateWindowEndMinutes()
			}
			val selectedIntervalHours = remember(configRefreshKey) {
				viewModel.getBackgroundCheckIntervalHours()
			}
			val intervalLabels = mapOf(
				6 to R.string.settings_interval_6h,
				12 to R.string.settings_interval_12h,
				24 to R.string.settings_interval_24h,
				48 to R.string.settings_interval_48h,
				168 to R.string.settings_interval_7d
			)
			val activeIntervalLabel = intervalLabels[selectedIntervalHours]?.let { stringResource(it) } ?: "${selectedIntervalHours}h"

			val windowSubtitle = if (!windowEnabled) {
				stringResource(R.string.update_window_summary_off)
			} else {
				val startFmt = com.apkup.data.ui.UpdateWindowEvaluator.formatMinutesOfDay(windowStartMins)
				val endFmt = com.apkup.data.ui.UpdateWindowEvaluator.formatMinutesOfDay(windowEndMins)
				val modePrefix = if (windowMode == com.apkup.data.ui.UpdateWindowMode.ALLOW_ONLY) {
					stringResource(R.string.settings_update_window_mode_allow)
				} else {
					stringResource(R.string.settings_update_window_mode_quiet)
				}
				"$modePrefix ($startFmt – $endFmt)"
			}

			SettingsSection(stringResource(R.string.settings_section_background_checks)) {
				SwitchSetting(
					getValue = { bgChecksEnabled },
					setValue = { enabled ->
						if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
							if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
								runCatching { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
							}
						}
						viewModel.setBackgroundChecksEnabled(enabled, launcher)
						bgChecksEnabled = viewModel.getBackgroundChecksEnabled()
					},
					text = stringResource(R.string.settings_background_checks),
					icon = R.drawable.ic_refresh,
					subtitle = if (bgChecksEnabled) activeIntervalLabel else null,
					onClick = { showIntervalSheet = true }
				)

				SwitchSetting(
					getValue = { windowEnabled },
					setValue = { enabled ->
						viewModel.setAutoUpdateWindowEnabled(enabled)
						windowEnabled = enabled
						configRefreshKey++
					},
					text = stringResource(R.string.settings_update_window_title),
					icon = R.drawable.ic_refresh,
					subtitle = windowSubtitle,
					onClick = { showUpdateWindowSheet = true }
				)
			}
		}

		// Sektion 4: Installation & Automatisierung
		item(key = "section_install_automation_${configRefreshKey}_${shizukuEnabled}_${rootEnabled}_$hasInstallPermission") {
			SettingsSection(stringResource(R.string.settings_section_install_automation)) {
				val shizukuAutoActive = remember(configRefreshKey, shizukuEnabled) {
					shizukuEnabled && viewModel.getAutoInstallUpdates()
				}
				val shizukuSubtitle = when {
					shizukuAutoActive -> stringResource(R.string.settings_shizuku_summary_auto)
					shizukuEnabled -> stringResource(R.string.settings_shizuku_summary_silent)
					else -> stringResource(R.string.settings_shizuku_summary_inactive)
				}
				// 2. Shizuku-Installer
				SwitchSetting(
					getValue = { viewModel.getUseShizukuInstaller() },
					setValue = { viewModel.setUseShizukuInstaller(it) },
					text = stringResource(R.string.settings_shizuku_installer),
					icon = R.drawable.ic_shizuku,
					subtitle = shizukuSubtitle,
					onClick = { viewModel.openShizukuSetupSheet() }
				)

				// 2b. Root-Installer (su Fallback neben Shizuku oder standalone)
				SwitchSetting(
					getValue = { rootEnabled },
					setValue = {
						viewModel.setUseRootInstaller(it)
						configRefreshKey++
					},
					text = stringResource(R.string.settings_root_installer),
					icon = R.drawable.ic_root,
					subtitle = when {
						rootEnabled && rootAvailable == false -> stringResource(R.string.settings_root_installer_status_missing)
						rootEnabled -> stringResource(R.string.settings_root_installer_subtitle_active)
						else -> stringResource(R.string.settings_root_installer_subtitle)
					},
					onClick = {
						viewModel.refreshRootStatus()
						showRootSetupSheet = true
					}
				)

				var rollbackBackupsEnabled by remember(configRefreshKey) {
					mutableStateOf(viewModel.getEnableRollbackBackups())
				}
				val rollbackBudgetMb = remember(configRefreshKey) { viewModel.getRollbackMaxBudgetMb() }
				val rollbackMaxAgeDays = remember(configRefreshKey) { viewModel.getRollbackMaxAgeDays() }
				val rollbackBudgetLabel = com.apkup.ui.component.rollbackBudgetLabel(rollbackBudgetMb)
				val rollbackMaxAgeLabel = com.apkup.ui.component.rollbackMaxAgeLabel(rollbackMaxAgeDays)
				val rollbackSizeText = remember(context, rollbackStats.second) {
					android.text.format.Formatter.formatShortFileSize(context, rollbackStats.second)
				}
				val rollbackSubtitle = if (rollbackBackupsEnabled) {
					if (rollbackStats.first > 0) {
						stringResource(
							R.string.settings_rollback_subtitle_active,
							rollbackBudgetLabel,
							rollbackMaxAgeLabel,
							rollbackSizeText
						)
					} else {
						"$rollbackBudgetLabel · $rollbackMaxAgeLabel"
					}
				} else {
					stringResource(R.string.settings_rollback_backups_desc)
				}
				SwitchSetting(
					getValue = { rollbackBackupsEnabled },
					setValue = { enabled ->
						viewModel.setEnableRollbackBackups(enabled)
						rollbackBackupsEnabled = enabled
						configRefreshKey++
					},
					text = stringResource(R.string.settings_rollback_backups_title),
					icon = R.drawable.ic_refresh,
					subtitle = rollbackSubtitle,
					onClick = { showRollbackSettingsSheet = true }
				)

				// Conditional Shizuku Warning Card if enabled but not running
				if (shizukuEnabled && !shizukuStatus.isBinderRunning) {
					Surface(
						onClick = { viewModel.openShizukuSetupSheet() },
						modifier = Modifier
							.fillMaxWidth()
							.padding(vertical = 4.dp),
						shape = RoundedCornerShape(16.dp),
						color = MaterialTheme.colorScheme.errorContainer,
						border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
					) {
						Row(
							modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(10.dp)
						) {
							Icon(
								imageVector = Icons.Default.Warning,
								contentDescription = null,
								tint = MaterialTheme.colorScheme.error,
								modifier = Modifier.size(22.dp)
							)
							Column(modifier = Modifier.weight(1f)) {
								Text(
									text = stringResource(R.string.shizuku_status_not_running),
									style = MaterialTheme.typography.titleSmall,
									fontWeight = FontWeight.Bold,
									color = MaterialTheme.colorScheme.onErrorContainer
								)
								Text(
									text = stringResource(R.string.shizuku_step2_desc_missing),
									style = MaterialTheme.typography.bodySmall,
									color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
								)
							}
						}
					}
				}

				// 3. Wenn Shizuku oder Root aktiv: Autonome Updates & Schutzregeln
				if (shizukuEnabled || rootEnabled) {
					val autoInstallEnabled = remember(configRefreshKey, shizukuEnabled, rootEnabled) { viewModel.getAutoInstallUpdates() }
					val autoInstallWifiOnly = remember(configRefreshKey) { viewModel.getAutoInstallWifiOnly() }
					val autoInstallChargingOnly = remember(configRefreshKey) { viewModel.getAutoInstallChargingOnly() }
					Surface(
						modifier = Modifier
							.fillMaxWidth()
							.padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 4.dp),
						shape = RoundedCornerShape(14.dp),
						color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
						onClick = {
							viewModel.setAutoInstallUpdates(!autoInstallEnabled)
							configRefreshKey++
						}
					) {
						Column(
							modifier = Modifier
								.fillMaxWidth()
								.padding(horizontal = 14.dp, vertical = 10.dp),
							verticalArrangement = Arrangement.spacedBy(8.dp)
						) {
							Row(
								modifier = Modifier.fillMaxWidth(),
								verticalAlignment = CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(10.dp)
							) {
								Icon(
									painter = painterResource(R.drawable.ic_install),
									contentDescription = null,
									tint = if (autoInstallEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
									modifier = Modifier.size(20.dp)
								)
								Column(modifier = Modifier.weight(1f)) {
									Text(
										text = stringResource(R.string.settings_auto_install_title),
										style = MaterialTheme.typography.bodyMedium,
										fontWeight = FontWeight.Medium
									)
									Text(
										text = stringResource(R.string.settings_auto_install_desc),
										style = MaterialTheme.typography.bodySmall,
										color = MaterialTheme.colorScheme.onSurfaceVariant
									)
								}
								androidx.compose.material3.Switch(
									checked = autoInstallEnabled,
									onCheckedChange = {
										viewModel.setAutoInstallUpdates(it)
										configRefreshKey++
									}
								)
							}
							val isReducedMotionAuto = ApkUpTheme.isReducedMotion
							AnimatedVisibility(
								visible = autoInstallEnabled,
								enter = if (isReducedMotionAuto) fadeIn(tween(60)) else expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
								exit = if (isReducedMotionAuto) fadeOut(tween(60)) else shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut()
							) {
								Row(
									modifier = Modifier
										.fillMaxWidth()
										.horizontalScroll(rememberScrollState()),
									horizontalArrangement = Arrangement.spacedBy(8.dp),
									verticalAlignment = CenterVertically
								) {
									FilterChip(
										selected = autoInstallWifiOnly,
										onClick = {
											viewModel.setAutoInstallWifiOnly(!autoInstallWifiOnly)
											configRefreshKey++
										},
										leadingIcon = if (autoInstallWifiOnly) {
											{
												Icon(
													imageVector = Icons.Default.Check,
													contentDescription = null,
													modifier = Modifier.size(FilterChipDefaults.IconSize)
												)
											}
										} else null,
										label = {
											Text(
												text = stringResource(R.string.settings_auto_install_wifi_only),
												style = MaterialTheme.typography.labelMedium,
												maxLines = 1
											)
										}
									)
									FilterChip(
										selected = autoInstallChargingOnly,
										onClick = {
											viewModel.setAutoInstallChargingOnly(!autoInstallChargingOnly)
											configRefreshKey++
										},
										leadingIcon = if (autoInstallChargingOnly) {
											{
												Icon(
													imageVector = Icons.Default.Check,
													contentDescription = null,
													modifier = Modifier.size(FilterChipDefaults.IconSize)
												)
											}
										} else null,
										label = {
											Text(
												text = stringResource(R.string.settings_auto_install_charging_only),
												style = MaterialTheme.typography.labelMedium,
												maxLines = 1
											)
										}
									)
								}
							}
						}
					}
				}

				// 4. Updates nur über WLAN laden (SplitSwitch mit Untermenü)
				val downloadWifiOnly = remember(configRefreshKey) { viewModel.getDownloadWifiOnly() }
				val downloadChargingOnly = remember(configRefreshKey) { viewModel.getDownloadChargingOnly() }

				SplitSwitchSetting(
					getValue = { downloadWifiOnly },
					setValue = {
						viewModel.setDownloadWifiOnly(it)
						configRefreshKey++
					},
					text = stringResource(R.string.settings_download_wifi_only),
					icon = R.drawable.ic_wifi,
					subtitle = when {
						downloadWifiOnly && downloadChargingOnly -> stringResource(R.string.settings_wifi_active_charging_desc)
						downloadWifiOnly -> stringResource(R.string.settings_download_wifi_only_desc)
						downloadChargingOnly -> stringResource(R.string.settings_charging_only_active_desc)
						else -> stringResource(R.string.settings_wifi_all_networks_desc)
					},
					onClick = { showWifiDownloadSheet = true }
				)
			}
		}

		// Sektion 5: Erweiterte Einstellungen (App-Verwaltung, System & Datensicherung - einklappbar)
		item(key = "section_advanced") {
			Surface(
				modifier = Modifier.fillMaxWidth(),
				shape = RoundedCornerShape(20.dp),
				color = MaterialTheme.colorScheme.surfaceContainerLow
			) {
				Column(
					modifier = Modifier
						.fillMaxWidth()
						.padding(14.dp),
					verticalArrangement = Arrangement.spacedBy(10.dp)
				) {
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.clip(RoundedCornerShape(12.dp))
							.clickable { isAdvancedExpanded = !isAdvancedExpanded }
							.padding(vertical = 4.dp),
						verticalAlignment = CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(10.dp)
					) {
						Box(
							modifier = Modifier
								.size(36.dp)
								.clip(RoundedCornerShape(10.dp))
								.background(MaterialTheme.colorScheme.surfaceContainerHigh),
							contentAlignment = Alignment.Center
						) {
							Icon(
								painter = painterResource(R.drawable.ic_system),
								contentDescription = null,
								tint = MaterialTheme.colorScheme.primary,
								modifier = Modifier.size(20.dp)
							)
						}
						Column(modifier = Modifier.weight(1f)) {
							Text(
								text = stringResource(R.string.settings_advanced_section),
								style = MaterialTheme.typography.titleSmall,
								fontWeight = FontWeight.SemiBold,
								color = MaterialTheme.colorScheme.onSurface
							)
							Text(
								text = stringResource(R.string.settings_advanced_subtitle),
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant
							)
						}
						val advancedChevronRotation by animateFloatAsState(
							targetValue = if (isAdvancedExpanded) 180f else 0f,
							animationSpec = if (ApkUpTheme.isReducedMotion) tween(60) else spring(stiffness = Spring.StiffnessMediumLow),
							label = "advanced_chevron"
						)
						Icon(
							imageVector = Icons.Default.KeyboardArrowDown,
							contentDescription = null,
							tint = MaterialTheme.colorScheme.onSurfaceVariant,
							modifier = Modifier
								.size(22.dp)
								.graphicsLayer { rotationZ = advancedChevronRotation }
						)
					}

					AnimatedVisibility(
						visible = isAdvancedExpanded,
						enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
						exit = shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut()
					) {
						Column(
							modifier = Modifier.fillMaxWidth(),
							verticalArrangement = Arrangement.spacedBy(14.dp)
						) {
							// 1. App-Verwaltung
							Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
								Text(
									text = stringResource(R.string.settings_section_app_management),
									style = MaterialTheme.typography.labelLarge,
									fontWeight = FontWeight.Bold,
									color = MaterialTheme.colorScheme.primary
								)
								SwitchSetting(
									getValue = { viewModel.getShowSystemApps() },
									setValue = { show ->
										viewModel.onShowSystemAppsChanged(
											show = show,
											appsViewModel = appsViewModel,
											updatesViewModel = updatesViewModel
										)
									},
									text = stringResource(R.string.settings_show_system_apps),
									icon = R.drawable.ic_system,
									subtitle = stringResource(R.string.settings_show_system_apps_desc)
								)
								// 1. Dauerhaft ausgeblendete Apps
								NavigationSettingItem(
									title = stringResource(R.string.hidden_apps_title),
									subtitle = if (ignoredApps.isEmpty()) stringResource(R.string.hidden_apps_none)
									else stringResource(R.string.hidden_apps_badge_count, ignoredApps.size),
									icon = R.drawable.ic_visible_off,
									badgeText = if (ignoredApps.isNotEmpty()) "${ignoredApps.size} Apps" else "0 Apps",
									badgeContainerColor = if (ignoredApps.isNotEmpty()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
									badgeContentColor = if (ignoredApps.isNotEmpty()) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
									onClick = { showHiddenAppsSheet = true }
								)

								// 2. Übersprungene Einzel-Updates
								NavigationSettingItem(
									title = stringResource(R.string.settings_ignored_versions_title),
									subtitle = if (ignoredVersions.isEmpty()) stringResource(R.string.settings_ignored_versions_empty)
									else stringResource(R.string.settings_ignored_versions_count, ignoredVersions.size),
									icon = R.drawable.ic_download,
									badgeText = if (ignoredVersions.isNotEmpty()) "${ignoredVersions.size}" else "0",
									badgeContainerColor = if (ignoredVersions.isNotEmpty()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
									badgeContentColor = if (ignoredVersions.isNotEmpty()) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
									onClick = { showIgnoredVersionsSheet = true }
								)
							}

							HorizontalDivider(
								thickness = 1.dp,
								color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
							)

							val highlightAlpha by animateFloatAsState(
								targetValue = if (highlightPermissions) 1f else 0f,
								animationSpec = tween(durationMillis = 450),
								label = "permissions_highlight"
							)

							// 2. System & Berechtigungen
							Column(
								modifier = Modifier
									.fillMaxWidth()
									.then(
										if (highlightAlpha > 0f) {
											Modifier
												.clip(RoundedCornerShape(16.dp))
												.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f * highlightAlpha))
												.border(
													width = (1.5 * highlightAlpha).dp,
													color = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f * highlightAlpha),
													shape = RoundedCornerShape(16.dp)
												)
										} else {
											Modifier
										}
									)
									.padding(
										horizontal = (6 * highlightAlpha).dp,
										vertical = 2.dp + (4 * highlightAlpha).dp
									),
								verticalArrangement = Arrangement.spacedBy(8.dp)
							) {
								Row(
									modifier = Modifier
										.fillMaxWidth()
										.clip(RoundedCornerShape(12.dp))
										.clickable { isPermissionsExpanded = !isPermissionsExpanded }
										.padding(horizontal = 4.dp, vertical = 6.dp),
									verticalAlignment = CenterVertically,
									horizontalArrangement = Arrangement.spacedBy(8.dp)
								) {
									Text(
										text = stringResource(R.string.settings_section_system_permissions),
										style = MaterialTheme.typography.labelLarge,
										fontWeight = FontWeight.Bold,
										color = MaterialTheme.colorScheme.primary,
										modifier = Modifier.weight(1f)
									)
									if (allPermissionsGranted) {
										val isDark = androidx.compose.foundation.isSystemInDarkTheme()
										Surface(
											shape = CircleShape,
											color = if (isDark) Color(0xFF1B3820) else Color(0xFFE8F5E9)
										) {
											Row(
												modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
												verticalAlignment = CenterVertically,
												horizontalArrangement = Arrangement.spacedBy(4.dp)
											) {
												Icon(
													imageVector = Icons.Default.Check,
													contentDescription = null,
													tint = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32),
													modifier = Modifier.size(13.dp)
												)
												Text(
													text = stringResource(R.string.settings_permissions_all_granted),
													style = MaterialTheme.typography.labelSmall,
													fontWeight = FontWeight.SemiBold,
													color = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
												)
											}
										}
									}
									val permissionsChevronRotation by animateFloatAsState(
										targetValue = if (isPermissionsExpanded) 180f else 0f,
										animationSpec = if (ApkUpTheme.isReducedMotion) tween(60) else spring(stiffness = Spring.StiffnessMediumLow),
										label = "permissions_chevron"
									)
									Icon(
										imageVector = Icons.Default.KeyboardArrowDown,
										contentDescription = null,
										tint = MaterialTheme.colorScheme.onSurfaceVariant,
										modifier = Modifier
											.size(20.dp)
											.graphicsLayer { rotationZ = permissionsChevronRotation }
									)
								}
								AnimatedVisibility(
									visible = isPermissionsExpanded,
									enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
									exit = shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut()
								) {
									Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
										// 1. Akku-Optimierung
										PermissionSettingItem(
											title = stringResource(R.string.settings_battery_optimization),
											subtitle = stringResource(
												if (isBatteryIgnoring) R.string.settings_battery_optimization_exempted
												else R.string.settings_battery_optimization_optimized
											),
											iconRes = R.drawable.ic_battery,
											isGranted = isBatteryIgnoring,
											statusText = stringResource(
												if (isBatteryIgnoring) R.string.settings_status_exempted
												else R.string.settings_status_optimized
											),
											onClick = openBatterySettings
										)

										// 2. Benachrichtigungen
										PermissionSettingItem(
											title = stringResource(R.string.settings_notifications),
											subtitle = stringResource(
												if (areNotificationsEnabled) R.string.settings_notifications_enabled
												else R.string.settings_notifications_disabled
											),
											iconRes = R.drawable.ic_notifications,
											isGranted = areNotificationsEnabled,
											statusText = stringResource(
												if (areNotificationsEnabled) R.string.settings_status_active
												else R.string.settings_status_disabled
											),
											onClick = {
												runCatching {
													val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
														putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
														addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
													}
													context.startActivity(intent)
												}.onFailure {
													runCatching {
														val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
															data = "package:${context.packageName}".toUri()
															addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
														}
														context.startActivity(intent)
													}
												}
											}
										)

										// 3. Unbekannte Apps installieren
										PermissionSettingItem(
											title = stringResource(R.string.settings_install_unknown_apps),
											subtitle = stringResource(
												if (hasInstallPermission) R.string.settings_install_unknown_apps_allowed
												else R.string.settings_install_unknown_apps_denied
											),
											iconRes = R.drawable.ic_install,
											isGranted = hasInstallPermission,
											statusText = stringResource(
												if (hasInstallPermission) R.string.settings_status_allowed
												else R.string.settings_status_blocked
											),
											onClick = {
												runCatching {
													val intent = Intent(
														Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
														"package:${context.packageName}".toUri()
													).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
													context.startActivity(intent)
												}.onFailure {
													runCatching {
														val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
															data = "package:${context.packageName}".toUri()
															addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
														}
														context.startActivity(intent)
													}
												}
											}
										)
									}
								}
							}

							HorizontalDivider(
								thickness = 1.dp,
								color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
							)

							// 2b. Speicherort für manuell gespeicherte APKs
							Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
								Text(
									text = stringResource(R.string.settings_section_apk_download_folder),
									style = MaterialTheme.typography.labelLarge,
									fontWeight = FontWeight.Bold,
									color = MaterialTheme.colorScheme.primary
								)
								Text(
									text = DownloadStorageManager.resolveSafFolderDisplayName(customDownloadFolderUri),
									style = MaterialTheme.typography.bodySmall,
									fontWeight = FontWeight.Medium,
									color = MaterialTheme.colorScheme.onSurface
								)
								Text(
									text = stringResource(R.string.settings_apk_download_folder_desc),
									style = MaterialTheme.typography.bodySmall,
									color = MaterialTheme.colorScheme.onSurfaceVariant
								)
								Row(
									modifier = Modifier
										.fillMaxWidth()
										.padding(top = 4.dp),
									horizontalArrangement = Arrangement.spacedBy(10.dp),
									verticalAlignment = CenterVertically
								) {
									FilledTonalButton(
										onClick = { pickDownloadFolderLauncher.launch(null) },
										modifier = Modifier.weight(1f),
										shape = CircleShape
									) {
										Icon(
											painter = painterResource(R.drawable.ic_download),
											contentDescription = null,
											modifier = Modifier.size(16.dp)
										)
										Spacer(modifier = Modifier.width(6.dp))
										Text(
											text = stringResource(R.string.action_choose_folder),
											maxLines = 1,
											overflow = TextOverflow.Ellipsis
										)
									}
									if (customDownloadFolderUri.isNotBlank()) {
										OutlinedButton(
											onClick = {
												viewModel.setCustomDownloadFolderUri("")
												configRefreshKey++
											},
											modifier = Modifier.weight(1f),
											shape = CircleShape
										) {
											Text(
												text = stringResource(R.string.action_reset_default_folder),
												maxLines = 1,
												overflow = TextOverflow.Ellipsis
											)
										}
									}
								}
							}

							HorizontalDivider(
								color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
							)

							// 3. Datensicherung & Export
							val isReducedMotionExport = ApkUpTheme.isReducedMotion
							val exportWaveProgress = remember { Animatable(1f) }
							LaunchedEffect(exportSuccessWaveTick) {
								if (exportSuccessWaveTick > 0 && !isReducedMotionExport) {
									exportWaveProgress.snapTo(0f)
									exportWaveProgress.animateTo(
										targetValue = 1f,
										animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing)
									)
								}
							}
							val exportWaveColor = MaterialTheme.colorScheme.primary
							Column(
								verticalArrangement = Arrangement.spacedBy(8.dp),
								modifier = Modifier
									.clip(RoundedCornerShape(14.dp))
									.drawWithContent {
										drawContent()
										val p = exportWaveProgress.value
										if (!isReducedMotionExport && p < 1f) {
											val sweepX = size.width * (p * 1.4f - 0.2f)
											val alpha = (1f - p).coerceIn(0f, 1f) * 0.24f
											drawRect(
												brush = Brush.linearGradient(
													colors = listOf(
														Color.Transparent,
														exportWaveColor.copy(alpha = alpha),
														Color.Transparent
													),
													start = Offset(sweepX - size.width * 0.2f, 0f),
													end = Offset(sweepX + size.width * 0.2f, size.height)
												)
											)
										}
									}
							) {
								Text(
									text = stringResource(R.string.settings_section_backup_restore),
									style = MaterialTheme.typography.labelLarge,
									fontWeight = FontWeight.Bold,
									color = MaterialTheme.colorScheme.primary
								)
								Text(
									text = stringResource(R.string.settings_backup_description),
									style = MaterialTheme.typography.bodySmall,
									color = MaterialTheme.colorScheme.onSurfaceVariant
								)
								Row(
									modifier = Modifier
										.fillMaxWidth()
										.padding(top = 4.dp),
									horizontalArrangement = Arrangement.spacedBy(10.dp),
									verticalAlignment = CenterVertically
								) {
									OutlinedButton(
										onClick = { exportBackupLauncher.launch("apkup-backup.json") },
										modifier = Modifier.weight(1f),
										shape = CircleShape
									) {
										Icon(
											imageVector = Icons.Default.Share,
											contentDescription = null,
											modifier = Modifier.size(16.dp)
										)
										Spacer(modifier = Modifier.width(6.dp))
										Text(
											text = stringResource(R.string.action_export_backup),
											maxLines = 1,
											overflow = TextOverflow.Ellipsis
										)
									}
									FilledTonalButton(
										onClick = { importBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
										modifier = Modifier.weight(1f),
										shape = CircleShape
									) {
										Icon(
											painter = painterResource(R.drawable.ic_download),
											contentDescription = null,
											modifier = Modifier.size(16.dp)
										)
										Spacer(modifier = Modifier.width(6.dp))
										Text(
											text = stringResource(R.string.action_import_backup),
											maxLines = 1,
											overflow = TextOverflow.Ellipsis
										)
									}
								}
								Row(
									modifier = Modifier
										.fillMaxWidth()
										.padding(top = 4.dp),
									horizontalArrangement = Arrangement.spacedBy(10.dp),
									verticalAlignment = CenterVertically
								) {
									OutlinedButton(
										onClick = { exportObtainiumLauncher.launch("apkup-obtainium-export.json") },
										modifier = Modifier.weight(1f),
										shape = CircleShape
									) {
										Icon(
											imageVector = Icons.Default.Share,
											contentDescription = null,
											modifier = Modifier.size(16.dp)
										)
										Spacer(modifier = Modifier.width(6.dp))
										Text(
											text = stringResource(R.string.action_export_obtainium),
											maxLines = 1,
											overflow = TextOverflow.Ellipsis
										)
									}
									FilledTonalButton(
										onClick = { importObtainiumLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
										modifier = Modifier.weight(1f),
										shape = CircleShape
									) {
										Icon(
											painter = painterResource(R.drawable.ic_download),
											contentDescription = null,
											modifier = Modifier.size(16.dp)
										)
										Spacer(modifier = Modifier.width(6.dp))
										Text(
											text = stringResource(R.string.action_import_obtainium),
											maxLines = 1,
											overflow = TextOverflow.Ellipsis
										)
									}
								}
								OutlinedButton(
									onClick = { importAppWatcherLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
									modifier = Modifier.fillMaxWidth(),
									shape = CircleShape
								) {
									Icon(
										painter = painterResource(R.drawable.ic_download),
										contentDescription = null,
										modifier = Modifier.size(16.dp)
									)
									Spacer(modifier = Modifier.width(6.dp))
									Text(
										text = stringResource(R.string.settings_import_app_watcher_title),
										maxLines = 1,
										overflow = TextOverflow.Ellipsis
									)
								}
							}

							HorizontalDivider(
								color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
							)

							// 4. Diagnose- & Update-Protokoll
							Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
								Text(
									text = stringResource(R.string.settings_section_diagnostics),
									style = MaterialTheme.typography.labelLarge,
									fontWeight = FontWeight.Bold,
									color = MaterialTheme.colorScheme.primary
								)
								Text(
									text = stringResource(R.string.settings_diagnostics_description),
									style = MaterialTheme.typography.bodySmall,
									color = MaterialTheme.colorScheme.onSurfaceVariant
								)
								Row(
									modifier = Modifier
										.fillMaxWidth()
										.clip(RoundedCornerShape(12.dp))
										.clickable { includeLogcatInDiagnostics = !includeLogcatInDiagnostics }
										.padding(vertical = 2.dp),
									verticalAlignment = CenterVertically,
									horizontalArrangement = Arrangement.spacedBy(8.dp)
								) {
									Checkbox(
										checked = includeLogcatInDiagnostics,
										onCheckedChange = { includeLogcatInDiagnostics = it }
									)
									Text(
										text = stringResource(R.string.settings_diagnostics_include_logcat),
										style = MaterialTheme.typography.bodySmall,
										color = MaterialTheme.colorScheme.onSurface
									)
								}
								Row(
									modifier = Modifier
										.fillMaxWidth()
										.padding(top = 4.dp),
									horizontalArrangement = Arrangement.spacedBy(10.dp),
									verticalAlignment = CenterVertically
								) {
									OutlinedButton(
										onClick = { exportDiagnosticLogLauncher.launch("apkup-diagnostic-log.txt") },
										modifier = Modifier.weight(1f),
										shape = CircleShape,
										contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
									) {
										Icon(
											painter = painterResource(R.drawable.ic_download),
											contentDescription = null,
											modifier = Modifier.size(16.dp)
										)
										Spacer(modifier = Modifier.width(6.dp))
										Text(
											text = stringResource(R.string.action_save_diagnostic_log),
											maxLines = 1,
											overflow = TextOverflow.Ellipsis
										)
									}
									FilledTonalButton(
										onClick = {
											if (!isSharingDiagnosticLog) {
												isSharingDiagnosticLog = true
												coroutineScope.launch {
													val shareResult = withContext(Dispatchers.IO) {
														runCatching {
															val report = viewModel.buildDiagnosticLogReport(
																includeLogcat = includeLogcatInDiagnostics
															)
															AppJournal.cleanupDiagnosticLogShareFiles(context)
															val dir = File(
																context.cacheDir,
																AppJournal.DIAGNOSTIC_LOGS_CACHE_DIR
															).apply { mkdirs() }
															val file = File(dir, "apkup-diagnostic-log-${System.currentTimeMillis()}.txt")
															file.writeText(report, Charsets.UTF_8)
															FileProvider.getUriForFile(
																context,
																"${context.packageName}.fileprovider",
																file
															)
														}
													}
													isSharingDiagnosticLog = false
													shareResult.onSuccess { contentUri ->
														runCatching {
															val shareIntent = Intent(Intent.ACTION_SEND).apply {
																type = "text/plain"
																putExtra(Intent.EXTRA_STREAM, contentUri)
																putExtra(
																	Intent.EXTRA_SUBJECT,
																	resources.getString(R.string.diagnostic_log_share_subject)
																)
																addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
															}
															context.startActivity(
																Intent.createChooser(
																	shareIntent,
																	resources.getString(R.string.action_share_diagnostic_log)
																)
															)
														}.onFailure {
															viewModel.notifyDiagnosticLogExported(false)
														}
													}.onFailure {
														viewModel.notifyDiagnosticLogExported(false)
													}
												}
											}
										},
										enabled = !isSharingDiagnosticLog,
										modifier = Modifier.weight(1f),
										shape = CircleShape,
										contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
									) {
										Icon(
											imageVector = Icons.Default.Share,
											contentDescription = null,
											modifier = Modifier.size(16.dp)
										)
										Spacer(modifier = Modifier.width(6.dp))
										Text(
											text = stringResource(R.string.action_share_diagnostic_log),
											maxLines = 1,
											overflow = TextOverflow.Ellipsis
										)
									}
								}
								OutlinedButton(
									onClick = { showSourceHealthSheet = true },
									modifier = Modifier.fillMaxWidth(),
									shape = CircleShape
								) {
									Icon(
										painter = painterResource(R.drawable.ic_refresh),
										contentDescription = null,
										modifier = Modifier.size(16.dp)
									)
									Spacer(modifier = Modifier.width(6.dp))
									Text(
										text = stringResource(R.string.settings_source_health_title),
										maxLines = 1,
										overflow = TextOverflow.Ellipsis
									)
								}
							}
						}
					}
				}
			}
		}
		item(key = "section_about") {
			Column(modifier = Modifier.fillMaxWidth()) {
				SettingsSection(stringResource(R.string.about)) {
					// 1. Unified Hero-Card
					Surface(
						modifier = Modifier.fillMaxWidth(),
						shape = RoundedCornerShape(20.dp),
						color = MaterialTheme.colorScheme.surfaceContainerHigh,
						border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
					) {
						Column(
							modifier = Modifier
								.fillMaxWidth()
								.padding(top = 16.dp, bottom = 14.dp, start = 16.dp, end = 16.dp),
							horizontalAlignment = CenterHorizontally,
							verticalArrangement = Arrangement.spacedBy(4.dp)
						) {
							Box(
								modifier = Modifier
									.size(52.dp)
									.clip(RoundedCornerShape(14.dp))
									.clickable(
										interactionSource = remember { MutableInteractionSource() },
										indication = null
									) {
										val now = System.currentTimeMillis()
										iconTapTimestamps.removeAll { now - it > 2500L }
										iconTapTimestamps.add(now)
										if (iconTapTimestamps.size >= 5) {
											iconTapTimestamps.clear()
											triggerEasterEgg()
										}
									},
								contentAlignment = Alignment.Center
							) {
								Icon(
									painter = painterResource(R.drawable.ic_launcher_background),
									contentDescription = null,
									tint = Color.Unspecified,
									modifier = Modifier.fillMaxSize()
								)
								Icon(
									painter = painterResource(R.drawable.ic_launcher_foreground),
									contentDescription = stringResource(R.string.app_name),
									tint = Color.Unspecified,
									modifier = Modifier.fillMaxSize()
								)
							}
							Spacer(modifier = Modifier.heightIn(min = 2.dp))
							Row(
								verticalAlignment = CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(6.dp)
							) {
								Text(
									text = stringResource(R.string.app_name),
									style = MaterialTheme.typography.titleMedium,
									fontWeight = FontWeight.Bold,
									color = MaterialTheme.colorScheme.onSurface,
									maxLines = 1
								)
								Text(
									text = "•",
									style = MaterialTheme.typography.labelMedium,
									color = MaterialTheme.colorScheme.onSurfaceVariant
								)
								AutoResizeText(
									text = "v" + BuildConfig.VERSION_NAME.removePrefix("v"),
									style = MaterialTheme.typography.labelMedium,
									fontWeight = FontWeight.SemiBold,
									color = MaterialTheme.colorScheme.primary,
									maxLines = 1
								)
							}
							AnimatedContent(
								targetState = isEasterEggActive,
								transitionSpec = {
									(fadeIn(animationSpec = tween(400)) + scaleIn(initialScale = 0.85f, animationSpec = tween(400)))
										.togetherWith(fadeOut(animationSpec = tween(300)) + scaleOut(targetScale = 0.85f, animationSpec = tween(300)))
								},
								label = "AboutEasterEggTransform"
							) { active ->
								if (active) {
									val infiniteTransition = rememberInfiniteTransition(label = "HeartbeatPillPulse")
									val pulseScale by infiniteTransition.animateFloat(
										initialValue = 1.0f,
										targetValue = 1.06f,
										animationSpec = infiniteRepeatable(
											animation = tween(650, easing = FastOutSlowInEasing),
											repeatMode = RepeatMode.Reverse
										),
										label = "HeartbeatPillScale"
									)
									Surface(
										shape = RoundedCornerShape(50),
										color = MaterialTheme.colorScheme.primaryContainer,
										modifier = Modifier
											.scale(pulseScale)
											.padding(vertical = 2.dp)
									) {
										Text(
											text = "n² \u2764\uFE0F\u200D\uD83D\uDD25",
											style = MaterialTheme.typography.labelLarge,
											fontWeight = FontWeight.Bold,
											color = MaterialTheme.colorScheme.onPrimaryContainer,
											modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
										)
									}
								} else {
									Box(
										modifier = Modifier.pointerInput(triggerEasterEgg) {
											awaitEachGesture {
												awaitFirstDown(requireUnconsumed = false)
												val passed = withTimeoutOrNull(2000L) {
													waitForUpOrCancellation()
												}
												if (passed == null) {
													triggerEasterEgg()
													waitForUpOrCancellation()
												}
											}
										},
										contentAlignment = Alignment.Center
									) {
										Text(
											text = stringResource(R.string.about_copyright),
											style = MaterialTheme.typography.bodySmall,
											fontWeight = FontWeight.Medium,
											color = MaterialTheme.colorScheme.onSurface
										)
									}
								}
							}
							Text(
								text = stringResource(R.string.about_tribute),
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
								textAlign = TextAlign.Center,
								modifier = Modifier.padding(horizontal = 4.dp)
							)
						}
					}

					// 2. Kompaktes Bento-Grid (Update prüfen & Was ist neu nebeneinander als flache Row-Kacheln)
					val selfUpdateState by mainViewModel.selfUpdateState.collectAsStateWithLifecycle()
					val isCheckingSelfUpdate = selfUpdateState is SelfUpdateState.Checking
					val isUpToDateSelfUpdate = selfUpdateState is SelfUpdateState.UpToDate
					val isErrorSelfUpdate = selfUpdateState is SelfUpdateState.Error
					val successGreen = Color(0xFF4CAF50)
					val updateTileBorderColor by animateColorAsState(
						targetValue = when {
							isUpToDateSelfUpdate -> successGreen.copy(alpha = 0.65f)
							isErrorSelfUpdate -> MaterialTheme.colorScheme.error.copy(alpha = 0.65f)
							isCheckingSelfUpdate -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
							else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
						},
						animationSpec = tween(250),
						label = "UpdateTileBorderColor"
					)
					val updateIconBgColor by animateColorAsState(
						targetValue = when {
							isUpToDateSelfUpdate -> successGreen.copy(alpha = 0.20f)
							isErrorSelfUpdate -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
							else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
						},
						animationSpec = tween(250),
						label = "UpdateIconBgColor"
					)
					val updateTitleColor by animateColorAsState(
						targetValue = when {
							isUpToDateSelfUpdate -> successGreen
							isErrorSelfUpdate -> MaterialTheme.colorScheme.error
							else -> MaterialTheme.colorScheme.onSurface
						},
						animationSpec = tween(250),
						label = "UpdateTitleColor"
					)

					Row(
						modifier = Modifier
							.fillMaxWidth()
							.height(IntrinsicSize.Min),
						horizontalArrangement = Arrangement.spacedBy(8.dp)
					) {
						Surface(
							onClick = {
								if (!isCheckingSelfUpdate) {
									mainViewModel.checkForSelfUpdate(manual = true, context = context)
								}
							},
							modifier = Modifier
								.weight(1f)
								.fillMaxHeight(),
							shape = RoundedCornerShape(14.dp),
							color = MaterialTheme.colorScheme.surfaceContainerHigh,
							border = BorderStroke(1.dp, updateTileBorderColor)
						) {
							Row(
								modifier = Modifier
									.fillMaxSize()
									.padding(horizontal = 10.dp, vertical = 8.dp),
								verticalAlignment = CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(8.dp)
							) {
								Box(
									modifier = Modifier
										.size(28.dp)
										.clip(RoundedCornerShape(8.dp))
										.background(updateIconBgColor),
									contentAlignment = Alignment.Center
								) {
									AnimatedContent(
										targetState = when {
											isCheckingSelfUpdate -> 1
											isUpToDateSelfUpdate -> 2
											isErrorSelfUpdate -> 3
											else -> 0
										},
										transitionSpec = {
											(fadeIn(tween(220)) + scaleIn(initialScale = 0.7f, animationSpec = tween(220)))
												.togetherWith(fadeOut(tween(180)) + scaleOut(targetScale = 0.7f, animationSpec = tween(180)))
										},
										label = "SelfUpdateBentoIcon"
									) { iconState ->
										when (iconState) {
											1 -> {
												val spinTransition = rememberInfiniteTransition(label = "SelfUpdateRefreshSpin")
												val rotation by spinTransition.animateFloat(
													initialValue = 0f,
													targetValue = 360f,
													animationSpec = infiniteRepeatable(
														animation = tween(durationMillis = 850, easing = LinearEasing),
														repeatMode = RepeatMode.Restart
													),
													label = "SelfUpdateRotationAngle"
												)
												Icon(
													painter = painterResource(R.drawable.ic_refresh),
													contentDescription = null,
													tint = MaterialTheme.colorScheme.primary,
													modifier = Modifier
														.size(15.dp)
														.graphicsLayer { rotationZ = rotation }
												)
											}
											2 -> {
												Icon(
													imageVector = Icons.Default.Check,
													contentDescription = null,
													tint = successGreen,
													modifier = Modifier.size(16.dp)
												)
											}
											3 -> {
												Icon(
													imageVector = Icons.Default.Warning,
													contentDescription = null,
													tint = MaterialTheme.colorScheme.error,
													modifier = Modifier.size(15.dp)
												)
											}
											else -> {
												Icon(
													painter = painterResource(R.drawable.ic_refresh),
													contentDescription = null,
													tint = MaterialTheme.colorScheme.primary,
													modifier = Modifier.size(15.dp)
												)
											}
										}
									}
								}
								Column(
									modifier = Modifier.weight(1f),
									verticalArrangement = Arrangement.spacedBy(1.dp)
								) {
									Text(
										text = when {
											isCheckingSelfUpdate -> stringResource(R.string.self_update_bento_checking_title)
											isUpToDateSelfUpdate -> stringResource(R.string.self_update_bento_uptodate_title)
											isErrorSelfUpdate -> stringResource(R.string.self_update_bento_error_title)
											else -> stringResource(R.string.self_update_bento_title)
										},
										style = MaterialTheme.typography.labelMedium,
										fontWeight = FontWeight.SemiBold,
										color = updateTitleColor,
										maxLines = 1,
										overflow = TextOverflow.Ellipsis
									)
									Text(
										text = when {
											isCheckingSelfUpdate -> stringResource(R.string.self_update_bento_checking_subtitle)
											isUpToDateSelfUpdate -> stringResource(R.string.self_update_bento_uptodate_subtitle)
											isErrorSelfUpdate -> stringResource(R.string.self_update_bento_error_subtitle)
											else -> stringResource(R.string.self_update_bento_subtitle)
										},
										style = MaterialTheme.typography.labelSmall,
										color = MaterialTheme.colorScheme.onSurfaceVariant,
										maxLines = 1,
										overflow = TextOverflow.Ellipsis
									)
								}
							}
						}

						Surface(
							onClick = { showChangelogSheet = true },
							modifier = Modifier
								.weight(1f)
								.fillMaxHeight(),
							shape = RoundedCornerShape(14.dp),
							color = MaterialTheme.colorScheme.surfaceContainerHigh,
							border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
						) {
							Row(
								modifier = Modifier
									.fillMaxSize()
									.padding(horizontal = 10.dp, vertical = 8.dp),
								verticalAlignment = CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(8.dp)
							) {
								Box(
									modifier = Modifier
										.size(28.dp)
										.clip(RoundedCornerShape(8.dp))
										.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)),
									contentAlignment = Alignment.Center
								) {
									Icon(
										painter = painterResource(R.drawable.ic_changelog),
										contentDescription = null,
										tint = MaterialTheme.colorScheme.primary,
										modifier = Modifier.size(15.dp)
									)
								}
								Column(
									modifier = Modifier.weight(1f),
									verticalArrangement = Arrangement.spacedBy(1.dp)
								) {
									Text(
										text = stringResource(R.string.changelog_bento_title),
										style = MaterialTheme.typography.labelMedium,
										fontWeight = FontWeight.SemiBold,
										color = MaterialTheme.colorScheme.onSurface,
										maxLines = 1,
										overflow = TextOverflow.Ellipsis
									)
									Text(
										text = stringResource(R.string.changelog_bento_subtitle),
										style = MaterialTheme.typography.labelSmall,
										color = MaterialTheme.colorScheme.onSurfaceVariant,
										maxLines = 1,
										overflow = TextOverflow.Ellipsis
									)
								}
							}
						}
					}

					// 3a. Kompakte Opt-In-Zeile für ApkUp Beta-/Vorab-Updates (direkt unter Update prüfen / Was ist neu)
					var includeSelfUpdatePreReleases by remember(configRefreshKey) {
						mutableStateOf(viewModel.getIncludeSelfUpdatePreReleases())
					}
					var showSelfUpdateBetaInfoDialog by remember { mutableStateOf(false) }
					Surface(
						modifier = Modifier.fillMaxWidth(),
						shape = RoundedCornerShape(14.dp),
						color = MaterialTheme.colorScheme.surfaceContainerHigh,
						border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
					) {
						Row(
							modifier = Modifier
								.fillMaxWidth()
								.clip(RoundedCornerShape(14.dp))
								.toggleable(
									value = includeSelfUpdatePreReleases,
									role = Role.Switch,
									onValueChange = { enabled ->
										viewModel.setIncludeSelfUpdatePreReleases(enabled)
										includeSelfUpdatePreReleases = enabled
									}
								)
								.padding(horizontal = 10.dp, vertical = 7.dp),
							verticalAlignment = CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(8.dp)
						) {
							Box(
								modifier = Modifier
									.size(28.dp)
									.clip(RoundedCornerShape(8.dp))
									.background(
										if (includeSelfUpdatePreReleases) {
											MaterialTheme.colorScheme.tertiaryContainer
										} else {
											MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
										}
									),
								contentAlignment = Alignment.Center
							) {
								Icon(
									painter = painterResource(R.drawable.ic_refresh),
									contentDescription = null,
									tint = if (includeSelfUpdatePreReleases) {
										MaterialTheme.colorScheme.onTertiaryContainer
									} else {
										MaterialTheme.colorScheme.primary
									},
									modifier = Modifier.size(15.dp)
								)
							}
							Column(
								modifier = Modifier.weight(1f),
								verticalArrangement = Arrangement.spacedBy(1.dp)
							) {
								Text(
									text = stringResource(R.string.self_update_prerelease_title),
									style = MaterialTheme.typography.labelMedium,
									fontWeight = FontWeight.SemiBold,
									color = MaterialTheme.colorScheme.onSurface,
									maxLines = 1,
									overflow = TextOverflow.Ellipsis
								)
								Text(
									text = stringResource(R.string.self_update_prerelease_subtitle_short),
									style = MaterialTheme.typography.labelSmall,
									color = MaterialTheme.colorScheme.onSurfaceVariant,
									maxLines = 1,
									overflow = TextOverflow.Ellipsis
								)
							}
							Box(
								modifier = Modifier
									.size(40.dp)
									.clip(CircleShape)
									.clickable { showSelfUpdateBetaInfoDialog = true },
								contentAlignment = Alignment.Center
							) {
								Icon(
									painter = painterResource(R.drawable.ic_info),
									contentDescription = stringResource(R.string.self_update_prerelease_title),
									tint = MaterialTheme.colorScheme.onSurfaceVariant,
									modifier = Modifier.size(16.dp)
								)
							}
							Switch(
								checked = includeSelfUpdatePreReleases,
								onCheckedChange = null,
								thumbContent = {
									Icon(
										imageVector = if (includeSelfUpdatePreReleases) Icons.Default.Check else Icons.Default.Close,
										contentDescription = null,
										modifier = Modifier.size(SwitchDefaults.IconSize)
									)
								}
							)
						}
					}

					if (showSelfUpdateBetaInfoDialog) {
						AlertDialog(
							onDismissRequest = { showSelfUpdateBetaInfoDialog = false },
							shape = RoundedCornerShape(24.dp),
							containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
							title = {
								Text(
									text = stringResource(R.string.self_update_prerelease_title),
									style = MaterialTheme.typography.titleMedium,
									fontWeight = FontWeight.Bold
								)
							},
							text = {
								Text(
									text = stringResource(R.string.self_update_prerelease_subtitle),
									style = MaterialTheme.typography.bodyMedium,
									color = MaterialTheme.colorScheme.onSurfaceVariant
								)
							},
							confirmButton = {
								TextButton(onClick = { showSelfUpdateBetaInfoDialog = false }) {
									Text(stringResource(android.R.string.ok))
								}
							}
						)
					}

					// 3b. Zweite Bento-Zeile (GitHub & Hilfe/Wiki nebeneinander unter der Beta-Karte)
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.height(IntrinsicSize.Min),
						horizontalArrangement = Arrangement.spacedBy(8.dp)
					) {
						Surface(
							onClick = { openCustomTab("https://github.com/DurchEins/apkup") },
							modifier = Modifier
								.weight(1f)
								.fillMaxHeight(),
							shape = RoundedCornerShape(14.dp),
							color = MaterialTheme.colorScheme.surfaceContainerHigh,
							border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
						) {
							Row(
								modifier = Modifier
									.fillMaxSize()
									.padding(horizontal = 10.dp, vertical = 8.dp),
								verticalAlignment = CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(8.dp)
							) {
								Box(
									modifier = Modifier
										.size(28.dp)
										.clip(RoundedCornerShape(8.dp))
										.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)),
									contentAlignment = Alignment.Center
								) {
									Icon(
										painter = painterResource(R.drawable.ic_github),
										contentDescription = null,
										tint = MaterialTheme.colorScheme.primary,
										modifier = Modifier.size(15.dp)
									)
								}
								Column(
									modifier = Modifier.weight(1f),
									verticalArrangement = Arrangement.spacedBy(1.dp)
								) {
									Text(
										text = stringResource(R.string.about_github_bento_short_title),
										style = MaterialTheme.typography.labelMedium,
										fontWeight = FontWeight.SemiBold,
										color = MaterialTheme.colorScheme.onSurface,
										maxLines = 1,
										overflow = TextOverflow.Ellipsis
									)
									Text(
										text = stringResource(R.string.about_github_bento_short_subtitle),
										style = MaterialTheme.typography.labelSmall,
										color = MaterialTheme.colorScheme.onSurfaceVariant,
										maxLines = 1,
										overflow = TextOverflow.Ellipsis
									)
								}
								Icon(
									painter = painterResource(R.drawable.ic_open_in_new),
									contentDescription = null,
									tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
									modifier = Modifier.size(13.dp)
								)
							}
						}

						Surface(
							onClick = { openCustomTab("https://durcheins.github.io/apkup/wiki/") },
							modifier = Modifier
								.weight(1f)
								.fillMaxHeight(),
							shape = RoundedCornerShape(14.dp),
							color = MaterialTheme.colorScheme.surfaceContainerHigh,
							border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
						) {
							Row(
								modifier = Modifier
									.fillMaxSize()
									.padding(horizontal = 10.dp, vertical = 8.dp),
								verticalAlignment = CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(8.dp)
							) {
								Box(
									modifier = Modifier
										.size(28.dp)
										.clip(RoundedCornerShape(8.dp))
										.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)),
									contentAlignment = Alignment.Center
								) {
									Icon(
										painter = painterResource(R.drawable.ic_info),
										contentDescription = null,
										tint = MaterialTheme.colorScheme.primary,
										modifier = Modifier.size(15.dp)
									)
								}
								Column(
									modifier = Modifier.weight(1f),
									verticalArrangement = Arrangement.spacedBy(1.dp)
								) {
									Text(
										text = stringResource(R.string.about_wiki_bento_title),
										style = MaterialTheme.typography.labelMedium,
										fontWeight = FontWeight.SemiBold,
										color = MaterialTheme.colorScheme.onSurface,
										maxLines = 1,
										overflow = TextOverflow.Ellipsis
									)
									Text(
										text = stringResource(R.string.about_wiki_bento_subtitle),
										style = MaterialTheme.typography.labelSmall,
										color = MaterialTheme.colorScheme.onSurfaceVariant,
										maxLines = 1,
										overflow = TextOverflow.Ellipsis
									)
								}
								Icon(
									painter = painterResource(R.drawable.ic_open_in_new),
									contentDescription = null,
									tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
									modifier = Modifier.size(13.dp)
								)
							}
						}
					}

					// 3c. Dezente Vollbreite-Support-Karte (Buy Me a Coffee, Coindrop, GitHub/GitLab Stars & Store-Reviews)
					Surface(
						onClick = { showSupportCommunitySheet = true },
						modifier = Modifier.fillMaxWidth(),
						shape = RoundedCornerShape(14.dp),
						color = MaterialTheme.colorScheme.surfaceContainerHigh,
						border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
					) {
						Row(
							modifier = Modifier
								.fillMaxWidth()
								.padding(horizontal = 12.dp, vertical = 10.dp),
							verticalAlignment = CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(10.dp)
						) {
							Box(
								modifier = Modifier
									.size(28.dp)
									.clip(RoundedCornerShape(8.dp))
									.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)),
								contentAlignment = Alignment.Center
							) {
								Icon(
									imageVector = Icons.Rounded.Favorite,
									contentDescription = null,
									tint = MaterialTheme.colorScheme.primary,
									modifier = Modifier.size(15.dp)
								)
							}
							Column(
								modifier = Modifier.weight(1f),
								verticalArrangement = Arrangement.spacedBy(1.dp)
							) {
								Text(
									text = stringResource(R.string.about_support_bento_title),
									style = MaterialTheme.typography.labelMedium,
									fontWeight = FontWeight.SemiBold,
									color = MaterialTheme.colorScheme.onSurface,
									maxLines = 1,
									overflow = TextOverflow.Ellipsis
								)
								Text(
									text = stringResource(R.string.about_support_bento_subtitle),
									style = MaterialTheme.typography.labelSmall,
									color = MaterialTheme.colorScheme.onSurfaceVariant,
									maxLines = 1,
									overflow = TextOverflow.Ellipsis
								)
							}
							Icon(
								imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
								contentDescription = null,
								tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
								modifier = Modifier.size(16.dp)
							)
						}
					}

					if (showSupportCommunitySheet) {
						com.apkup.ui.component.SupportCommunityBottomSheet(
							onDismiss = { showSupportCommunitySheet = false }
						)
					}

					// 4. Dezenter rahmenloser Footer-Link für das rumboalla-Originalprojekt
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.clip(RoundedCornerShape(10.dp))
							.clickable { openCustomTab("https://github.com/rumboalla/apkupdater") }
							.padding(horizontal = 8.dp, vertical = 6.dp),
						verticalAlignment = CenterVertically,
						horizontalArrangement = Arrangement.Center
					) {
						Icon(
							painter = painterResource(R.drawable.ic_github),
							contentDescription = null,
							tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
							modifier = Modifier.size(12.dp)
						)
						Spacer(modifier = Modifier.width(6.dp))
						Text(
							text = stringResource(R.string.about_original_project),
							style = MaterialTheme.typography.labelSmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
							textAlign = TextAlign.Center
						)
						Spacer(modifier = Modifier.width(4.dp))
						Icon(
							painter = painterResource(R.drawable.ic_open_in_new),
							contentDescription = null,
							tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
							modifier = Modifier.size(11.dp)
						)
					}
				}

				// Easter-Egg: Im Ruhezustand unsichtbar (0.dp, alpha = 0f), fährt erst per Overscroll-Pull direkt unter der "Über"-Karte aus
				val revealHeight = (72.dp * pullProgress)
				Box(
					modifier = Modifier
						.fillMaxWidth()
						.height(revealHeight)
						.graphicsLayer {
							alpha = (pullProgress * 1.15f).coerceIn(0f, 1f)
							scaleX = 0.85f + 0.15f * pullProgress
							scaleY = 0.85f + 0.15f * pullProgress
						},
					contentAlignment = Alignment.Center
				) {
					if (pullProgress > 0.005f) {
						Column(
							modifier = Modifier
								.fillMaxWidth()
								.padding(top = 12.dp),
							horizontalAlignment = CenterHorizontally,
							verticalArrangement = Arrangement.spacedBy(4.dp)
						) {
							Text(
								text = "n² \u2764\uFE0F\u200D\uD83D\uDD25",
								style = MaterialTheme.typography.labelMedium,
								fontWeight = FontWeight.SemiBold,
								color = MaterialTheme.colorScheme.onSurface
							)
							Surface(
								shape = RoundedCornerShape(50),
								color = MaterialTheme.colorScheme.surfaceContainerHighest
							) {
								Text(
									text = "Version: Für immer • Since 2021",
									style = MaterialTheme.typography.labelSmall,
									color = MaterialTheme.colorScheme.onSurfaceVariant,
									modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
								)
							}
						}
					}
				}
			}
		}
	}

	if (showHiddenAppsSheet) {
		val installedApps = appsViewModel.state().collectAsStateWithLifecycle().value.apps()
		HiddenAppsBottomSheet(
			ignoredPackages = ignoredApps,
			installedApps = installedApps,
			onRestoreSelected = { selected ->
				viewModel.unignoreApps(selected, appsViewModel, updatesViewModel)
			},
			onHideMore = { toHide ->
				viewModel.ignoreApps(toHide, appsViewModel, updatesViewModel)
			},
			onDismiss = { showHiddenAppsSheet = false }
		)
	}

	if (showIgnoredVersionsSheet) {
		IgnoredVersionsBottomSheet(
			entries = ignoredVersions,
			onUnignore = { id ->
				viewModel.unignoreVersion(id)
				updatesViewModel.syncIgnoredFromPrefs()
			},
			onClearAll = {
				viewModel.clearAllIgnoredVersions()
				updatesViewModel.syncIgnoredFromPrefs()
			},
			onDismiss = { showIgnoredVersionsSheet = false }
		)
	}

	if (showShizukuSheet) {
		ShizukuSetupBottomSheet(
			status = shizukuStatus,
			onDismiss = { viewModel.dismissShizukuSetupSheet() },
			onOpenPlayStore = {
				val marketIntent = Intent(
					Intent.ACTION_VIEW,
					"market://details?id=${SessionInstaller.SHIZUKU_PACKAGE_NAME}".toUri()
				).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
				runCatching { context.startActivity(marketIntent) }
					.onFailure {
						openCustomTab("https://play.google.com/store/apps/details?id=${SessionInstaller.SHIZUKU_PACKAGE_NAME}")
					}
			},
			onOpenShizukuApp = {
				context.packageManager
					.getLaunchIntentForPackage(SessionInstaller.SHIZUKU_PACKAGE_NAME)
					?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
					?.let { launchIntent ->
						runCatching { context.startActivity(launchIntent) }
					}
			},
			onRequestPermission = {
				viewModel.requestShizukuPermissionFromSheet()
			},
			onRecheck = {
				viewModel.refreshShizukuStatus()
			}
		)
	}

	if (showRootSetupSheet) {
		RootSetupBottomSheet(
			rootEnabled = rootEnabled,
			rootAvailable = rootAvailable,
			onRecheck = {
				viewModel.refreshRootStatus()
			},
			onToggleRoot = { enable ->
				viewModel.setUseRootInstaller(enable)
				configRefreshKey++
			},
			onDismiss = { showRootSetupSheet = false }
		)
	}

	if (showIntervalSheet) {
		BackgroundCheckIntervalBottomSheet(
			selectedHours = viewModel.getBackgroundCheckIntervalHours(),
			onSelectHours = { hours ->
				viewModel.setBackgroundCheckIntervalHours(hours)
				configRefreshKey++
			},
			onDismiss = { showIntervalSheet = false }
		)
	}

	if (showApkMirrorSheet) {
		ApkMirrorSettingsBottomSheet(
			directEnabled = viewModel.getDirectApkMirrorDownloads(),
			onDirectChanged = {
				viewModel.setDirectApkMirrorDownloads(it)
				configRefreshKey++
			},
			onDismiss = { showApkMirrorSheet = false }
		)
	}

	if (showFDroidReposSheet) {
		FDroidReposBottomSheet(
			repos = fdroidRepos,
			onToggleRepo = { repoId, enabled ->
				viewModel.setFDroidRepoEnabled(repoId, enabled)
				configRefreshKey++
			},
			onAddCustomRepo = { rawUrl, explicitName, explicitFingerprint ->
				val res = viewModel.addFDroidRepoConfig(rawUrl, explicitName, explicitFingerprint)
				if (res.isSuccess) {
					configRefreshKey++
				}
				res
			},
			onRemoveCustomRepo = { repoId ->
				viewModel.removeFDroidRepoConfig(repoId)
				configRefreshKey++
			},
			onDismiss = { showFDroidReposSheet = false }
		)
	}

	if (showSourcePrioritySheet) {
		com.apkup.ui.component.SourcePriorityBottomSheet(
			order = sourcePriorityOrder,
			onMove = { index, delta -> viewModel.moveSourcePriority(index, delta) },
			onReset = { viewModel.resetSourcePriorityOrder() },
			onDismiss = { showSourcePrioritySheet = false }
		)
	}

	if (showSourceHealthSheet) {
		com.apkup.ui.component.SourceHealthBottomSheet(
			onDismiss = { showSourceHealthSheet = false }
		)
	}

	if (showPreReleaseSheet) {
		com.apkup.ui.component.PreReleaseBottomSheet(
			enabled = viewModel.getIncludePreReleasesGlobal(),
			selectedChannels = viewModel.getAllowedPreReleaseChannels(),
			onToggleEnabled = { enabled ->
				viewModel.setIncludePreReleasesGlobal(enabled)
				configRefreshKey++
			},
			onToggleChannel = { channel, active ->
				viewModel.setPreReleaseChannelEnabled(channel, active)
				configRefreshKey++
			},
			onDismiss = { showPreReleaseSheet = false }
		)
	}

	if (showUpdateWindowSheet) {
		com.apkup.ui.component.UpdateWindowBottomSheet(
			enabled = viewModel.getAutoUpdateWindowEnabled(),
			mode = viewModel.getAutoUpdateWindowMode(),
			startMinutes = viewModel.getAutoUpdateWindowStartMinutes(),
			endMinutes = viewModel.getAutoUpdateWindowEndMinutes(),
			onToggleEnabled = { enabled ->
				viewModel.setAutoUpdateWindowEnabled(enabled)
				configRefreshKey++
			},
			onModeChanged = { newMode ->
				viewModel.setAutoUpdateWindowMode(newMode)
				configRefreshKey++
			},
			onStartMinutesChanged = { mins ->
				viewModel.setAutoUpdateWindowStartMinutes(mins)
				configRefreshKey++
			},
			onEndMinutesChanged = { mins ->
				viewModel.setAutoUpdateWindowEndMinutes(mins)
				configRefreshKey++
			},
			onDismiss = { showUpdateWindowSheet = false }
		)
	}

	if (showRollbackSettingsSheet) {
		com.apkup.ui.component.RollbackSettingsBottomSheet(
			enabled = viewModel.getEnableRollbackBackups(),
			budgetMb = viewModel.getRollbackMaxBudgetMb(),
			maxAgeDays = viewModel.getRollbackMaxAgeDays(),
			backupCount = rollbackStats.first,
			backupTotalBytes = rollbackStats.second,
			onToggleEnabled = { enabled ->
				viewModel.setEnableRollbackBackups(enabled)
				configRefreshKey++
			},
			onBudgetSelected = { budgetMb ->
				viewModel.setRollbackMaxBudgetMb(budgetMb) { count, bytes ->
					rollbackStats = count to bytes
				}
				configRefreshKey++
			},
			onMaxAgeSelected = { maxAgeDays ->
				viewModel.setRollbackMaxAgeDays(maxAgeDays) { count, bytes ->
					rollbackStats = count to bytes
				}
				configRefreshKey++
			},
			onClearAllBackups = {
				viewModel.clearAllRollbackBackups { count, bytes ->
					rollbackStats = count to bytes
					configRefreshKey++
				}
			},
			onDismiss = { showRollbackSettingsSheet = false }
		)
	}

	if (showWifiDownloadSheet) {
		WifiDownloadBottomSheet(
			wifiOnly = viewModel.getDownloadWifiOnly(),
			onWifiOnlyChanged = {
				viewModel.setDownloadWifiOnly(it)
				configRefreshKey++
			},
			chargingOnly = viewModel.getDownloadChargingOnly(),
			onChargingOnlyChanged = {
				viewModel.setDownloadChargingOnly(it)
				configRefreshKey++
			},
			bgCheckWifiOnly = viewModel.getBackgroundCheckWifiOnly(),
			onBgCheckWifiOnlyChanged = {
				viewModel.setBackgroundCheckWifiOnly(it)
				configRefreshKey++
			},
			onDismiss = { showWifiDownloadSheet = false }
		)
	}

	if (showChangelogSheet) {
		ChangelogBottomSheet(
			onDismiss = {
				showChangelogSheet = false
				mainViewModel.dismissChangelogSheet(BuildConfig.VERSION_CODE.toLong())
			},
			onOpenSupport = {
				showChangelogSheet = false
				mainViewModel.dismissChangelogSheet(BuildConfig.VERSION_CODE.toLong())
				showSupportCommunitySheet = true
			}
		)
	}

	if (showGitHubTokenDialog) {
		val initialGitHubToken = viewModel.getGitHubToken()
		ApiTokenBottomSheet(
			iconRes = R.drawable.ic_github,
			title = stringResource(R.string.source_github),
			statusSubtitle = if (initialGitHubToken.isNotEmpty()) {
				stringResource(R.string.github_token_status_set)
			} else {
				stringResource(R.string.settings_source_github_desc)
			},
			description = stringResource(R.string.github_token_dialog_desc),
			placeholder = stringResource(R.string.github_token_dialog_hint),
			initialToken = initialGitHubToken,
			createTokenUrl = "https://github.com/settings/tokens/new?description=ApkUp&scopes=",
			createTokenButtonText = stringResource(R.string.token_action_generate_github),
			guideUrl = "https://github.com/DurchEins/apkup/wiki/Configuring-GitHub-&-GitLab-Tokens",
			onDismiss = { showGitHubTokenDialog = false },
			onVerifyToken = { raw -> viewModel.validateGitHubToken(raw) },
			onVerifiedMessage = { msg -> viewModel.notifyTextSnack(msg, com.apkup.data.snack.SnackVisualType.SUCCESS) },
			onSaveToken = { token ->
				viewModel.setGitHubToken(token)
				configRefreshKey++
			}
		)
	}

	if (showGitLabTokenDialog) {
		val initialGitLabToken = viewModel.getGitLabToken()
		ApiTokenBottomSheet(
			iconRes = R.drawable.ic_gitlab,
			title = stringResource(R.string.source_gitlab),
			statusSubtitle = if (initialGitLabToken.isNotEmpty()) {
				stringResource(R.string.gitlab_token_status_set)
			} else {
				stringResource(R.string.settings_source_gitlab_desc)
			},
			description = stringResource(R.string.gitlab_token_dialog_desc),
			placeholder = stringResource(R.string.gitlab_token_dialog_hint),
			initialToken = initialGitLabToken,
			createTokenUrl = "https://gitlab.com/-/user_settings/personal_access_tokens?name=ApkUp&scopes=read_api",
			createTokenButtonText = stringResource(R.string.token_action_generate_gitlab),
			guideUrl = "https://github.com/DurchEins/apkup/wiki/Configuring-GitHub-&-GitLab-Tokens",
			onDismiss = { showGitLabTokenDialog = false },
			onVerifyToken = { raw -> viewModel.validateGitLabToken(raw) },
			onVerifiedMessage = { msg -> viewModel.notifyTextSnack(msg, com.apkup.data.snack.SnackVisualType.SUCCESS) },
			onSaveToken = { token ->
				viewModel.setGitLabToken(token)
				configRefreshKey++
			}
		)
	}

	if (showCodebergTokenDialog) {
		val initialCodebergToken = viewModel.getCodebergToken()
		ApiTokenBottomSheet(
			iconRes = R.drawable.ic_codeberg,
			title = stringResource(R.string.source_codeberg),
			statusSubtitle = if (initialCodebergToken.isNotEmpty()) {
				stringResource(R.string.codeberg_token_status_set)
			} else {
				stringResource(R.string.settings_source_codeberg_desc)
			},
			description = stringResource(R.string.codeberg_token_dialog_desc),
			placeholder = stringResource(R.string.codeberg_token_dialog_hint),
			initialToken = initialCodebergToken,
			createTokenUrl = "https://codeberg.org/user/settings/applications",
			createTokenButtonText = stringResource(R.string.token_action_generate_codeberg),
			guideUrl = "https://github.com/DurchEins/apkup/wiki/Configuring-GitHub-&-GitLab-Tokens",
			onDismiss = { showCodebergTokenDialog = false },
			onVerifyToken = { raw -> viewModel.validateCodebergToken(raw) },
			onVerifiedMessage = { msg -> viewModel.notifyTextSnack(msg, com.apkup.data.snack.SnackVisualType.SUCCESS) },
			onSaveToken = { token ->
				viewModel.setCodebergToken(token)
				configRefreshKey++
			}
		)
	}

	obtainiumImportPreview?.let { preview ->
		ObtainiumImportBottomSheet(
			preview = preview,
			onDismiss = { obtainiumImportPreview = null },
			onConfirmImport = { importMappings, importFDroidRepos, selectedWatchedPackages ->
				viewModel.applyObtainiumImport(
					preview = preview,
					importInstalledMappings = importMappings,
					importFDroidRepos = importFDroidRepos,
					selectedWatchedPackages = selectedWatchedPackages
				)
				if (selectedWatchedPackages.isNotEmpty()) {
					appsViewModel.refresh(false)
				}
				configRefreshKey++
				obtainiumImportPreview = null
			}
		)
	}
}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApiTokenBottomSheet(
	iconRes: Int,
	title: String,
	statusSubtitle: String,
	description: String,
	placeholder: String,
	initialToken: String,
	createTokenUrl: String? = null,
	createTokenButtonText: String? = null,
	guideUrl: String? = null,
	onDismiss: () -> Unit,
	onVerifyToken: (suspend (String) -> TokenValidationResult)? = null,
	onVerifiedMessage: ((String) -> Unit)? = null,
	onSaveToken: (String) -> Unit
) {
	val resources = LocalResources.current
	val uriHandler = LocalUriHandler.current
	val coroutineScope = rememberCoroutineScope()
	val sheetState = rememberExpandedModalSheetState()
	val tokenState = rememberTextFieldState(initialText = initialToken)
	var isTokenVisible by remember { mutableStateOf(false) }
	var isVerifying by remember { mutableStateOf(false) }
	var isSaving by remember { mutableStateOf(false) }
	var validationResult by remember { mutableStateOf<TokenValidationResult?>(null) }
	var validatedTokenValue by remember { mutableStateOf<String?>(null) }
	var isGuideExpanded by rememberSaveable { mutableStateOf(initialToken.isBlank()) }

	LaunchedEffect(tokenState.text) {
		if (tokenState.text.toString().trim() != validatedTokenValue) {
			validationResult = null
		}
	}

	fun formatValidationMessage(result: TokenValidationResult): String {
		return when (result) {
			is TokenValidationResult.Valid -> {
				if (!result.rateLimitRemaining.isNullOrBlank() && !result.rateLimitTotal.isNullOrBlank()) {
					resources.getString(
						R.string.token_validation_valid_with_limit,
						result.username,
						result.rateLimitRemaining,
						result.rateLimitTotal
					)
				} else {
					resources.getString(R.string.token_validation_valid_user, result.username)
				}
			}
			is TokenValidationResult.Invalid -> {
				resources.getString(R.string.token_validation_invalid, result.httpCode)
			}
			is TokenValidationResult.Error -> {
				resources.getString(R.string.token_validation_error, result.message)
			}
		}
	}

	ModalBottomSheet(
		onDismissRequest = { if (!isSaving) onDismiss() },
		sheetState = sheetState,
		containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
		contentColor = MaterialTheme.colorScheme.onSurface,
		dragHandle = {
			Box(
				modifier = Modifier
					.padding(vertical = 12.dp)
					.width(36.dp)
					.height(4.dp)
					.clip(CircleShape)
					.background(MaterialTheme.colorScheme.outlineVariant)
			)
		}
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.verticalScroll(rememberScrollState())
				.navigationBarsPadding()
				.imePadding()
				.padding(horizontal = 24.dp)
				.padding(bottom = 28.dp),
			verticalArrangement = Arrangement.spacedBy(14.dp)
		) {
			Row(
				verticalAlignment = CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(12.dp)
			) {
				Box(
					modifier = Modifier
						.size(40.dp)
						.clip(RoundedCornerShape(12.dp))
						.background(MaterialTheme.colorScheme.surfaceContainerHigh),
					contentAlignment = Alignment.Center
				) {
					Icon(
						painter = painterResource(iconRes),
						contentDescription = null,
						tint = MaterialTheme.colorScheme.primary,
						modifier = Modifier.size(22.dp)
					)
				}
				Column {
					Text(
						text = title,
						style = MaterialTheme.typography.titleMedium,
						fontWeight = FontWeight.Bold
					)
					Text(
						text = statusSubtitle,
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
			}

			Text(
				text = description,
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)

			if (!createTokenUrl.isNullOrBlank() || !guideUrl.isNullOrBlank()) {
				Surface(
					shape = RoundedCornerShape(16.dp),
					color = MaterialTheme.colorScheme.surfaceContainer,
					modifier = Modifier.fillMaxWidth()
				) {
					Column(
						modifier = Modifier
							.fillMaxWidth()
							.padding(14.dp),
						verticalArrangement = Arrangement.spacedBy(8.dp)
					) {
						Row(
							modifier = Modifier
								.fillMaxWidth()
								.clickable { isGuideExpanded = !isGuideExpanded },
							horizontalArrangement = Arrangement.SpaceBetween,
							verticalAlignment = CenterVertically
						) {
							Row(
								verticalAlignment = CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(8.dp)
							) {
								Icon(
									imageVector = Icons.Default.Info,
									contentDescription = null,
									tint = MaterialTheme.colorScheme.primary,
									modifier = Modifier.size(18.dp)
								)
								Text(
									text = stringResource(R.string.token_guide_header),
									style = MaterialTheme.typography.labelLarge,
									fontWeight = FontWeight.SemiBold,
									color = MaterialTheme.colorScheme.onSurface
								)
							}
							Icon(
								imageVector = if (isGuideExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
								contentDescription = null,
								tint = MaterialTheme.colorScheme.primary,
								modifier = Modifier.size(18.dp)
							)
						}

						AnimatedVisibility(visible = isGuideExpanded) {
							val tokenCustomTab = com.apkup.ui.component.rememberCustomTabOpener()
							Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
								Text(
									text = stringResource(R.string.token_guide_hint),
									style = MaterialTheme.typography.bodySmall,
									color = MaterialTheme.colorScheme.onSurfaceVariant
								)
								Row(
									modifier = Modifier.fillMaxWidth(),
									horizontalArrangement = Arrangement.spacedBy(8.dp)
								) {
									if (!createTokenUrl.isNullOrBlank() && !createTokenButtonText.isNullOrBlank()) {
										FilledTonalButton(
											onClick = { tokenCustomTab(createTokenUrl) },
											modifier = Modifier.weight(1f),
											shape = RoundedCornerShape(14.dp),
											contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
										) {
											Text(
												text = createTokenButtonText,
												style = MaterialTheme.typography.labelSmall,
												fontWeight = FontWeight.SemiBold,
												maxLines = 1,
												overflow = TextOverflow.Ellipsis
											)
										}
									}
									if (!guideUrl.isNullOrBlank()) {
										OutlinedButton(
											onClick = { tokenCustomTab(guideUrl) },
											modifier = Modifier.weight(1f),
											shape = RoundedCornerShape(14.dp),
											contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
										) {
											Text(
												text = stringResource(R.string.token_action_guide_wiki),
												style = MaterialTheme.typography.labelSmall,
												maxLines = 1,
												overflow = TextOverflow.Ellipsis
											)
										}
									}
								}
							}
						}
					}
				}
			}

			Surface(
				shape = RoundedCornerShape(16.dp),
				color = MaterialTheme.colorScheme.surfaceContainer,
				modifier = Modifier.fillMaxWidth()
			) {
				Column(
					modifier = Modifier
						.fillMaxWidth()
						.padding(14.dp),
					verticalArrangement = Arrangement.spacedBy(10.dp)
				) {
					OutlinedSecureTextField(
						state = tokenState,
						label = { Text(stringResource(R.string.token_input_label)) },
						placeholder = { Text(placeholder) },
						keyboardOptions = KeyboardOptions(
							keyboardType = KeyboardType.Password,
							autoCorrectEnabled = false
						),
						textObfuscationMode = if (isTokenVisible) {
							TextObfuscationMode.Visible
						} else {
							TextObfuscationMode.Hidden
						},
						trailingIcon = {
							androidx.compose.material3.IconButton(onClick = { isTokenVisible = !isTokenVisible }) {
								Icon(
									painter = painterResource(
										if (isTokenVisible) R.drawable.ic_visible_off else R.drawable.ic_visible
									),
									contentDescription = stringResource(R.string.token_toggle_visibility)
								)
							}
						},
						modifier = Modifier.fillMaxWidth()
					)

					if (onVerifyToken != null) {
						OutlinedButton(
							onClick = {
								val trimmed = tokenState.text.toString().trim()
								if (trimmed.isNotEmpty() && !isVerifying && !isSaving) {
									isVerifying = true
									coroutineScope.launch {
										val res = onVerifyToken(trimmed)
										validationResult = res
										validatedTokenValue = trimmed
										isVerifying = false
									}
								}
							},
							enabled = tokenState.text.isNotBlank() && !isVerifying && !isSaving,
							modifier = Modifier.fillMaxWidth(),
							shape = RoundedCornerShape(14.dp)
						) {
							if (isVerifying) {
								CircularProgressIndicator(
									modifier = Modifier.size(16.dp),
									strokeWidth = 2.dp
								)
								Spacer(modifier = Modifier.width(8.dp))
								Text(stringResource(R.string.token_validation_checking))
							} else {
								Icon(
									imageVector = Icons.Default.Check,
									contentDescription = null,
									modifier = Modifier.size(16.dp)
								)
								Spacer(modifier = Modifier.width(6.dp))
								Text(stringResource(R.string.token_dialog_verify_button))
							}
						}
					}

					val currentResult = validationResult
					if (currentResult != null) {
						val bannerBg = when (currentResult) {
							is TokenValidationResult.Valid -> MaterialTheme.colorScheme.primaryContainer
							is TokenValidationResult.Invalid -> MaterialTheme.colorScheme.errorContainer
							is TokenValidationResult.Error -> MaterialTheme.colorScheme.surfaceContainerHighest
						}
						val bannerFg = when (currentResult) {
							is TokenValidationResult.Valid -> MaterialTheme.colorScheme.onPrimaryContainer
							is TokenValidationResult.Invalid -> MaterialTheme.colorScheme.onErrorContainer
							is TokenValidationResult.Error -> MaterialTheme.colorScheme.onSurfaceVariant
						}
						val bannerIcon = when (currentResult) {
							is TokenValidationResult.Valid -> Icons.Default.Check
							is TokenValidationResult.Invalid -> Icons.Default.Warning
							is TokenValidationResult.Error -> Icons.Default.Info
						}
						Surface(
							modifier = Modifier.fillMaxWidth(),
							shape = RoundedCornerShape(14.dp),
							color = bannerBg
						) {
							Row(
								modifier = Modifier
									.fillMaxWidth()
									.padding(horizontal = 12.dp, vertical = 10.dp),
								verticalAlignment = CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(8.dp)
							) {
								Icon(
									imageVector = bannerIcon,
									contentDescription = null,
									tint = bannerFg,
									modifier = Modifier.size(18.dp)
								)
								Text(
									text = formatValidationMessage(currentResult),
									style = MaterialTheme.typography.bodySmall,
									fontWeight = FontWeight.Medium,
									color = bannerFg
								)
							}
						}
					}
				}
			}

			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.spacedBy(10.dp)
			) {
				FilledTonalButton(
					onClick = onDismiss,
					enabled = !isSaving && !isVerifying,
					modifier = Modifier.weight(1f),
					shape = RoundedCornerShape(14.dp)
				) {
					Text(stringResource(android.R.string.cancel))
				}
				Button(
					onClick = {
						val trimmed = tokenState.text.toString().trim()
						if (trimmed.isEmpty() || onVerifyToken == null) {
							onSaveToken(trimmed)
							onDismiss()
						} else if (validatedTokenValue == trimmed && validationResult != null) {
							onSaveToken(trimmed)
							validationResult?.let { res ->
								onVerifiedMessage?.invoke(formatValidationMessage(res))
							}
							onDismiss()
						} else if (!isSaving && !isVerifying) {
							isSaving = true
							coroutineScope.launch {
								val res = onVerifyToken(trimmed)
								validationResult = res
								validatedTokenValue = trimmed
								isSaving = false
								when (res) {
									is TokenValidationResult.Valid,
									is TokenValidationResult.Error -> {
										onSaveToken(trimmed)
										onVerifiedMessage?.invoke(formatValidationMessage(res))
										onDismiss()
									}
									is TokenValidationResult.Invalid -> {
										// Bei ungültigem Token bleibt das Sheet geöffnet zur Korrektur.
									}
								}
							}
						}
					},
					enabled = !isSaving && !isVerifying,
					modifier = Modifier.weight(1f),
					shape = RoundedCornerShape(14.dp)
				) {
					if (isSaving) {
						CircularProgressIndicator(
							modifier = Modifier.size(16.dp),
							strokeWidth = 2.dp,
							color = MaterialTheme.colorScheme.onPrimary
						)
						Spacer(modifier = Modifier.width(8.dp))
						Text(
							text = stringResource(R.string.token_validation_checking),
							maxLines = 1,
							overflow = TextOverflow.Ellipsis
						)
					} else {
						Text(stringResource(R.string.github_token_dialog_save))
					}
				}
			}

			if (initialToken.isNotEmpty()) {
				TextButton(
					onClick = {
						onSaveToken("")
						onDismiss()
					},
					enabled = !isSaving && !isVerifying,
					modifier = Modifier.fillMaxWidth()
				) {
					Text(
						text = stringResource(R.string.github_token_dialog_clear),
						color = MaterialTheme.colorScheme.error
					)
				}
			}
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShizukuSetupBottomSheet(
	status: ShizukuSetupStatus,
	onDismiss: () -> Unit,
	onOpenPlayStore: () -> Unit,
	onOpenShizukuApp: () -> Unit,
	onRequestPermission: () -> Unit,
	onRecheck: () -> Unit
) {
	val sheetState = rememberExpandedModalSheetState()
	var recheckPulseCount by remember { mutableIntStateOf(0) }
	var showRecheckedFeedback by remember { mutableStateOf(false) }

	LaunchedEffect(recheckPulseCount) {
		if (recheckPulseCount > 0) {
			showRecheckedFeedback = true
			delay(1600L)
			showRecheckedFeedback = false
		}
	}

	ModalBottomSheet(
		onDismissRequest = onDismiss,
		sheetState = sheetState,
		shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
		containerColor = MaterialTheme.colorScheme.surfaceContainerLow
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.verticalScroll(rememberScrollState())
				.padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
			verticalArrangement = Arrangement.spacedBy(14.dp)
		) {
			Row(
				verticalAlignment = CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(14.dp)
			) {
				Box(
					modifier = Modifier
						.size(48.dp)
						.clip(RoundedCornerShape(14.dp))
						.background(MaterialTheme.colorScheme.primaryContainer),
					contentAlignment = Alignment.Center
				) {
					Icon(
						painter = painterResource(R.drawable.ic_shizuku),
						contentDescription = null,
						tint = MaterialTheme.colorScheme.onPrimaryContainer,
						modifier = Modifier.size(24.dp)
					)
				}
				Column(modifier = Modifier.weight(1f)) {
					Text(
						text = stringResource(R.string.shizuku_setup_title),
						style = MaterialTheme.typography.titleLarge,
						fontWeight = FontWeight.Bold
					)
					Text(
						text = stringResource(R.string.shizuku_setup_explanation),
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
			}

			// Prominent Reboot Notice for Non-Root Shizuku (Task 10)
			Surface(
				modifier = Modifier.fillMaxWidth(),
				shape = RoundedCornerShape(18.dp),
				color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.75f)
			) {
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = 14.dp, vertical = 12.dp),
					verticalAlignment = Alignment.Top,
					horizontalArrangement = Arrangement.spacedBy(10.dp)
				) {
					Icon(
						imageVector = Icons.Default.Info,
						contentDescription = null,
						tint = MaterialTheme.colorScheme.onTertiaryContainer,
						modifier = Modifier
							.padding(top = 2.dp)
							.size(20.dp)
					)
					Text(
						text = stringResource(R.string.shizuku_setup_reboot_notice),
						style = MaterialTheme.typography.bodySmall,
						fontWeight = FontWeight.Medium,
						color = MaterialTheme.colorScheme.onTertiaryContainer
					)
				}
			}

			// Step 1: Shizuku App installed
			ShizukuStepCard(
				stepNumber = 1,
				title = stringResource(R.string.shizuku_step1_title),
				description = stringResource(
					if (status.isInstalled) R.string.shizuku_step1_desc_ok else R.string.shizuku_step1_desc_missing
				),
				statusBadgeText = stringResource(
					if (status.isInstalled) R.string.shizuku_status_installed else R.string.shizuku_status_not_installed
				),
				isCompleted = status.isInstalled,
				actionLabel = if (!status.isInstalled) stringResource(R.string.shizuku_step1_action) else null,
				onActionClick = onOpenPlayStore
			)

			// Step 2: Shizuku Binder running
			ShizukuStepCard(
				stepNumber = 2,
				title = stringResource(R.string.shizuku_step2_title),
				description = stringResource(
					if (status.isBinderRunning) R.string.shizuku_step2_desc_ok else R.string.shizuku_step2_desc_missing
				),
				statusBadgeText = stringResource(
					if (status.isBinderRunning) R.string.shizuku_status_running else R.string.shizuku_status_not_running
				),
				isCompleted = status.isBinderRunning,
				actionLabel = if (status.isInstalled && !status.isBinderRunning) {
					stringResource(R.string.shizuku_step2_action)
				} else null,
				onActionClick = onOpenShizukuApp
			)

			// Step 3: Shizuku Permission granted
			ShizukuStepCard(
				stepNumber = 3,
				title = stringResource(R.string.shizuku_step3_title),
				description = stringResource(
					if (status.isPermissionGranted) R.string.shizuku_step3_desc_ok else R.string.shizuku_step3_desc_missing
				),
				statusBadgeText = stringResource(
					if (status.isPermissionGranted) R.string.shizuku_status_granted else R.string.shizuku_status_missing_permission
				),
				isCompleted = status.isPermissionGranted,
				actionLabel = if (status.isBinderRunning && !status.isPermissionGranted) {
					stringResource(R.string.shizuku_step3_action)
				} else null,
				onActionClick = onRequestPermission
			)

			Row(
				modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
				horizontalArrangement = Arrangement.spacedBy(10.dp)
			) {
				OutlinedButton(
					onClick = {
						onRecheck()
						recheckPulseCount++
					},
					modifier = Modifier.weight(1f).heightIn(min = 48.dp),
					shape = CircleShape
				) {
					Icon(
						imageVector = if (showRecheckedFeedback) Icons.Default.Check else Icons.Default.Refresh,
						contentDescription = null,
						modifier = Modifier.size(18.dp)
					)
					Spacer(modifier = Modifier.width(6.dp))
					Text(
						text = stringResource(
							if (showRecheckedFeedback) R.string.shizuku_setup_rechecked_feedback else R.string.shizuku_setup_recheck
						)
					)
				}
				TextButton(
					onClick = onDismiss,
					modifier = Modifier.heightIn(min = 48.dp)
				) {
					Text(text = stringResource(R.string.shizuku_setup_close))
				}
			}
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RootSetupBottomSheet(
	rootEnabled: Boolean,
	rootAvailable: Boolean?,
	onRecheck: () -> Unit,
	onToggleRoot: (Boolean) -> Unit,
	onDismiss: () -> Unit
) {
	val sheetState = rememberExpandedModalSheetState()
	var recheckPulseCount by remember { mutableIntStateOf(0) }
	var showRecheckedFeedback by remember { mutableStateOf(false) }

	LaunchedEffect(recheckPulseCount) {
		if (recheckPulseCount > 0) {
			showRecheckedFeedback = true
			delay(1600L)
			showRecheckedFeedback = false
		}
	}

	val statusTitle = when (rootAvailable) {
		true -> stringResource(R.string.root_setup_status_granted)
		false -> stringResource(R.string.root_setup_status_denied)
		null -> stringResource(R.string.root_setup_status_checking)
	}
	val statusDesc = when (rootAvailable) {
		true -> stringResource(R.string.root_setup_status_desc_granted)
		false -> stringResource(R.string.root_setup_status_desc_denied)
		null -> stringResource(R.string.root_setup_status_desc_checking)
	}

	ModalBottomSheet(
		onDismissRequest = onDismiss,
		sheetState = sheetState,
		shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
		containerColor = MaterialTheme.colorScheme.surfaceContainerLow
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.verticalScroll(rememberScrollState())
				.padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
			verticalArrangement = Arrangement.spacedBy(14.dp)
		) {
			Row(
				verticalAlignment = CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(14.dp)
			) {
				Box(
					modifier = Modifier
						.size(48.dp)
						.clip(RoundedCornerShape(14.dp))
						.background(MaterialTheme.colorScheme.primaryContainer),
					contentAlignment = Alignment.Center
				) {
					Icon(
						painter = painterResource(R.drawable.ic_root),
						contentDescription = null,
						tint = MaterialTheme.colorScheme.onPrimaryContainer,
						modifier = Modifier.size(24.dp)
					)
				}
				Column(modifier = Modifier.weight(1f)) {
					Text(
						text = stringResource(R.string.settings_root_installer),
						style = MaterialTheme.typography.titleLarge,
						fontWeight = FontWeight.Bold
					)
					Text(
						text = stringResource(R.string.root_setup_sheet_desc),
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
			}

			val isGranted = rootAvailable == true
			Surface(
				modifier = Modifier.fillMaxWidth(),
				shape = RoundedCornerShape(20.dp),
				color = when (rootAvailable) {
					true -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
					false -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
					null -> MaterialTheme.colorScheme.surfaceContainerHigh
				}
			) {
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(14.dp),
					verticalAlignment = Alignment.Top,
					horizontalArrangement = Arrangement.spacedBy(12.dp)
				) {
					Box(
						modifier = Modifier
							.size(30.dp)
							.clip(CircleShape)
							.background(
								when (rootAvailable) {
									true -> Color(0xFF2E7D32)
									false -> MaterialTheme.colorScheme.error
									null -> MaterialTheme.colorScheme.surfaceContainerHighest
								}
							),
						contentAlignment = Alignment.Center
					) {
						Icon(
							imageVector = when (rootAvailable) {
								true -> Icons.Default.Check
								false -> Icons.Default.Close
								null -> Icons.Default.Refresh
							},
							contentDescription = null,
							tint = if (rootAvailable == null) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
							modifier = Modifier.size(18.dp)
						)
					}
					Column(
						modifier = Modifier.weight(1f),
						verticalArrangement = Arrangement.spacedBy(4.dp)
					) {
						Text(
							text = stringResource(R.string.root_setup_status_title),
							style = MaterialTheme.typography.labelMedium,
							color = MaterialTheme.colorScheme.onSurfaceVariant
						)
						Text(
							text = statusTitle,
							style = MaterialTheme.typography.titleSmall,
							fontWeight = FontWeight.Bold,
							color = MaterialTheme.colorScheme.onSurface
						)
						Text(
							text = statusDesc,
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant
						)
					}
				}
			}

			if (!isGranted) {
				Surface(
					modifier = Modifier.fillMaxWidth(),
					shape = RoundedCornerShape(18.dp),
					color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.75f)
				) {
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.padding(horizontal = 14.dp, vertical = 12.dp),
						verticalAlignment = Alignment.Top,
						horizontalArrangement = Arrangement.spacedBy(10.dp)
					) {
						Icon(
							imageVector = Icons.Default.Info,
							contentDescription = null,
							tint = MaterialTheme.colorScheme.onTertiaryContainer,
							modifier = Modifier
								.padding(top = 2.dp)
								.size(20.dp)
						)
						Text(
							text = stringResource(R.string.root_setup_fallback_note),
							style = MaterialTheme.typography.bodySmall,
							fontWeight = FontWeight.Medium,
							color = MaterialTheme.colorScheme.onTertiaryContainer
						)
					}
				}
			}

			if (isGranted || rootEnabled) {
				Button(
					onClick = { onToggleRoot(!rootEnabled) },
					shape = CircleShape,
					modifier = Modifier
						.fillMaxWidth()
						.heightIn(min = 48.dp)
				) {
					Text(
						text = stringResource(
							if (rootEnabled) R.string.root_setup_btn_disable else R.string.root_setup_btn_enable
						)
					)
				}
			}

			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween,
				verticalAlignment = CenterVertically
			) {
				OutlinedButton(
					onClick = {
						onRecheck()
						recheckPulseCount++
					},
					shape = CircleShape,
					modifier = Modifier.heightIn(min = 48.dp)
				) {
					Icon(
						imageVector = if (showRecheckedFeedback) Icons.Default.Check else Icons.Default.Refresh,
						contentDescription = null,
						modifier = Modifier.size(18.dp)
					)
					Spacer(modifier = Modifier.width(6.dp))
					Text(
						text = stringResource(
							if (showRecheckedFeedback) R.string.shizuku_setup_rechecked_feedback else R.string.shizuku_setup_recheck
						)
					)
				}
				TextButton(
					onClick = onDismiss,
					modifier = Modifier.heightIn(min = 48.dp)
				) {
					Text(text = stringResource(R.string.shizuku_setup_close))
				}
			}
		}
	}
}

@Composable
private fun ShizukuStepCard(
	stepNumber: Int,
	title: String,
	description: String,
	statusBadgeText: String,
	isCompleted: Boolean,
	actionLabel: String?,
	onActionClick: () -> Unit
) {
	val successGreen = Color(0xFF2E7D32)
	val successGreenContainer = Color(0xFFE8F5E9)

	Surface(
		modifier = Modifier.fillMaxWidth(),
		shape = RoundedCornerShape(20.dp),
		color = if (isCompleted) {
			MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
		} else {
			MaterialTheme.colorScheme.surfaceContainerHigh
		}
	) {
		Column(
			modifier = Modifier.fillMaxWidth().padding(14.dp),
			verticalArrangement = Arrangement.spacedBy(10.dp)
		) {
			Row(
				verticalAlignment = CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(12.dp)
			) {
				Box(
					modifier = Modifier
						.size(30.dp)
						.clip(CircleShape)
						.background(
							if (isCompleted) successGreen
							else MaterialTheme.colorScheme.surfaceContainerHighest
						),
					contentAlignment = Alignment.Center
				) {
					if (isCompleted) {
						Icon(
							imageVector = Icons.Default.Check,
							contentDescription = null,
							tint = Color.White,
							modifier = Modifier.size(18.dp)
						)
					} else {
						Text(
							text = stepNumber.toString(),
							style = MaterialTheme.typography.labelLarge,
							fontWeight = FontWeight.Bold,
							color = MaterialTheme.colorScheme.onSurfaceVariant
						)
					}
				}
				Column(
					modifier = Modifier.weight(1f),
					verticalArrangement = Arrangement.spacedBy(4.dp)
				) {
					Row(
						modifier = Modifier.fillMaxWidth(),
						verticalAlignment = CenterVertically,
						horizontalArrangement = Arrangement.SpaceBetween
					) {
						Text(
							text = title,
							style = MaterialTheme.typography.titleSmall,
							fontWeight = FontWeight.SemiBold,
							color = MaterialTheme.colorScheme.onSurface,
							modifier = Modifier.weight(1f, fill = false)
						)
						Spacer(modifier = Modifier.width(8.dp))
						Surface(
							shape = RoundedCornerShape(999.dp),
							color = if (isCompleted) {
								successGreenContainer
							} else {
								MaterialTheme.colorScheme.errorContainer
							}
						) {
							Row(
								modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
								verticalAlignment = CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(4.dp)
							) {
								Icon(
									imageVector = if (isCompleted) Icons.Default.Check else Icons.Default.Warning,
									contentDescription = null,
									tint = if (isCompleted) successGreen else MaterialTheme.colorScheme.onErrorContainer,
									modifier = Modifier.size(12.dp)
								)
								Text(
									text = statusBadgeText,
									style = MaterialTheme.typography.labelSmall,
									fontWeight = FontWeight.SemiBold,
									color = if (isCompleted) successGreen else MaterialTheme.colorScheme.onErrorContainer,
									maxLines = 1
								)
							}
						}
					}
					Text(
						text = description,
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
			}

			if (!actionLabel.isNullOrBlank()) {
				Button(
					onClick = onActionClick,
					modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
					shape = CircleShape
				) {
					Text(text = actionLabel)
				}
			}
		}
	}
}

@Composable
private fun UpdateDisplayModePreview(displayMode: Int) {
	var isExpanded by rememberSaveable { mutableStateOf(false) }

	Surface(
		modifier = Modifier
			.fillMaxWidth()
			.padding(top = 4.dp),
		shape = RoundedCornerShape(18.dp),
		color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f)
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(12.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp)
		) {
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.clip(RoundedCornerShape(8.dp))
					.clickable { isExpanded = !isExpanded }
					.padding(vertical = 2.dp),
				horizontalArrangement = Arrangement.SpaceBetween,
				verticalAlignment = CenterVertically
			) {
				Row(
					verticalAlignment = CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(8.dp)
				) {
					Text(
						text = stringResource(R.string.settings_preview_title),
						style = MaterialTheme.typography.labelMedium,
						fontWeight = FontWeight.SemiBold,
						color = MaterialTheme.colorScheme.primary
					)
					Text(
						text = if (displayMode == 0) stringResource(R.string.updates_detail_view) else stringResource(R.string.updates_compact_view),
						style = MaterialTheme.typography.labelSmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
				Icon(
					imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
					contentDescription = null,
					tint = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.size(20.dp)
				)
			}

			AnimatedVisibility(
				visible = isExpanded,
				enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
				exit = shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut()
			) {
				AnimatedContent(
					targetState = displayMode,
					transitionSpec = {
						(fadeIn(animationSpec = tween(220, delayMillis = 50)) +
						 scaleIn(initialScale = 0.94f, animationSpec = tween(220, delayMillis = 50)))
							.togetherWith(fadeOut(animationSpec = tween(150)))
							.using(SizeTransform(clip = false))
					},
					label = "PreviewModeAnimation"
				) { mode ->
					if (mode == 0) {
						// 1-Spalten Detail-Ansicht (exaktes 1:1 Spiegelbild von UpdateCard)
						Surface(
							modifier = Modifier.fillMaxWidth(),
							shape = RoundedCornerShape(20.dp),
							color = MaterialTheme.colorScheme.surfaceContainerLow
						) {
							Column(
								modifier = Modifier
									.fillMaxWidth()
									.padding(14.dp),
								verticalArrangement = Arrangement.spacedBy(10.dp)
							) {
								Row(
									verticalAlignment = Alignment.Top,
									horizontalArrangement = Arrangement.spacedBy(12.dp),
									modifier = Modifier.fillMaxWidth()
								) {
									Box(
										modifier = Modifier
											.size(52.dp)
											.clip(RoundedCornerShape(14.dp))
											.background(MaterialTheme.colorScheme.surfaceContainer),
										contentAlignment = Alignment.Center
									) {
										Icon(
											painter = painterResource(R.drawable.ic_system),
											contentDescription = null,
											tint = MaterialTheme.colorScheme.primary,
											modifier = Modifier.size(28.dp)
										)
									}
									Column(
										modifier = Modifier.weight(1f),
										verticalArrangement = Arrangement.spacedBy(2.dp)
									) {
										Row(
											modifier = Modifier.fillMaxWidth(),
											horizontalArrangement = Arrangement.SpaceBetween,
											verticalAlignment = Alignment.CenterVertically
										) {
											Text(
												text = "System parental controls",
												style = MaterialTheme.typography.titleMedium,
												fontWeight = FontWeight.Bold,
												color = MaterialTheme.colorScheme.onSurface,
												maxLines = 1,
												overflow = TextOverflow.Ellipsis,
												modifier = Modifier.weight(1f, fill = false)
											)
											Text(
												text = "26.2 MB",
												style = MaterialTheme.typography.labelSmall,
												color = MaterialTheme.colorScheme.onSurfaceVariant
											)
										}
										Row(
											verticalAlignment = Alignment.CenterVertically,
											horizontalArrangement = Arrangement.spacedBy(6.dp)
										) {
											Text(
												text = "1.0.966003905",
												style = MaterialTheme.typography.labelSmall,
												color = MaterialTheme.colorScheme.onSurfaceVariant,
												maxLines = 1,
												overflow = TextOverflow.Ellipsis
											)
											Icon(
												imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
												contentDescription = null,
												tint = MaterialTheme.colorScheme.primary,
												modifier = Modifier.size(12.dp)
											)
											Text(
												text = "1.0.1007282986",
												style = MaterialTheme.typography.labelSmall,
												fontWeight = FontWeight.Bold,
												color = MaterialTheme.colorScheme.primary,
												maxLines = 1,
												overflow = TextOverflow.Ellipsis
											)
										}
										Row(
											verticalAlignment = Alignment.CenterVertically,
											horizontalArrangement = Arrangement.spacedBy(6.dp),
											modifier = Modifier.padding(top = 2.dp)
										) {
											Surface(
												shape = RoundedCornerShape(4.dp),
												color = MaterialTheme.colorScheme.surfaceContainer
											) {
												Row(
													modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
													verticalAlignment = Alignment.CenterVertically,
													horizontalArrangement = Arrangement.spacedBy(4.dp)
												) {
													Icon(
														painter = painterResource(R.drawable.ic_play),
														contentDescription = null,
														tint = MaterialTheme.colorScheme.onSurfaceVariant,
														modifier = Modifier.size(11.dp)
													)
													Text(
														text = "Google Play",
														style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
														color = MaterialTheme.colorScheme.onSurfaceVariant
													)
												}
											}
											Surface(
												shape = RoundedCornerShape(4.dp),
												color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
											) {
												Row(
													modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
													verticalAlignment = Alignment.CenterVertically,
													horizontalArrangement = Arrangement.spacedBy(3.dp)
												) {
													Icon(
														imageVector = Icons.Default.Layers,
														contentDescription = null,
														tint = MaterialTheme.colorScheme.onPrimaryContainer,
														modifier = Modifier.size(11.dp)
													)
													Text(
														text = "+1",
														style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
														color = MaterialTheme.colorScheme.onPrimaryContainer
													)
												}
											}
										}
									}
								}

								Surface(
									shape = RoundedCornerShape(10.dp),
									color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f),
									modifier = Modifier.fillMaxWidth()
								) {
									Row(
										modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
										verticalAlignment = Alignment.CenterVertically,
										horizontalArrangement = Arrangement.spacedBy(6.dp)
									) {
										Icon(
											imageVector = Icons.Filled.Info,
											contentDescription = null,
											tint = MaterialTheme.colorScheme.primary,
											modifier = Modifier.size(13.dp)
										)
										Text(
											text = "• First version",
											style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
											color = MaterialTheme.colorScheme.onSurfaceVariant,
											maxLines = 1,
											overflow = TextOverflow.Ellipsis
										)
									}
								}

								Row(
									modifier = Modifier.fillMaxWidth(),
									horizontalArrangement = Arrangement.SpaceBetween,
									verticalAlignment = Alignment.CenterVertically
								) {
									Text(
										text = stringResource(R.string.updates_detail_view),
										style = MaterialTheme.typography.labelMedium,
										color = MaterialTheme.colorScheme.onSurfaceVariant
									)
									Surface(
										shape = RoundedCornerShape(10.dp),
										color = MaterialTheme.colorScheme.primary
									) {
										Row(
											modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
											verticalAlignment = Alignment.CenterVertically,
											horizontalArrangement = Arrangement.spacedBy(6.dp)
										) {
											Icon(
												painter = painterResource(R.drawable.ic_install),
												contentDescription = null,
												tint = MaterialTheme.colorScheme.onPrimary,
												modifier = Modifier.size(14.dp)
											)
											Text(
												text = stringResource(R.string.update_action),
												style = MaterialTheme.typography.labelMedium,
												fontWeight = FontWeight.Bold,
												color = MaterialTheme.colorScheme.onPrimary
											)
										}
									}
								}
							}
						}
					} else {
						// 1-Spalten Kompakt-Ansicht (exaktes 1:1 Spiegelbild von CompactUpdatesGrid)
						Surface(
							modifier = Modifier.fillMaxWidth(),
							shape = RoundedCornerShape(14.dp),
							color = MaterialTheme.colorScheme.surfaceContainerLow
						) {
							Row(
								modifier = Modifier
									.fillMaxWidth()
									.padding(horizontal = 14.dp, vertical = 10.dp),
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(12.dp)
							) {
								Box(
									modifier = Modifier
										.size(42.dp)
										.clip(RoundedCornerShape(12.dp))
										.background(MaterialTheme.colorScheme.surfaceContainer),
									contentAlignment = Alignment.Center
								) {
									Icon(
										painter = painterResource(R.drawable.ic_system),
										contentDescription = null,
										tint = MaterialTheme.colorScheme.primary,
										modifier = Modifier.size(22.dp)
									)
								}
								Column(
									modifier = Modifier.weight(1f),
									verticalArrangement = Arrangement.spacedBy(2.dp)
								) {
									Text(
										text = "System parental controls",
										style = MaterialTheme.typography.bodyMedium,
										fontWeight = FontWeight.Bold,
										color = MaterialTheme.colorScheme.onSurface,
										maxLines = 1,
										overflow = TextOverflow.Ellipsis
									)
									Row(
										verticalAlignment = Alignment.CenterVertically,
										horizontalArrangement = Arrangement.spacedBy(4.dp)
									) {
										Text(
											text = "1.0.966003905",
											style = MaterialTheme.typography.labelSmall,
											color = MaterialTheme.colorScheme.onSurfaceVariant,
											maxLines = 1,
											overflow = TextOverflow.Ellipsis
										)
										Icon(
											imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
											contentDescription = null,
											tint = MaterialTheme.colorScheme.primary,
											modifier = Modifier.size(10.dp)
										)
										Text(
											text = "1.0.1007282986",
											style = MaterialTheme.typography.labelSmall,
											fontWeight = FontWeight.Bold,
											color = MaterialTheme.colorScheme.primary,
											maxLines = 1,
											overflow = TextOverflow.Ellipsis
										)
									}
								}
								Surface(
									shape = RoundedCornerShape(10.dp),
									color = MaterialTheme.colorScheme.primary
								) {
									Row(
										modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
										verticalAlignment = Alignment.CenterVertically,
										horizontalArrangement = Arrangement.spacedBy(4.dp)
									) {
										Icon(
											painter = painterResource(R.drawable.ic_install),
											contentDescription = null,
											tint = MaterialTheme.colorScheme.onPrimary,
											modifier = Modifier.size(13.dp)
										)
										Text(
											text = stringResource(R.string.update_action),
											style = MaterialTheme.typography.labelSmall,
											fontWeight = FontWeight.Bold,
											color = MaterialTheme.colorScheme.onPrimary
										)
									}
								}
							}
						}
					}
				}
			}
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HiddenAppsBottomSheet(
	ignoredPackages: List<String>,
	installedApps: List<AppInstalled>,
	onRestoreSelected: (Set<String>) -> Unit,
	onHideMore: (Set<String>) -> Unit,
	onDismiss: () -> Unit
) {
	val sheetState = rememberExpandedModalSheetState()
	var isAddingMore by remember { mutableStateOf(false) }
	var searchQuery by remember { mutableStateOf("") }
	val selectedToRestore = remember(ignoredPackages) { mutableStateMapOf<String, Boolean>() }
	val selectedToHide = remember(isAddingMore) { mutableStateMapOf<String, Boolean>() }

	val installedByPackage = remember(installedApps) {
		installedApps.associateBy { it.packageName }
	}

	ModalBottomSheet(
		onDismissRequest = onDismiss,
		sheetState = sheetState,
		shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
		containerColor = MaterialTheme.colorScheme.surfaceContainerLow
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 20.dp, vertical = 8.dp)
				.navigationBarsPadding(),
			verticalArrangement = Arrangement.spacedBy(12.dp)
		) {
			if (!isAddingMore) {
				// MODE: View and restore hidden apps
				Row(
					modifier = Modifier.fillMaxWidth(),
					verticalAlignment = CenterVertically,
					horizontalArrangement = Arrangement.SpaceBetween
				) {
					Column {
						Text(
							text = stringResource(R.string.hidden_apps_title),
							style = MaterialTheme.typography.titleLarge,
							fontWeight = FontWeight.Bold
						)
						Text(
							text = if (ignoredPackages.isEmpty()) {
								stringResource(R.string.hidden_apps_empty)
							} else {
								stringResource(R.string.hidden_apps_count, ignoredPackages.size)
							},
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant
						)
					}
					TextButton(onClick = onDismiss) {
						Text(stringResource(R.string.shizuku_setup_close))
					}
				}

				Text(
					text = stringResource(R.string.hidden_apps_explanation),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)

				if (ignoredPackages.isNotEmpty()) {
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.spacedBy(8.dp)
					) {
						TextButton(onClick = {
							ignoredPackages.forEach { selectedToRestore[it] = true }
						}) {
							Text(stringResource(R.string.hidden_apps_select_all))
						}
						TextButton(onClick = {
							selectedToRestore.clear()
						}) {
							Text(stringResource(R.string.hidden_apps_unselect_all))
						}
					}


					LazyColumn(
						modifier = Modifier
							.fillMaxWidth()
							.heightIn(max = 380.dp),
						verticalArrangement = Arrangement.spacedBy(8.dp)
					) {
						items(ignoredPackages, key = { it }) { pkg ->
							val appName = installedByPackage[pkg]?.name ?: pkg
							val isChecked = selectedToRestore[pkg] == true
							Surface(
								modifier = Modifier
									.fillMaxWidth()
									.clip(RoundedCornerShape(16.dp))
									.clickable { selectedToRestore[pkg] = !isChecked },
								shape = RoundedCornerShape(16.dp),
								color = MaterialTheme.colorScheme.surfaceContainerHigh
							) {
								Row(
									modifier = Modifier
										.fillMaxWidth()
										.padding(horizontal = 12.dp, vertical = 10.dp),
									verticalAlignment = CenterVertically,
									horizontalArrangement = Arrangement.spacedBy(12.dp)
								) {
									Checkbox(
										checked = isChecked,
										onCheckedChange = { selectedToRestore[pkg] = it }
									)
									Box(
										modifier = Modifier
											.size(38.dp)
											.clip(RoundedCornerShape(10.dp))
											.background(MaterialTheme.colorScheme.surfaceContainerHighest),
										contentAlignment = Alignment.Center
									) {
										LoadingImageApp(pkg, Modifier.requiredSize(48.dp))
									}
									Column(modifier = Modifier.weight(1f)) {
										Text(
											text = appName,
											style = MaterialTheme.typography.titleSmall,
											fontWeight = FontWeight.SemiBold,
											maxLines = 1,
											overflow = TextOverflow.Ellipsis
										)
										Text(
											text = pkg,
											style = MaterialTheme.typography.bodySmall,
											color = MaterialTheme.colorScheme.onSurfaceVariant,
											maxLines = 1,
											overflow = TextOverflow.Ellipsis
										)
									}
								}
							}
						}
					}
				}

				val selectedCount = selectedToRestore.values.count { it }
				Column(
					modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
					verticalArrangement = Arrangement.spacedBy(8.dp)
				) {
					Button(
						onClick = {
							val toRestore = selectedToRestore.filter { it.value }.keys
							if (toRestore.isNotEmpty()) {
								onRestoreSelected(toRestore)
								selectedToRestore.clear()
							}
						},
						enabled = selectedCount > 0,
						modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
						shape = CircleShape
					) {
						Text(
							text = if (selectedCount > 0) {
								stringResource(R.string.hidden_apps_restore_selected) + " ($selectedCount)"
							} else {
								stringResource(R.string.hidden_apps_restore_selected)
							}
						)
					}

					OutlinedButton(
						onClick = {
							searchQuery = ""
							selectedToHide.clear()
							isAddingMore = true
						},
						modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
						shape = CircleShape
					) {
						Icon(
							painter = painterResource(R.drawable.ic_visible_off),
							contentDescription = null,
							modifier = Modifier.size(18.dp)
						)
						Spacer(modifier = Modifier.width(6.dp))
						Text(stringResource(R.string.hidden_apps_hide_more))
					}
				}
			} else {
				// MODE: Search and select installed apps to hide
				Row(
					modifier = Modifier.fillMaxWidth(),
					verticalAlignment = CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(8.dp)
				) {
					TextButton(onClick = { isAddingMore = false }) {
						Text(stringResource(R.string.action_back))
					}
					Text(
						text = stringResource(R.string.hidden_apps_hide_more),
						style = MaterialTheme.typography.titleMedium,
						fontWeight = FontWeight.Bold,
						modifier = Modifier.weight(1f)
					)
				}

				OutlinedTextField(
					value = searchQuery,
					onValueChange = { searchQuery = it },
					modifier = Modifier.fillMaxWidth(),
					placeholder = { Text(stringResource(R.string.hidden_apps_search)) },
					singleLine = true,
					shape = RoundedCornerShape(16.dp),
					colors = OutlinedTextFieldDefaults.colors(
						focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
						unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
					)
				)

				val candidates = remember(installedApps, ignoredPackages, searchQuery) {
					val ignoredSet = ignoredPackages.toSet()
					installedApps.filterNot { it.packageName in ignoredSet }
						.filter {
							if (searchQuery.isBlank()) true
							else it.name.contains(searchQuery, ignoreCase = true) ||
								 it.packageName.contains(searchQuery, ignoreCase = true)
						}
				}

				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.spacedBy(8.dp)
				) {
					TextButton(onClick = {
						candidates.forEach { selectedToHide[it.packageName] = true }
					}) {
						Text(stringResource(R.string.hidden_apps_select_all))
					}
					TextButton(onClick = {
						selectedToHide.clear()
					}) {
						Text(stringResource(R.string.hidden_apps_unselect_all))
					}
				}


				LazyColumn(
					modifier = Modifier
						.fillMaxWidth()
						.heightIn(max = 340.dp),
					verticalArrangement = Arrangement.spacedBy(6.dp)
				) {
					items(candidates, key = { it.packageName }) { app ->
						val isChecked = selectedToHide[app.packageName] == true
						Surface(
							modifier = Modifier
								.fillMaxWidth()
								.clip(RoundedCornerShape(14.dp))
								.clickable { selectedToHide[app.packageName] = !isChecked },
							shape = RoundedCornerShape(14.dp),
							color = MaterialTheme.colorScheme.surfaceContainerHigh
						) {
							Row(
								modifier = Modifier
									.fillMaxWidth()
									.padding(horizontal = 12.dp, vertical = 8.dp),
								verticalAlignment = CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(12.dp)
							) {
								Checkbox(
									checked = isChecked,
									onCheckedChange = { selectedToHide[app.packageName] = it }
								)
								Box(
									modifier = Modifier
										.size(36.dp)
										.clip(RoundedCornerShape(8.dp))
										.background(MaterialTheme.colorScheme.surfaceContainerHighest),
										contentAlignment = Alignment.Center
								) {
									LoadingImageApp(app.packageName, Modifier.requiredSize(44.dp))
								}
								Column(modifier = Modifier.weight(1f)) {
									Text(
										text = app.name,
										style = MaterialTheme.typography.titleSmall,
										fontWeight = FontWeight.SemiBold,
										maxLines = 1,
										overflow = TextOverflow.Ellipsis
									)
									Text(
										text = app.packageName,
										style = MaterialTheme.typography.bodySmall,
										color = MaterialTheme.colorScheme.onSurfaceVariant,
										maxLines = 1,
										overflow = TextOverflow.Ellipsis
									)
								}
							}
						}
					}
				}

				val selectedCountToHide = selectedToHide.values.count { it }
				Button(
					onClick = {
						val toHide = selectedToHide.filter { it.value }.keys
						if (toHide.isNotEmpty()) {
							onHideMore(toHide)
							isAddingMore = false
						}
					},
					enabled = selectedCountToHide > 0,
					modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
					shape = CircleShape
				) {
					Text(
						text = if (selectedCountToHide > 0) {
							stringResource(R.string.hidden_apps_hide_selected) + " ($selectedCountToHide)"
						} else {
							stringResource(R.string.hidden_apps_hide_selected)
						}
					)
				}
			}
		}
	}
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IgnoredVersionsBottomSheet(
	entries: List<IgnoredVersionEntry>,
	onUnignore: (Int) -> Unit,
	onClearAll: () -> Unit,
	onDismiss: () -> Unit
) {
	val sheetState = rememberExpandedModalSheetState()
	ModalBottomSheet(
		onDismissRequest = onDismiss,
		sheetState = sheetState,
		containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
		dragHandle = { BottomSheetDefaults.DragHandle() }
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 20.dp)
				.padding(bottom = 32.dp)
				.verticalScroll(rememberScrollState()),
			verticalArrangement = Arrangement.spacedBy(14.dp)
		) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(10.dp)
			) {
				Box(
					modifier = Modifier
						.size(36.dp)
						.clip(RoundedCornerShape(10.dp))
						.background(MaterialTheme.colorScheme.secondaryContainer),
					contentAlignment = Alignment.Center
				) {
					Icon(
						painter = painterResource(R.drawable.ic_download),
						contentDescription = null,
						tint = MaterialTheme.colorScheme.onSecondaryContainer,
						modifier = Modifier.size(20.dp)
					)
				}
				Column(modifier = Modifier.weight(1f)) {
					Text(
						text = stringResource(R.string.settings_ignored_versions_title),
						style = MaterialTheme.typography.titleMedium,
						fontWeight = FontWeight.Bold,
						color = MaterialTheme.colorScheme.onSurface
					)
					Text(
						text = if (entries.isEmpty()) stringResource(R.string.settings_ignored_versions_empty)
						else stringResource(R.string.settings_ignored_versions_count, entries.size),
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
			}

			Text(
				text = stringResource(R.string.settings_ignored_versions_explanation),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)

			if (entries.isEmpty()) {
				Surface(
					modifier = Modifier.fillMaxWidth(),
					shape = RoundedCornerShape(16.dp),
					color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
				) {
					Text(
						text = stringResource(R.string.settings_ignored_versions_empty),
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						modifier = Modifier.padding(16.dp)
					)
				}
			} else {
				Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
					entries.forEach { entry ->
						Surface(
							modifier = Modifier.fillMaxWidth(),
							shape = RoundedCornerShape(14.dp),
							color = MaterialTheme.colorScheme.surfaceContainerHighest
						) {
							Row(
								modifier = Modifier
									.fillMaxWidth()
									.padding(horizontal = 12.dp, vertical = 10.dp),
								verticalAlignment = CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(10.dp)
							) {
								if (entry.packageName.isNotBlank()) {
									Box(
										modifier = Modifier
											.size(32.dp)
											.clip(RoundedCornerShape(8.dp))
											.background(MaterialTheme.colorScheme.surfaceContainer),
										contentAlignment = Alignment.Center
									) {
										LoadingImageApp(entry.packageName, Modifier.requiredSize(40.dp))
									}
								}
								Column(modifier = Modifier.weight(1f)) {
									Text(
										text = entry.appName.ifBlank { entry.packageName.ifBlank { "ID #${entry.id}" } },
										style = MaterialTheme.typography.bodyMedium,
										fontWeight = FontWeight.SemiBold,
										color = MaterialTheme.colorScheme.onSurface,
										maxLines = 1,
										overflow = TextOverflow.Ellipsis
									)
									val subtitleParts = buildList {
										if (entry.version.isNotBlank()) add(entry.version)
										if (entry.versionCode > 0L) add("(${entry.versionCode})")
										if (entry.sourceName.isNotBlank()) add("• ${entry.sourceName}")
									}
									if (subtitleParts.isNotEmpty()) {
										Text(
											text = subtitleParts.joinToString(" "),
											style = MaterialTheme.typography.labelSmall,
											color = MaterialTheme.colorScheme.onSurfaceVariant,
											maxLines = 1,
											overflow = TextOverflow.Ellipsis
										)
									}
								}
								FilledTonalButton(
									onClick = { onUnignore(entry.id) },
									contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
									shape = CircleShape
								) {
									Icon(
										imageVector = Icons.Default.Refresh,
										contentDescription = null,
										modifier = Modifier.size(14.dp)
									)
									Spacer(modifier = Modifier.width(4.dp))
									Text(
										text = stringResource(R.string.action_unignore_version),
										style = MaterialTheme.typography.labelSmall,
										fontWeight = FontWeight.SemiBold
									)
								}
							}
						}
					}

					OutlinedButton(
						onClick = onClearAll,
						modifier = Modifier
							.fillMaxWidth()
							.padding(top = 4.dp),
						shape = CircleShape
					) {
						Icon(
							imageVector = Icons.Default.Delete,
							contentDescription = null,
							modifier = Modifier.size(16.dp)
						)
						Spacer(modifier = Modifier.width(6.dp))
						Text(stringResource(R.string.action_clear_ignored_versions))
					}
				}
			}
		}
	}
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTopBar() = TopAppBar(
	title = {
		Text(
			text = stringResource(R.string.tab_settings),
			style = MaterialTheme.typography.headlineMedium,
			fontWeight = FontWeight.Bold
		)
	},
	colors = TopAppBarDefaults.topAppBarColors(
		containerColor = MaterialTheme.colorScheme.background,
		scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
	),
	windowInsets = WindowInsets(0),
	navigationIcon = {
		Box(
			modifier = Modifier
				.minimumInteractiveComponentSize()
				.padding(start = 8.dp, end = 4.dp)
				.size(36.dp)
				.clip(MaterialTheme.shapes.small)
				.background(MaterialTheme.colorScheme.primaryContainer),
			contentAlignment = Alignment.Center
		) {
			Icon(
				imageVector = Icons.Filled.Settings,
				contentDescription = stringResource(R.string.tab_settings),
				tint = MaterialTheme.colorScheme.onPrimaryContainer,
				modifier = Modifier.size(18.dp)
			)
		}
	}
)

internal fun performHeartbeatHaptic(
	context: Context,
	hapticFeedback: HapticFeedback? = null
) {
	var succeeded = false
	try {
		val timings = longArrayOf(0, 50, 110, 75, 120, 50, 110, 75)
		val amplitudes = intArrayOf(0, 180, 0, 255, 0, 180, 0, 255)
		val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
			vibratorManager?.defaultVibrator
		} else {
			@Suppress("DEPRECATION")
			context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
		}
		if (vibrator != null && vibrator.hasVibrator()) {
			if (vibrator.hasAmplitudeControl()) {
				vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
				succeeded = true
			} else {
				vibrator.vibrate(VibrationEffect.createWaveform(timings, -1))
				succeeded = true
			}
		}
	} catch (_: Exception) {}

	if (!succeeded) {
		hapticFeedback?.performHapticFeedback(HapticFeedbackType.LongPress)
	}
}



@Composable
private fun PermissionSettingItem(
	title: String,
	subtitle: String,
	iconRes: Int,
	isGranted: Boolean,
	statusText: String,
	onClick: () -> Unit
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.heightIn(min = 48.dp)
			.clip(RoundedCornerShape(12.dp))
			.clickable(onClick = onClick)
			.padding(horizontal = 4.dp, vertical = 6.dp),
		verticalAlignment = CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(10.dp)
	) {
		Box(
			modifier = Modifier
				.size(32.dp)
				.clip(RoundedCornerShape(10.dp))
				.background(MaterialTheme.colorScheme.surfaceContainerHigh),
			contentAlignment = Alignment.Center
		) {
			Icon(
				painter = painterResource(iconRes),
				contentDescription = null,
				tint = MaterialTheme.colorScheme.primary,
				modifier = Modifier.size(18.dp)
			)
		}
		Column(modifier = Modifier.weight(1f)) {
			Text(
				text = title,
				style = MaterialTheme.typography.bodyMedium,
				fontWeight = FontWeight.Medium,
				color = MaterialTheme.colorScheme.onSurface,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
			Text(
				text = subtitle,
				style = MaterialTheme.typography.labelSmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
		}
		val isDark = androidx.compose.foundation.isSystemInDarkTheme()
		val isReducedMotion = ApkUpTheme.isReducedMotion
		val targetBadgeBg = if (isGranted) {
			if (isDark) Color(0xFF1B3820) else Color(0xFFE8F5E9)
		} else {
			if (isDark) Color(0xFF3E1A1A) else Color(0xFFFFEBEE)
		}
		val targetBadgeColor = if (isGranted) {
			if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
		} else {
			if (isDark) Color(0xFFFF8A80) else Color(0xFFC62828)
		}
		val badgeBg by animateColorAsState(
			targetValue = targetBadgeBg,
			animationSpec = if (isReducedMotion) tween(60) else tween(260),
			label = "PermissionBadgeBg"
		)
		val badgeColor by animateColorAsState(
			targetValue = targetBadgeColor,
			animationSpec = if (isReducedMotion) tween(60) else tween(260),
			label = "PermissionBadgeColor"
		)
		val checkScale = remember { Animatable(1f) }
		var prevGranted by remember { mutableStateOf(isGranted) }
		LaunchedEffect(isGranted) {
			if (isGranted && !prevGranted && !isReducedMotion) {
				checkScale.snapTo(0.25f)
				checkScale.animateTo(
					targetValue = 1f,
					animationSpec = spring(dampingRatio = 0.45f, stiffness = 420f)
				)
			}
			prevGranted = isGranted
		}
		Surface(
			shape = CircleShape,
			color = badgeBg
		) {
			Row(
				modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
				verticalAlignment = CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(4.dp)
			) {
				AnimatedContent(
					targetState = isGranted,
					transitionSpec = {
						if (isReducedMotion) {
							fadeIn(tween(60)) togetherWith fadeOut(tween(60))
						} else {
							(scaleIn(spring(dampingRatio = 0.55f, stiffness = 500f), initialScale = 0.5f) + fadeIn()) togetherWith
								(scaleOut(targetScale = 0.5f) + fadeOut())
						}
					},
					label = "PermissionCheckIcon"
				) { granted ->
					Icon(
						imageVector = if (granted) Icons.Default.Check else Icons.Default.Close,
						contentDescription = null,
						tint = badgeColor,
						modifier = Modifier
							.size(11.dp)
							.graphicsLayer {
								scaleX = if (granted) checkScale.value else 1f
								scaleY = if (granted) checkScale.value else 1f
							}
					)
				}
				Text(
					text = statusText,
					style = MaterialTheme.typography.labelSmall,
					fontWeight = FontWeight.SemiBold,
					color = badgeColor
				)
			}
		}
		Icon(
			imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
			contentDescription = null,
			tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
			modifier = Modifier.size(16.dp)
		)
	}
}

private data class SetupWarningItem(
	val id: String,
	val title: String,
	val subtitle: String,
	val shortName: String,
	val actionText: String,
	val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackgroundCheckIntervalBottomSheet(
	selectedHours: Int,
	onSelectHours: (Int) -> Unit,
	onDismiss: () -> Unit
) {
	val sheetState = rememberExpandedModalSheetState()
	val intervalOptions = listOf(
		BackgroundCheckInterval.HOURS_6 to ("6h" to R.string.settings_interval_6h),
		BackgroundCheckInterval.HOURS_12 to ("12h" to R.string.settings_interval_12h),
		BackgroundCheckInterval.HOURS_24 to ("24h" to R.string.settings_interval_24h),
		BackgroundCheckInterval.HOURS_48 to ("48h" to R.string.settings_interval_48h),
		BackgroundCheckInterval.DAYS_7 to ("7d" to R.string.settings_interval_7d)
	)

	ModalBottomSheet(
		onDismissRequest = onDismiss,
		sheetState = sheetState,
		containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
		contentColor = MaterialTheme.colorScheme.onSurface,
		dragHandle = {
			Box(
				modifier = Modifier
					.padding(vertical = 12.dp)
					.width(36.dp)
					.height(4.dp)
					.clip(CircleShape)
					.background(MaterialTheme.colorScheme.outlineVariant)
			)
		}
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 24.dp)
				.padding(bottom = 32.dp),
			verticalArrangement = Arrangement.spacedBy(16.dp)
		) {
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(12.dp)
			) {
				Box(
					modifier = Modifier
						.size(40.dp)
						.clip(RoundedCornerShape(12.dp))
						.background(MaterialTheme.colorScheme.surfaceContainerHigh),
					contentAlignment = Alignment.Center
				) {
					Icon(
						painter = painterResource(R.drawable.ic_refresh),
						contentDescription = null,
						tint = MaterialTheme.colorScheme.primary,
						modifier = Modifier.size(22.dp)
					)
				}
				Column {
					Text(
						text = stringResource(R.string.settings_background_interval_title),
						style = MaterialTheme.typography.titleMedium,
						fontWeight = FontWeight.Bold
					)
					Text(
						text = stringResource(R.string.settings_background_checks),
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
			}

			Column(
				modifier = Modifier.fillMaxWidth(),
				verticalArrangement = Arrangement.spacedBy(8.dp)
			) {
				intervalOptions.forEach { (interval, meta) ->
					val isSelected = interval.hours == selectedHours
					Surface(
						modifier = Modifier
							.fillMaxWidth()
							.clip(RoundedCornerShape(16.dp))
							.clickable {
								onSelectHours(interval.hours)
								onDismiss()
							},
						shape = RoundedCornerShape(16.dp),
						color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
						border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
					) {
						Row(
							modifier = Modifier
								.fillMaxWidth()
								.padding(horizontal = 16.dp, vertical = 14.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.SpaceBetween
						) {
							Row(
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(12.dp)
							) {
								Surface(
									shape = RoundedCornerShape(8.dp),
									color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
								) {
									Text(
										text = meta.first,
										modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
										style = MaterialTheme.typography.labelMedium,
										fontWeight = FontWeight.Bold,
										color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
									)
								}
								Text(
									text = stringResource(meta.second),
									style = MaterialTheme.typography.bodyMedium,
									fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
									color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
								)
							}
							if (isSelected) {
								Icon(
									imageVector = Icons.Default.Check,
									contentDescription = null,
									tint = MaterialTheme.colorScheme.primary,
									modifier = Modifier.size(20.dp)
								)
							}
						}
					}
				}
			}
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApkMirrorSettingsBottomSheet(
	directEnabled: Boolean,
	onDirectChanged: (Boolean) -> Unit,
	onDismiss: () -> Unit
) {
	val sheetState = rememberExpandedModalSheetState()

	ModalBottomSheet(
		onDismissRequest = onDismiss,
		sheetState = sheetState,
		containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
		contentColor = MaterialTheme.colorScheme.onSurface,
		dragHandle = {
			Box(
				modifier = Modifier
					.padding(vertical = 12.dp)
					.width(36.dp)
					.height(4.dp)
					.clip(CircleShape)
					.background(MaterialTheme.colorScheme.outlineVariant)
			)
		}
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.verticalScroll(rememberScrollState())
				.padding(horizontal = 24.dp)
				.padding(bottom = 32.dp),
			verticalArrangement = Arrangement.spacedBy(16.dp)
		) {
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(12.dp)
			) {
				Box(
					modifier = Modifier
						.size(40.dp)
						.clip(RoundedCornerShape(12.dp))
						.background(MaterialTheme.colorScheme.surfaceContainerHigh),
					contentAlignment = Alignment.Center
				) {
					Icon(
						painter = painterResource(R.drawable.ic_apkmirror),
						contentDescription = null,
						tint = MaterialTheme.colorScheme.primary,
						modifier = Modifier.size(22.dp)
					)
				}
				Column {
					Text(
						text = stringResource(R.string.source_apkmirror),
						style = MaterialTheme.typography.titleMedium,
						fontWeight = FontWeight.Bold
					)
					Text(
						text = stringResource(R.string.settings_apkmirror_direct_downloads),
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
			}

			Surface(
				modifier = Modifier.fillMaxWidth(),
				shape = RoundedCornerShape(16.dp),
				color = MaterialTheme.colorScheme.surfaceContainer
			) {
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = 16.dp, vertical = 14.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(12.dp)
				) {
					Column(modifier = Modifier.weight(1f)) {
						Text(
							text = stringResource(R.string.settings_apkmirror_direct_downloads),
							style = MaterialTheme.typography.bodyMedium,
							fontWeight = FontWeight.SemiBold
						)
						Text(
							text = stringResource(R.string.settings_apkmirror_direct_downloads_desc),
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant
						)
					}
					Switch(
						checked = directEnabled,
						onCheckedChange = onDirectChanged
					)
				}
			}

			Surface(
				modifier = Modifier.fillMaxWidth(),
				shape = RoundedCornerShape(14.dp),
				color = MaterialTheme.colorScheme.surfaceContainer
			) {
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = 14.dp, vertical = 10.dp),
					verticalAlignment = CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(10.dp)
				) {
					Icon(
						imageVector = Icons.Default.Info,
						contentDescription = null,
						tint = MaterialTheme.colorScheme.primary,
						modifier = Modifier.size(18.dp)
					)
					Text(
						text = stringResource(R.string.apkmirror_cloudflare_auto_hint),
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						modifier = Modifier.weight(1f)
					)
				}
			}

			FilledTonalButton(
				onClick = onDismiss,
				modifier = Modifier.fillMaxWidth(),
				shape = RoundedCornerShape(14.dp)
			) {
				Text(text = stringResource(R.string.shizuku_setup_close))
			}
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FDroidReposBottomSheet(
	repos: List<FDroidRepoConfig>,
	onToggleRepo: (String, Boolean) -> Unit,
	onAddCustomRepo: (rawUrl: String, explicitName: String, explicitFingerprint: String) -> Result<FDroidRepoConfig>,
	onRemoveCustomRepo: (String) -> Unit,
	onDismiss: () -> Unit
) {
	val sheetState = rememberExpandedModalSheetState()
	var showAddDialog by remember { mutableStateOf(false) }

	ModalBottomSheet(
		onDismissRequest = onDismiss,
		sheetState = sheetState,
		containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
		contentColor = MaterialTheme.colorScheme.onSurface,
		dragHandle = {
			Box(
				modifier = Modifier
					.padding(vertical = 12.dp)
					.width(36.dp)
					.height(4.dp)
					.clip(CircleShape)
					.background(MaterialTheme.colorScheme.outlineVariant)
			)
		}
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.verticalScroll(rememberScrollState())
				.padding(horizontal = 24.dp)
				.padding(bottom = 32.dp),
			verticalArrangement = Arrangement.spacedBy(14.dp)
		) {
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(12.dp)
			) {
				Box(
					modifier = Modifier
						.size(40.dp)
						.clip(RoundedCornerShape(12.dp))
						.background(MaterialTheme.colorScheme.surfaceContainerHigh),
					contentAlignment = Alignment.Center
				) {
					Icon(
						painter = painterResource(R.drawable.ic_izzy),
						contentDescription = null,
						tint = MaterialTheme.colorScheme.primary,
						modifier = Modifier.size(22.dp)
					)
				}
				Column(modifier = Modifier.weight(1f)) {
					Text(
						text = stringResource(R.string.fdroid_repos_sheet_title),
						style = MaterialTheme.typography.titleMedium,
						fontWeight = FontWeight.Bold
					)
					Text(
						text = stringResource(R.string.fdroid_repos_sheet_desc),
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
			}

			Column(
				modifier = Modifier.fillMaxWidth(),
				verticalArrangement = Arrangement.spacedBy(8.dp)
			) {
				repos.forEach { repo ->
					Surface(
						modifier = Modifier.fillMaxWidth(),
						shape = RoundedCornerShape(16.dp),
						color = MaterialTheme.colorScheme.surfaceContainer
					) {
						Row(
							modifier = Modifier
								.fillMaxWidth()
								.padding(horizontal = 16.dp, vertical = 12.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(10.dp)
						) {
							Box(
								modifier = Modifier
									.size(34.dp)
									.clip(RoundedCornerShape(10.dp))
									.background(MaterialTheme.colorScheme.surfaceContainerHigh),
								contentAlignment = Alignment.Center
							) {
								Icon(
									painter = painterResource(com.apkup.ui.component.resolveScanSourceIcon(repo.name)),
									contentDescription = null,
									tint = if (repo.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
									modifier = Modifier.size(18.dp)
								)
							}
							Column(modifier = Modifier.weight(1f)) {
								Text(
									text = repo.name,
									style = MaterialTheme.typography.bodyMedium,
									fontWeight = FontWeight.SemiBold,
									maxLines = 1,
									overflow = TextOverflow.Ellipsis
								)
								Text(
									text = repo.normalizedUrl,
									style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
									color = MaterialTheme.colorScheme.onSurfaceVariant,
									maxLines = 1,
									overflow = TextOverflow.Ellipsis
								)
								if (repo.normalizedFingerprint.isBlank()) {
									Spacer(modifier = Modifier.height(4.dp))
									Surface(
										shape = CircleShape,
										color = MaterialTheme.colorScheme.tertiaryContainer
									) {
										Row(
											modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
											verticalAlignment = Alignment.CenterVertically,
											horizontalArrangement = Arrangement.spacedBy(4.dp)
										) {
											Icon(
												imageVector = Icons.Filled.Warning,
												contentDescription = null,
												tint = MaterialTheme.colorScheme.onTertiaryContainer,
												modifier = Modifier.size(12.dp)
											)
											Text(
												text = stringResource(R.string.custom_fdroid_unpinned_badge),
												style = MaterialTheme.typography.labelSmall,
												fontWeight = FontWeight.SemiBold,
												color = MaterialTheme.colorScheme.onTertiaryContainer
											)
										}
									}
									Text(
										text = stringResource(R.string.custom_fdroid_unpinned_hint),
										style = MaterialTheme.typography.labelSmall,
										color = MaterialTheme.colorScheme.onSurfaceVariant
									)
								} else if (repo.isTofuPinned) {
									Spacer(modifier = Modifier.height(4.dp))
									Surface(
										shape = CircleShape,
										color = MaterialTheme.colorScheme.secondaryContainer
									) {
										Row(
											modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
											verticalAlignment = Alignment.CenterVertically,
											horizontalArrangement = Arrangement.spacedBy(4.dp)
										) {
											Icon(
												imageVector = Icons.Filled.Info,
												contentDescription = null,
												tint = MaterialTheme.colorScheme.onSecondaryContainer,
												modifier = Modifier.size(12.dp)
											)
											Text(
												text = stringResource(R.string.custom_fdroid_tofu_pinned_badge),
												style = MaterialTheme.typography.labelSmall,
												fontWeight = FontWeight.SemiBold,
												color = MaterialTheme.colorScheme.onSecondaryContainer
											)
										}
									}
									Text(
										text = stringResource(
											R.string.custom_fdroid_tofu_pinned_hint,
											repo.normalizedFingerprint.take(16)
										),
										style = MaterialTheme.typography.labelSmall,
										color = MaterialTheme.colorScheme.onSurfaceVariant
									)
								} else if (repo.normalizedFingerprint.length >= 16) {
									val shortFp = repo.normalizedFingerprint.take(8) + "…" + repo.normalizedFingerprint.takeLast(8)
									Text(
										text = "SHA-256: $shortFp",
										style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
										color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
										maxLines = 1
									)
								}
							}
							if (!repo.isBuiltIn) {
								androidx.compose.material3.IconButton(
									onClick = { onRemoveCustomRepo(repo.id) }
								) {
									Icon(
										imageVector = Icons.Default.Delete,
										contentDescription = stringResource(R.string.fdroid_repo_delete_cd),
										tint = MaterialTheme.colorScheme.error
									)
								}
							}
							Switch(
								checked = repo.enabled,
								onCheckedChange = { enabled -> onToggleRepo(repo.id, enabled) }
							)
						}
					}
				}
			}

			OutlinedButton(
				onClick = { showAddDialog = true },
				modifier = Modifier.fillMaxWidth(),
				shape = RoundedCornerShape(14.dp)
			) {
				Icon(
					imageVector = Icons.Default.Add,
					contentDescription = null,
					modifier = Modifier.size(18.dp)
				)
				Spacer(Modifier.width(8.dp))
				Text(text = stringResource(R.string.fdroid_repo_add_button))
			}

			FilledTonalButton(
				onClick = onDismiss,
				modifier = Modifier.fillMaxWidth(),
				shape = RoundedCornerShape(14.dp)
			) {
				Text(text = stringResource(R.string.shizuku_setup_close))
			}
		}
	}

	if (showAddDialog) {
		var rawUrl by remember { mutableStateOf("") }
		var customName by remember { mutableStateOf("") }
		var customFingerprint by remember { mutableStateOf("") }
		var errorMessage by remember { mutableStateOf<String?>(null) }

		AlertDialog(
			onDismissRequest = { showAddDialog = false },
			shape = RoundedCornerShape(28.dp),
			containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
			title = { Text(stringResource(R.string.fdroid_repo_add_title)) },
			text = {
				Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
					Text(
						text = stringResource(R.string.fdroid_repo_add_desc),
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
					OutlinedTextField(
						value = rawUrl,
						onValueChange = {
							rawUrl = it
							errorMessage = null
						},
						label = { Text(stringResource(R.string.fdroid_repo_url_label)) },
						placeholder = { Text(stringResource(R.string.fdroid_repo_url_hint)) },
						singleLine = true,
						modifier = Modifier.fillMaxWidth()
					)
					OutlinedTextField(
						value = customName,
						onValueChange = { customName = it },
						label = { Text(stringResource(R.string.fdroid_repo_name_label)) },
						singleLine = true,
						modifier = Modifier.fillMaxWidth()
					)
					OutlinedTextField(
						value = customFingerprint,
						onValueChange = {
							customFingerprint = it
							errorMessage = null
						},
						label = { Text(stringResource(R.string.fdroid_repo_fingerprint_label)) },
						singleLine = true,
						modifier = Modifier.fillMaxWidth()
					)
					errorMessage?.let { err ->
						Text(
							text = err,
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.error
						)
					}
				}
			},
			confirmButton = {
				Button(
					onClick = {
						val res = onAddCustomRepo(rawUrl, customName, customFingerprint)
						if (res.isSuccess) {
							showAddDialog = false
						} else {
							errorMessage = res.exceptionOrNull()?.message
						}
					},
					enabled = rawUrl.isNotBlank(),
					shape = CircleShape
				) {
					Text(stringResource(R.string.github_token_dialog_save))
				}
			},
			dismissButton = {
				TextButton(onClick = { showAddDialog = false }) {
					Text(stringResource(R.string.action_cancel))
				}
			}
		)
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WifiDownloadBottomSheet(
	wifiOnly: Boolean,
	onWifiOnlyChanged: (Boolean) -> Unit,
	chargingOnly: Boolean,
	onChargingOnlyChanged: (Boolean) -> Unit,
	bgCheckWifiOnly: Boolean,
	onBgCheckWifiOnlyChanged: (Boolean) -> Unit,
	onDismiss: () -> Unit
) {
	val sheetState = rememberExpandedModalSheetState()

	ModalBottomSheet(
		onDismissRequest = onDismiss,
		sheetState = sheetState,
		containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
		contentColor = MaterialTheme.colorScheme.onSurface,
		dragHandle = {
			Box(
				modifier = Modifier
					.padding(vertical = 12.dp)
					.width(36.dp)
					.height(4.dp)
					.clip(CircleShape)
					.background(MaterialTheme.colorScheme.outlineVariant)
			)
		}
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 24.dp)
				.padding(bottom = 32.dp),
			verticalArrangement = Arrangement.spacedBy(16.dp)
		) {
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(12.dp)
			) {
				Box(
					modifier = Modifier
						.size(40.dp)
						.clip(RoundedCornerShape(12.dp))
						.background(MaterialTheme.colorScheme.surfaceContainerHigh),
					contentAlignment = Alignment.Center
				) {
					Icon(
						painter = painterResource(R.drawable.ic_wifi),
						contentDescription = null,
						tint = MaterialTheme.colorScheme.primary,
						modifier = Modifier.size(22.dp)
					)
				}
				Column {
					Text(
						text = stringResource(R.string.settings_wifi_dialog_title),
						style = MaterialTheme.typography.titleMedium,
						fontWeight = FontWeight.Bold
					)
					Text(
						text = stringResource(R.string.settings_wifi_dialog_desc),
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
			}

			Surface(
				modifier = Modifier.fillMaxWidth(),
				shape = RoundedCornerShape(16.dp),
				color = MaterialTheme.colorScheme.surfaceContainer
			) {
				Column(modifier = Modifier.fillMaxWidth()) {
					// 1. Updates nur über WLAN laden
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.padding(horizontal = 16.dp, vertical = 14.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(12.dp)
					) {
						Column(modifier = Modifier.weight(1f)) {
							Text(
								text = stringResource(R.string.settings_download_wifi_only),
								style = MaterialTheme.typography.bodyMedium,
								fontWeight = FontWeight.SemiBold
							)
							Text(
								text = stringResource(R.string.settings_download_wifi_only_desc),
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant
							)
						}
						Switch(
							checked = wifiOnly,
							onCheckedChange = onWifiOnlyChanged
						)
					}

					HorizontalDivider(
						modifier = Modifier.padding(horizontal = 16.dp),
						thickness = 0.5.dp,
						color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
					)

					// 2. Nur beim Laden
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.padding(horizontal = 16.dp, vertical = 14.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(12.dp)
					) {
						Column(modifier = Modifier.weight(1f)) {
							Text(
								text = stringResource(R.string.settings_auto_install_charging_only),
								style = MaterialTheme.typography.bodyMedium,
								fontWeight = FontWeight.SemiBold
							)
							Text(
								text = stringResource(R.string.settings_download_charging_only_desc),
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant
							)
						}
						Switch(
							checked = chargingOnly,
							onCheckedChange = onChargingOnlyChanged
						)
					}

					HorizontalDivider(
						modifier = Modifier.padding(horizontal = 16.dp),
						thickness = 0.5.dp,
						color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
					)

					// 3. Hintergrundprüfungen nur über WLAN
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.padding(horizontal = 16.dp, vertical = 14.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(12.dp)
					) {
						Column(modifier = Modifier.weight(1f)) {
							Text(
								text = stringResource(R.string.settings_bg_check_wifi_only),
								style = MaterialTheme.typography.bodyMedium,
								fontWeight = FontWeight.SemiBold
							)
							Text(
								text = stringResource(R.string.settings_bg_check_wifi_only_desc),
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant
							)
						}
						Switch(
							checked = bgCheckWifiOnly,
							onCheckedChange = onBgCheckWifiOnlyChanged
						)
					}
				}
			}

			FilledTonalButton(
				onClick = onDismiss,
				modifier = Modifier.fillMaxWidth(),
				shape = RoundedCornerShape(14.dp)
			) {
				Text(text = stringResource(R.string.shizuku_setup_close))
			}
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ObtainiumImportBottomSheet(
	preview: ObtainiumImportPreview,
	onDismiss: () -> Unit,
	onConfirmImport: (importMappings: Boolean, importFDroidRepos: Boolean, selectedWatchedPackages: Set<String>) -> Unit
) {
	val sheetState = rememberExpandedModalSheetState()
	var importMappings by remember { mutableStateOf(preview.matchedInstalledBindings.isNotEmpty()) }
	var importFDroidRepos by remember { mutableStateOf(preview.detectedFDroidRepos.isNotEmpty()) }
	val selectedWatched = remember(preview) {
		mutableStateMapOf<String, Boolean>().apply {
			preview.uninstalledCandidateApps.forEach { put(it.packageName, false) }
		}
	}

	ModalBottomSheet(
		onDismissRequest = onDismiss,
		sheetState = sheetState,
		containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
		contentColor = MaterialTheme.colorScheme.onSurface,
		dragHandle = {
			Box(
				modifier = Modifier
					.padding(vertical = 12.dp)
					.width(36.dp)
					.height(4.dp)
					.clip(CircleShape)
					.background(MaterialTheme.colorScheme.outlineVariant)
			)
		}
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.verticalScroll(rememberScrollState())
				.navigationBarsPadding()
				.imePadding()
				.padding(horizontal = 24.dp)
				.padding(bottom = 28.dp),
			verticalArrangement = Arrangement.spacedBy(12.dp)
		) {
			Text(
				text = stringResource(R.string.obtainium_import_title),
				style = MaterialTheme.typography.titleMedium,
				fontWeight = FontWeight.Bold
			)
			Text(
				text = stringResource(R.string.obtainium_import_subtitle, preview.totalEntries),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)

			if (!preview.hasAnyImportableData) {
				Surface(
					modifier = Modifier.fillMaxWidth(),
					shape = RoundedCornerShape(14.dp),
					color = MaterialTheme.colorScheme.surfaceContainer
				) {
					Text(
						text = stringResource(R.string.obtainium_import_nothing),
						style = MaterialTheme.typography.bodyMedium,
						modifier = Modifier.padding(14.dp)
					)
				}
			} else {
				if (preview.matchedInstalledBindings.isNotEmpty()) {
					Surface(
						modifier = Modifier.fillMaxWidth(),
						shape = RoundedCornerShape(14.dp),
						color = MaterialTheme.colorScheme.surfaceContainer
					) {
						Row(
							modifier = Modifier
								.fillMaxWidth()
								.clickable { importMappings = !importMappings }
								.padding(horizontal = 12.dp, vertical = 10.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(8.dp)
						) {
							Checkbox(checked = importMappings, onCheckedChange = { importMappings = it })
							Column(modifier = Modifier.weight(1f)) {
								Text(
									text = stringResource(R.string.obtainium_import_section_mappings, preview.matchedInstalledBindings.size),
									style = MaterialTheme.typography.bodyMedium,
									fontWeight = FontWeight.SemiBold
								)
								val sampleNames = preview.matchedInstalledBindings.keys.take(4).joinToString(", ") { pkg ->
									preview.matchedInstalledNames[pkg] ?: pkg
								}
								Text(
									text = sampleNames,
									style = MaterialTheme.typography.bodySmall,
									color = MaterialTheme.colorScheme.onSurfaceVariant,
									maxLines = 2,
									overflow = TextOverflow.Ellipsis
								)
							}
						}
					}
				}

				if (preview.detectedFDroidRepos.isNotEmpty()) {
					Surface(
						modifier = Modifier.fillMaxWidth(),
						shape = RoundedCornerShape(14.dp),
						color = MaterialTheme.colorScheme.surfaceContainer
					) {
						Row(
							modifier = Modifier
								.fillMaxWidth()
								.clickable { importFDroidRepos = !importFDroidRepos }
								.padding(horizontal = 12.dp, vertical = 10.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(8.dp)
						) {
							Checkbox(checked = importFDroidRepos, onCheckedChange = { importFDroidRepos = it })
							Column(modifier = Modifier.weight(1f)) {
								Text(
									text = stringResource(R.string.obtainium_import_section_fdroid, preview.detectedFDroidRepos.size),
									style = MaterialTheme.typography.bodyMedium,
									fontWeight = FontWeight.SemiBold
								)
								Text(
									text = preview.detectedFDroidRepos.joinToString(", ") { it.name },
									style = MaterialTheme.typography.bodySmall,
									color = MaterialTheme.colorScheme.onSurfaceVariant,
									maxLines = 2,
									overflow = TextOverflow.Ellipsis
								)
							}
						}
					}
				}

				if (preview.uninstalledCandidateApps.isNotEmpty()) {
					Surface(
						modifier = Modifier.fillMaxWidth(),
						shape = RoundedCornerShape(14.dp),
						color = MaterialTheme.colorScheme.surfaceContainer
					) {
						Column(
							modifier = Modifier.padding(12.dp),
							verticalArrangement = Arrangement.spacedBy(6.dp)
						) {
							Text(
								text = stringResource(R.string.obtainium_import_section_watched, preview.uninstalledCandidateApps.size),
								style = MaterialTheme.typography.bodyMedium,
								fontWeight = FontWeight.SemiBold
							)
							Text(
								text = stringResource(R.string.obtainium_import_watched_desc),
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant
							)
							preview.uninstalledCandidateApps.forEach { candidate ->
								val checked = selectedWatched[candidate.packageName] == true
								Row(
									modifier = Modifier
										.fillMaxWidth()
										.clickable { selectedWatched[candidate.packageName] = !checked }
										.padding(vertical = 2.dp),
									verticalAlignment = Alignment.CenterVertically,
									horizontalArrangement = Arrangement.spacedBy(8.dp)
								) {
									Checkbox(
										checked = checked,
										onCheckedChange = { selectedWatched[candidate.packageName] = it }
									)
									Column(modifier = Modifier.weight(1f)) {
										Text(
											text = candidate.appName,
											style = MaterialTheme.typography.bodySmall,
											fontWeight = FontWeight.SemiBold,
											maxLines = 1,
											overflow = TextOverflow.Ellipsis
										)
										Text(
											text = candidate.packageName,
											style = MaterialTheme.typography.labelSmall,
											color = MaterialTheme.colorScheme.onSurfaceVariant,
											maxLines = 1,
											overflow = TextOverflow.Ellipsis
										)
									}
								}
							}
						}
					}
				}
			}

			val chosenWatchedSet = selectedWatched.filterValues { it }.keys
			val hasSelection = (importMappings && preview.matchedInstalledBindings.isNotEmpty()) ||
				(importFDroidRepos && preview.detectedFDroidRepos.isNotEmpty()) ||
				chosenWatchedSet.isNotEmpty()

			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.spacedBy(10.dp)
			) {
				FilledTonalButton(
					onClick = onDismiss,
					modifier = Modifier.weight(1f),
					shape = CircleShape
				) {
					Text(text = stringResource(android.R.string.cancel))
				}
				Button(
					onClick = { onConfirmImport(importMappings, importFDroidRepos, chosenWatchedSet) },
					enabled = hasSelection,
					modifier = Modifier.weight(1f),
					shape = CircleShape
				) {
					Text(text = stringResource(R.string.obtainium_import_btn_apply))
				}
			}
		}
	}
}

private fun readUtf8FromSafUri(
	context: Context,
	uri: android.net.Uri,
	maxBytes: Int = SettingsViewModel.MAX_BACKUP_JSON_CHARS
): String? = runCatching {
	context.contentResolver.openInputStream(uri)?.use { stream ->
		val out = java.io.ByteArrayOutputStream()
		val buf = ByteArray(8192)
		var total = 0
		while (true) {
			val read = stream.read(buf)
			if (read == -1) break
			total += read
			if (total > maxBytes) {
				throw IllegalArgumentException("SAF file exceeds maximum allowed size ($maxBytes bytes)")
			}
			out.write(buf, 0, read)
		}
		out.toString(Charsets.UTF_8.name())
	}
}.getOrNull()
