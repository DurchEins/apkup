package com.apkup.ui.screen

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PushPin
import com.apkup.data.ui.SourcePriorityKeys
import com.apkup.ui.component.ApkUpExpressiveLoadingIndicator
import com.apkup.ui.component.ApkUpSplitActionButton
import com.apkup.ui.component.ApkUpWavyLinearProgressIndicator
import com.apkup.ui.component.sourcePriorityLabel
import com.apkup.ui.theme.ApkUpTheme
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apkup.R
import com.apkup.ui.component.CollapsibleScrollHeader
import com.apkup.ui.component.rememberCollapsibleHeaderState
import com.apkup.data.ui.ApkMirrorSource
import com.apkup.data.ui.AppUpdate
import com.apkup.data.ui.cleanChangelogText
import com.apkup.data.ui.FDroidSource
import com.apkup.data.ui.GitHubSource
import com.apkup.data.ui.CodebergSource
import com.apkup.data.ui.CustomFDroidSource
import com.apkup.data.ui.GitLabSource
import com.apkup.data.ui.isWatchedApp
import com.apkup.data.ui.InstallOrigin
import com.apkup.data.ui.IzzyOnDroidSource
import com.apkup.data.ui.Link
import com.apkup.data.ui.PlaySource
import com.apkup.data.ui.Source
import com.apkup.data.ui.hasPotentialSignatureConflict
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
import com.apkup.util.BatchProgressCalculator
import com.apkup.util.BatchProgressSummary
import com.apkup.util.BatchProgressUnits
import com.apkup.ui.component.toUpdateCardContent
import com.apkup.ui.component.updateGridColumnCount
import com.apkup.util.getAppName
import com.apkup.util.InstallLog
import com.apkup.util.SourceHealthTracker
import com.apkup.util.openSafeHttpsUri
import com.apkup.viewmodel.AppsViewModel
import com.apkup.viewmodel.UpdatesViewModel
import com.apkup.viewmodel.partitionInstallQueueCandidates
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

enum class UpdatesSortOrder {
	NAME_ASC,
	NAME_DESC,
	SOURCE,
	RELEASE_DATE
}

private val RELEASE_DATE_FORMATTERS = listOf(
	java.time.format.DateTimeFormatter.ISO_LOCAL_DATE,
	java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy", java.util.Locale.ENGLISH),
	java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy", java.util.Locale.ENGLISH),
	java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.ENGLISH),
	java.time.format.DateTimeFormatter.ofPattern("d. MMM yyyy", java.util.Locale.GERMAN),
	java.time.format.DateTimeFormatter.ofPattern("d. MMMM yyyy", java.util.Locale.GERMAN),
	java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy", java.util.Locale.GERMAN)
)

private val RELEASE_DATE_TIME_FORMATTERS = listOf(
	java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME,
	java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
)

internal fun parseReleaseDateMillis(raw: String): Long {
	val trimmed = raw.trim()
	if (trimmed.isEmpty()) return 0L
	trimmed.toLongOrNull()?.let { numeric ->
		return if (numeric > 1_000_000_000_000L) numeric else numeric * 1_000L
	}
	runCatching { return java.time.Instant.parse(trimmed).toEpochMilli() }
	runCatching { return java.time.OffsetDateTime.parse(trimmed).toInstant().toEpochMilli() }
	for (formatter in RELEASE_DATE_TIME_FORMATTERS) {
		runCatching {
			return java.time.LocalDateTime.parse(trimmed, formatter)
				.atZone(java.time.ZoneOffset.UTC)
				.toInstant()
				.toEpochMilli()
		}
	}
	for (formatter in RELEASE_DATE_FORMATTERS) {
		runCatching {
			return java.time.LocalDate.parse(trimmed, formatter)
				.atStartOfDay(java.time.ZoneOffset.UTC)
				.toInstant()
				.toEpochMilli()
		}
	}
	return 0L
}

internal fun effectiveReleaseDateMillis(update: AppUpdate): Long =
	parseReleaseDateMillis(update.updatedOn).takeIf { it > 0L }
		?: update.alternativeUpdates.maxOfOrNull { parseReleaseDateMillis(it.updatedOn) }
		?: 0L

internal fun filterAndSortUpdates(
	updates: List<AppUpdate>,
	selectedSourceFilter: String?,
	searchQuery: String,
	sortOrder: UpdatesSortOrder
): List<AppUpdate> {
	val bySource = if (selectedSourceFilter == null) {
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

	val cleanQuery = searchQuery.trim()
	val byQuery = if (cleanQuery.isEmpty()) {
		bySource
	} else {
		bySource.filter {
			it.name.contains(cleanQuery, ignoreCase = true) ||
				it.packageName.contains(cleanQuery, ignoreCase = true)
		}
	}

	fun sortName(update: AppUpdate): String =
		update.name.trim().ifEmpty { update.packageName }.lowercase()

	return when (sortOrder) {
		UpdatesSortOrder.NAME_ASC -> byQuery.sortedWith(
			compareBy<AppUpdate> { sortName(it) }
				.thenBy { it.packageName.lowercase() }
		)
		UpdatesSortOrder.NAME_DESC -> byQuery.sortedWith(
			compareByDescending<AppUpdate> { sortName(it) }
				.thenByDescending { it.packageName.lowercase() }
		)
		UpdatesSortOrder.SOURCE -> byQuery.sortedWith(
			compareBy<AppUpdate> { it.source.name.lowercase() }
				.thenBy { sortName(it) }
		)
		UpdatesSortOrder.RELEASE_DATE -> byQuery.sortedWith(
			compareByDescending<AppUpdate> { effectiveReleaseDateMillis(it) }
				.thenBy { sortName(it) }
		)
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesTopBar(
	viewModel: UpdatesViewModel,
	updatesCount: Int? = null,
	hasInstallableUpdates: Boolean = false,
	onOpenHistory: (() -> Unit)? = null,
	selectedCount: Int = 0,
	allSelectableSelected: Boolean = false,
	onClearSelection: (() -> Unit)? = null,
	onToggleSelectAll: (() -> Unit)? = null,
	searchExpanded: Boolean = false,
	onToggleSearch: (() -> Unit)? = null,
	sortOrder: UpdatesSortOrder = UpdatesSortOrder.NAME_ASC,
	onSortOrderChange: ((UpdatesSortOrder) -> Unit)? = null
) {
	if (selectedCount > 0) {
		TopAppBar(
			title = {
				Text(
					text = stringResource(R.string.updates_selected_count, selectedCount),
					style = MaterialTheme.typography.titleLarge,
					fontWeight = FontWeight.Bold,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis
				)
			},
			colors = TopAppBarDefaults.topAppBarColors(
				containerColor = MaterialTheme.colorScheme.surfaceContainer,
				scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
			),
			navigationIcon = {
				IconButton(onClick = { onClearSelection?.invoke() }) {
					Icon(
						imageVector = Icons.Filled.Close,
						contentDescription = stringResource(R.string.updates_clear_selection)
					)
				}
			},
			actions = {
				if (onToggleSelectAll != null) {
					TextButton(onClick = onToggleSelectAll) {
						Text(
							text = stringResource(
								if (allSelectableSelected) R.string.updates_deselect_all else R.string.updates_select_all
							),
							style = MaterialTheme.typography.labelLarge,
							fontWeight = FontWeight.SemiBold
						)
					}
				}
			},
			windowInsets = WindowInsets(0)
		)
		return
	}

	TopAppBar(
		title = {
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(8.dp)
			) {
				Text(
					text = stringResource(R.string.tab_updates),
					style = MaterialTheme.typography.headlineMedium,
					fontWeight = FontWeight.Bold,
					maxLines = 1,
					softWrap = false,
					overflow = TextOverflow.Ellipsis,
					modifier = Modifier.weight(1f, fill = false)
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
			if (onToggleSearch != null) {
				IconButton(onClick = onToggleSearch) {
					Icon(
						imageVector = if (searchExpanded) Icons.Filled.Clear else Icons.Filled.Search,
						contentDescription = stringResource(R.string.updates_search_placeholder)
					)
				}
			}
			if (onSortOrderChange != null) {
				var sortMenuExpanded by remember { mutableStateOf(false) }
				Box {
					IconButton(onClick = { sortMenuExpanded = true }) {
						Icon(
							imageVector = Icons.AutoMirrored.Rounded.Sort,
							contentDescription = stringResource(R.string.updates_sort_action),
							tint = if (sortOrder != UpdatesSortOrder.NAME_ASC) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
						)
					}
					DropdownMenu(
						expanded = sortMenuExpanded,
						onDismissRequest = { sortMenuExpanded = false },
						shape = RoundedCornerShape(18.dp),
						containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
					) {
						listOf(
							UpdatesSortOrder.NAME_ASC to R.string.updates_sort_name,
							UpdatesSortOrder.NAME_DESC to R.string.updates_sort_name_desc,
							UpdatesSortOrder.SOURCE to R.string.updates_sort_source,
							UpdatesSortOrder.RELEASE_DATE to R.string.updates_sort_release_date
						).forEach { (option, labelRes) ->
							DropdownMenuItem(
								text = {
									Text(
										text = stringResource(labelRes),
										fontWeight = if (sortOrder == option) FontWeight.SemiBold else FontWeight.Normal,
										color = if (sortOrder == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
									)
								},
								trailingIcon = {
									if (sortOrder == option) {
										Icon(
											imageVector = Icons.Filled.Check,
											contentDescription = null,
											tint = MaterialTheme.colorScheme.primary,
											modifier = Modifier.size(18.dp)
										)
									}
								},
								onClick = {
									onSortOrderChange(option)
									sortMenuExpanded = false
								}
							)
						}
					}
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
}

@Composable
fun UpdatesScreenLoading(
	viewModel: UpdatesViewModel,
	onOpenHistory: (() -> Unit)? = null
) = Column(Modifier.fillMaxSize()) {
	val view = LocalView.current
	val initialSource = when {
		viewModel.isUseApkMirrorEnabled() -> stringResource(R.string.source_apkmirror)
		viewModel.isUsePlayEnabled() -> stringResource(R.string.source_play)
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
	val handler = com.apkup.ui.component.rememberCustomTabUriHandler()
	UpdatesTopBar(viewModel = viewModel, onOpenHistory = onOpenHistory)
	UpdatesMessage(
		title = stringResource(R.string.updates_error_title),
		message = stringResource(R.string.updates_error_message),
		icon = Icons.Filled.Info,
		actionLabel = stringResource(R.string.error_banner_wiki_help),
		onAction = { handler.openSafeHttpsUri(resolveWikiUrlForSourceError(null)) },
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
) {
	val installLog: InstallLog = koinInject()
	var failedUpdateIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
	var jumpToErrorTick by remember { mutableIntStateOf(0) }
	LaunchedEffect(installLog) {
		installLog.status().collect { status ->
			failedUpdateIds = if (status.success) {
				failedUpdateIds - status.id
			} else if (status.id != 0) {
				failedUpdateIds + status.id
			} else {
				failedUpdateIds
			}
		}
	}
	LaunchedEffect(isChecking) {
		if (isChecking) failedUpdateIds = emptySet()
	}

	val context = LocalContext.current
	val handler = com.apkup.ui.component.rememberCustomTabUriHandler()
	val displayMode = viewModel.getUpdatesDisplayMode()
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

	val activeTasksMap by viewModel.activeTasksFlow.collectAsStateWithLifecycle()
	val completedTasks by viewModel.completedTasksFlow.collectAsStateWithLifecycle()
	val batchProgress by viewModel.batchProgressFlow.collectAsStateWithLifecycle()
	val readyUpdateIds by viewModel.readyUpdateIds.collectAsStateWithLifecycle()
	val activeTasks = remember(activeTasksMap) { activeTasksMap.values.toList() }
	var showQueueSheet by rememberSaveable { mutableStateOf(false) }
	val showMobileDataDialog by viewModel.showMobileDataDialog.collectAsStateWithLifecycle()
	val showBatteryPromptDialog by viewModel.showBatteryPromptDialog.collectAsStateWithLifecycle()

	var selectedSourceFilter by rememberSaveable { mutableStateOf<String?>(null) }
	var searchExpanded by rememberSaveable { mutableStateOf(false) }
	var searchQuery by rememberSaveable { mutableStateOf("") }
	var sortOrder by rememberSaveable { mutableStateOf(UpdatesSortOrder.NAME_ASC) }

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

	val displayedUpdates = remember(updates, selectedSourceFilter, searchQuery, sortOrder) {
		filterAndSortUpdates(
			updates = updates,
			selectedSourceFilter = selectedSourceFilter,
			searchQuery = searchQuery,
			sortOrder = sortOrder
		)
	}

	val activeSources = viewModel.getActiveSourceNames()
	val fdroidRepoNames = viewModel.getEnabledFDroidRepoNames().toSet()
	val localeLanguage = LocalConfiguration.current.locales[0]?.language.orEmpty()
	val isGerman = localeLanguage.startsWith("de", ignoreCase = true)
	val directMirror = viewModel.isDirectApkMirrorEnabled()
	val hasInstallable = displayedUpdates.any {
		it.isDirectInstallable(directMirror) || it.alternativeUpdates.any { alt -> alt.isDirectInstallable(directMirror) }
	}

	val historyEntries by viewModel.updateHistoryFlow.collectAsStateWithLifecycle()
	var pendingWatchedApkMirrorConfirm by remember {
		mutableStateOf<Pair<List<AppUpdate>, () -> Unit>?>(null)
	}

	val customTabHandler = com.apkup.ui.component.rememberCustomTabUriHandler()

	val requestInstallSingle: (AppUpdate) -> Unit = { target ->
		val execute = {
			requestNotificationPermissionIfNeeded()
			viewModel.install(target, customTabHandler)
		}
		if (target.isWatchedApp() && target.source == ApkMirrorSource) {
			pendingWatchedApkMirrorConfirm = listOf(target) to execute
		} else {
			execute()
		}
	}

	val requestSaveApkSingle: (AppUpdate) -> Unit = { target ->
		val execute = {
			viewModel.saveApk(target, customTabHandler)
		}
		if (target.isWatchedApp() && target.source == ApkMirrorSource) {
			pendingWatchedApkMirrorConfirm = listOf(target) to execute
		} else {
			execute()
		}
	}

	var selectedUpdateIds by rememberSaveable(
		stateSaver = listSaver(
			save = { it.toList() },
			restore = { it.toSet() }
		)
	) { mutableStateOf(emptySet<Int>()) }

	val selectableUpdates = remember(displayedUpdates, activeTasksMap) {
		displayedUpdates.filter { update ->
			val liveTask = activeTasksMap[update.id]
				?: activeTasksMap.values.firstOrNull { it.packageName == update.packageName }
			val effInstalling = liveTask?.let { it.isInstalling || !it.isQueued } ?: update.isInstalling
			val effQueued = liveTask?.isQueued ?: update.isQueued
			!effInstalling && !effQueued
		}
	}
	val selectableIds = remember(selectableUpdates) { selectableUpdates.map { it.id }.toSet() }

	LaunchedEffect(selectableIds) {
		if (selectedUpdateIds.isNotEmpty()) {
			val filtered = selectedUpdateIds.intersect(selectableIds)
			if (filtered != selectedUpdateIds) {
				selectedUpdateIds = filtered
			}
		}
	}

	val isSelectionMode = selectedUpdateIds.isNotEmpty()
	val allSelectableSelected = selectableIds.isNotEmpty() && selectableIds.all { it in selectedUpdateIds }
	val selectedUpdates = remember(displayedUpdates, selectedUpdateIds) {
		displayedUpdates.filter { it.id in selectedUpdateIds }
	}

	BackHandler(enabled = isSelectionMode) {
		selectedUpdateIds = emptySet()
	}

	BackHandler(enabled = !isSelectionMode && (searchExpanded || searchQuery.isNotBlank())) {
		searchQuery = ""
		searchExpanded = false
	}

	val toggleSelection: (Int) -> Unit = { updateId ->
		if (updateId in selectableIds) {
			selectedUpdateIds = if (updateId in selectedUpdateIds) {
				selectedUpdateIds - updateId
			} else {
				selectedUpdateIds + updateId
			}
		}
	}

	val collapsibleHeaderState = rememberCollapsibleHeaderState(
		pinned = isSelectionMode ||
			searchExpanded ||
			searchQuery.isNotBlank() ||
			activeTasks.isNotEmpty() ||
			isChecking ||
			displayedUpdates.isEmpty()
	)

	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.background)
	) {
	Column(modifier = Modifier.fillMaxSize()) {
	UpdatesTopBar(
		viewModel = viewModel,
		updatesCount = displayedUpdates.size,
		hasInstallableUpdates = hasInstallable,
		onOpenHistory = onOpenHistory,
		selectedCount = selectedUpdateIds.size,
		allSelectableSelected = allSelectableSelected,
		onClearSelection = { selectedUpdateIds = emptySet() },
		onToggleSelectAll = {
			selectedUpdateIds = if (allSelectableSelected) emptySet() else selectableIds
		},
		searchExpanded = searchExpanded,
		onToggleSearch = if (updates.isNotEmpty()) {
			{
				if (searchExpanded) {
					searchQuery = ""
					searchExpanded = false
				} else {
					searchExpanded = true
				}
			}
		} else null,
		sortOrder = sortOrder,
		onSortOrderChange = if (updates.isNotEmpty()) {
			{ newOrder -> sortOrder = newOrder }
		} else null
	)

	AnimatedVisibility(
		visible = !isSelectionMode && (searchExpanded || searchQuery.isNotBlank()),
		enter = expandVertically() + fadeIn(),
		exit = shrinkVertically() + fadeOut()
	) {
		DockedUpdatesSearchBar(
			query = searchQuery,
			onQueryChange = { searchQuery = it },
			onClear = {
				if (searchQuery.isNotBlank()) {
					searchQuery = ""
				} else {
					searchExpanded = false
				}
			}
		)
	}

	CollapsibleScrollHeader(state = collapsibleHeaderState) {
		Column(modifier = Modifier.fillMaxWidth()) {
			if ((updates.isNotEmpty() && !isChecking) || activeTasks.isNotEmpty() || isSelectionMode) {
				UpdatesHeroBanner(
					updatesCount = displayedUpdates.size,
					hasInstallable = hasInstallable,
					activeTasks = activeTasks,
					completedCount = completedTasks.size,
					batchProgress = batchProgress,
					selectedCount = selectedUpdateIds.size,
					onInstallAll = {
						val (directQueue, _) = partitionInstallQueueCandidates(displayedUpdates, directMirror)
						val riskyWatched = directQueue.filter { it.isWatchedApp() && it.source == ApkMirrorSource }
						val execute: () -> Unit = {
							requestNotificationPermissionIfNeeded()
							viewModel.installAll(displayedUpdates)
						}
						if (riskyWatched.isNotEmpty()) {
							pendingWatchedApkMirrorConfirm = riskyWatched to execute
						} else {
							execute()
						}
					},
					onInstallSelected = {
						val toInstall = selectedUpdates
						val (directQueue, _) = partitionInstallQueueCandidates(toInstall, directMirror)
						val riskyWatched = directQueue.filter { it.isWatchedApp() && it.source == ApkMirrorSource }
						val execute: () -> Unit = {
							requestNotificationPermissionIfNeeded()
							selectedUpdateIds = emptySet()
							viewModel.installSelected(toInstall)
						}
						if (riskyWatched.isNotEmpty()) {
							pendingWatchedApkMirrorConfirm = riskyWatched to execute
						} else {
							execute()
						}
					},
					onOpenQueue = { showQueueSheet = true },
					onOpenHistory = onOpenHistory,
					onSelectAll = {
						selectedUpdateIds = displayedUpdates
							.filter { it.name.isNotBlank() || it.packageName.isNotBlank() }
							.map { it.id }
							.toSet()
					}
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
				isGerman = isGerman,
				fdroidRepoNames = fdroidRepoNames
			)
			Surface(
				onClick = { handler.openSafeHttpsUri(wikiUrl) },
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
					isGerman = isGerman,
					fdroidRepoNames = fdroidRepoNames
				)
				Surface(
					onClick = { handler.openSafeHttpsUri(wikiUrl) },
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
		}
	}

	when {
		isChecking && updates.isEmpty() -> UpdatesLoadingList(Modifier.weight(1f))
		hasSourceErrors && updates.isEmpty() -> UpdatesMessage(
			title = stringResource(R.string.updates_partial_empty_title),
			message = formatPartialEmptyMessage(
				sourceErrors = sourceErrors,
				activeSources = activeSources,
				isGerman = isGerman,
				fdroidRepoNames = fdroidRepoNames
			),
			icon = Icons.Filled.Info,
			actionLabel = stringResource(R.string.error_banner_wiki_help),
			onAction = {
				val firstEntry = sourceErrors.entries.firstOrNull()
				handler.openSafeHttpsUri(resolveWikiUrlForSourceError(firstEntry?.key, firstEntry?.value))
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
			title = stringResource(
				if (searchQuery.isNotBlank()) R.string.updates_search_empty_title else R.string.updates_empty_title
			),
			message = stringResource(
				if (searchQuery.isNotBlank()) R.string.updates_search_empty_message else R.string.updates_empty_source_filter_message
			),
			modifier = Modifier.weight(1f)
		)
		displayMode == 1 -> {
			UpdatesPullToRefreshBox(
				isChecking = isChecking,
				onRefresh = { viewModel.refresh() },
				modifier = Modifier.weight(1f)
			) {
				CompactUpdatesGrid(
					viewModel = viewModel,
					appsViewModel = appsViewModel,
					updates = displayedUpdates,
					scrollResetKey = Triple(sortOrder, selectedSourceFilter, searchQuery.trim()),
					activeTasksMap = activeTasksMap,
					readyUpdateIds = readyUpdateIds,
					failedUpdateIds = failedUpdateIds,
					jumpToErrorTick = jumpToErrorTick,
					extraBottomPadding = 0.dp,
					handler = handler,
					modifier = Modifier
						.fillMaxSize()
						.nestedScroll(collapsibleHeaderState.nestedScrollConnection),
					onRequestInstall = requestInstallSingle,
					onRequestSaveApk = requestSaveApkSingle,
					isSelectionMode = isSelectionMode,
					selectedUpdateIds = selectedUpdateIds,
					onToggleSelection = toggleSelection
				)
			}
		}
		else -> {
			UpdatesPullToRefreshBox(
				isChecking = isChecking,
				onRefresh = { viewModel.refresh() },
				modifier = Modifier.weight(1f)
			) {
				UpdatesList(
					viewModel = viewModel,
					appsViewModel = appsViewModel,
					updates = displayedUpdates,
					scrollResetKey = Triple(sortOrder, selectedSourceFilter, searchQuery.trim()),
					activeTasksMap = activeTasksMap,
					readyUpdateIds = readyUpdateIds,
					failedUpdateIds = failedUpdateIds,
					jumpToErrorTick = jumpToErrorTick,
					extraBottomPadding = 0.dp,
					handler = handler,
					modifier = Modifier
						.fillMaxSize()
						.nestedScroll(collapsibleHeaderState.nestedScrollConnection),
					onRequestInstall = requestInstallSingle,
					onRequestSaveApk = requestSaveApkSingle,
					isSelectionMode = isSelectionMode,
					selectedUpdateIds = selectedUpdateIds,
					onToggleSelection = toggleSelection
				)
			}
		}
	}
	}

	val hasVisibleFailedItem = remember(failedUpdateIds, displayedUpdates) {
		failedUpdateIds.isNotEmpty() && displayedUpdates.any { it.id in failedUpdateIds }
	}
	val isReducedMotionDock = ApkUpTheme.isReducedMotion

	AnimatedVisibility(
		visible = hasVisibleFailedItem && !isSelectionMode,
		enter = if (isReducedMotionDock) fadeIn(tween(80)) else slideInVertically { it } + fadeIn(),
		exit = if (isReducedMotionDock) fadeOut(tween(80)) else slideOutVertically { it } + fadeOut(),
		modifier = Modifier
			.align(Alignment.BottomCenter)
			.navigationBarsPadding()
			.padding(bottom = 96.dp)
	) {
		Surface(
			onClick = { jumpToErrorTick++ },
			shape = CircleShape,
			color = MaterialTheme.colorScheme.errorContainer,
			contentColor = MaterialTheme.colorScheme.onErrorContainer,
			tonalElevation = 4.dp,
			shadowElevation = 6.dp,
			border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
		) {
			Row(
				modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(6.dp)
			) {
				Icon(
					imageVector = Icons.Filled.Warning,
					contentDescription = null,
					modifier = Modifier.size(16.dp)
				)
				Text(
					text = stringResource(R.string.updates_jump_to_error),
					style = MaterialTheme.typography.labelLarge,
					fontWeight = FontWeight.SemiBold
				)
			}
		}
	}
	}

	if (showQueueSheet) {
		ActiveQueueBottomSheet(
			activeTasks = activeTasks,
			completedTasks = completedTasks,
			batchProgress = batchProgress,
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

	if (showBatteryPromptDialog) {
		androidx.compose.material3.AlertDialog(
			onDismissRequest = { viewModel.dismissBatteryPromptDialog() },
			title = {
				Text(
					text = stringResource(R.string.dialog_battery_title),
					style = MaterialTheme.typography.titleLarge,
					fontWeight = FontWeight.Bold
				)
			},
			text = {
				Text(
					text = stringResource(R.string.dialog_battery_message),
					style = MaterialTheme.typography.bodyMedium
				)
			},
			confirmButton = {
				Button(onClick = { viewModel.confirmBatteryDownload() }) {
					Text(text = stringResource(R.string.dialog_battery_confirm))
				}
			},
			dismissButton = {
				TextButton(onClick = { viewModel.dismissBatteryPromptDialog() }) {
					Text(text = stringResource(R.string.action_cancel))
				}
			}
		)
	}

	pendingWatchedApkMirrorConfirm?.let { (riskyUpdates, confirmAction) ->
		val appNames = riskyUpdates.joinToString(", ") { candidate ->
			candidate.name.ifBlank { candidate.packageName }
		}
		androidx.compose.material3.AlertDialog(
			onDismissRequest = { pendingWatchedApkMirrorConfirm = null },
			icon = {
				Icon(
					imageVector = Icons.Filled.Warning,
					contentDescription = null,
					tint = MaterialTheme.colorScheme.error
				)
			},
			title = {
				Text(
					text = stringResource(R.string.apkmirror_watched_first_install_title),
					style = MaterialTheme.typography.titleLarge,
					fontWeight = FontWeight.Bold
				)
			},
			text = {
				Text(
					text = stringResource(R.string.apkmirror_watched_first_install_message, appNames),
					style = MaterialTheme.typography.bodyMedium
				)
			},
			confirmButton = {
				Button(
					onClick = {
						pendingWatchedApkMirrorConfirm = null
						confirmAction()
					}
				) {
					Text(text = stringResource(R.string.apkmirror_watched_first_install_confirm))
				}
			},
			dismissButton = {
				TextButton(onClick = { pendingWatchedApkMirrorConfirm = null }) {
					Text(text = stringResource(R.string.action_cancel))
				}
			}
		)
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpdatesPullToRefreshBox(
	isChecking: Boolean,
	onRefresh: () -> Unit,
	modifier: Modifier = Modifier,
	content: @Composable () -> Unit
) {
	val pullToRefreshState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
	val pullHaptics = LocalHapticFeedback.current
	var pullThresholdCrossed by remember { mutableStateOf(false) }
	LaunchedEffect(pullToRefreshState.distanceFraction, isChecking) {
		if (!isChecking && pullToRefreshState.distanceFraction >= 1f && !pullThresholdCrossed) {
			pullThresholdCrossed = true
			pullHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
		} else if (pullToRefreshState.distanceFraction < 0.75f) {
			pullThresholdCrossed = false
		}
	}
	androidx.compose.material3.pulltorefresh.PullToRefreshBox(
		isRefreshing = isChecking,
		onRefresh = onRefresh,
		state = pullToRefreshState,
		modifier = modifier,
		indicator = {
			if (!isChecking && pullToRefreshState.distanceFraction > 0.05f) {
				androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator(
					state = pullToRefreshState,
					isRefreshing = false,
					modifier = Modifier.align(Alignment.TopCenter)
				)
			}
		}
	) {
		content()
	}
}

@Composable
private fun DockedUpdatesSearchBar(
	query: String,
	onQueryChange: (String) -> Unit,
	onClear: () -> Unit
) {
	val focusManager = LocalFocusManager.current
	TextField(
		value = query,
		onValueChange = onQueryChange,
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 14.dp, vertical = 4.dp)
			.height(54.dp)
			.clip(CircleShape),
		placeholder = {
			Text(
				text = stringResource(R.string.updates_search_placeholder),
				style = MaterialTheme.typography.bodyMedium
			)
		},
		leadingIcon = {
			Icon(
				imageVector = Icons.Filled.Search,
				contentDescription = null,
				tint = MaterialTheme.colorScheme.primary
			)
		},
		trailingIcon = {
			if (query.isNotBlank()) {
				IconButton(onClick = {
					onClear()
					focusManager.clearFocus()
				}) {
					Icon(
						imageVector = Icons.Filled.Clear,
						contentDescription = null,
						tint = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
			}
		},
		singleLine = true,
		textStyle = MaterialTheme.typography.bodyMedium,
		keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
		keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
		shape = CircleShape,
		colors = TextFieldDefaults.colors(
			focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
			unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
			focusedIndicatorColor = Color.Transparent,
			unfocusedIndicatorColor = Color.Transparent,
			disabledIndicatorColor = Color.Transparent
		)
	)
}

@Composable
private fun UpdatesHeroBanner(
	updatesCount: Int,
	hasInstallable: Boolean,
	activeTasks: List<DownloadTaskState> = emptyList(),
	completedCount: Int = 0,
	batchProgress: BatchProgressSummary? = null,
	selectedCount: Int = 0,
	onInstallAll: () -> Unit,
	onInstallSelected: () -> Unit = {},
	onOpenQueue: () -> Unit = {},
	onOpenHistory: (() -> Unit)? = null,
	onSelectAll: () -> Unit = {}
) {
	val context = LocalContext.current
	val resources = LocalResources.current
	val shape = MaterialTheme.shapes.large
	if (selectedCount > 0) {
		Surface(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 12.dp, vertical = 4.dp),
			shape = shape,
			color = MaterialTheme.colorScheme.secondaryContainer,
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
								imageVector = Icons.Filled.Check,
								contentDescription = null,
								tint = MaterialTheme.colorScheme.onPrimary,
								modifier = Modifier.size(20.dp)
							)
						}
					}
					Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
						Text(
							text = stringResource(R.string.updates_selected_count, selectedCount),
							style = MaterialTheme.typography.titleMedium,
							fontWeight = FontWeight.Bold,
							color = MaterialTheme.colorScheme.onSecondaryContainer,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis
						)
						Text(
							text = stringResource(R.string.updates_install_selected_subtitle),
							style = MaterialTheme.typography.labelSmall,
							color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f),
							maxLines = 1,
							overflow = TextOverflow.Ellipsis
						)
					}
				}
				Spacer(Modifier.width(8.dp))
				Button(
					onClick = onInstallSelected,
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
						text = stringResource(R.string.updates_install_selected, selectedCount),
						style = MaterialTheme.typography.labelMedium,
						fontWeight = FontWeight.Bold
					)
				}
			}
		}
		return
	}
	if (activeTasks.isNotEmpty()) {
		val currentTask = activeTasks.firstOrNull { !it.isQueued || it.isInstalling } ?: activeTasks.first()
		val currentAppName = currentTask.appName.ifBlank {
			context.getAppName(currentTask.packageName).ifBlank { currentTask.packageName }
		}
		val totalInRun = (batchProgress?.totalCount ?: (completedCount + activeTasks.size)).coerceAtLeast(1)
		val currentStep = (batchProgress?.currentStep ?: (completedCount + 1)).coerceAtMost(totalInRun)
		val liveSubtitle = when {
			currentTask.isInstalling && currentTask.isSaveOnly -> "$currentAppName • ${stringResource(R.string.queue_status_saving)}"
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
		val batchMetaLine = remember(resources, batchProgress, totalInRun) {
			if (batchProgress == null) {
				null
			} else {
				val parts = mutableListOf<String>()
				batchProgress.overallPercent?.let { pct ->
					if (totalInRun > 1) {
						parts += resources.getString(R.string.queue_overall_percent, pct)
					}
				}
				if (batchProgress.totalKnownBytes > 0L && totalInRun > 1) {
					parts += "${formatSizeMegabytes(batchProgress.transferredBytes)} / ${formatSizeMegabytes(batchProgress.totalKnownBytes)}"
				}
				val progressUnits = BatchProgressUnits(
					secondsShort = resources.getString(R.string.unit_seconds_short),
					minutesShort = resources.getString(R.string.unit_minutes_short),
					hoursShort = resources.getString(R.string.unit_hours_short),
					bytesPerSec = resources.getString(R.string.unit_bytes_per_sec),
					kilobytesPerSec = resources.getString(R.string.unit_kb_per_sec),
					megabytesPerSec = resources.getString(R.string.unit_mb_per_sec)
				)
				if (batchProgress.speedBytesPerSec > 0L) {
					parts += BatchProgressCalculator.formatSpeed(batchProgress.speedBytesPerSec, progressUnits)
				}
				batchProgress.etaSeconds?.let { eta ->
					parts += BatchProgressCalculator.formatEta(eta, progressUnits)
				}
				parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
			}
		}
		Surface(
			onClick = onOpenQueue,
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 12.dp, vertical = 4.dp),
			shape = shape,
			color = MaterialTheme.colorScheme.surfaceContainerLow,
			border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
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
						ApkUpExpressiveLoadingIndicator(
							modifier = Modifier.size(40.dp),
							containerColor = MaterialTheme.colorScheme.primaryContainer,
							indicatorColor = MaterialTheme.colorScheme.onPrimaryContainer
						)
						Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
							val allSaveOnly = activeTasks.isNotEmpty() && activeTasks.all { it.isSaveOnly }
							Text(
								text = stringResource(
									if (allSaveOnly) R.string.queue_banner_title_saving else R.string.queue_banner_title,
									currentStep,
									totalInRun
								),
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
							if (batchMetaLine != null) {
								Text(
									text = batchMetaLine,
									style = MaterialTheme.typography.labelSmall,
									fontWeight = FontWeight.Medium,
									color = MaterialTheme.colorScheme.primary,
									maxLines = 1,
									overflow = TextOverflow.Ellipsis
								)
							}
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
				val bannerFraction = when {
					totalInRun > 1 && batchProgress?.overallPercent != null ->
						(batchProgress.overallPercent / 100f).coerceIn(0f, 1f)
					!currentTask.isInstalling && currentTask.total > 0L && currentTask.progress > 0L ->
						(currentTask.progress.toFloat() / currentTask.total).coerceIn(0f, 1f)
					else -> null
				}
				if (bannerFraction != null) {
					ApkUpWavyLinearProgressIndicator(
						progress = { bannerFraction },
						modifier = Modifier.fillMaxWidth(),
						color = MaterialTheme.colorScheme.primary,
						trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
					)
				} else {
					ApkUpWavyLinearProgressIndicator(
						progress = null,
						modifier = Modifier.fillMaxWidth(),
						color = MaterialTheme.colorScheme.primary,
						trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
					)
				}
			}
		}
		return
	}

	val bannerHaptics = LocalHapticFeedback.current
	var hadActiveTasks by remember { mutableStateOf(false) }
	var showBatchCompletePop by remember { mutableStateOf(false) }
	LaunchedEffect(activeTasks.isNotEmpty()) {
		if (activeTasks.isNotEmpty()) {
			hadActiveTasks = true
			showBatchCompletePop = false
		} else if (hadActiveTasks) {
			hadActiveTasks = false
			showBatchCompletePop = true
			bannerHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
			kotlinx.coroutines.delay(1500L)
			showBatchCompletePop = false
		}
	}

	Surface(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 12.dp, vertical = 4.dp)
			.animateContentSize(animationSpec = ApkUpTheme.motion.defaultSpatialSpec()),
		shape = shape,
		color = MaterialTheme.colorScheme.surfaceContainerLow,
		border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)),
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
					color = MaterialTheme.colorScheme.primaryContainer,
					modifier = Modifier.size(42.dp)
				) {
					Box(contentAlignment = Alignment.Center) {
						Icon(
							painter = painterResource(R.drawable.ic_install),
							contentDescription = null,
							tint = MaterialTheme.colorScheme.onPrimaryContainer,
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
				Spacer(Modifier.width(8.dp))
				androidx.compose.animation.AnimatedContent(
					targetState = showBatchCompletePop,
					label = "UpdateAllMorph"
				) { completed ->
					if (completed) {
						Surface(
							shape = CircleShape,
							color = MaterialTheme.colorScheme.primaryContainer,
							border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
						) {
							Row(
								modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(6.dp)
							) {
								Icon(
									imageVector = Icons.Rounded.CheckCircle,
									contentDescription = null,
									tint = MaterialTheme.colorScheme.primary,
									modifier = Modifier.size(18.dp)
								)
							}
						}
					} else {
						ApkUpSplitActionButton(
							label = stringResource(R.string.install_all),
							iconRes = R.drawable.ic_install,
							onClick = onInstallAll,
							menuContent = { dismiss ->
								if (onOpenHistory != null) {
									DropdownMenuItem(
										text = { Text(stringResource(R.string.update_history_title)) },
										leadingIcon = {
											Icon(
												imageVector = Icons.Rounded.History,
												contentDescription = null
											)
										},
										onClick = {
											dismiss()
											onOpenHistory()
										}
									)
								}
								DropdownMenuItem(
									text = { Text(stringResource(R.string.updates_select_all)) },
									leadingIcon = {
										Icon(
											imageVector = Icons.Filled.Check,
									contentDescription = null
								)
							},
							onClick = {
								dismiss()
								onSelectAll()
							}
						)
					}
				)
					}
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
	batchProgress: BatchProgressSummary? = null,
	onCancelTask: (Int) -> Unit,
	onCancelAll: () -> Unit,
	onDismiss: () -> Unit
) {
	val context = LocalContext.current
	val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
	val currentActiveTasks = activeTasks
	val currentCompletedTasks = completedTasks

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

			batchProgress?.let { summary ->
				Surface(
					modifier = Modifier.fillMaxWidth(),
					shape = RoundedCornerShape(16.dp),
					color = MaterialTheme.colorScheme.surfaceContainerHigh,
					border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
				) {
					Column(
						modifier = Modifier
							.fillMaxWidth()
							.padding(horizontal = 14.dp, vertical = 10.dp),
						verticalArrangement = Arrangement.spacedBy(6.dp)
					) {
						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.SpaceBetween,
							verticalAlignment = Alignment.CenterVertically
						) {
							val allSaveOnly = currentActiveTasks.isNotEmpty() && currentActiveTasks.all { it.isSaveOnly }
							Text(
								text = stringResource(
									if (allSaveOnly) R.string.queue_banner_title_saving else R.string.queue_banner_title,
									summary.currentStep,
									summary.totalCount
								),
								style = MaterialTheme.typography.labelLarge,
								fontWeight = FontWeight.Bold,
								color = MaterialTheme.colorScheme.onSurface
							)
							summary.overallPercent?.let { pct ->
								Text(
									text = "$pct%",
									style = MaterialTheme.typography.labelLarge,
									fontWeight = FontWeight.Bold,
									color = MaterialTheme.colorScheme.primary
								)
							}
						}
						val progressUnits = BatchProgressUnits(
							secondsShort = stringResource(R.string.unit_seconds_short),
							minutesShort = stringResource(R.string.unit_minutes_short),
							hoursShort = stringResource(R.string.unit_hours_short),
							bytesPerSec = stringResource(R.string.unit_bytes_per_sec),
							kilobytesPerSec = stringResource(R.string.unit_kb_per_sec),
							megabytesPerSec = stringResource(R.string.unit_mb_per_sec)
						)
						val metaParts = buildList {
							if (summary.totalKnownBytes > 0L) {
								add("${formatSizeMegabytes(summary.transferredBytes)} / ${formatSizeMegabytes(summary.totalKnownBytes)}")
							}
							if (summary.speedBytesPerSec > 0L) {
								add(BatchProgressCalculator.formatSpeed(summary.speedBytesPerSec, progressUnits))
							}
							summary.etaSeconds?.let { eta ->
								add(BatchProgressCalculator.formatEta(eta, progressUnits))
							}
						}
						if (metaParts.isNotEmpty()) {
							Text(
								text = metaParts.joinToString(" · "),
								style = MaterialTheme.typography.labelSmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis
							)
						}
						if (summary.overallPercent != null) {
							LinearProgressIndicator(
								progress = { (summary.overallPercent / 100f).coerceIn(0f, 1f) },
								modifier = Modifier
									.fillMaxWidth()
									.height(5.dp)
									.clip(RoundedCornerShape(3.dp)),
								color = MaterialTheme.colorScheme.primary,
								trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
							)
						} else {
							LinearProgressIndicator(
								modifier = Modifier
									.fillMaxWidth()
									.height(5.dp)
									.clip(RoundedCornerShape(3.dp)),
								color = MaterialTheme.colorScheme.primary,
								trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
							)
						}
						DownloadSpeedSparkline(speedBytesPerSec = summary.speedBytesPerSec)
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
						task.isInstalling && task.isSaveOnly -> stringResource(R.string.queue_status_saving)
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
		color = MaterialTheme.colorScheme.surfaceContainerLow,
		shape = MaterialTheme.shapes.large,
		border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
		modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
	) {
		Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
			if (checkedApps != null && totalApps != null && totalApps > 0) {
				val completed = checkedApps.coerceIn(0, totalApps - 1)
				Row(
					modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
					horizontalArrangement = Arrangement.spacedBy(10.dp),
					verticalAlignment = Alignment.CenterVertically
				) {
					ApkUpExpressiveLoadingIndicator(modifier = Modifier.size(28.dp))
					Text(
						text = stringResource(R.string.updates_checking_apps, completed, totalApps),
						style = MaterialTheme.typography.labelMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
						modifier = Modifier.weight(1f)
					)
					if (!resolvedActiveSource.isNullOrBlank()) {
						Text(
							text = stringResource(R.string.updates_checking_source, resolvedActiveSource),
							style = MaterialTheme.typography.labelSmall,
							fontWeight = FontWeight.SemiBold,
							color = MaterialTheme.colorScheme.primary,
							maxLines = 1
						)
					}
				}
				ApkUpWavyLinearProgressIndicator(
					progress = if (completed > 0) ({ completed.toFloat() / totalApps }) else null,
					modifier = Modifier.fillMaxWidth(),
					color = MaterialTheme.colorScheme.primary,
					trackColor = MaterialTheme.colorScheme.secondaryContainer
				)
			} else {
				Row(
					modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
					horizontalArrangement = Arrangement.spacedBy(10.dp),
					verticalAlignment = Alignment.CenterVertically
				) {
					ApkUpExpressiveLoadingIndicator(modifier = Modifier.size(28.dp))
					Text(
						text = stringResource(R.string.updates_checking),
						style = MaterialTheme.typography.labelMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
						modifier = Modifier.weight(1f)
					)
					if (!resolvedActiveSource.isNullOrBlank()) {
						Text(
							text = stringResource(R.string.updates_checking_source, resolvedActiveSource),
							style = MaterialTheme.typography.labelSmall,
							fontWeight = FontWeight.SemiBold,
							color = MaterialTheme.colorScheme.primary,
							maxLines = 1
						)
					}
				}
				ApkUpWavyLinearProgressIndicator(
					progress = null,
					modifier = Modifier.fillMaxWidth(),
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
private fun DownloadSpeedSparkline(
	speedBytesPerSec: Long,
	modifier: Modifier = Modifier
) {
	val samples = remember { androidx.compose.runtime.mutableStateListOf<Float>() }
	LaunchedEffect(speedBytesPerSec) {
		if (speedBytesPerSec > 0L) {
			samples.add(speedBytesPerSec.toFloat())
			if (samples.size > 24) {
				samples.removeAt(0)
			}
		}
	}
	if (samples.size < 2) return
	val lineColor = MaterialTheme.colorScheme.primary
	val fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
	Canvas(
		modifier = modifier
			.fillMaxWidth()
			.height(20.dp)
	) {
		val maxVal = (samples.maxOrNull() ?: 1f).coerceAtLeast(1f)
		val stepX = if (samples.size > 1) size.width / (samples.size - 1).toFloat() else size.width
		val linePath = Path()
		val fillPath = Path()
		samples.forEachIndexed { idx, value ->
			val x = idx * stepX
			val norm = (value / maxVal).coerceIn(0f, 1f)
			val y = size.height - (norm * (size.height - 4.dp.toPx()) + 2.dp.toPx())
			if (idx == 0) {
				linePath.moveTo(x, y)
				fillPath.moveTo(x, size.height)
				fillPath.lineTo(x, y)
			} else {
				linePath.lineTo(x, y)
				fillPath.lineTo(x, y)
			}
		}
		fillPath.lineTo(size.width, size.height)
		fillPath.close()
		drawPath(path = fillPath, color = fillColor)
		drawPath(
			path = linePath,
			color = lineColor,
			style = Stroke(width = 1.75.dp.toPx(), cap = StrokeCap.Round)
		)
	}
}

@Composable
private fun StaggeredCascadeItem(
	index: Int,
	modifier: Modifier = Modifier,
	content: @Composable () -> Unit
) {
	val isReducedMotion = ApkUpTheme.isReducedMotion
	val shouldAnimate = !isReducedMotion && index < 12
	val progress = remember { Animatable(if (shouldAnimate) 0f else 1f) }
	LaunchedEffect(isReducedMotion) {
		if (isReducedMotion) {
			progress.snapTo(1f)
		} else if (progress.value < 1f) {
			kotlinx.coroutines.delay(index * 25L)
			progress.animateTo(
				targetValue = 1f,
				animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
			)
		}
	}
	Box(
		modifier = modifier.graphicsLayer {
			val p = progress.value
			alpha = p
			translationY = (1f - p) * 16.dp.toPx()
		}
	) {
		content()
	}
}

@Composable
private fun LiveInstallStepperPill(
	liveTask: DownloadTaskState?,
	isCachedReady: Boolean,
	isInstalling: Boolean,
	isQueued: Boolean,
	modifier: Modifier = Modifier
) {
	val activeStage = when {
		liveTask?.isInstalling == true -> 3
		isCachedReady || (liveTask != null && liveTask.total > 0L && liveTask.progress >= liveTask.total) -> 2
		liveTask != null && !liveTask.isQueued && !liveTask.isResolvingLink -> 1
		isInstalling && liveTask == null -> 3
		isQueued -> 0
		else -> 0
	}
	val steps = listOf(
		stringResource(R.string.updates_step_check),
		stringResource(R.string.updates_step_download),
		"${stringResource(R.string.updates_step_verify)} ✓",
		stringResource(R.string.updates_step_install)
	)
	Surface(
		shape = CircleShape,
		color = MaterialTheme.colorScheme.surfaceContainerHigh,
		modifier = modifier.fillMaxWidth()
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 8.dp, vertical = 4.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.SpaceBetween
		) {
			steps.forEachIndexed { idx, label ->
				val isCompleted = idx < activeStage
				val isCurrent = idx == activeStage
				val textColor = when {
					isCompleted -> ApkUpTheme.colors.success
					isCurrent -> MaterialTheme.colorScheme.primary
					else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
				}
				Text(
					text = label,
					style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
					fontWeight = if (isCurrent || isCompleted) FontWeight.Bold else FontWeight.Normal,
					color = textColor,
					maxLines = 1,
					softWrap = false,
					overflow = TextOverflow.Ellipsis,
					modifier = Modifier.weight(1f, fill = false)
				)
				if (idx < steps.lastIndex) {
					Text(
						text = "→",
						style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
						color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
					)
				}
			}
		}
	}
}

@Composable
private fun TrustShieldBadge(
	isVerified: Boolean,
	modifier: Modifier = Modifier
) {
	val isReducedMotion = ApkUpTheme.isReducedMotion
	val flipAngle = remember { Animatable(if (isVerified) 180f else 0f) }
	LaunchedEffect(isVerified, isReducedMotion) {
		val target = if (isVerified) 180f else 0f
		if (isReducedMotion) {
			flipAngle.snapTo(target)
		} else {
			flipAngle.animateTo(
				targetValue = target,
				animationSpec = spring(dampingRatio = 0.65f, stiffness = 360f)
			)
		}
	}
	val showVerified = flipAngle.value >= 90f
	val tint by animateColorAsState(
		targetValue = if (showVerified) ApkUpTheme.colors.success else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
		animationSpec = tween(if (isReducedMotion) 60 else 200),
		label = "TrustShieldTint"
	)
	Icon(
		imageVector = if (showVerified) Icons.Rounded.VerifiedUser else Icons.Outlined.Shield,
		contentDescription = null,
		tint = tint,
		modifier = modifier
			.size(14.dp)
			.graphicsLayer {
				rotationY = if (showVerified) flipAngle.value - 180f else flipAngle.value
			}
	)
}

@Composable
private fun SourceIconWithCooldown(
	source: Source,
	modifier: Modifier = Modifier
) {
	val isApkMirror = source == ApkMirrorSource
	var remainingSeconds by remember(isApkMirror) { mutableIntStateOf(0) }
	if (isApkMirror) {
		LaunchedEffect(Unit) {
			while (true) {
				val entry = SourceHealthTracker.snapshot().firstOrNull {
					it.sourceName.equals("ApkMirror", ignoreCase = true)
				}
				val rem = entry?.remainingCooldownSeconds()?.toInt() ?: 0
				remainingSeconds = rem
				kotlinx.coroutines.delay(if (rem > 0) 1000L else 2500L)
			}
		}
	}
	val ringColor = MaterialTheme.colorScheme.primary
	Box(
		modifier = modifier.size(if (remainingSeconds > 0) 15.dp else 11.dp),
		contentAlignment = Alignment.Center
	) {
		if (remainingSeconds > 0) {
			val fraction = (remainingSeconds / 600f).coerceIn(0.05f, 1f)
			Canvas(modifier = Modifier.fillMaxSize()) {
				drawArc(
					color = ringColor.copy(alpha = 0.2f),
					startAngle = 0f,
					sweepAngle = 360f,
					useCenter = false,
					style = Stroke(width = 1.4.dp.toPx())
				)
				drawArc(
					color = ringColor,
					startAngle = -90f,
					sweepAngle = 360f * fraction,
					useCenter = false,
					style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
				)
			}
		}
		Icon(
			painter = painterResource(source.resourceId),
			contentDescription = null,
			tint = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.size(11.dp)
		)
	}
}

@Composable
private fun UpdatesList(
	viewModel: UpdatesViewModel,
	appsViewModel: AppsViewModel = koinViewModel(),
	updates: List<AppUpdate>,
	scrollResetKey: Any = Unit,
	activeTasksMap: Map<Int, DownloadTaskState> = emptyMap(),
	readyUpdateIds: Set<Int> = emptySet(),
	failedUpdateIds: Set<Int> = emptySet(),
	jumpToErrorTick: Int = 0,
	extraBottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
	handler: UriHandler,
	modifier: Modifier,
	onRequestInstall: (AppUpdate) -> Unit,
	onRequestSaveApk: (AppUpdate) -> Unit,
	isSelectionMode: Boolean = false,
	selectedUpdateIds: Set<Int> = emptySet(),
	onToggleSelection: (Int) -> Unit = {}
) {
	var selectedPackageName by remember { mutableStateOf<String?>(null) }
	val selectedUpdate = updates.find { it.packageName == selectedPackageName }
	val haptic = LocalHapticFeedback.current
	val gridState = rememberLazyGridState()
	var lastScrollResetKey by remember { mutableStateOf(scrollResetKey) }
	LaunchedEffect(scrollResetKey) {
		if (lastScrollResetKey != scrollResetKey) {
			lastScrollResetKey = scrollResetKey
			gridState.scrollToItem(0)
		}
	}
	LaunchedEffect(jumpToErrorTick) {
		if (jumpToErrorTick > 0) {
			val idx = updates.indexOfFirst { it.id in failedUpdateIds }
			if (idx >= 0) {
				gridState.animateScrollToItem(idx)
			}
		}
	}

	BoxWithConstraints(modifier) {
		val columns = updateGridColumnCount(maxWidth.value, compact = false)
		LazyVerticalGrid(
			columns = GridCells.Fixed(columns),
			state = gridState,
			modifier = Modifier.fillMaxSize(),
			contentPadding = PaddingValues(
				start = NormalUpdateCardLayout.gridHorizontalPaddingDp.dp,
				top = NormalUpdateCardLayout.gridTopPaddingDp.dp,
				end = NormalUpdateCardLayout.gridHorizontalPaddingDp.dp,
				bottom = NormalUpdateCardLayout.gridBottomPaddingDp.dp + extraBottomPadding
			),
			verticalArrangement = Arrangement.spacedBy(NormalUpdateCardLayout.gridSpacingDp.dp),
			horizontalArrangement = Arrangement.spacedBy(NormalUpdateCardLayout.gridSpacingDp.dp)
		) {
			gridItemsIndexed(items = updates, key = { _, u -> u.id }) { index, update ->
				val liveTask = activeTasksMap[update.id]
					?: activeTasksMap.values.firstOrNull { it.packageName == update.packageName }
				val isCachedReady = update.id in readyUpdateIds || update.alternativeUpdates.any { it.id in readyUpdateIds }
				val isFailed = update.id in failedUpdateIds
				val effInstalling = liveTask?.let { it.isInstalling || !it.isQueued } ?: update.isInstalling
				val effQueued = liveTask?.isQueued ?: update.isQueued
				val isSelectable = !effInstalling && !effQueued
				val isSelected = update.id in selectedUpdateIds
				StaggeredCascadeItem(
					index = index,
					modifier = Modifier.animateItem()
				) {
					UpdateCard(
						update = update,
						liveTask = liveTask,
						isCachedReady = isCachedReady,
						isFailed = isFailed,
						isSelectionMode = isSelectionMode,
						isSelected = isSelected,
						isSelectable = isSelectable,
						onSelect = {
							if (isSelectionMode) {
								if (isSelectable) onToggleSelection(update.id)
							} else {
								selectedPackageName = update.packageName
							}
						},
						onLongPress = {
							if (isSelectable) {
								haptic.performHapticFeedback(HapticFeedbackType.LongPress)
								onToggleSelection(update.id)
							}
						},
						onInstall = {
							onRequestInstall(update)
						}
					)
				}
			}
		}
	}

	selectedUpdate?.let { update ->
		val pinnedAppSources by viewModel.pinnedAppSourcesFlow.collectAsStateWithLifecycle()
		UpdateDetailsSheet(
			update = update,
			activeTasksMap = activeTasksMap,
			isCachedReady = update.id in readyUpdateIds || update.alternativeUpdates.any { it.id in readyUpdateIds },
			readyUpdateIds = readyUpdateIds,
			pinnedSourceKey = pinnedAppSources[update.packageName],
			onTogglePinSource = { key -> viewModel.pinAppSource(update.packageName, key) },
			onDismiss = { selectedPackageName = null },
			onInstallVariant = { chosen ->
				onRequestInstall(chosen)
				if (chosen.isBrowserOnlyUpdate) {
					selectedPackageName = null
				}
			},
			onSaveApkVariant = { chosen ->
				onRequestSaveApk(chosen)
			},
			onIgnoreVersion = { versionId ->
				if (update.isWatchedApp()) {
					viewModel.markWatchedAppSeen(update)
				} else {
					viewModel.ignoreVersion(versionId)
				}
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun UpdateCard(
	update: AppUpdate,
	liveTask: DownloadTaskState? = null,
	isCachedReady: Boolean = false,
	isFailed: Boolean = false,
	isSelectionMode: Boolean = false,
	isSelected: Boolean = false,
	isSelectable: Boolean = true,
	modifier: Modifier = Modifier,
	onSelect: () -> Unit,
	onLongPress: () -> Unit = {},
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

	val cardShape = MaterialTheme.shapes.large
	Surface(
		modifier = modifier
			.fillMaxWidth()
			.clip(cardShape)
			.combinedClickable(
				onClick = onSelect,
				onLongClick = onLongPress
			)
			.animateContentSize(animationSpec = ApkUpTheme.motion.defaultSpatialSpec()),
		shape = cardShape,
		color = when {
			isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
			isFailed -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f)
			else -> MaterialTheme.colorScheme.surfaceContainerLow
		},
		border = BorderStroke(
			width = if (isSelected || isFailed) 1.5.dp else 1.dp,
			color = when {
				isSelected -> MaterialTheme.colorScheme.primary
				isFailed -> MaterialTheme.colorScheme.error
				else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
			}
		)
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
				Box(modifier = Modifier.size(52.dp)) {
					Box(
						modifier = Modifier
							.fillMaxSize()
							.clip(RoundedCornerShape(14.dp))
							.background(MaterialTheme.colorScheme.surfaceContainer),
						contentAlignment = Alignment.Center
					) {
						LoadingImageApp(
							card.packageName,
							Modifier.fillMaxSize()
						)
					}
					if (isSelected) {
						Surface(
							shape = CircleShape,
							color = MaterialTheme.colorScheme.primary,
							border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.surface),
							modifier = Modifier
								.align(Alignment.BottomEnd)
								.size(20.dp)
						) {
							Box(contentAlignment = Alignment.Center) {
								Icon(
									imageVector = Icons.Filled.Check,
									contentDescription = null,
									tint = MaterialTheme.colorScheme.onPrimary,
									modifier = Modifier.size(12.dp)
								)
							}
						}
					} else if (isSelectionMode && isSelectable) {
						Box(
							modifier = Modifier
								.align(Alignment.BottomEnd)
								.size(20.dp)
								.clip(CircleShape)
								.background(MaterialTheme.colorScheme.surfaceContainerHigh)
								.border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
						)
					}
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
					// Row 1: Versions (or "Beobachtet · Neu" for uninstalled watched apps)
					if (update.isWatchedApp()) {
						Row(
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(6.dp)
						) {
							Surface(
								shape = RoundedCornerShape(6.dp),
								color = MaterialTheme.colorScheme.secondaryContainer
							) {
								Text(
									text = stringResource(R.string.updates_watched_new_badge),
									style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
									color = MaterialTheme.colorScheme.onSecondaryContainer,
									modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
									maxLines = 1
								)
							}
							Text(
								text = card.availableVersion.ifBlank { "—" },
								style = MaterialTheme.typography.labelSmall,
								fontWeight = FontWeight.Bold,
								color = MaterialTheme.colorScheme.primary,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis
							)
						}
					} else {
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
								SourceIconWithCooldown(source = card.source)
								Text(
									text = sourceName,
									style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
									color = MaterialTheme.colorScheme.onSurfaceVariant
								)
								val isVerifiedReady = isCachedReady || (liveTask != null && liveTask.total > 0L && liveTask.progress >= liveTask.total)
								TrustShieldBadge(isVerified = isVerifiedReady)
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

			// Progress bar if downloading/installing/saving
			if (effInstalling || effQueued) {
				val isSaveOnlyTask = liveTask?.isSaveOnly == true
				val cardProgressLabel = when {
					liveTask?.isInstalling == true && isSaveOnlyTask -> stringResource(R.string.queue_status_saving)
					liveTask?.isInstalling == true -> stringResource(R.string.queue_status_installing)
					liveTask?.isResolvingLink == true && effProgress == 0L -> stringResource(R.string.queue_status_resolving_apkmirror)
					effQueued && !effInstalling -> stringResource(R.string.queued_update)
					effTotal > 0L && effProgress > 0L -> {
						val pct = ((effProgress * 100) / effTotal).toInt().coerceIn(0, 100)
						"${stringResource(R.string.downloading_update_percent, pct)} (${formatSizeMegabytes(effProgress)} / ${formatSizeMegabytes(effTotal)})"
					}
					effProgress > 0L -> "${stringResource(R.string.queue_status_downloading)} (${formatSizeMegabytes(effProgress)})"
					isSaveOnlyTask -> stringResource(R.string.queue_status_saving)
					else -> stringResource(R.string.installing_update)
				}
				Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
					LiveInstallStepperPill(
						liveTask = liveTask,
						isCachedReady = isCachedReady,
						isInstalling = effInstalling,
						isQueued = effQueued
					)
					Text(
						text = cardProgressLabel,
						style = MaterialTheme.typography.labelSmall,
						color = MaterialTheme.colorScheme.primary,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)
					ApkUpWavyLinearProgressIndicator(
						progress = if (effTotal > 0L && effProgress > 0L) {
							{ (effProgress.toFloat() / effTotal).coerceIn(0f, 1f) }
						} else {
							null
						},
						modifier = Modifier.fillMaxWidth(),
						color = MaterialTheme.colorScheme.primary,
						trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
					)
				}
			}

			// Bottom Actions: Details link & Install Button (hidden in selection mode)
			if (!isSelectionMode) {
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
								text = stringResource(
									when {
										isCachedReady -> R.string.install_cached_action
										update.isWatchedApp() -> R.string.install_action
										else -> R.string.update_action
									}
								),
								style = MaterialTheme.typography.labelMedium,
								fontWeight = FontWeight.Bold
							)
						}
					}
				}
			}
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun CompactUpdatesGrid(
	viewModel: UpdatesViewModel,
	appsViewModel: AppsViewModel = koinViewModel(),
	updates: List<AppUpdate>,
	scrollResetKey: Any = Unit,
	activeTasksMap: Map<Int, DownloadTaskState> = emptyMap(),
	readyUpdateIds: Set<Int> = emptySet(),
	failedUpdateIds: Set<Int> = emptySet(),
	jumpToErrorTick: Int = 0,
	extraBottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
	handler: UriHandler,
	modifier: Modifier,
	onRequestInstall: (AppUpdate) -> Unit,
	onRequestSaveApk: (AppUpdate) -> Unit,
	isSelectionMode: Boolean = false,
	selectedUpdateIds: Set<Int> = emptySet(),
	onToggleSelection: (Int) -> Unit = {}
) {
	var selectedPackageName by remember { mutableStateOf<String?>(null) }
	val selectedUpdate = updates.find { it.packageName == selectedPackageName }
	val haptic = LocalHapticFeedback.current
	val gridState = rememberLazyGridState()
	var lastScrollResetKey by remember { mutableStateOf(scrollResetKey) }
	LaunchedEffect(scrollResetKey) {
		if (lastScrollResetKey != scrollResetKey) {
			lastScrollResetKey = scrollResetKey
			gridState.scrollToItem(0)
		}
	}
	LaunchedEffect(jumpToErrorTick) {
		if (jumpToErrorTick > 0) {
			val idx = updates.indexOfFirst { it.id in failedUpdateIds }
			if (idx >= 0) {
				gridState.animateScrollToItem(idx)
			}
		}
	}

	BoxWithConstraints(modifier) {
		val columns = updateGridColumnCount(maxWidth.value, compact = true)
		LazyVerticalGrid(
			columns = GridCells.Fixed(columns),
			state = gridState,
			modifier = Modifier.fillMaxSize(),
			contentPadding = PaddingValues(
				start = CompactUpdateCardLayout.gridHorizontalPaddingDp.dp,
				top = CompactUpdateCardLayout.gridTopPaddingDp.dp,
				end = CompactUpdateCardLayout.gridHorizontalPaddingDp.dp,
				bottom = CompactUpdateCardLayout.gridBottomPaddingDp.dp + extraBottomPadding
			),
			verticalArrangement = Arrangement.spacedBy(CompactUpdateCardLayout.gridSpacingDp.dp),
			horizontalArrangement = Arrangement.spacedBy(CompactUpdateCardLayout.gridSpacingDp.dp)
		) {
			gridItemsIndexed(items = updates, key = { _, u -> u.id }) { index, update ->
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
				val isSelectable = !effInstalling && !effQueued
				val isSelected = update.id in selectedUpdateIds
				val isFailed = update.id in failedUpdateIds
				val isCachedReady = update.id in readyUpdateIds || update.alternativeUpdates.any { it.id in readyUpdateIds }
				val cardShape = RoundedCornerShape(14.dp)
				StaggeredCascadeItem(
					index = index,
					modifier = Modifier.animateItem()
				) {
				Surface(
					modifier = Modifier
						.fillMaxWidth()
						.clip(cardShape)
						.combinedClickable(
							onClick = {
								if (isSelectionMode) {
									if (isSelectable) onToggleSelection(update.id)
								} else {
									selectedPackageName = update.packageName
								}
							},
							onLongClick = {
								if (isSelectable) {
									haptic.performHapticFeedback(HapticFeedbackType.LongPress)
									onToggleSelection(update.id)
								}
							}
						)
						.animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
					shape = cardShape,
					color = when {
						isSelected -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
						isFailed -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f)
						else -> MaterialTheme.colorScheme.surfaceContainerLow
					},
					border = when {
						isSelected -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
						isFailed -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.error)
						else -> null
					}
				) {
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.padding(horizontal = 14.dp, vertical = 10.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(12.dp)
					) {
						// 42dp app icon
						Box(modifier = Modifier.size(42.dp)) {
							Box(
								modifier = Modifier
									.fillMaxSize()
									.clip(RoundedCornerShape(12.dp))
									.background(MaterialTheme.colorScheme.surfaceContainer),
								contentAlignment = Alignment.Center
							) {
								LoadingImageApp(
									card.packageName,
									Modifier.fillMaxSize()
								)
							}
							if (isSelected) {
								Surface(
									shape = CircleShape,
									color = MaterialTheme.colorScheme.primary,
									border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.surface),
									modifier = Modifier
										.align(Alignment.BottomEnd)
										.size(18.dp)
								) {
									Box(contentAlignment = Alignment.Center) {
										Icon(
											imageVector = Icons.Filled.Check,
											contentDescription = null,
											tint = MaterialTheme.colorScheme.onPrimary,
											modifier = Modifier.size(11.dp)
										)
									}
								}
							} else if (isSelectionMode && isSelectable) {
								Box(
									modifier = Modifier
										.align(Alignment.BottomEnd)
										.size(18.dp)
										.clip(CircleShape)
										.background(MaterialTheme.colorScheme.surfaceContainerHigh)
										.border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
								)
							}
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
								if (update.isWatchedApp()) {
									Surface(
										shape = RoundedCornerShape(5.dp),
										color = MaterialTheme.colorScheme.secondaryContainer
									) {
										Text(
											text = stringResource(R.string.updates_watched_new_badge),
											style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold),
											color = MaterialTheme.colorScheme.onSecondaryContainer,
											modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
											maxLines = 1
										)
									}
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
								} else {
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
								}
								val resolvedSourceLabel = sourceName(card.source)
								Row(
									verticalAlignment = Alignment.CenterVertically,
									horizontalArrangement = Arrangement.spacedBy(4.dp)
								) {
									SourceIconWithCooldown(source = card.source)
									Text(
										text = resolvedSourceLabel,
										style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
										color = MaterialTheme.colorScheme.onSurfaceVariant,
										maxLines = 1
									)
									val isVerifiedReady = isCachedReady || (liveTask != null && liveTask.total > 0L && liveTask.progress >= liveTask.total)
									TrustShieldBadge(isVerified = isVerifiedReady)
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
								val isSaveOnlyTask = liveTask?.isSaveOnly == true
								val compactLabel = when {
									liveTask?.isInstalling == true && isSaveOnlyTask -> stringResource(R.string.queue_status_saving)
									liveTask?.isInstalling == true -> stringResource(R.string.queue_status_installing)
									liveTask?.isResolvingLink == true && effProgress == 0L -> stringResource(R.string.queue_status_resolving_apkmirror)
									effQueued && !effInstalling -> stringResource(R.string.queued_update)
									effTotal > 0L && effProgress > 0L -> {
										val pct = ((effProgress * 100) / effTotal).toInt().coerceIn(0, 100)
										"$pct%"
									}
									isSaveOnlyTask -> stringResource(R.string.queue_status_saving)
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

						// Right Action: Fast download button (hidden in selection mode)
						if (!isSelectionMode && card.canInstall && !effQueued && !effInstalling) {
							IconButton(
								onClick = {
									onRequestInstall(update)
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
	}

	selectedUpdate?.let { update ->
		val pinnedAppSources by viewModel.pinnedAppSourcesFlow.collectAsStateWithLifecycle()
		UpdateDetailsSheet(
			update = update,
			activeTasksMap = activeTasksMap,
			isCachedReady = update.id in readyUpdateIds || update.alternativeUpdates.any { it.id in readyUpdateIds },
			readyUpdateIds = readyUpdateIds,
			pinnedSourceKey = pinnedAppSources[update.packageName],
			onTogglePinSource = { key -> viewModel.pinAppSource(update.packageName, key) },
			onDismiss = { selectedPackageName = null },
			onInstallVariant = { chosen ->
				onRequestInstall(chosen)
				if (chosen.isBrowserOnlyUpdate) {
					selectedPackageName = null
				}
			},
			onSaveApkVariant = { chosen ->
				onRequestSaveApk(chosen)
			},
			onIgnoreVersion = { versionId ->
				if (update.isWatchedApp()) {
					viewModel.markWatchedAppSeen(update)
				} else {
					viewModel.ignoreVersion(versionId)
				}
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
	activeTasksMap: Map<Int, DownloadTaskState> = emptyMap(),
	isCachedReady: Boolean = false,
	readyUpdateIds: Set<Int> = emptySet(),
	pinnedSourceKey: String? = null,
	onTogglePinSource: (String?) -> Unit = {},
	onDismiss: () -> Unit,
	onInstallVariant: (AppUpdate) -> Unit,
	onSaveApkVariant: (AppUpdate) -> Unit = {},
	onIgnoreVersion: (Int) -> Unit,
	onIgnoreAppPermanently: (String) -> Unit
) {
	val context = LocalContext.current
	val handler = com.apkup.ui.component.rememberCustomTabUriHandler()
	val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
	val liveTask = activeTasksMap[update.id]
		?: activeTasksMap.values.firstOrNull { it.packageName == update.packageName }

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

	val isWideScreen = LocalConfiguration.current.screenWidthDp >= 840
	val sheetBody: @Composable () -> Unit = {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.verticalScroll(rememberScrollState())
				.padding(start = 24.dp, end = 24.dp, top = if (isWideScreen) 24.dp else 0.dp, bottom = 28.dp),
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
							text = if (activeVariant.isWatchedApp()) {
								"${stringResource(R.string.updates_watched_new_badge)} · ${card.availableVersion.ifBlank { "—" }}"
							} else {
								"${card.installedVersion.ifBlank { "—" }} → ${card.availableVersion.ifBlank { "—" }}"
							},
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

			val activeSourceKey = SourcePriorityKeys.fromSource(activeVariant.source)
			val isPinnedToActive = pinnedSourceKey == activeSourceKey
			FilterChip(
				selected = isPinnedToActive,
				onClick = {
					onTogglePinSource(if (isPinnedToActive) null else activeSourceKey)
				},
				label = {
					Text(
						text = if (isPinnedToActive) {
							stringResource(R.string.pin_source_menu_pinned, sourcePriorityLabel(activeSourceKey))
						} else {
							stringResource(R.string.pin_source_menu_action)
						}
					)
				},
				leadingIcon = {
					Icon(
						imageVector = Icons.Rounded.PushPin,
						contentDescription = null,
						modifier = Modifier.size(16.dp)
					)
				}
			)

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
				val openCustomTab = com.apkup.ui.component.rememberCustomTabOpener()
				Surface(
					onClick = { openCustomTab(resolveWikiUrlForSourceError("signature")) },
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

			val isSaveOnlyTask = liveTask?.isSaveOnly == true
			if (isUpdateBusy) {
				val detailStatusText = when {
					liveTask?.isInstalling == true && isSaveOnlyTask -> stringResource(R.string.queue_status_saving)
					liveTask?.isInstalling == true -> stringResource(R.string.queue_status_installing)
					liveTask?.isResolvingLink == true && effProgress == 0L -> stringResource(R.string.queue_status_resolving_apkmirror)
					effQueued && !effInstalling -> stringResource(R.string.queued_update)
					effProgress > 0L -> stringResource(R.string.queue_status_downloading)
					isSaveOnlyTask -> stringResource(R.string.queue_status_saving)
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
						!isSaveOnlyTask && effQueued && !effInstalling -> stringResource(R.string.queued_update)
						!isSaveOnlyTask && effInstalling -> stringResource(R.string.installing_update)
						isVariantCachedReady -> stringResource(R.string.install_cached_action)
						activeVariant.isWatchedApp() -> stringResource(R.string.install_action)
						else -> stringResource(R.string.update_action)
					}
				)
			}

			if (activeVariant.link !is Link.Empty) {
				FilledTonalButton(
					onClick = { onSaveApkVariant(activeVariant) },
					enabled = !isUpdateBusy,
					shape = CircleShape,
					modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
				) {
					Icon(
						painter = painterResource(R.drawable.ic_download),
						contentDescription = null,
						modifier = Modifier.size(18.dp)
					)
					Spacer(Modifier.width(8.dp))
					Text(
						text = when {
							isSaveOnlyTask && effQueued && !effInstalling -> stringResource(R.string.queued_update)
							isSaveOnlyTask && effInstalling -> stringResource(R.string.queue_status_saving)
							else -> stringResource(R.string.action_save_apk)
						}
					)
				}
			}

			OutlinedButton(
				onClick = { onIgnoreVersion(activeVariant.id) },
				enabled = !isUpdateBusy,
				shape = CircleShape,
				modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
			) {
				Text(
					if (activeVariant.isWatchedApp()) {
						stringResource(R.string.watched_mark_seen_action)
					} else {
						stringResource(R.string.skip_this_version)
					}
				)
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
	if (isWideScreen) {
		val isReducedMotion = ApkUpTheme.isReducedMotion
		androidx.compose.ui.window.Dialog(
			onDismissRequest = onDismiss,
			properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
		) {
			Box(
				modifier = Modifier
					.fillMaxSize()
					.clickable(
						interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
						indication = null,
						onClick = onDismiss
					)
					.padding(16.dp),
				contentAlignment = Alignment.CenterEnd
			) {
				var paneVisible by remember { mutableStateOf(false) }
				LaunchedEffect(Unit) { paneVisible = true }
				AnimatedVisibility(
					visible = paneVisible,
					enter = if (isReducedMotion) fadeIn(tween(80)) else slideInHorizontally(spring(dampingRatio = 0.84f, stiffness = 380f)) { it } + fadeIn(),
					exit = if (isReducedMotion) fadeOut(tween(80)) else slideOutHorizontally(spring(dampingRatio = 0.84f, stiffness = 380f)) { it } + fadeOut()
				) {
					Surface(
						shape = RoundedCornerShape(28.dp),
						color = MaterialTheme.colorScheme.surfaceContainerLow,
						tonalElevation = 6.dp,
						shadowElevation = 12.dp,
						modifier = Modifier
							.width(420.dp)
							.clickable(
								interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
								indication = null,
								onClick = {}
							)
					) {
						sheetBody()
					}
				}
			}
		}
	} else {
		ModalBottomSheet(
			onDismissRequest = onDismiss,
			sheetState = sheetState,
			shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
			containerColor = MaterialTheme.colorScheme.surfaceContainerLow
		) {
			sheetBody()
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
	FDroidSource -> stringResource(R.string.source_fdroid)
	GitHubSource -> stringResource(R.string.source_github)
	GitLabSource -> stringResource(R.string.source_gitlab)
	CodebergSource -> stringResource(R.string.source_codeberg)
	CustomFDroidSource -> stringResource(R.string.source_custom_fdroid)
	else -> source.name
}

internal fun resolveWikiUrlForSourceError(sourceName: String?, reason: String? = null): String {
	val base = "https://github.com/DurchEins/apkup/wiki/Troubleshooting-Source-Errors"
	val lowerSource = sourceName?.lowercase().orEmpty()
	val lowerReason = reason?.lowercase().orEmpty()

	if (lowerReason.isNotBlank()) {
		when {
			lowerReason.contains("dns") || lowerReason.contains("unknownhost") ->
				return "$base#dns-resolution-failed"
			lowerReason.contains("timeout") || lowerReason.contains("zeitüberschreitung") ->
				return "$base#connection-timeout"
			lowerReason.contains("401") || lowerReason.contains("403") || lowerReason.contains("429") || lowerReason.contains("auth") || lowerReason.contains("rate") -> {
				return when {
					lowerSource.contains("play") -> "$base#play-store-rate-limit"
					lowerSource.contains("github") && (lowerReason.contains("403") || lowerReason.contains("rate")) -> "$base#github-rate-limit"
					lowerSource.contains("gitlab") || lowerSource.contains("codeberg") || lowerSource.contains("forgejo") -> "$base#gitlab-auth-error"
					else -> "$base#auth-and-rate-limits"
				}
			}
			lowerReason.contains("404") || lowerReason.contains("500") || lowerReason.contains("502") || lowerReason.contains("503") || lowerReason.contains("504") -> {
				return if (lowerSource.contains("apkmirror") && lowerReason.contains("404")) {
					"$base#apkmirror-404"
				} else {
					"$base#server-errors"
				}
			}
			lowerReason.contains("cloudflare") || lowerReason.contains("captcha") || lowerReason.contains("browser") ->
				return "$base#apkmirror-cloudflare"
			lowerReason.contains("signature") || lowerReason.contains("incompatible") || lowerReason.contains("konflikt") ->
				return "$base#signature-conflicts"
			lowerReason.contains("asset") || lowerReason.contains("repo") ->
				return "$base#custom-repo-linking"
		}
	}

	return when {
		lowerSource.contains("signature") ->
			"$base#signature-conflicts"
		lowerSource.contains("apkmirror") ->
			"https://github.com/DurchEins/apkup/wiki/Update-Sources,-Repository-Linking-&-Signature-Protection#23-direct-apkmirror-resolution--hardened-apkm-split-extraction"
		lowerSource.contains("play") ->
			"$base#play-store-rate-limit"
		lowerSource.contains("github") ->
			"$base#github-rate-limit"
		lowerSource.contains("gitlab") ->
			"https://github.com/DurchEins/apkup/wiki/Update-Sources,-Repository-Linking-&-Signature-Protection#25-three-tier-repository-resolution-for-github-gitlab--codeberg"
		lowerSource.contains("codeberg") || lowerSource.contains("forgejo") ->
			"https://github.com/DurchEins/apkup/wiki/Configuring-GitHub-&-GitLab-Tokens"
		lowerSource.contains("fdroid") || lowerSource.contains("f-droid") || lowerSource.contains("izzy") ->
			"https://github.com/DurchEins/apkup/wiki/F-Droid-&-Index-V2-Repositories"
		else ->
			"$base#diagnostic-checklist"
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

internal fun filterSurvivingSources(
	activeSources: List<String>,
	errorKeys: Set<String>,
	fdroidRepoNames: Set<String> = emptySet()
): List<String> {
	val hasFDroidError = errorKeys.any { it.equals("F-Droid / IzzyOnDroid", ignoreCase = true) }
	val hasCustomFDroidError = errorKeys.any { it.equals("Custom F-Droid", ignoreCase = true) }
	val builtInFDroidNames = com.apkup.data.fdroid.FDroidRepoConfig.defaultPresets().map { it.name }
	return activeSources.filter { sourceName ->
		if (errorKeys.any { it.equals(sourceName, ignoreCase = true) }) return@filter false
		if (hasFDroidError && (
			sourceName.equals("IzzyOnDroid", ignoreCase = true) ||
				sourceName.equals("F-Droid", ignoreCase = true) ||
				sourceName.equals("F-Droid / IzzyOnDroid", ignoreCase = true) ||
				builtInFDroidNames.any { it.equals(sourceName, ignoreCase = true) } ||
				fdroidRepoNames.any { it.equals(sourceName, ignoreCase = true) }
			)
		) {
			return@filter false
		}
		if (hasCustomFDroidError && sourceName.equals("Custom F-Droid", ignoreCase = true)) {
			return@filter false
		}
		true
	}
}

internal fun formatSourceErrorBanner(
	sourceErrors: Map<String, String>,
	activeSources: List<String> = listOf("APKMirror", "Google Play"),
	isGerman: Boolean = true,
	fdroidRepoNames: Set<String> = emptySet()
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
	val survivingSources = filterSurvivingSources(activeSources, sourceErrors.keys, fdroidRepoNames)
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
	isGerman: Boolean = true,
	fdroidRepoNames: Set<String> = emptySet()
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
	val survivingSources = filterSurvivingSources(activeSources, sourceErrors.keys, fdroidRepoNames)
	val survivingText = if (survivingSources.isNotEmpty()) {
		val names = survivingSources.joinToString(" & ")
		if (isGerman) "$names hat keine Updates gemeldet." else "$names reported no updates."
	} else {
		if (isGerman) "Keine erreichbare Quelle hat Updates gemeldet." else "No reachable source reported updates."
	}
	return "$failedDescription. $survivingText"
}
