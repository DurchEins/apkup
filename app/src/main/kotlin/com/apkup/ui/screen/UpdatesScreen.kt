package com.apkup.ui.screen

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apkup.R
import com.apkup.data.ui.ApkMirrorSource
import com.apkup.data.ui.AppUpdate
import com.apkup.data.ui.cleanChangelogText
import com.apkup.data.ui.GitHubSource
import com.apkup.data.ui.GitLabSource
import com.apkup.data.ui.InstallOrigin
import com.apkup.data.ui.IzzyOnDroidSource
import com.apkup.data.ui.PlaySource
import com.apkup.data.ui.Source
import com.apkup.data.ui.hasPotentialSignatureConflict
import com.apkup.prefs.Prefs
import com.apkup.service.DownloadInstallService
import com.apkup.service.DownloadTaskState
import com.apkup.ui.component.AppEmptyState
import com.apkup.ui.component.CompactUpdateCardLayout
import com.apkup.ui.component.GrandFestivalEmptyState
import com.apkup.ui.component.MultiSourceScanTray
import com.apkup.ui.component.ScanSourceStatus
import com.apkup.ui.component.LoadingImageApp
import com.apkup.ui.component.NormalUpdateCardLayout
import com.apkup.ui.component.RefreshIcon
import com.apkup.ui.component.UpdateHistoryBottomSheet
import com.apkup.ui.component.shimmering
import com.apkup.ui.component.toUpdateCardContent
import com.apkup.ui.component.updateGridColumnCount
import com.apkup.util.getAppName
import com.apkup.viewmodel.AppsViewModel
import com.apkup.viewmodel.UpdatesViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import java.util.Locale


@Composable
fun UpdatesScreen(
	viewModel: UpdatesViewModel,
	appsViewModel: AppsViewModel = koinViewModel()
) {
	var showHistorySheet by rememberSaveable { mutableStateOf(false) }

	viewModel.state().collectAsStateWithLifecycle().value.onLoading {
		UpdatesScreenLoading(
			viewModel = viewModel,
			onOpenHistory = { showHistorySheet = true }
		)
	}.onError {
		UpdatesScreenError(
			viewModel = viewModel,
			onOpenHistory = { showHistorySheet = true }
		)
	}.onSuccess {
		UpdatesScreenSuccess(
			viewModel = viewModel,
			appsViewModel = appsViewModel,
			updates = it.updates,
			hasSourceErrors = it.hasSourceErrors,
			isChecking = it.isChecking,
			checkedApps = it.checkedApps,
			totalApps = it.totalApps,
			activeSourceName = it.activeSourceName,
			sourceErrors = it.sourceErrors,
			lastCheckedAtMillis = it.lastCheckedAtMillis,
			onOpenHistory = { showHistorySheet = true }
		)
	}

	if (showHistorySheet) {
		UpdateHistoryBottomSheet(onDismiss = { showHistorySheet = false })
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesTopBar(
	viewModel: UpdatesViewModel,
	updatesCount: Int? = null,
	hasInstallableUpdates: Boolean = false,
	onOpenHistory: (() -> Unit)? = null
) = TopAppBar(
	title = {
		Row(
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(8.dp)
		) {
			Text(
				text = stringResource(R.string.tab_updates),
				style = MaterialTheme.typography.headlineMedium,
				fontWeight = FontWeight.Bold
			)
			if (updatesCount != null && updatesCount > 0) {
				Surface(
					shape = CircleShape,
					color = MaterialTheme.colorScheme.primaryContainer
				) {
					Text(
						text = updatesCount.toString(),
						modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
						style = MaterialTheme.typography.labelMedium,
						fontWeight = FontWeight.SemiBold,
						color = MaterialTheme.colorScheme.onPrimaryContainer
					)
				}
			}
		}
	},
	colors = TopAppBarDefaults.topAppBarColors(
		containerColor = MaterialTheme.colorScheme.background,
		scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
	),
	actions = {
		if (onOpenHistory != null) {
			IconButton(onClick = onOpenHistory) {
				Icon(
					imageVector = Icons.Rounded.History,
					contentDescription = stringResource(R.string.update_history_title)
				)
			}
		}
		IconButton(onClick = { viewModel.refresh() }) {
			RefreshIcon(stringResource(R.string.refresh_updates))
		}
	},
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
				imageVector = Icons.Rounded.Update,
				contentDescription = stringResource(R.string.tab_updates),
				tint = MaterialTheme.colorScheme.primary,
				modifier = Modifier.size(20.dp)
			)
		}
	}
)

@Composable
fun UpdatesScreenLoading(
	viewModel: UpdatesViewModel,
	onOpenHistory: (() -> Unit)? = null
) = Column(Modifier.fillMaxSize()) {
	val view = LocalView.current
	val prefs = koinInject<Prefs>()
	val initialSource = when {
		prefs.useApkMirror.get() -> stringResource(R.string.source_apkmirror)
		prefs.usePlay.get() -> stringResource(R.string.source_play)
		else -> null
	}
	DisposableEffect(Unit) {
		view.keepScreenOn = true
		onDispose { view.keepScreenOn = false }
	}
	UpdatesTopBar(viewModel = viewModel, onOpenHistory = onOpenHistory)
	UpdateCheckProgressBar(activeSourceName = initialSource)
	UpdatesLoadingList(Modifier.weight(1f))
}

@Composable
fun UpdatesScreenError(
	viewModel: UpdatesViewModel,
	onOpenHistory: (() -> Unit)? = null
) = Column(Modifier.fillMaxSize()) {
	val handler = LocalUriHandler.current
	UpdatesTopBar(viewModel = viewModel, onOpenHistory = onOpenHistory)
	UpdatesMessage(
		title = stringResource(R.string.updates_error_title),
		message = stringResource(R.string.updates_error_message),
		icon = Icons.Filled.Info,
		actionLabel = stringResource(R.string.error_banner_wiki_help),
		onAction = { handler.openUri(resolveWikiUrlForSourceError(null)) },
		modifier = Modifier.weight(1f)
	)
}

@Composable
fun UpdatesScreenSuccess(
	viewModel: UpdatesViewModel,
	appsViewModel: AppsViewModel = koinViewModel(),
	updates: List<AppUpdate>,
	hasSourceErrors: Boolean = false,
	isChecking: Boolean = false,
	checkedApps: Int? = null,
	totalApps: Int? = null,
	activeSourceName: String? = null,
	sourceErrors: Map<String, String> = emptyMap(),
	lastCheckedAtMillis: Long? = null,
	onOpenHistory: (() -> Unit)? = null
) = Column(
	modifier = Modifier
		.fillMaxSize()
		.background(MaterialTheme.colorScheme.background)
) {
	val context = LocalContext.current
	val handler = LocalUriHandler.current
	val prefs = koinInject<Prefs>()
	val displayMode = prefs.updatesDisplayMode.get()
	val view = LocalView.current
	DisposableEffect(isChecking) {
		view.keepScreenOn = isChecking
		onDispose { view.keepScreenOn = false }
	}

	val notificationPermissionLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.RequestPermission()
	) {}
	val requestNotificationPermissionIfNeeded: () -> Unit = remember(context, notificationPermissionLauncher) {
		{
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
				if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
					runCatching {
						notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
					}
				}
			}
		}
	}

	val activeTasksMap by DownloadInstallService.activeTasksFlow.collectAsStateWithLifecycle()
	val completedTasks by DownloadInstallService.completedTasksFlow.collectAsStateWithLifecycle()
	val readyUpdateIds by viewModel.readyUpdateIds.collectAsStateWithLifecycle()
	val activeTasks = remember(activeTasksMap) { activeTasksMap.values.toList() }
	var showQueueSheet by rememberSaveable { mutableStateOf(false) }
	val showMobileDataDialog by viewModel.showMobileDataDialog.collectAsStateWithLifecycle()

	var selectedSourceFilter by rememberSaveable { mutableStateOf<String?>(null) }

	val sourceCounts = remember(updates) {
		val map = linkedMapOf<Source, Int>()
		for (update in updates) {
			val allSources = (listOf(update.source) + update.alternativeUpdates.map { it.source }).distinctBy { it.name }
			for (src in allSources) {
				map[src] = (map[src] ?: 0) + 1
			}
		}
		map
	}

	LaunchedEffect(updates) {
		if (selectedSourceFilter != null && sourceCounts.none { it.key.name == selectedSourceFilter }) {
			selectedSourceFilter = null
		}
	}

	val displayedUpdates = remember(updates, selectedSourceFilter) {
		if (selectedSourceFilter == null) {
			updates
		} else {
			updates.mapNotNull { update ->
				if (update.source.name == selectedSourceFilter) {
					update
				} else {
					val alt = update.alternativeUpdates.firstOrNull { it.source.name == selectedSourceFilter }
					alt?.copy(alternativeUpdates = update.alternativeUpdates)
				}
			}
		}
	}

	val activeSources = buildList {
		if (prefs.useApkMirror.get()) add("APKMirror")
		if (prefs.usePlay.get()) add("Google Play")
		if (prefs.useIzzyOnDroid.get()) add("IzzyOnDroid")
		if (prefs.useGitHub.get()) add("GitHub")
		if (prefs.useGitLab.get()) add("GitLab")
	}
	val localeLanguage = LocalConfiguration.current.locales[0]?.language.orEmpty()
	val isGerman = localeLanguage.startsWith("de", ignoreCase = true)
	val directMirror = prefs.directApkMirrorDownloads.get()
	val hasInstallable = displayedUpdates.any {
		it.isDirectInstallable(directMirror) || it.alternativeUpdates.any { alt -> alt.isDirectInstallable(directMirror) }
	}

	val historyEntries by prefs.updateHistoryFlow.collectAsStateWithLifecycle()

	UpdatesTopBar(
		viewModel = viewModel,
		updatesCount = displayedUpdates.size,
		hasInstallableUpdates = hasInstallable,
		onOpenHistory = onOpenHistory
	)

	if ((updates.isNotEmpty() && !isChecking) || activeTasks.isNotEmpty()) {
		UpdatesHeroBanner(
			updatesCount = displayedUpdates.size,
			hasInstallable = hasInstallable,
			activeTasks = activeTasks,
			completedCount = completedTasks.size,
			onInstallAll = {
				requestNotificationPermissionIfNeeded()
				viewModel.installAll()
			},
			onOpenQueue = { showQueueSheet = true }
		)
	}

	if (updates.isNotEmpty() && sourceCounts.isNotEmpty()) {
		UpdatesSourceFilterRow(
			totalCount = updates.size,
			sourceCounts = sourceCounts,
			selectedSource = selectedSourceFilter,
			onSelectSource = { selectedSourceFilter = it }
		)
	}

	if (isChecking) {
		UpdateCheckProgressBar(checkedApps, totalApps, activeSourceName, activeSources, sourceCounts)
	} else if (updates.isEmpty() && ((lastCheckedAtMillis != null && lastCheckedAtMillis > 0L) || historyEntries.isNotEmpty())) {
		LastCheckedStatusBanner(
			lastCheckedAtMillis = lastCheckedAtMillis,
			historyCount = historyEntries.size,
			onOpenHistory = onOpenHistory
		)
	}

	if (hasSourceErrors && updates.isNotEmpty()) {
		val errorEntries = remember(sourceErrors) { sourceErrors.entries.toList() }
		if (errorEntries.size <= 1) {
			val singleEntry = errorEntries.firstOrNull()
			val wikiUrl = resolveWikiUrlForSourceError(singleEntry?.key, singleEntry?.value)
			val bannerText = formatSourceErrorBanner(
				sourceErrors = sourceErrors,
				activeSources = activeSources,
				isGerman = isGerman
			)
			Surface(
				onClick = { handler.openUri(wikiUrl) },
				color = MaterialTheme.colorScheme.errorContainer,
				shape = RoundedCornerShape(24.dp),
				modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
			) {
				Row(
					modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(10.dp)
				) {
					Icon(
						imageVector = Icons.Filled.Info,
						contentDescription = null,
						tint = MaterialTheme.colorScheme.onErrorContainer,
						modifier = Modifier.size(20.dp)
					)
					Text(
						text = bannerText,
						color = MaterialTheme.colorScheme.onErrorContainer,
						style = MaterialTheme.typography.labelMedium,
						modifier = Modifier.weight(1f)
					)
					Surface(
						shape = CircleShape,
						color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.14f)
					) {
						Text(
							text = stringResource(R.string.error_banner_wiki_help),
							style = MaterialTheme.typography.labelSmall,
							fontWeight = FontWeight.SemiBold,
							color = MaterialTheme.colorScheme.onErrorContainer,
							modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
						)
					}
				}
			}
		} else {
			val pagerState = rememberPagerState(pageCount = { errorEntries.size })
			HorizontalPager(
				state = pagerState,
				modifier = Modifier.fillMaxWidth()
			) { page ->
				val entry = errorEntries[page]
				val wikiUrl = resolveWikiUrlForSourceError(entry.key, entry.value)
				val singleBannerText = formatSourceErrorBanner(
					sourceErrors = mapOf(entry.key to entry.value),
					activeSources = activeSources,
					isGerman = isGerman
				)
				Surface(
					onClick = { handler.openUri(wikiUrl) },
					color = MaterialTheme.colorScheme.errorContainer,
					shape = RoundedCornerShape(24.dp),
					modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
				) {
					Row(
						modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(10.dp)
					) {
						Icon(
							imageVector = Icons.Filled.Info,
							contentDescription = null,
							tint = MaterialTheme.colorScheme.onErrorContainer,
							modifier = Modifier.size(20.dp)
						)
						Text(
							text = singleBannerText,
							color = MaterialTheme.colorScheme.onErrorContainer,
							style = MaterialTheme.typography.labelMedium,
							modifier = Modifier.weight(1f)
						)
						Column(
							horizontalAlignment = Alignment.End,
							verticalArrangement = Arrangement.spacedBy(4.dp)
						) {
							Surface(
								shape = CircleShape,
								color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.14f)
							) {
								Text(
									text = "${page + 1}/${errorEntries.size}",
									style = MaterialTheme.typography.labelSmall,
									fontWeight = FontWeight.SemiBold,
									color = MaterialTheme.colorScheme.onErrorContainer,
									modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
								)
							}
							Surface(
								shape = CircleShape,
								color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.14f)
							) {
								Text(
									text = stringResource(R.string.error_banner_wiki_help),
									style = MaterialTheme.typography.labelSmall,
									fontWeight = FontWeight.SemiBold,
									color = MaterialTheme.colorScheme.onErrorContainer,
									modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
								)
							}
						}
					}
				}
			}
		}
	}

	when {
		isChecking && updates.isEmpty() -> UpdatesLoadingList(Modifier.weight(1f))
		hasSourceErrors && updates.isEmpty() -> UpdatesMessage(
			title = stringResource(R.string.updates_partial_empty_title),
			message = formatPartialEmptyMessage(
				sourceErrors = sourceErrors,
				activeSources = activeSources,
				isGerman = isGerman
			),
			icon = Icons.Filled.Info,
			actionLabel = stringResource(R.string.error_banner_wiki_help),
			onAction = {
				val firstEntry = sourceErrors.entries.firstOrNull()
				handler.openUri(resolveWikiUrlForSourceError(firstEntry?.key, firstEntry?.value))
			},
			modifier = Modifier.weight(1f)
		)
		updates.isEmpty() -> GrandFestivalEmptyState(
			title = stringResource(R.string.updates_empty_title),
			message = stringResource(R.string.updates_empty_message),
			onRefresh = { viewModel.refresh() },
			modifier = Modifier.weight(1f)
		)
		displayedUpdates.isEmpty() -> UpdatesMessage(
			title = stringResource(R.string.updates_empty_title),
			message = stringResource(R.string.updates_empty_source_filter_message),
			modifier = Modifier.weight(1f)
		)
		displayMode == 1 -> CompactUpdatesGrid(
			viewModel = viewModel,
			appsViewModel = appsViewModel,
			updates = displayedUpdates,
			activeTasksMap = activeTasksMap,
			readyUpdateIds = readyUpdateIds,
			handler = handler,
			modifier = Modifier.weight(1f),
			onBeforeInstall = requestNotificationPermissionIfNeeded
		)
		else -> UpdatesList(
			viewModel = viewModel,
			appsViewModel = appsViewModel,
			updates = displayedUpdates,
			activeTasksMap = activeTasksMap,
			readyUpdateIds = readyUpdateIds,
			handler = handler,
			modifier = Modifier.weight(1f),
			onBeforeInstall = requestNotificationPermissionIfNeeded
		)
	}

	if (showQueueSheet) {
		ActiveQueueBottomSheet(
			activeTasks = activeTasks,
			completedTasks = completedTasks,
			onCancelTask = { id -> viewModel.cancelInstall(id) },
			onCancelAll = {
				viewModel.cancelAllInstalls()
				showQueueSheet = false
			},
			onDismiss = { showQueueSheet = false }
		)
	}

	if (showMobileDataDialog) {
		androidx.compose.material3.AlertDialog(
			onDismissRequest = { viewModel.dismissMobileDataDialog() },
			title = {
				Text(
					text = stringResource(R.string.dialog_mobile_data_title),
					style = MaterialTheme.typography.titleLarge,
					fontWeight = FontWeight.Bold
				)
			},
			text = {
				Text(
					text = stringResource(R.string.dialog_mobile_data_message),
					style = MaterialTheme.typography.bodyMedium
				)
			},
			confirmButton = {
				Button(onClick = { viewModel.confirmMobileDataDownload() }) {
					Text(text = stringResource(R.string.dialog_mobile_data_confirm))
				}
			},
			dismissButton = {
				TextButton(onClick = { viewModel.dismissMobileDataDialog() }) {
					Text(text = stringResource(R.string.action_cancel))
				}
			}
		)
	}
}

@Composable
private fun UpdatesHeroBanner(
	updatesCount: Int,
	hasInstallable: Boolean,
	activeTasks: List<DownloadTaskState> = emptyList(),
	completedCount: Int = 0,
	onInstallAll: () -> Unit,
	onOpenQueue: () -> Unit = {}
) {
	val context = LocalContext.current
	val shape = RoundedCornerShape(24.dp)
	if (activeTasks.isNotEmpty()) {
		val currentTask = activeTasks.firstOrNull { !it.isQueued || it.isInstalling } ?: activeTasks.first()
		val currentAppName = currentTask.appName.ifBlank {
			context.getAppName(currentTask.packageName).ifBlank { currentTask.packageName }
		}
		val totalInRun = (completedCount + activeTasks.size).coerceAtLeast(1)
		val currentStep = (completedCount + 1).coerceAtMost(totalInRun)
		val liveSubtitle = when {
			currentTask.isInstalling -> "$currentAppName • ${stringResource(R.string.queue_status_installing)}"
			currentTask.isResolvingLink && currentTask.progress == 0L ->
				"$currentAppName • ${stringResource(R.string.queue_status_resolving_apkmirror)}"
			currentTask.total > 0L && currentTask.progress > 0L -> {
				val pct = ((currentTask.progress * 100) / currentTask.total).toInt().coerceIn(0, 100)
				"$currentAppName • $pct% (${formatSizeMegabytes(currentTask.progress)} / ${formatSizeMegabytes(currentTask.total)})"
			}
			currentTask.progress > 0L -> {
				"$currentAppName • ${formatSizeMegabytes(currentTask.progress)}"
			}
			else -> stringResource(R.string.queue_banner_subtitle_tap, currentAppName)
		}
		Surface(
			onClick = onOpenQueue,
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 12.dp, vertical = 4.dp),
			shape = shape,
			color = MaterialTheme.colorScheme.surfaceContainer,
			shadowElevation = 0.dp
		) {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 16.dp, vertical = 12.dp),
				verticalArrangement = Arrangement.spacedBy(8.dp)
			) {
				Row(
					modifier = Modifier.fillMaxWidth(),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.SpaceBetween
				) {
					Row(
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(12.dp),
						modifier = Modifier.weight(1f, fill = false)
					) {
						Surface(
							shape = CircleShape,
							color = MaterialTheme.colorScheme.primary,
							modifier = Modifier.size(40.dp)
						) {
							Box(contentAlignment = Alignment.Center) {
								CircularProgressIndicator(
									modifier = Modifier.size(20.dp),
									strokeWidth = 2.2.dp,
									color = MaterialTheme.colorScheme.onPrimary
								)
							}
						}
						Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
							Text(
								text = stringResource(R.string.queue_banner_title, currentStep, totalInRun),
								style = MaterialTheme.typography.titleSmall,
								fontWeight = FontWeight.Bold,
								color = MaterialTheme.colorScheme.onSurface,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis
							)
							Text(
								text = liveSubtitle,
								style = MaterialTheme.typography.labelSmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis
							)
						}
					}
					Spacer(Modifier.width(8.dp))
					Surface(
						shape = CircleShape,
						color = MaterialTheme.colorScheme.primary
					) {
						Row(
							modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(6.dp)
						) {
							Text(
								text = stringResource(R.string.queue_banner_action_details),
								style = MaterialTheme.typography.labelMedium,
								fontWeight = FontWeight.Bold,
								color = MaterialTheme.colorScheme.onPrimary
							)
							Surface(
								shape = CircleShape,
								color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.22f)
							) {
								Text(
									text = activeTasks.size.toString(),
									style = MaterialTheme.typography.labelSmall,
									fontWeight = FontWeight.Bold,
									color = MaterialTheme.colorScheme.onPrimary,
									modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
								)
							}
						}
					}
				}
				if (!currentTask.isInstalling && currentTask.total > 0L && currentTask.progress > 0L) {
					LinearProgressIndicator(
						progress = { (currentTask.progress.toFloat() / currentTask.total).coerceIn(0f, 1f) },
						modifier = Modifier
							.fillMaxWidth()
							.height(5.dp)
							.clip(RoundedCornerShape(3.dp)),
						color = MaterialTheme.colorScheme.primary,
						trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
					)
				} else {
					LinearProgressIndicator(
						modifier = Modifier
							.fillMaxWidth()
							.height(5.dp)
							.clip(RoundedCornerShape(3.dp)),
						color = MaterialTheme.colorScheme.primary,
						trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
					)
				}
			}
		}
		return
	}

	Surface(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 12.dp, vertical = 4.dp),
		shape = shape,
		color = MaterialTheme.colorScheme.surfaceContainer,
		shadowElevation = 0.dp
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 16.dp, vertical = 12.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.SpaceBetween
		) {
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(12.dp),
				modifier = Modifier.weight(1f, fill = false)
			) {
				Surface(
					shape = CircleShape,
					color = MaterialTheme.colorScheme.primary,
					modifier = Modifier.size(40.dp)
				) {
					Box(contentAlignment = Alignment.Center) {
						Icon(
							painter = painterResource(R.drawable.ic_install),
							contentDescription = null,
							tint = MaterialTheme.colorScheme.onPrimary,
							modifier = Modifier.size(20.dp)
						)
					}
				}
				Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
					Text(
						text = pluralStringResource(R.plurals.updates_banner_title_count, updatesCount, updatesCount),
						style = MaterialTheme.typography.titleMedium,
						fontWeight = FontWeight.Bold,
						color = MaterialTheme.colorScheme.onSurface
					)
					Text(
						text = stringResource(if (hasInstallable) R.string.tab_updates else R.string.updates_empty_source_filter_message),
						style = MaterialTheme.typography.labelSmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)
				}
			}
			if (hasInstallable) {
				Button(
					onClick = onInstallAll,
					shape = CircleShape,
					colors = ButtonDefaults.buttonColors(
						containerColor = MaterialTheme.colorScheme.primary,
						contentColor = MaterialTheme.colorScheme.onPrimary
					),
					contentPadding = PaddingValues(start = 14.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
				) {
					Icon(
						painter = painterResource(R.drawable.ic_install),
						contentDescription = null,
						modifier = Modifier.size(16.dp)
					)
					Spacer(Modifier.width(6.dp))
					Text(
						text = stringResource(R.string.install_all),
						style = MaterialTheme.typography.labelMedium,
						fontWeight = FontWeight.Bold
					)
				}
			}
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActiveQueueBottomSheet(
	activeTasks: List<DownloadTaskState>,
	completedTasks: List<DownloadTaskState>,
	onCancelTask: (Int) -> Unit,
	onCancelAll: () -> Unit,
	onDismiss: () -> Unit
) {
	val context = LocalContext.current
	val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
	val liveActiveMap by DownloadInstallService.activeTasksFlow.collectAsStateWithLifecycle()
	val liveCompleted by DownloadInstallService.completedTasksFlow.collectAsStateWithLifecycle()
	val currentActiveTasks = if (liveActiveMap.isNotEmpty() || activeTasks.isEmpty()) {
		liveActiveMap.values.toList()
	} else {
		activeTasks
	}
	val currentCompletedTasks = if (liveCompleted.isNotEmpty() || completedTasks.isEmpty()) {
		liveCompleted
	} else {
		completedTasks
	}

	LaunchedEffect(currentActiveTasks.size, currentCompletedTasks.size) {
		if (currentActiveTasks.isEmpty() && currentCompletedTasks.isEmpty()) {
			onDismiss()
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
				.padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
			verticalArrangement = Arrangement.spacedBy(12.dp)
		) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.SpaceBetween
			) {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(10.dp),
					modifier = Modifier.weight(1f, fill = false)
				) {
					Text(
						text = stringResource(R.string.queue_sheet_title),
						style = MaterialTheme.typography.titleLarge,
						fontWeight = FontWeight.Bold
					)
					if (currentActiveTasks.isNotEmpty()) {
						Surface(
							shape = CircleShape,
							color = MaterialTheme.colorScheme.primaryContainer
						) {
							Text(
								text = currentActiveTasks.size.toString(),
								style = MaterialTheme.typography.labelMedium,
								fontWeight = FontWeight.Bold,
								color = MaterialTheme.colorScheme.onPrimaryContainer,
								modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
							)
						}
					}
				}
				if (currentActiveTasks.isNotEmpty()) {
					TextButton(onClick = onCancelAll) {
						Icon(
							imageVector = Icons.Filled.Close,
							contentDescription = null,
							tint = MaterialTheme.colorScheme.error,
							modifier = Modifier.size(16.dp)
						)
						Spacer(Modifier.width(4.dp))
						Text(
							text = stringResource(R.string.queue_cancel_all),
							color = MaterialTheme.colorScheme.error,
							style = MaterialTheme.typography.labelLarge,
							fontWeight = FontWeight.SemiBold
						)
					}
				}
			}

			LazyColumn(
				modifier = Modifier
					.fillMaxWidth()
					.heightIn(max = 460.dp),
				verticalArrangement = Arrangement.spacedBy(8.dp)
			) {
				items(items = currentActiveTasks, key = { "active_${it.id}" }) { task ->
					val appName = task.appName.ifBlank {
						context.getAppName(task.packageName).ifBlank { task.packageName }
					}
					val statusLabel = when {
						task.isInstalling -> stringResource(R.string.queue_status_installing)
						task.isResolvingLink && task.progress == 0L -> stringResource(R.string.queue_status_resolving_apkmirror)
						task.isQueued && task.progress == 0L -> stringResource(R.string.queue_status_waiting)
						task.total > 0L && task.progress > 0L -> {
							val pct = ((task.progress * 100) / task.total).toInt().coerceIn(0, 100)
							"${stringResource(R.string.downloading_update_percent, pct)} (${formatSizeMegabytes(task.progress)} / ${formatSizeMegabytes(task.total)})"
						}
						task.progress > 0L -> "${stringResource(R.string.queue_status_downloading)} (${formatSizeMegabytes(task.progress)})"
						else -> stringResource(R.string.queue_status_downloading)
					}
					Surface(
						modifier = Modifier.fillMaxWidth(),
						shape = RoundedCornerShape(18.dp),
						color = MaterialTheme.colorScheme.surfaceContainerHigh,
						border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
					) {
						Column(
							modifier = Modifier
								.fillMaxWidth()
								.padding(horizontal = 12.dp, vertical = 10.dp),
							verticalArrangement = Arrangement.spacedBy(8.dp)
						) {
							Row(
								modifier = Modifier.fillMaxWidth(),
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(10.dp)
							) {
								Box(
									modifier = Modifier
										.size(40.dp)
										.clip(RoundedCornerShape(10.dp))
										.background(MaterialTheme.colorScheme.surfaceContainerHighest),
									contentAlignment = Alignment.Center
								) {
									LoadingImageApp(task.packageName, Modifier.requiredSize(50.dp))
								}
								Column(
									modifier = Modifier.weight(1f),
									verticalArrangement = Arrangement.spacedBy(2.dp)
								) {
									Row(
										verticalAlignment = Alignment.CenterVertically,
										horizontalArrangement = Arrangement.spacedBy(6.dp)
									) {
										Text(
											text = appName,
											style = MaterialTheme.typography.bodyMedium,
											fontWeight = FontWeight.SemiBold,
											color = MaterialTheme.colorScheme.onSurface,
											maxLines = 1,
											overflow = TextOverflow.Ellipsis,
											modifier = Modifier.weight(1f, fill = false)
										)
										Icon(
											painter = painterResource(task.source.resourceId),
											contentDescription = null,
											tint = MaterialTheme.colorScheme.onSurfaceVariant,
											modifier = Modifier.size(13.dp)
										)
										if (task.version.isNotBlank()) {
											Text(
												text = task.version,
												style = MaterialTheme.typography.labelSmall,
												color = MaterialTheme.colorScheme.onSurfaceVariant,
												maxLines = 1
											)
										}
									}
									Text(
										text = statusLabel,
										style = MaterialTheme.typography.labelSmall,
										fontWeight = FontWeight.Medium,
										color = if (task.isQueued && task.progress == 0L && !task.isInstalling) {
											MaterialTheme.colorScheme.onSurfaceVariant
										} else {
											MaterialTheme.colorScheme.primary
										},
										maxLines = 1,
										overflow = TextOverflow.Ellipsis
									)
								}
								IconButton(
									onClick = { onCancelTask(task.id) },
									modifier = Modifier.size(32.dp)
								) {
									Icon(
										imageVector = Icons.Filled.Close,
										contentDescription = stringResource(R.string.action_cancel),
										tint = MaterialTheme.colorScheme.onSurfaceVariant,
										modifier = Modifier.size(18.dp)
									)
								}
							}
							if (!task.isQueued || task.isInstalling || task.progress > 0L) {
								if (!task.isInstalling && !task.isResolvingLink && task.total > 0L && task.progress > 0L) {
									LinearProgressIndicator(
										progress = { (task.progress.toFloat() / task.total).coerceIn(0f, 1f) },
										modifier = Modifier
											.fillMaxWidth()
											.height(5.dp)
											.clip(RoundedCornerShape(3.dp)),
										color = MaterialTheme.colorScheme.primary,
										trackColor = MaterialTheme.colorScheme.secondaryContainer
									)
								} else {
									LinearProgressIndicator(
										modifier = Modifier
											.fillMaxWidth()
											.height(5.dp)
											.clip(RoundedCornerShape(3.dp)),
										color = MaterialTheme.colorScheme.primary,
										trackColor = MaterialTheme.colorScheme.secondaryContainer
									)
								}
							}
						}
					}
				}

				if (currentCompletedTasks.isNotEmpty()) {
					item(key = "completed_header") {
						Text(
							text = "${stringResource(R.string.queue_section_completed)} (${currentCompletedTasks.size})",
							style = MaterialTheme.typography.labelLarge,
							fontWeight = FontWeight.SemiBold,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
							modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
						)
					}
					items(items = currentCompletedTasks, key = { "done_${it.id}" }) { done ->
						val appName = done.appName.ifBlank {
							context.getAppName(done.packageName).ifBlank { done.packageName }
						}
						Surface(
							modifier = Modifier.fillMaxWidth(),
							shape = RoundedCornerShape(14.dp),
							color = MaterialTheme.colorScheme.surfaceContainer
						) {
							Row(
								modifier = Modifier
									.fillMaxWidth()
									.padding(horizontal = 12.dp, vertical = 8.dp),
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(10.dp)
							) {
								Box(
									modifier = Modifier
										.size(32.dp)
										.clip(RoundedCornerShape(8.dp))
										.background(MaterialTheme.colorScheme.surfaceContainerHigh),
									contentAlignment = Alignment.Center
								) {
									LoadingImageApp(done.packageName, Modifier.requiredSize(40.dp))
								}
								Column(modifier = Modifier.weight(1f)) {
									Text(
										text = appName,
										style = MaterialTheme.typography.bodySmall,
										fontWeight = FontWeight.SemiBold,
										color = MaterialTheme.colorScheme.onSurface,
										maxLines = 1,
										overflow = TextOverflow.Ellipsis
									)
									if (done.version.isNotBlank()) {
										Text(
											text = done.version,
											style = MaterialTheme.typography.labelSmall,
											color = MaterialTheme.colorScheme.onSurfaceVariant,
											maxLines = 1
										)
									}
								}
								Surface(
									shape = CircleShape,
									color = Color(0xFFE8F5E9)
								) {
									Row(
										modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
										verticalAlignment = Alignment.CenterVertically,
										horizontalArrangement = Arrangement.spacedBy(4.dp)
									) {
										Icon(
											imageVector = Icons.Filled.Check,
											contentDescription = null,
											tint = Color(0xFF2E7D32),
											modifier = Modifier.size(12.dp)
										)
										Text(
											text = stringResource(R.string.queue_item_completed),
											style = MaterialTheme.typography.labelSmall,
											fontWeight = FontWeight.SemiBold,
											color = Color(0xFF2E7D32)
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
private fun UpdatesSourceFilterRow(
	totalCount: Int,
	sourceCounts: Map<Source, Int>,
	selectedSource: String?,
	onSelectSource: (String?) -> Unit
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.horizontalScroll(rememberScrollState())
			.padding(horizontal = 12.dp, vertical = 4.dp),
		horizontalArrangement = Arrangement.spacedBy(6.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		val allSelected = selectedSource == null
		FilterChip(
			selected = allSelected,
			onClick = { onSelectSource(null) },
			modifier = Modifier.height(32.dp),
			label = {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(6.dp)
				) {
					Text(
						text = stringResource(R.string.apps_filter_all),
						style = MaterialTheme.typography.labelSmall,
						fontWeight = if (allSelected) FontWeight.Bold else FontWeight.Medium
					)
					Surface(
						shape = CircleShape,
						color = if (allSelected) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.16f)
						else MaterialTheme.colorScheme.surfaceContainerHighest
					) {
						Text(
							text = totalCount.toString(),
							style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
							fontWeight = FontWeight.Bold,
							color = if (allSelected) MaterialTheme.colorScheme.onSecondaryContainer
							else MaterialTheme.colorScheme.onSurfaceVariant,
							modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
						)
					}
				}
			},
			leadingIcon = if (allSelected) {
				{
					Icon(
						imageVector = Icons.Filled.Check,
						contentDescription = null,
						modifier = Modifier.size(13.dp)
					)
				}
			} else null,
			shape = CircleShape,
			border = null,
			colors = FilterChipDefaults.filterChipColors(
				selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
				selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
				selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
				containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
				labelColor = MaterialTheme.colorScheme.onSurfaceVariant
			)
		)

		sourceCounts.forEach { (source, count) ->
			if (count > 0) {
				val isSelected = selectedSource == source.name
				FilterChip(
					selected = isSelected,
					onClick = { onSelectSource(if (isSelected) null else source.name) },
					modifier = Modifier.height(32.dp),
					label = {
						Row(
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(6.dp)
						) {
							Text(
								text = sourceName(source),
								style = MaterialTheme.typography.labelSmall,
								fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
							)
							Surface(
								shape = CircleShape,
								color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.16f)
								else MaterialTheme.colorScheme.surfaceContainerHighest
							) {
								Text(
									text = count.toString(),
									style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
									fontWeight = FontWeight.Bold,
									color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
									else MaterialTheme.colorScheme.onSurfaceVariant,
									modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
								)
							}
						}
					},
					leadingIcon = if (isSelected) {
						{
							Icon(
								imageVector = Icons.Filled.Check,
								contentDescription = null,
								modifier = Modifier.size(13.dp)
							)
						}
					} else {
						{
							Icon(
								painter = painterResource(source.resourceId),
								contentDescription = null,
								modifier = Modifier.size(13.dp),
								tint = MaterialTheme.colorScheme.onSurfaceVariant
							)
						}
					},
					shape = CircleShape,
					border = null,
					colors = FilterChipDefaults.filterChipColors(
						selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
						selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
						selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
						containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
						labelColor = MaterialTheme.colorScheme.onSurfaceVariant
					)
				)
			}
		}
	}
}

@Composable
private fun LastCheckedStatusBanner(
	lastCheckedAtMillis: Long?,
	historyCount: Int = 0,
	onOpenHistory: (() -> Unit)? = null
) {
	val hasLastChecked = lastCheckedAtMillis != null && lastCheckedAtMillis > 0L
	val statusText = if (hasLastChecked) {
		val now = System.currentTimeMillis()
		val elapsedMinutes = ((now - lastCheckedAtMillis).coerceAtLeast(0L)) / 60_000L
		when {
			elapsedMinutes < 1L -> stringResource(R.string.updates_last_checked_just_now)
			elapsedMinutes < 60L -> stringResource(R.string.updates_last_checked_minutes, elapsedMinutes.toInt())
			elapsedMinutes < 1440L -> stringResource(R.string.updates_last_checked_hours, (elapsedMinutes / 60L).toInt())
			else -> stringResource(R.string.updates_last_checked_days, (elapsedMinutes / 1440L).toInt())
		}
	} else null

	Row(
		modifier = Modifier
			.fillMaxWidth()
			.horizontalScroll(rememberScrollState())
			.padding(horizontal = 14.dp, vertical = 2.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(8.dp)
	) {
		if (statusText != null) {
			Surface(
				shape = CircleShape,
				color = MaterialTheme.colorScheme.surfaceContainerHigh
			) {
				Text(
					text = statusText,
					modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					maxLines = 1
				)
			}
		}
		if (historyCount > 0 && onOpenHistory != null) {
			Surface(
				onClick = onOpenHistory,
				shape = CircleShape,
				color = MaterialTheme.colorScheme.secondaryContainer
			) {
				Row(
					modifier = Modifier.padding(horizontal = 11.dp, vertical = 4.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(5.dp)
				) {
					Icon(
						imageVector = Icons.Rounded.History,
						contentDescription = null,
						tint = MaterialTheme.colorScheme.onSecondaryContainer,
						modifier = Modifier.size(13.dp)
					)
					Text(
						text = stringResource(R.string.updates_recent_history_pill, historyCount),
						style = MaterialTheme.typography.labelSmall,
						fontWeight = FontWeight.SemiBold,
						color = MaterialTheme.colorScheme.onSecondaryContainer,
						maxLines = 1
					)
					Icon(
						imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
						contentDescription = null,
						tint = MaterialTheme.colorScheme.onSecondaryContainer,
						modifier = Modifier.size(12.dp)
					)
				}
			}
		}
	}
}

@Composable
private fun UpdateCheckProgressBar(
	checkedApps: Int? = null,
	totalApps: Int? = null,
	activeSourceName: String? = null,
	activeSources: List<String> = emptyList(),
	sourceCounts: Map<Source, Int> = emptyMap()
) {
	val resolvedActiveSource = when {
		activeSourceName.isNullOrBlank() -> null
		activeSourceName.equals("Google Play", ignoreCase = true) ||
			activeSourceName.equals("Play", ignoreCase = true) -> stringResource(R.string.source_play)
		activeSourceName.equals("APKMirror", ignoreCase = true) -> stringResource(R.string.source_apkmirror)
		else -> activeSourceName
	}
	Surface(
		color = MaterialTheme.colorScheme.surfaceContainer,
		shape = RoundedCornerShape(24.dp),
		border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
		modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
	) {
		Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
			if (checkedApps != null && totalApps != null && totalApps > 0) {
				val completed = checkedApps.coerceIn(0, totalApps - 1)
				Row(
					modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically
				) {
					Text(
						text = stringResource(R.string.updates_checking_apps, completed, totalApps),
						style = MaterialTheme.typography.labelMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
						modifier = Modifier.weight(1f, fill = false)
					)
					if (!resolvedActiveSource.isNullOrBlank()) {
						Spacer(Modifier.width(8.dp))
						Text(
							text = stringResource(R.string.updates_checking_source, resolvedActiveSource),
							style = MaterialTheme.typography.labelSmall,
							fontWeight = FontWeight.Medium,
							color = MaterialTheme.colorScheme.primary,
							maxLines = 1
						)
					}
				}
				if (completed > 0) {
					LinearProgressIndicator(
						progress = { completed.toFloat() / totalApps },
						modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
						color = MaterialTheme.colorScheme.primary,
						trackColor = MaterialTheme.colorScheme.secondaryContainer
					)
				} else {
					LinearProgressIndicator(
						modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
						color = MaterialTheme.colorScheme.primary,
						trackColor = MaterialTheme.colorScheme.secondaryContainer
					)
				}
			} else {
				Row(
					modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically
				) {
					Text(
						text = stringResource(R.string.updates_checking),
						style = MaterialTheme.typography.labelMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
						modifier = Modifier.weight(1f, fill = false)
					)
					if (!resolvedActiveSource.isNullOrBlank()) {
						Spacer(Modifier.width(8.dp))
						Text(
							text = stringResource(R.string.updates_checking_source, resolvedActiveSource),
							style = MaterialTheme.typography.labelSmall,
							fontWeight = FontWeight.Medium,
							color = MaterialTheme.colorScheme.primary,
							maxLines = 1
						)
					}
				}
				LinearProgressIndicator(
					modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
					color = MaterialTheme.colorScheme.primary,
					trackColor = MaterialTheme.colorScheme.secondaryContainer
				)
			}

			val effectiveSources = if (activeSources.isNotEmpty()) activeSources else listOfNotNull(resolvedActiveSource)
			if (effectiveSources.isNotEmpty()) {
				val scanStatuses = effectiveSources.map { source ->
					val isScanning = activeSourceName?.contains(source, ignoreCase = true) == true || activeSourceName == source
					val count = sourceCounts.entries.firstOrNull { it.key.toString().contains(source, ignoreCase = true) }?.value ?: 0
					ScanSourceStatus(name = source, isScanning = isScanning, updateCount = count)
				}
				Spacer(Modifier.height(8.dp))
				MultiSourceScanTray(statuses = scanStatuses)
			}
		}
	}
}

@Composable
private fun UpdatesList(
	viewModel: UpdatesViewModel,
	appsViewModel: AppsViewModel = koinViewModel(),
	updates: List<AppUpdate>,
	activeTasksMap: Map<Int, DownloadTaskState> = emptyMap(),
	readyUpdateIds: Set<Int> = emptySet(),
	handler: UriHandler,
	modifier: Modifier,
	onBeforeInstall: () -> Unit = {}
) {
	var selectedPackageName by remember { mutableStateOf<String?>(null) }
	val selectedUpdate = updates.find { it.packageName == selectedPackageName }

	BoxWithConstraints(modifier) {
		val columns = updateGridColumnCount(maxWidth.value, compact = false)
		LazyVerticalGrid(
			columns = GridCells.Fixed(columns),
			modifier = Modifier.fillMaxSize(),
			contentPadding = PaddingValues(
				start = NormalUpdateCardLayout.gridHorizontalPaddingDp.dp,
				top = NormalUpdateCardLayout.gridTopPaddingDp.dp,
				end = NormalUpdateCardLayout.gridHorizontalPaddingDp.dp,
				bottom = NormalUpdateCardLayout.gridBottomPaddingDp.dp
			),
			verticalArrangement = Arrangement.spacedBy(NormalUpdateCardLayout.gridSpacingDp.dp),
			horizontalArrangement = Arrangement.spacedBy(NormalUpdateCardLayout.gridSpacingDp.dp)
		) {
			gridItems(items = updates, key = { it.id }) { update ->
				val liveTask = activeTasksMap[update.id]
					?: activeTasksMap.values.firstOrNull { it.packageName == update.packageName }
				val isCachedReady = update.id in readyUpdateIds || update.alternativeUpdates.any { it.id in readyUpdateIds }
				UpdateCard(
					update = update,
					liveTask = liveTask,
					isCachedReady = isCachedReady,
					onSelect = { selectedPackageName = update.packageName },
					onInstall = {
						onBeforeInstall()
						viewModel.install(update, handler)
					}
				)
			}
		}
	}

	selectedUpdate?.let { update ->
		UpdateDetailsSheet(
			update = update,
			isCachedReady = update.id in readyUpdateIds || update.alternativeUpdates.any { it.id in readyUpdateIds },
			readyUpdateIds = readyUpdateIds,
			onDismiss = { selectedPackageName = null },
			onInstallVariant = { chosen ->
				onBeforeInstall()
				viewModel.install(chosen, handler)
				if (chosen.isBrowserOnlyUpdate) {
					selectedPackageName = null
				}
			},
			onIgnoreVersion = { versionId ->
				viewModel.ignoreVersion(versionId)
				selectedPackageName = null
			},
			onIgnoreAppPermanently = { packageName ->
				viewModel.ignoreAppPermanently(packageName)
				appsViewModel.refresh(false)
				selectedPackageName = null
			}
		)
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpdateCard(
	update: AppUpdate,
	liveTask: DownloadTaskState? = null,
	isCachedReady: Boolean = false,
	onSelect: () -> Unit,
	onInstall: () -> Unit
) {
	val context = LocalContext.current
	val appName = update.name.ifBlank {
		context.getAppName(update.packageName).ifBlank { update.packageName }
	}
	val card = update.toUpdateCardContent(appName)
	val sourceName = sourceName(card.source)
	val effInstalling = liveTask?.let { it.isInstalling || !it.isQueued } ?: card.isInstalling
	val effQueued = liveTask?.isQueued ?: update.isQueued
	val effProgress = liveTask?.progress ?: update.progress
	val effTotal = liveTask?.total?.takeIf { it > 0L } ?: update.total

	val cardShape = RoundedCornerShape(20.dp)
	Surface(
		onClick = onSelect,
		modifier = Modifier
			.fillMaxWidth()
			.animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
		shape = cardShape,
		color = MaterialTheme.colorScheme.surfaceContainerLow
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(16.dp),
			verticalArrangement = Arrangement.spacedBy(10.dp)
		) {
			// Top Row: App Icon (46dp) + App Name & Size + Versions
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
					LoadingImageApp(
						card.packageName,
						Modifier.fillMaxSize()
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
							text = card.appName,
							style = MaterialTheme.typography.titleMedium,
							fontWeight = FontWeight.Bold,
							color = MaterialTheme.colorScheme.onSurface,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
							modifier = Modifier.weight(1f, fill = false)
						)
						if (update.totalSize > 0L) {
							Text(
								text = formatSizeMegabytes(update.totalSize),
								style = MaterialTheme.typography.labelSmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant
							)
						}
					}
					// Row 1: Versions
					Row(
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(6.dp)
					) {
						Text(
							text = card.installedVersion.ifBlank { "—" },
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
							text = card.availableVersion.ifBlank { "—" },
							style = MaterialTheme.typography.labelSmall,
							fontWeight = FontWeight.Bold,
							color = MaterialTheme.colorScheme.primary,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis
						)
					}
					// Row 2: Source Badge & Multi-Source Indicator Badge
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
									painter = painterResource(card.source.resourceId),
									contentDescription = null,
									tint = MaterialTheme.colorScheme.onSurfaceVariant,
									modifier = Modifier.size(11.dp)
								)
								Text(
									text = sourceName,
									style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
									color = MaterialTheme.colorScheme.onSurfaceVariant
								)
							}
						}
						if (card.additionalSourcesCount > 0) {
							val sourcesPlural = if (card.additionalSourcesCount == 1) {
								stringResource(R.string.update_sources_multiple_count_singular, card.additionalSourcesCount)
							} else {
								stringResource(R.string.update_sources_multiple_count_plural, card.additionalSourcesCount)
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
										text = sourcesPlural,
										style = MaterialTheme.typography.labelSmall.copy(
											fontSize = 10.sp,
											fontWeight = FontWeight.Bold
										),
										color = MaterialTheme.colorScheme.onPrimaryContainer
									)
								}
							}
						}
					}				}
			}

			// Changelog snippet if available
			val cleanChangelog = remember(update.whatsNew) { cleanChangelogText(update.whatsNew) }
			if (cleanChangelog.isNotBlank()) {
				Surface(
					shape = RoundedCornerShape(10.dp),
					color = MaterialTheme.colorScheme.surfaceContainer,
					modifier = Modifier.fillMaxWidth()
				) {
					Row(
						modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
						horizontalArrangement = Arrangement.spacedBy(8.dp),
						verticalAlignment = Alignment.Top
					) {
						Icon(
							imageVector = Icons.Default.Info,
							contentDescription = null,
							tint = MaterialTheme.colorScheme.primary,
							modifier = Modifier.size(14.dp).padding(top = 1.dp)
						)
						Text(
							text = cleanChangelog,
							style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
							color = MaterialTheme.colorScheme.onSurfaceVariant,
							maxLines = 2,
							overflow = TextOverflow.Ellipsis
						)
					}
				}
			}

			// Progress bar if downloading/installing
			if (effInstalling || effQueued) {
				val cardProgressLabel = when {
					liveTask?.isInstalling == true -> stringResource(R.string.queue_status_installing)
					liveTask?.isResolvingLink == true && effProgress == 0L -> stringResource(R.string.queue_status_resolving_apkmirror)
					effQueued && !effInstalling -> stringResource(R.string.queued_update)
					effTotal > 0L && effProgress > 0L -> {
						val pct = ((effProgress * 100) / effTotal).toInt().coerceIn(0, 100)
						"${stringResource(R.string.downloading_update_percent, pct)} (${formatSizeMegabytes(effProgress)} / ${formatSizeMegabytes(effTotal)})"
					}
					effProgress > 0L -> "${stringResource(R.string.queue_status_downloading)} (${formatSizeMegabytes(effProgress)})"
					else -> stringResource(R.string.installing_update)
				}
				Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
					Text(
						text = cardProgressLabel,
						style = MaterialTheme.typography.labelSmall,
						color = MaterialTheme.colorScheme.primary,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)
					LinearProgressIndicator(
						progress = { if (effTotal > 0L && effProgress > 0L) (effProgress.toFloat() / effTotal).coerceIn(0f, 1f) else 0f },
						modifier = Modifier.fillMaxWidth().height(4.dp),
						color = MaterialTheme.colorScheme.primary,
						trackColor = MaterialTheme.colorScheme.surfaceContainer
					)
				}
			}

			// Bottom Actions: Details link & Install Button
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.SpaceBetween
			) {
				TextButton(
					onClick = onSelect,
					contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
				) {
					Text(
						text = stringResource(R.string.updates_detail_view),
						style = MaterialTheme.typography.labelMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
				if (card.canInstall && !effQueued && !effInstalling) {
					Button(
						onClick = onInstall,
						shape = RoundedCornerShape(10.dp),
						contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
						colors = ButtonDefaults.buttonColors(
							containerColor = MaterialTheme.colorScheme.primary,
							contentColor = MaterialTheme.colorScheme.onPrimary
						)
					) {
						Icon(
							painter = painterResource(R.drawable.ic_install),
							contentDescription = null,
							modifier = Modifier.size(15.dp)
						)
						Spacer(Modifier.width(6.dp))
						Text(
							text = stringResource(if (isCachedReady) R.string.install_cached_action else R.string.update_action),
							style = MaterialTheme.typography.labelMedium,
							fontWeight = FontWeight.Bold
						)
					}
				}
			}
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactUpdatesGrid(
	viewModel: UpdatesViewModel,
	appsViewModel: AppsViewModel = koinViewModel(),
	updates: List<AppUpdate>,
	activeTasksMap: Map<Int, DownloadTaskState> = emptyMap(),
	readyUpdateIds: Set<Int> = emptySet(),
	handler: UriHandler,
	modifier: Modifier,
	onBeforeInstall: () -> Unit = {}
) {
	var selectedPackageName by remember { mutableStateOf<String?>(null) }
	val selectedUpdate = updates.find { it.packageName == selectedPackageName }

	BoxWithConstraints(modifier) {
		val columns = updateGridColumnCount(maxWidth.value, compact = true)
		LazyVerticalGrid(
			columns = GridCells.Fixed(columns),
			modifier = Modifier.fillMaxSize(),
			contentPadding = PaddingValues(
				start = CompactUpdateCardLayout.gridHorizontalPaddingDp.dp,
				top = CompactUpdateCardLayout.gridTopPaddingDp.dp,
				end = CompactUpdateCardLayout.gridHorizontalPaddingDp.dp,
				bottom = CompactUpdateCardLayout.gridBottomPaddingDp.dp
			),
			verticalArrangement = Arrangement.spacedBy(CompactUpdateCardLayout.gridSpacingDp.dp),
			horizontalArrangement = Arrangement.spacedBy(CompactUpdateCardLayout.gridSpacingDp.dp)
		) {
			gridItems(items = updates, key = { it.id }) { update ->
				val context = LocalContext.current
				val appName = update.name.ifBlank {
					context.getAppName(update.packageName).ifBlank { update.packageName }
				}
				val card = update.toUpdateCardContent(appName)
				val liveTask = activeTasksMap[update.id]
					?: activeTasksMap.values.firstOrNull { it.packageName == update.packageName }
				val effInstalling = liveTask?.let { it.isInstalling || !it.isQueued } ?: card.isInstalling
				val effQueued = liveTask?.isQueued ?: update.isQueued
				val effProgress = liveTask?.progress ?: update.progress
				val effTotal = liveTask?.total?.takeIf { it > 0L } ?: update.total
				val isCachedReady = update.id in readyUpdateIds || update.alternativeUpdates.any { it.id in readyUpdateIds }
				val cardShape = RoundedCornerShape(14.dp)
				Surface(
					onClick = { selectedPackageName = update.packageName },
					modifier = Modifier
						.fillMaxWidth()
						.animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
					shape = cardShape,
					color = MaterialTheme.colorScheme.surfaceContainerLow
				) {
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.padding(horizontal = 14.dp, vertical = 10.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(12.dp)
					) {
						// 42dp app icon
						Box(
							modifier = Modifier
								.size(42.dp)
								.clip(RoundedCornerShape(12.dp))
								.background(MaterialTheme.colorScheme.surfaceContainer),
							contentAlignment = Alignment.Center
						) {
							LoadingImageApp(
								card.packageName,
								Modifier.fillMaxSize()
						)
						}

						// Main Text (Title, versions, source)
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
									text = card.appName,
									style = MaterialTheme.typography.titleSmall,
									fontWeight = FontWeight.SemiBold,
									color = MaterialTheme.colorScheme.onSurface,
									maxLines = 1,
									overflow = TextOverflow.Ellipsis,
									modifier = Modifier.weight(1f, fill = false)
								)
								if (update.totalSize > 0L) {
									Text(
										text = formatSizeMegabytes(update.totalSize),
										style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
										color = MaterialTheme.colorScheme.onSurfaceVariant
									)
								}
							}

							Row(
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(4.dp)
							) {
								Text(
									text = card.installedVersion.ifBlank { "—" },
									style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
									color = MaterialTheme.colorScheme.onSurfaceVariant,
									maxLines = 1,
									overflow = TextOverflow.Ellipsis,
									modifier = Modifier.weight(1f, fill = false)
								)
								Text(
									text = "→",
									style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
									color = MaterialTheme.colorScheme.primary,
									fontWeight = FontWeight.Bold
								)
								Text(
									text = card.availableVersion.ifBlank { "—" },
									style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
									fontWeight = FontWeight.Bold,
									color = MaterialTheme.colorScheme.primary,
									maxLines = 1,
									overflow = TextOverflow.Ellipsis,
									modifier = Modifier.weight(1f, fill = false)
								)
								Text(
									text = "•",
									style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
									color = MaterialTheme.colorScheme.outline
								)
								val resolvedSourceLabel = sourceName(card.source)
								Row(
									verticalAlignment = Alignment.CenterVertically,
									horizontalArrangement = Arrangement.spacedBy(4.dp)
								) {
									Icon(
										painter = painterResource(card.source.resourceId),
										contentDescription = null,
										tint = MaterialTheme.colorScheme.onSurfaceVariant,
										modifier = Modifier.size(10.dp)
									)
									Text(
										text = resolvedSourceLabel,
										style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
										color = MaterialTheme.colorScheme.onSurfaceVariant,
										maxLines = 1
									)
									if (card.additionalSourcesCount > 0) {
										Surface(
											shape = RoundedCornerShape(4.dp),
											color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
										) {
											Text(
												text = "+${card.additionalSourcesCount}",
												modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
												style = MaterialTheme.typography.labelSmall.copy(
													fontSize = 9.sp,
													fontWeight = FontWeight.Bold
												),
												color = MaterialTheme.colorScheme.onPrimaryContainer
											)
										}
									}
								}
							}

							if (effInstalling || effQueued) {
								val compactLabel = when {
									liveTask?.isInstalling == true -> stringResource(R.string.queue_status_installing)
									liveTask?.isResolvingLink == true && effProgress == 0L -> stringResource(R.string.queue_status_resolving_apkmirror)
									effQueued && !effInstalling -> stringResource(R.string.queued_update)
									effTotal > 0L && effProgress > 0L -> {
										val pct = ((effProgress * 100) / effTotal).toInt().coerceIn(0, 100)
										"$pct%"
									}
									else -> stringResource(R.string.installing_update)
								}
								Row(
									verticalAlignment = Alignment.CenterVertically,
									horizontalArrangement = Arrangement.spacedBy(6.dp),
									modifier = Modifier.padding(top = 2.dp)
								) {
									LinearProgressIndicator(
										progress = { if (effTotal > 0L && effProgress > 0L) (effProgress.toFloat() / effTotal).coerceIn(0f, 1f) else 0f },
										modifier = Modifier.weight(1f).height(3.dp),
										color = MaterialTheme.colorScheme.primary,
										trackColor = MaterialTheme.colorScheme.surfaceContainer
									)
									Text(
										text = compactLabel,
										style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
										fontWeight = FontWeight.Bold,
										color = MaterialTheme.colorScheme.primary
									)
								}
							}
						}

						// Right Action: Fast download button
						if (card.canInstall && !effQueued && !effInstalling) {
							IconButton(
								onClick = {
									onBeforeInstall()
									viewModel.install(update, handler)
								},
								modifier = Modifier
									.size(36.dp)
									.background(MaterialTheme.colorScheme.surfaceContainer, CircleShape)
							) {
								Icon(
									painter = painterResource(R.drawable.ic_install),
									contentDescription = null,
									tint = MaterialTheme.colorScheme.primary,
									modifier = Modifier.size(16.dp)
								)
							}
						}
					}
				}
			}
		}
	}

	selectedUpdate?.let { update ->
		UpdateDetailsSheet(
			update = update,
			isCachedReady = update.id in readyUpdateIds || update.alternativeUpdates.any { it.id in readyUpdateIds },
			readyUpdateIds = readyUpdateIds,
			onDismiss = { selectedPackageName = null },
			onInstallVariant = { chosen ->
				onBeforeInstall()
				viewModel.install(chosen, handler)
				if (chosen.isBrowserOnlyUpdate) {
					selectedPackageName = null
				}
			},
			onIgnoreVersion = { versionId ->
				viewModel.ignoreVersion(versionId)
				selectedPackageName = null
			},
			onIgnoreAppPermanently = { packageName ->
				viewModel.ignoreAppPermanently(packageName)
				appsViewModel.refresh(false)
				selectedPackageName = null
			}
		)
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpdateDetailsSheet(
	update: AppUpdate,
	isCachedReady: Boolean = false,
	readyUpdateIds: Set<Int> = emptySet(),
	onDismiss: () -> Unit,
	onInstallVariant: (AppUpdate) -> Unit,
	onIgnoreVersion: (Int) -> Unit,
	onIgnoreAppPermanently: (String) -> Unit
) {
	val context = LocalContext.current
	val handler = LocalUriHandler.current
	val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
	val liveActiveMap by DownloadInstallService.activeTasksFlow.collectAsStateWithLifecycle()
	val liveTask = liveActiveMap[update.id]
		?: liveActiveMap.values.firstOrNull { it.packageName == update.packageName }

	val sourceVariants = remember(update.packageName, update.alternativeUpdates.size) {
		update.alternativeUpdates.takeIf { it.size > 1 } ?: listOf(update)
	}
	var selectedSourceIndex by remember(update.packageName) {
		val idx = sourceVariants.indexOfFirst { it.source == update.source }.coerceAtLeast(0)
		mutableIntStateOf(idx)
	}
	val rawActiveVariant = sourceVariants.getOrElse(selectedSourceIndex) { update }
	val effInstalling = liveTask?.let { it.isInstalling || !it.isQueued } ?: update.isInstalling
	val effQueued = liveTask?.isQueued ?: update.isQueued
	val effProgress = liveTask?.progress ?: update.progress
	val effTotal = liveTask?.total?.takeIf { it > 0L } ?: update.total

	val activeVariant = rawActiveVariant.copy(
		isInstalling = effInstalling,
		isQueued = effQueued,
		progress = effProgress,
		total = effTotal
	)

	val appName = activeVariant.name.ifBlank {
		context.getAppName(activeVariant.packageName).ifBlank { activeVariant.packageName }
	}
	val card = activeVariant.toUpdateCardContent(appName)
	val originText = installOriginLabel(activeVariant.installOrigin)
	val showSignatureWarning = hasPotentialSignatureConflict(activeVariant.installOrigin, activeVariant.source)
	val isUpdateBusy = effInstalling || effQueued

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
			Row(verticalAlignment = Alignment.CenterVertically) {
				Box(
					modifier = Modifier
						.size(60.dp)
						.clip(RoundedCornerShape(16.dp))
						.background(MaterialTheme.colorScheme.surfaceContainerHigh),
					contentAlignment = Alignment.Center
				) {
					LoadingImageApp(activeVariant.packageName, Modifier.requiredSize(76.dp))
				}
				Column(
					modifier = Modifier.weight(1f).padding(start = 16.dp),
					verticalArrangement = Arrangement.spacedBy(3.dp)
				) {
					Text(
						text = appName,
						style = MaterialTheme.typography.titleLarge,
						fontWeight = FontWeight.Bold
					)
					Text(
						text = activeVariant.packageName,
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
					Surface(
						shape = CircleShape,
						color = MaterialTheme.colorScheme.primaryContainer
					) {
						Text(
							text = "${card.installedVersion.ifBlank { "—" }} → ${card.availableVersion.ifBlank { "—" }}",
							modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
							style = MaterialTheme.typography.labelMedium,
							fontWeight = FontWeight.SemiBold,
							color = MaterialTheme.colorScheme.onPrimaryContainer
						)
					}
				}
			}

			if (sourceVariants.size > 1) {
				Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
					Text(
						text = stringResource(R.string.update_source_select),
						style = MaterialTheme.typography.labelLarge,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.spacedBy(10.dp),
						verticalAlignment = Alignment.CenterVertically
					) {
						sourceVariants.forEachIndexed { index, variant ->
							val isSelected = index == selectedSourceIndex
							Surface(
								onClick = { if (!isUpdateBusy) selectedSourceIndex = index },
								enabled = !isUpdateBusy,
								modifier = Modifier
									.weight(1f)
									.heightIn(min = 44.dp),
								shape = CircleShape,
								color = if (isSelected) {
									MaterialTheme.colorScheme.secondaryContainer
								} else {
									MaterialTheme.colorScheme.surfaceContainerHigh
								},
								border = if (isSelected) {
									androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
								} else null
							) {
								Row(
									modifier = Modifier
										.fillMaxWidth()
										.padding(horizontal = 12.dp, vertical = 10.dp),
									horizontalArrangement = Arrangement.Center,
									verticalAlignment = Alignment.CenterVertically
								) {
									Icon(
										painter = painterResource(variant.source.resourceId),
										contentDescription = null,
										tint = if (isSelected) {
											MaterialTheme.colorScheme.onSecondaryContainer
										} else {
											MaterialTheme.colorScheme.onSurfaceVariant
										},
										modifier = Modifier.size(16.dp)
									)
									Spacer(modifier = Modifier.width(8.dp))
									Text(
										text = sourceName(variant.source),
										style = MaterialTheme.typography.labelLarge,
										fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
										color = if (isSelected) {
											MaterialTheme.colorScheme.onSecondaryContainer
										} else {
											MaterialTheme.colorScheme.onSurfaceVariant
										},
										maxLines = 1,
										overflow = TextOverflow.Ellipsis
									)
								}
							}
						}
					}
				}
			}

			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.spacedBy(8.dp),
				verticalAlignment = Alignment.CenterVertically
			) {
				Surface(
					shape = CircleShape,
					color = MaterialTheme.colorScheme.surfaceContainerHigh
				) {
					Row(
						modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(6.dp)
					) {
						Icon(
							painter = painterResource(activeVariant.source.resourceId),
							contentDescription = null,
							tint = MaterialTheme.colorScheme.primary,
							modifier = Modifier.size(14.dp)
						)
						Text(
							text = sourceName(activeVariant.source),
							style = MaterialTheme.typography.labelMedium,
							fontWeight = FontWeight.Medium
						)
					}
				}
				if (activeVariant.totalSize > 0L) {
					Surface(
						shape = CircleShape,
						color = MaterialTheme.colorScheme.surfaceContainerHigh
					) {
						Text(
							text = formatSizeMegabytes(activeVariant.totalSize),
							modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
							style = MaterialTheme.typography.labelMedium,
							color = MaterialTheme.colorScheme.onSurfaceVariant
						)
					}
				}
				if (activeVariant.updatedOn.isNotBlank()) {
					Surface(
						shape = CircleShape,
						color = MaterialTheme.colorScheme.surfaceContainerHigh
					) {
						Text(
							text = activeVariant.updatedOn,
							modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
							style = MaterialTheme.typography.labelMedium,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis
						)
					}
				}
			}

			Surface(
				shape = RoundedCornerShape(12.dp),
				color = MaterialTheme.colorScheme.surfaceContainer
			) {
				Text(
					text = stringResource(R.string.origin_label, originText),
					modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
			}

			if (showSignatureWarning) {
				Surface(
					onClick = { handler.openUri(resolveWikiUrlForSourceError("signature")) },
					shape = RoundedCornerShape(16.dp),
					color = MaterialTheme.colorScheme.tertiaryContainer
				) {
					Row(
						modifier = Modifier.fillMaxWidth().padding(12.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(10.dp)
					) {
						Icon(
							imageVector = Icons.Filled.Warning,
							contentDescription = null,
							tint = MaterialTheme.colorScheme.onTertiaryContainer,
							modifier = Modifier.size(20.dp)
						)
						Text(
							text = stringResource(
								R.string.signature_conflict_warning,
								originText,
								sourceName(activeVariant.source)
							),
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onTertiaryContainer,
							modifier = Modifier.weight(1f)
						)
						Surface(
							shape = CircleShape,
							color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.14f)
						) {
							Text(
								text = stringResource(R.string.error_banner_wiki_help),
								style = MaterialTheme.typography.labelSmall,
								fontWeight = FontWeight.SemiBold,
								color = MaterialTheme.colorScheme.onTertiaryContainer,
								modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
							)
						}
					}
				}
			}

			Surface(
				shape = RoundedCornerShape(24.dp),
				border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
				color = MaterialTheme.colorScheme.surfaceContainer,
				modifier = Modifier.fillMaxWidth()
			) {
				Column(
					modifier = Modifier.fillMaxWidth().padding(14.dp),
					verticalArrangement = Arrangement.spacedBy(6.dp)
				) {
					Text(
						text = stringResource(R.string.whats_new_title),
						style = MaterialTheme.typography.titleSmall,
						fontWeight = FontWeight.SemiBold,
						color = MaterialTheme.colorScheme.primary
					)
					Text(
						text = activeVariant.whatsNew.ifBlank { stringResource(R.string.whats_new_empty) },
						style = MaterialTheme.typography.bodyMedium,
						color = if (activeVariant.whatsNew.isNotBlank()) {
							MaterialTheme.colorScheme.onSurface
						} else {
							MaterialTheme.colorScheme.onSurfaceVariant
						}
					)
				}
			}

			if (isUpdateBusy) {
				val detailStatusText = when {
					liveTask?.isInstalling == true -> stringResource(R.string.queue_status_installing)
					liveTask?.isResolvingLink == true && effProgress == 0L -> stringResource(R.string.queue_status_resolving_apkmirror)
					effQueued && !effInstalling -> stringResource(R.string.queued_update)
					else -> stringResource(R.string.installing_update)
				}
				Surface(
					shape = RoundedCornerShape(24.dp),
					border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
					color = MaterialTheme.colorScheme.surfaceContainerHigh,
					modifier = Modifier.fillMaxWidth()
				) {
					Column(
						modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
						verticalArrangement = Arrangement.spacedBy(8.dp)
					) {
						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.SpaceBetween,
							verticalAlignment = Alignment.CenterVertically
						) {
							Text(
								text = detailStatusText,
								style = MaterialTheme.typography.labelLarge,
								fontWeight = FontWeight.SemiBold,
								color = MaterialTheme.colorScheme.primary
							)
							if (liveTask?.isInstalling != true && effTotal > 0L && effProgress > 0L) {
								val fraction = (effProgress.toFloat() / effTotal).coerceIn(0f, 1f)
								val pct = (fraction * 100).toInt()
								Text(
									text = "$pct% (${formatSizeMegabytes(effProgress)} / ${formatSizeMegabytes(effTotal)})",
									style = MaterialTheme.typography.labelMedium,
									color = MaterialTheme.colorScheme.onSurfaceVariant
								)
							}
						}
						if (liveTask?.isInstalling != true && effTotal > 0L && effProgress > 0L) {
							LinearProgressIndicator(
								progress = { (effProgress.toFloat() / effTotal).coerceIn(0f, 1f) },
								modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
								color = MaterialTheme.colorScheme.primary,
								trackColor = MaterialTheme.colorScheme.secondaryContainer
							)
						} else {
							LinearProgressIndicator(
								modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
								color = MaterialTheme.colorScheme.primary,
								trackColor = MaterialTheme.colorScheme.secondaryContainer
							)
						}
					}
				}
			}

			val isVariantCachedReady = activeVariant.id in readyUpdateIds || (activeVariant.id == update.id && isCachedReady)
			Button(
				onClick = { onInstallVariant(activeVariant) },
				enabled = !isUpdateBusy,
				shape = CircleShape,
				modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
			) {
				Icon(painterResource(R.drawable.ic_install), contentDescription = null)
				Spacer(Modifier.width(8.dp))
				Text(
					text = when {
						effQueued && !effInstalling -> stringResource(R.string.queued_update)
						effInstalling -> stringResource(R.string.installing_update)
						isVariantCachedReady -> stringResource(R.string.install_cached_action)
						else -> stringResource(R.string.update_action)
					}
				)
			}

			OutlinedButton(
				onClick = { onIgnoreVersion(activeVariant.id) },
				enabled = !isUpdateBusy,
				shape = CircleShape,
				modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
			) {
				Text(stringResource(R.string.skip_this_version))
			}

			TextButton(
				onClick = { onIgnoreAppPermanently(activeVariant.packageName) },
				enabled = !isUpdateBusy,
				shape = CircleShape,
				modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
			) {
				Text(
					text = stringResource(R.string.ignore_app_permanently),
					color = MaterialTheme.colorScheme.error
				)
			}
		}
	}
}

@Composable
private fun installOriginLabel(origin: InstallOrigin): String = when (origin) {
	InstallOrigin.PLAY_STORE -> stringResource(R.string.origin_play)
	InstallOrigin.FDROID -> stringResource(R.string.origin_fdroid)
	InstallOrigin.APKMIRROR -> stringResource(R.string.origin_apkmirror)
	InstallOrigin.REVANCED -> stringResource(R.string.origin_revanced)
	InstallOrigin.SIDELOAD -> stringResource(R.string.origin_sideload)
	InstallOrigin.SYSTEM -> stringResource(R.string.origin_system)
}

internal fun formatSizeMegabytes(bytes: Long): String {
	if (bytes <= 0L) return ""
	val mb = bytes.toDouble() / (1024.0 * 1024.0)
	return String.format(Locale.US, "%.1f MB", mb)
}

@Composable
private fun UpdatesMessage(
	title: String,
	message: String,
	modifier: Modifier = Modifier,
	icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Rounded.Update,
	actionLabel: String? = null,
	onAction: (() -> Unit)? = null
) = AppEmptyState(title, message, icon, modifier, actionLabel, onAction)

@Composable
private fun UpdatesLoadingList(modifier: Modifier = Modifier) = LazyColumn(
	modifier = modifier.fillMaxSize(),
	contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
	verticalArrangement = Arrangement.spacedBy(12.dp)
) {
	items(3) {
		ElevatedCard(
			modifier = Modifier.fillMaxWidth(),
			shape = RoundedCornerShape(24.dp),
			colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
		) {
			Column(
				modifier = Modifier.fillMaxWidth().padding(16.dp),
				verticalArrangement = Arrangement.spacedBy(14.dp)
			) {
				Row(verticalAlignment = Alignment.CenterVertically) {
					SkeletonBlock(Modifier.size(68.dp), RoundedCornerShape(18.dp))
					Column(
						modifier = Modifier.weight(1f).padding(start = 16.dp),
						verticalArrangement = Arrangement.spacedBy(8.dp)
					) {
						SkeletonBlock(Modifier.fillMaxWidth(0.72f).height(18.dp), RoundedCornerShape(8.dp))
						SkeletonBlock(Modifier.fillMaxWidth(0.9f).height(14.dp), RoundedCornerShape(8.dp))
					}
				}
				Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
					SkeletonBlock(Modifier.weight(1f).height(64.dp), RoundedCornerShape(16.dp))
					SkeletonBlock(Modifier.weight(1f).height(64.dp), RoundedCornerShape(16.dp))
				}
				SkeletonBlock(Modifier.fillMaxWidth().height(52.dp), RoundedCornerShape(28.dp))
			}
		}
	}
}

@Composable
private fun SkeletonBlock(modifier: Modifier, shape: Shape) = Box(
	modifier.clip(shape).shimmering(true)
)

@Composable
private fun sourceName(source: Source): String = when (source) {
	ApkMirrorSource -> stringResource(R.string.source_apkmirror)
	PlaySource -> stringResource(R.string.source_play)
	IzzyOnDroidSource -> stringResource(R.string.source_izzyondroid)
	GitHubSource -> stringResource(R.string.source_github)
	GitLabSource -> stringResource(R.string.source_gitlab)
	else -> source.name
}

internal fun resolveWikiUrlForSourceError(sourceName: String?, reason: String? = null): String {
	val base = "https://github.com/DurchEins/apkup/wiki/Update-Sources,-Repository-Linking-&-Signature-Protection"
	val lowerSource = sourceName?.lowercase().orEmpty()
	val lowerReason = reason?.lowercase().orEmpty()

	if (lowerReason.isNotBlank()) {
		when {
			lowerReason.contains("dns") || lowerReason.contains("unknownhost") ->
				return "$base#41-dns-error--server-unreachable"
			lowerReason.contains("timeout") || lowerReason.contains("zeitüberschreitung") ->
				return "$base#42-network-timeout--connection-timed-out"
			lowerReason.contains("401") || lowerReason.contains("403") || lowerReason.contains("429") || lowerReason.contains("auth") || lowerReason.contains("rate") -> {
				return when {
					lowerSource.contains("play") -> "$base#39-common-error-google-play-store-download-fails-http-429-or-paid-apps"
					lowerSource.contains("github") && (lowerReason.contains("403") || lowerReason.contains("rate")) -> "$base#310-common-error-github-updates-are-temporarily-not-found-http-403-rate-limit"
					else -> "$base#43-auth-error--rate-limit-http-401--403--429"
				}
			}
			lowerReason.contains("404") || lowerReason.contains("500") || lowerReason.contains("502") || lowerReason.contains("503") || lowerReason.contains("504") -> {
				return if (lowerSource.contains("apkmirror") && lowerReason.contains("404")) {
					"$base#38-common-error-apkmirror-shows-an-update-but-the-webpage-says-404-not-found"
				} else {
					"$base#44-server-error--not-found-http-404--500--502--503"
				}
			}
			lowerReason.contains("cloudflare") || lowerReason.contains("captcha") || lowerReason.contains("browser") ->
				return "$base#45-apkmirror-cloudflare-protection--browser-fallback"
			lowerReason.contains("signature") || lowerReason.contains("incompatible") || lowerReason.contains("konflikt") ->
				return "$base#46-installation-failed--signature-conflicts"
			lowerReason.contains("asset") || lowerReason.contains("repo") ->
				return "$base#47-repository-linking--no-matching-apk-asset"
		}
	}

	return when {
		lowerSource.contains("signature") ->
			"$base#31-why-does-apkup-sometimes-show-a-warning-icon-for-a-signature-conflict"
		lowerSource.contains("apkmirror") ->
			"$base#23-direct-apkmirror-resolution--hardened-apkm-split-extraction"
		lowerSource.contains("play") ->
			"$base#39-common-error-google-play-store-download-fails-http-429-or-paid-apps"
		lowerSource.contains("github") ->
			"$base#310-common-error-github-updates-are-temporarily-not-found-http-403-rate-limit"
		lowerSource.contains("gitlab") ->
			"$base#25-three-tier-repository-resolution-for-github--gitlab"
		lowerSource.contains("izzy") ->
			"$base#25-three-tier-repository-resolution-for-github--gitlab"
		else ->
			"$base#-part-4-common-error-messages--self-help-troubleshooting"
	}
}

internal fun localizeFailureReason(rawReason: String, isGerman: Boolean): String {
	val lower = rawReason.lowercase()
	val isAuth = lower.contains("auth")
	val httpCode = Regex("HTTP \\d{3}", RegexOption.IGNORE_CASE).find(rawReason)?.value?.uppercase()
	val isTimeout = lower.contains("timeout")
	val isDns = lower.contains("dns") || lower.contains("unknownhost")

	return when {
		isAuth && httpCode != null -> if (isGerman) "Auth-Fehler / $httpCode" else "Auth error / $httpCode"
		isAuth && isTimeout -> if (isGerman) "Netzwerk-Timeout / Auth-Fehler" else "Network timeout / Auth error"
		isAuth -> if (isGerman) "Auth-Fehler" else "Auth error"
		httpCode != null -> httpCode
		isTimeout -> if (isGerman) "Netzwerk-Timeout" else "Network timeout"
		isDns -> if (isGerman) "DNS-Fehler" else "DNS lookup failed"
		else -> if (isGerman) "Netzwerk-/Verarbeitungsfehler" else "Network/processing error"
	}
}

internal fun formatSourceErrorBanner(
	sourceErrors: Map<String, String>,
	activeSources: List<String> = listOf("APKMirror", "Google Play"),
	isGerman: Boolean = true
): String {
	if (sourceErrors.isEmpty()) {
		return if (isGerman) {
			"Google Play-Prüfung fehlgeschlagen (Netzwerk-Timeout / Auth-Fehler). APKMirror-Ergebnisse werden angezeigt."
		} else {
			"Google Play check failed (Network timeout / Auth error). Showing APKMirror results."
		}
	}
	val failedEntries = sourceErrors.entries.toList()
	val failedDescription = failedEntries.joinToString(separator = "; ") { (source, rawReason) ->
		val reason = localizeFailureReason(rawReason, isGerman)
		if (isGerman) "$source-Prüfung fehlgeschlagen ($reason)" else "$source check failed ($reason)"
	}
	val survivingSources = activeSources.filter { it !in sourceErrors.keys }
	val survivingText = when {
		survivingSources.isNotEmpty() -> {
			val names = survivingSources.joinToString(" & ")
			if (isGerman) "$names-Ergebnisse werden angezeigt." else "Showing $names results."
		}
		else -> {
			val names = failedEntries.joinToString(" & ") { it.key }
			if (isGerman) "Teilweise $names-Ergebnisse werden angezeigt." else "Showing partial $names results."
		}
	}
	return "$failedDescription. $survivingText"
}

internal fun formatPartialEmptyMessage(
	sourceErrors: Map<String, String>,
	activeSources: List<String> = listOf("APKMirror", "Google Play"),
	isGerman: Boolean = true
): String {
	if (sourceErrors.isEmpty()) {
		return if (isGerman) {
			"Google Play-Prüfung fehlgeschlagen (Netzwerk-Timeout / Auth-Fehler). APKMirror hat keine Updates gemeldet."
		} else {
			"Google Play check failed (Network timeout / Auth error). APKMirror reported no updates."
		}
	}
	val failedEntries = sourceErrors.entries.toList()
	val failedDescription = failedEntries.joinToString(separator = "; ") { (source, rawReason) ->
		val reason = localizeFailureReason(rawReason, isGerman)
		if (isGerman) "$source-Prüfung fehlgeschlagen ($reason)" else "$source check failed ($reason)"
	}
	val survivingSources = activeSources.filter { it !in sourceErrors.keys }
	val survivingText = if (survivingSources.isNotEmpty()) {
		val names = survivingSources.joinToString(" & ")
		if (isGerman) "$names hat keine Updates gemeldet." else "$names reported no updates."
	} else {
		if (isGerman) "Keine erreichbare Quelle hat Updates gemeldet." else "No reachable source reported updates."
	}
	return "$failedDescription. $survivingText"
}
