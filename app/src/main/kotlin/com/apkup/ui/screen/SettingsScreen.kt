package com.apkup.ui.screen

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.ButtonDefaults
import com.apkup.util.Badger
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.apkup.R
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
import com.apkup.util.SessionInstaller
import com.apkup.viewmodel.AppsViewModel
import com.apkup.viewmodel.MainViewModel
import com.apkup.viewmodel.SettingsViewModel
import com.apkup.viewmodel.TokenValidationResult
import com.apkup.viewmodel.UpdatesViewModel
import com.apkup.prefs.Prefs
import com.apkup.ui.component.ChangelogBottomSheet
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel


@Composable
fun SettingsScreen(
	viewModel: SettingsViewModel = koinViewModel(),
	appsViewModel: AppsViewModel = koinViewModel(),
	updatesViewModel: UpdatesViewModel = koinViewModel(),
	mainViewModel: MainViewModel = koinViewModel()
) = Column {
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
			}
		}
		lifecycleOwner.lifecycle.addObserver(observer)
		onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
	}
	val prefs = koinInject<Prefs>()
	val ignoredVersions = viewModel.ignoredVersions().collectAsStateWithLifecycle().value
	val ignoredApps = viewModel.ignoredApps().collectAsStateWithLifecycle().value
	val shizukuEnabled = viewModel.shizukuEnabled().collectAsStateWithLifecycle().value
	val shizukuStatus = viewModel.shizukuSetupStatus().collectAsStateWithLifecycle().value
	val showShizukuSheet = viewModel.showShizukuSetupSheet().collectAsStateWithLifecycle().value
	var showHiddenAppsSheet by remember { mutableStateOf(false) }
	var showIgnoredVersionsSheet by remember { mutableStateOf(false) }
	var showChangelogSheet by remember { mutableStateOf(false) }
	var showIntervalSheet by remember { mutableStateOf(false) }
	var showApkMirrorSheet by remember { mutableStateOf(false) }
	var showWifiDownloadSheet by remember { mutableStateOf(false) }
	var showGitHubTokenDialog by remember { mutableStateOf(false) }
	var showGitLabTokenDialog by remember { mutableStateOf(false) }
	var isAdvancedExpanded by rememberSaveable { mutableStateOf(false) }
	var configRefreshKey by remember { mutableIntStateOf(0) }

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

	val exportBackupLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.CreateDocument("application/json")
	) { uri ->
		if (uri != null) {
			val success = runCatching {
				val json = viewModel.exportBackupJson()
				context.contentResolver.openOutputStream(uri)?.use { stream ->
					stream.write(json.toByteArray(Charsets.UTF_8))
				} ?: throw IllegalStateException("Unable to open output stream")
			}.isSuccess
			viewModel.notifyBackupExported(success)
		}
	}

	val exportObtainiumLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.CreateDocument("application/json")
	) { uri ->
		if (uri != null) {
			val success = runCatching {
				val appNames = (appsViewModel.state().value as? com.apkup.data.ui.AppsUiState.Success)?.apps?.associate { it.packageName to it.name }.orEmpty()
				val json = viewModel.exportObtainiumJson(appNames)
				context.contentResolver.openOutputStream(uri)?.use { stream ->
					stream.write(json.toByteArray(Charsets.UTF_8))
				} ?: throw IllegalStateException("Unable to open output stream")
			}.isSuccess
			viewModel.notifyObtainiumExported(success)
		}
	}

	val importBackupLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.OpenDocument()
	) { uri ->
		if (uri != null) {
			val rawJson = runCatching {
				context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
			}.getOrNull()
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

		val badger = koinInject<Badger>()
		val activeIssues = remember(isBatteryIgnoring, areNotificationsEnabled, hasInstallPermission, shizukuEnabled, shizukuStatus.isBinderRunning) {
			val list = mutableListOf<SetupWarningItem>()
			if (!hasInstallPermission) {
				list.add(
					SetupWarningItem(
						id = "install",
						title = context.getString(R.string.warning_install_title),
						subtitle = context.getString(R.string.warning_install_subtitle),
						shortName = context.getString(R.string.settings_install_unknown_apps),
						actionText = context.getString(R.string.warning_action_allow),
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
						title = context.getString(R.string.warning_notifications_title),
						subtitle = context.getString(R.string.warning_notifications_subtitle),
						shortName = context.getString(R.string.settings_notifications),
						actionText = context.getString(R.string.warning_action_enable),
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
						title = context.getString(R.string.warning_battery_title),
						subtitle = context.getString(R.string.warning_battery_subtitle),
						shortName = context.getString(R.string.settings_battery_optimization),
						actionText = context.getString(R.string.warning_action_exempt),
						onClick = {
							runCatching {
								val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
									addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
								}
								context.startActivity(intent)
							}.onFailure {
								runCatching {
									context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
										addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
									})
								}
							}
						}
					)
				)
			}
			if (shizukuEnabled && !shizukuStatus.isBinderRunning) {
				list.add(
					SetupWarningItem(
						id = "shizuku",
						title = context.getString(R.string.warning_shizuku_title),
						subtitle = context.getString(R.string.warning_shizuku_subtitle),
						shortName = "Shizuku",
						actionText = context.getString(R.string.warning_action_check),
						onClick = { viewModel.openShizukuSetupSheet() }
					)
				)
			}
			list
		}

		LaunchedEffect(activeIssues.size) {
			badger.changeSettingsBadge(if (activeIssues.isNotEmpty()) "!" else "")
		}
		LazyColumn(
			modifier = Modifier
				.fillMaxSize()
				.offset { IntOffset(0, overscrollOffset.roundToInt()) },
			contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 120.dp),
			verticalArrangement = Arrangement.spacedBy(14.dp)
		) {
			// TemporÃ¤re Status-Warnkarte (nur sichtbar, wenn Einstellungen fehlen)
			if (activeIssues.isNotEmpty()) {
				item(key = "setup_active_warnings") {
					val isDark = androidx.compose.foundation.isSystemInDarkTheme()
					Surface(
						modifier = Modifier.fillMaxWidth(),
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
									       else activeIssues.joinToString(" â€¢ ") { it.shortName },
									style = MaterialTheme.typography.bodySmall,
									color = if (isDark) Color(0xFFD1D5DB) else Color(0xFF5D4037),
									maxLines = 1,
									overflow = TextOverflow.Ellipsis
								)
							}
							FilledTonalButton(
								onClick = { activeIssues.first().onClick() },
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
		item(key = "section_sources_$configRefreshKey") {
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
						if (directApkMirrorEnabled) stringResource(R.string.settings_apkmirror_direct_downloads) else null
					} else null,
					onClick = { showApkMirrorSheet = true }
				)
				SwitchSetting(
					{ viewModel.getUsePlay() },
					{ viewModel.setUsePlay(it) },
					stringResource(R.string.source_play),
					R.drawable.ic_play
				)
				SwitchSetting(
					{ viewModel.getUseIzzyOnDroid() },
					{ viewModel.setUseIzzyOnDroid(it) },
					stringResource(R.string.source_izzyondroid),
					R.drawable.ic_izzy
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
			}
		}

		// Sektion 3: Automatische Suche & Zeitplan
		item(key = "section_background_checks_$configRefreshKey") {
			val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
			var bgChecksEnabled by remember(configRefreshKey) {
				mutableStateOf(viewModel.getBackgroundChecksEnabled())
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

			SettingsSection(stringResource(R.string.settings_section_background_checks)) {
				SwitchSetting(
					getValue = { bgChecksEnabled },
					setValue = { enabled ->
						viewModel.setBackgroundChecksEnabled(enabled, launcher)
						bgChecksEnabled = viewModel.getBackgroundChecksEnabled()
					},
					text = stringResource(R.string.settings_background_checks),
					icon = R.drawable.ic_refresh,
					subtitle = if (bgChecksEnabled) activeIntervalLabel else null,
					onClick = { showIntervalSheet = true }
				)
			}
		}

		// Sektion 4: Installation & Automatisierung
		item(key = "section_install_automation_${configRefreshKey}_${shizukuEnabled}_$hasInstallPermission") {
			SettingsSection(stringResource(R.string.settings_section_install_automation)) {
				// 2. Shizuku-Installer
				SwitchSetting(
					getValue = { viewModel.getUseShizukuInstaller() },
					setValue = { viewModel.setUseShizukuInstaller(it) },
					text = stringResource(R.string.settings_shizuku_installer),
					icon = R.drawable.ic_root,
					subtitle = stringResource(R.string.settings_shizuku_installer_subtitle),
					onClick = { viewModel.openShizukuSetupSheet() }
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

				// 3. Wenn Shizuku aktiv: Autonome Updates & Schutzregeln
				if (shizukuEnabled) {
					val autoInstallEnabled = remember(configRefreshKey, shizukuEnabled) { viewModel.getAutoInstallUpdates() }
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
							if (autoInstallEnabled) {
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
						else -> stringResource(R.string.settings_wifi_all_networks_desc)
					},
					onClick = { showWifiDownloadSheet = true }
				)
			}
		}

		// Sektion 5: Erweiterte Einstellungen (App-Verwaltung, System & Datensicherung - einklappbar)
		item(key = "section_advanced_${isAdvancedExpanded}_${isBatteryIgnoring}_${areNotificationsEnabled}_${hasInstallPermission}_$configRefreshKey") {
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
						Icon(
							imageVector = if (isAdvancedExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
							contentDescription = null,
							tint = MaterialTheme.colorScheme.onSurfaceVariant,
							modifier = Modifier.size(22.dp)
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

							// 2. System & Berechtigungen
							Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
								Text(
									text = stringResource(R.string.settings_section_system_permissions),
									style = MaterialTheme.typography.labelLarge,
									fontWeight = FontWeight.Bold,
									color = MaterialTheme.colorScheme.primary
								)
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
									onClick = {
										runCatching {
											val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
												addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
											}
											context.startActivity(intent)
										}.onFailure {
											runCatching {
												context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
													addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
												})
											}
										}
									}
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

							HorizontalDivider(
								thickness = 1.dp,
								color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
							)

							// 3. Datensicherung & Export
							Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
								OutlinedButton(
									onClick = { exportObtainiumLauncher.launch("apkup-obtainium-export.json") },
									modifier = Modifier
										.fillMaxWidth()
										.padding(top = 4.dp),
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

					// 3. Zweite Bento-Zeile (GitHub & Hilfe/Wiki nebeneinander als 2x2-Vervollständigung)
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.height(IntrinsicSize.Min),
						horizontalArrangement = Arrangement.spacedBy(8.dp)
					) {
						Surface(
							onClick = { handler.openUri("https://github.com/DurchEins/apkup") },
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
							onClick = { handler.openUri("https://github.com/DurchEins/apkup/wiki") },
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

					// 4. Dezenter rahmenloser Footer-Link für das rumboalla-Originalprojekt
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.clip(RoundedCornerShape(10.dp))
							.clickable { handler.openUri("https://github.com/rumboalla/apkupdater") }
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
				val webIntent = Intent(
					Intent.ACTION_VIEW,
					"https://play.google.com/store/apps/details?id=${SessionInstaller.SHIZUKU_PACKAGE_NAME}".toUri()
				).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
				runCatching { context.startActivity(marketIntent) }
					.onFailure { runCatching { context.startActivity(webIntent) } }
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
				prefs.lastSeenVersionCode.put(BuildConfig.VERSION_CODE.toLong())
			}
		)
	}

	if (showGitHubTokenDialog) {
		ApiTokenDialog(
			title = stringResource(R.string.github_token_dialog_title),
			description = stringResource(R.string.github_token_dialog_desc),
			placeholder = stringResource(R.string.github_token_dialog_hint),
			initialToken = viewModel.getGitHubToken(),
			createTokenUrl = "https://github.com/settings/tokens/new?description=ApkUp&scopes=",
			createTokenButtonText = stringResource(R.string.token_action_generate_github),
			guideUrl = "https://github.com/DurchEins/apkup/wiki",
			onDismiss = { showGitHubTokenDialog = false },
			onVerifyToken = { raw -> viewModel.validateGitHubToken(raw) },
			onVerifiedMessage = { msg -> viewModel.notifyTextSnack(msg) },
			onSaveToken = { token ->
				viewModel.setGitHubToken(token)
				configRefreshKey++
			}
		)
	}

	if (showGitLabTokenDialog) {
		ApiTokenDialog(
			title = stringResource(R.string.gitlab_token_dialog_title),
			description = stringResource(R.string.gitlab_token_dialog_desc),
			placeholder = stringResource(R.string.gitlab_token_dialog_hint),
			initialToken = viewModel.getGitLabToken(),
			createTokenUrl = "https://gitlab.com/-/user_settings/personal_access_tokens?name=ApkUp&scopes=read_api",
			createTokenButtonText = stringResource(R.string.token_action_generate_gitlab),
			guideUrl = "https://github.com/DurchEins/apkup/wiki",
			onDismiss = { showGitLabTokenDialog = false },
			onVerifyToken = { raw -> viewModel.validateGitLabToken(raw) },
			onVerifiedMessage = { msg -> viewModel.notifyTextSnack(msg) },
			onSaveToken = { token ->
				viewModel.setGitLabToken(token)
				configRefreshKey++
			}
		)
	}
}
}

@Composable
private fun ApiTokenDialog(
	title: String,
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
	val context = LocalContext.current
	val uriHandler = LocalUriHandler.current
	val coroutineScope = rememberCoroutineScope()
	var tokenText by remember { mutableStateOf(initialToken) }
	var isTokenVisible by remember { mutableStateOf(false) }
	var isVerifying by remember { mutableStateOf(false) }
	var isSaving by remember { mutableStateOf(false) }
	var validationResult by remember { mutableStateOf<TokenValidationResult?>(null) }
	var validatedTokenValue by remember { mutableStateOf<String?>(null) }
	var isGuideExpanded by rememberSaveable { mutableStateOf(initialToken.isBlank()) }

	fun formatValidationMessage(result: TokenValidationResult): String {
		return when (result) {
			is TokenValidationResult.Valid -> {
				if (!result.rateLimitRemaining.isNullOrBlank() && !result.rateLimitTotal.isNullOrBlank()) {
					context.getString(
						R.string.token_validation_valid_with_limit,
						result.username,
						result.rateLimitRemaining,
						result.rateLimitTotal
					)
				} else {
					context.getString(R.string.token_validation_valid_user, result.username)
				}
			}
			is TokenValidationResult.Invalid -> {
				context.getString(R.string.token_validation_invalid, result.httpCode)
			}
			is TokenValidationResult.Error -> {
				context.getString(R.string.token_validation_error, result.message)
			}
		}
	}

	AlertDialog(
		onDismissRequest = {
			if (!isSaving) onDismiss()
		},
		shape = RoundedCornerShape(28.dp),
		containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
		title = { Text(title) },
		text = {
			Column(
				modifier = Modifier.verticalScroll(rememberScrollState()),
				verticalArrangement = Arrangement.spacedBy(10.dp)
			) {
				Text(
					text = description,
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)

				if (!createTokenUrl.isNullOrBlank() || !guideUrl.isNullOrBlank()) {
					Surface(
						shape = RoundedCornerShape(14.dp),
						color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
						modifier = Modifier.fillMaxWidth()
					) {
						Column(
							modifier = Modifier
								.fillMaxWidth()
								.padding(10.dp),
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
									horizontalArrangement = Arrangement.spacedBy(6.dp)
								) {
									Icon(
										imageVector = Icons.Default.Info,
										contentDescription = null,
										tint = MaterialTheme.colorScheme.primary,
										modifier = Modifier.size(16.dp)
									)
									Text(
										text = stringResource(R.string.token_guide_header),
										style = MaterialTheme.typography.labelMedium,
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
								Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
												onClick = { uriHandler.openUri(createTokenUrl) },
												modifier = Modifier.weight(1f),
												contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
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
												onClick = { uriHandler.openUri(guideUrl) },
												modifier = Modifier.weight(1f),
												contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
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

				OutlinedTextField(
					value = tokenText,
					onValueChange = {
						tokenText = it
						if (it.trim() != validatedTokenValue) {
							validationResult = null
						}
					},
					label = { Text("Token") },
					placeholder = { Text(placeholder) },
					singleLine = true,
					visualTransformation = if (isTokenVisible) {
						androidx.compose.ui.text.input.VisualTransformation.None
					} else {
						androidx.compose.ui.text.input.PasswordVisualTransformation()
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
							val trimmed = tokenText.trim()
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
						enabled = tokenText.isNotBlank() && !isVerifying && !isSaving,
						modifier = Modifier.fillMaxWidth(),
						shape = CircleShape
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
		},
		confirmButton = {
			Button(
				onClick = {
					val trimmed = tokenText.trim()
					if (trimmed.isEmpty() || onVerifyToken == null) {
						onSaveToken(trimmed)
						onDismiss()
					} else if (validatedTokenValue == trimmed && validationResult != null) {
						// Token wurde im Dialog bereits für exakt diesen Wert geprüft -> direkt speichern
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
									// Bei ungültigem Token bleibt der Dialog beim ersten Klick geöffnet,
									// damit der Nutzer die Fehlermeldung sofort sieht und korrigieren kann.
								}
							}
						}
					}
				},
				enabled = !isSaving && !isVerifying,
				shape = CircleShape
			) {
				if (isSaving) {
					CircularProgressIndicator(
						modifier = Modifier.size(16.dp),
						strokeWidth = 2.dp,
						color = MaterialTheme.colorScheme.onPrimary
					)
					Spacer(modifier = Modifier.width(8.dp))
					Text(stringResource(R.string.token_validation_checking))
				} else {
					Text(stringResource(R.string.github_token_dialog_save))
				}
			}
		},
		dismissButton = {
			Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				if (initialToken.isNotEmpty()) {
					TextButton(
						onClick = {
							onSaveToken("")
							onDismiss()
						},
						enabled = !isSaving && !isVerifying
					) {
						Text(
							stringResource(R.string.github_token_dialog_clear),
							color = MaterialTheme.colorScheme.error
						)
					}
				}
				TextButton(
					onClick = onDismiss,
					enabled = !isSaving && !isVerifying
				) {
					Text(stringResource(android.R.string.cancel))
				}
			}
		}
	)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackgroundCheckIntervalSelector(
	selectedHours: Int,
	enabled: Boolean,
	onSelectHours: (Int) -> Unit
) {
	val intervalOptions = listOf(
		BackgroundCheckInterval.HOURS_6 to ("6h" to R.string.settings_interval_6h),
		BackgroundCheckInterval.HOURS_12 to ("12h" to R.string.settings_interval_12h),
		BackgroundCheckInterval.HOURS_24 to ("24h" to R.string.settings_interval_24h),
		BackgroundCheckInterval.HOURS_48 to ("48h" to R.string.settings_interval_48h),
		BackgroundCheckInterval.DAYS_7 to ("7d" to R.string.settings_interval_7d)
	)

	Surface(
		modifier = Modifier
			.fillMaxWidth()
			.padding(top = 2.dp),
		shape = RoundedCornerShape(14.dp),
		color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (enabled) 0.75f else 0.4f)
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 10.dp, vertical = 8.dp),
			verticalArrangement = Arrangement.spacedBy(6.dp)
		) {
			val activeOption = intervalOptions.firstOrNull { it.first.hours == selectedHours }
			val activeLabel = activeOption?.let { stringResource(it.second.second) } ?: ""
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = CenterVertically,
				horizontalArrangement = Arrangement.SpaceBetween
			) {
				Text(
					text = stringResource(R.string.settings_background_interval_title),
					style = MaterialTheme.typography.labelMedium,
					fontWeight = FontWeight.SemiBold,
					color = if (enabled) {
						MaterialTheme.colorScheme.onSurface
					} else {
						MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
					}
				)
				if (activeLabel.isNotBlank()) {
					Text(
						text = activeLabel,
						style = MaterialTheme.typography.labelSmall,
						color = MaterialTheme.colorScheme.primary,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)
				}
			}

			Row(
				modifier = Modifier
					.fillMaxWidth()
					.horizontalScroll(rememberScrollState()),
				horizontalArrangement = Arrangement.spacedBy(6.dp),
				verticalAlignment = CenterVertically
			) {
				intervalOptions.forEach { (interval, chipInfo) ->
					val (shortLabel, _) = chipInfo
					val isSelected = selectedHours == interval.hours
					FilterChip(
						selected = isSelected,
						onClick = { onSelectHours(interval.hours) },
						enabled = enabled,
						label = {
							Text(
								text = shortLabel,
								style = MaterialTheme.typography.labelMedium,
								fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
							)
						},
						shape = CircleShape,
						colors = FilterChipDefaults.filterChipColors(
							selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
							selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
						)
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
	val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
						painter = painterResource(R.drawable.ic_root),
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
	val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
						Text("\u2190 Zur\u00FCck")
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
	val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
			fontWeight = FontWeight.SemiBold
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
				.clip(RoundedCornerShape(12.dp))
				.background(MaterialTheme.colorScheme.surfaceContainerHigh),
			contentAlignment = Alignment.Center
		) {
			Icon(
				imageVector = Icons.Filled.Settings,
				contentDescription = stringResource(R.string.tab_settings),
				tint = MaterialTheme.colorScheme.primary,
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
		val badgeBg = if (isGranted) {
			if (isDark) Color(0xFF1B3820) else Color(0xFFE8F5E9)
		} else {
			if (isDark) Color(0xFF3E1A1A) else Color(0xFFFFEBEE)
		}
		val badgeColor = if (isGranted) {
			if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
		} else {
			if (isDark) Color(0xFFFF8A80) else Color(0xFFC62828)
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
				Icon(
					imageVector = if (isGranted) Icons.Default.Check else Icons.Default.Close,
					contentDescription = null,
					tint = badgeColor,
					modifier = Modifier.size(11.dp)
				)
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
	val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
	val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
private fun WifiDownloadBottomSheet(
	wifiOnly: Boolean,
	onWifiOnlyChanged: (Boolean) -> Unit,
	chargingOnly: Boolean,
	onChargingOnlyChanged: (Boolean) -> Unit,
	bgCheckWifiOnly: Boolean,
	onBgCheckWifiOnlyChanged: (Boolean) -> Unit,
	onDismiss: () -> Unit
) {
	val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
