package com.apkup.ui.screen

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.DateUtils
import androidx.core.content.FileProvider
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import com.apkup.ui.component.installedAppsGridColumnCount
import com.apkup.ui.component.rememberExpandedModalSheetState
import com.apkup.util.Themer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apkup.R
import com.apkup.data.repomapping.RepoBinding
import com.apkup.data.snack.TextSnack
import com.apkup.data.ui.AppInstalled
import com.apkup.data.ui.AppsQuickFilter
import com.apkup.data.ui.AppsSortOrder
import com.apkup.data.ui.AppsUiState
import com.apkup.data.ui.InstallOrigin
import com.apkup.data.ui.filterAndSortInstalledApps
import com.apkup.repository.RepoMappingManager
import com.apkup.ui.component.AppEmptyState
import com.apkup.ui.component.CollapsibleScrollHeader
import com.apkup.ui.component.LinkRepoDialog
import com.apkup.ui.theme.ApkUpTheme
import com.apkup.ui.component.PinAppSourceBottomSheet
import com.apkup.ui.component.sourcePriorityLabel
import com.apkup.ui.component.LoadingGrid
import com.apkup.ui.component.LoadingImageApp
import com.apkup.ui.component.UpdateHistoryBottomSheet
import com.apkup.ui.component.rememberCollapsibleHeaderState
import com.apkup.util.SnackBar
import com.apkup.util.openAppInPlayStore
import com.apkup.util.openAppSystemSettings
import com.apkup.util.openUrl
import com.apkup.viewmodel.AppsViewModel
import com.apkup.viewmodel.AppsViewModel.ScopeTab
import com.apkup.viewmodel.UpdatesViewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AppsScreen(
	viewModel: AppsViewModel = koinViewModel(),
	updatesViewModel: UpdatesViewModel = koinViewModel()
) {
	viewModel.state().collectAsStateWithLifecycle().value.onLoading {
		AppsScreenLoading()
	}.onError {
		AppsScreenError()
	}.onSuccess {
		AppsScreenSuccess(viewModel, updatesViewModel, it)
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreenSuccess(
	viewModel: AppsViewModel,
	updatesViewModel: UpdatesViewModel,
	state: AppsUiState.Success
) = Column(
	modifier = Modifier
		.fillMaxSize()
		.background(MaterialTheme.colorScheme.background)
) {
	val resources = LocalResources.current
	val searchQuery by viewModel.searchQuery().collectAsStateWithLifecycle()
	val pinnedAppSources by viewModel.pinnedAppSources().collectAsStateWithLifecycle()
	var pinTargetApp by remember { mutableStateOf<AppInstalled?>(null) }
	val quickFilter by viewModel.quickFilter().collectAsStateWithLifecycle()
	val scopeTab by viewModel.scopeTab().collectAsStateWithLifecycle()
	val viewMode by viewModel.viewMode().collectAsStateWithLifecycle()
	val showSystemApps by viewModel.showSystemAppsFlow.collectAsStateWithLifecycle()
	val customBindings by viewModel.customRepoBindingsFlow.collectAsStateWithLifecycle()
	val mappedPackages = remember(customBindings, state.apps) { viewModel.getMappedPackages() }
	var searchExpanded by rememberSaveable { mutableStateOf(false) }
	var showHistorySheet by rememberSaveable { mutableStateOf(false) }
	var showWatchAppSheet by rememberSaveable { mutableStateOf(false) }
	var showShareScopeDialog by rememberSaveable { mutableStateOf(false) }
	var sortOrder by rememberSaveable { mutableStateOf(AppsSortOrder.NAME_ASC) }
	val snackBar: SnackBar = koinInject()
	val themer: Themer = koinInject()
	val configuredGridColumns by themer.appsGridColumnsFlow().collectAsStateWithLifecycle()

	// Tier 1 Predictive Back: If search is active, back press dismisses search first
	BackHandler(enabled = searchExpanded || searchQuery.isNotBlank()) {
		viewModel.setSearchQuery("")
		searchExpanded = false
	}

	val userAppsCount = remember(state.apps) { state.apps.count { !it.isSystemApp } }
	val systemAppsCount = remember(state.apps) { state.apps.count { it.isSystemApp } }

	// Scope filter (User vs System)
	val scopedApps = remember(state.apps, scopeTab, showSystemApps) {
		if (!showSystemApps) {
			state.apps.filter { !it.isSystemApp }
		} else {
			when (scopeTab) {
				ScopeTab.USER -> state.apps.filter { !it.isSystemApp }
				ScopeTab.SYSTEM -> state.apps.filter { it.isSystemApp }
			}
		}
	}

	// Secondary filter chips, search & sorting
	val filteredApps = remember(scopedApps, searchQuery, quickFilter, sortOrder, mappedPackages) {
		filterAndSortInstalledApps(
			apps = scopedApps,
			searchQuery = searchQuery,
			quickFilter = quickFilter,
			sortOrder = sortOrder,
			mappedPackages = mappedPackages
		)
	}

	val sourceLabelPlay = stringResource(R.string.source_play)
	val sourceLabelFdroid = stringResource(R.string.source_fdroid)
	val sourceLabelApkMirror = stringResource(R.string.source_apkmirror)
	val sourceLabelRevanced = stringResource(R.string.origin_revanced)
	val sourceLabelSideload = stringResource(R.string.apps_filter_sideload)
	val sourceLabelSystem = stringResource(R.string.apps_filter_system)

	// Group apps preserving active sort order (flat when sorted by recently updated)
	val groupedApps = remember(
		filteredApps,
		sortOrder,
		sourceLabelPlay,
		sourceLabelFdroid,
		sourceLabelApkMirror,
		sourceLabelRevanced,
		sourceLabelSideload,
		sourceLabelSystem
	) {
		when (sortOrder) {
			AppsSortOrder.NAME_ASC, AppsSortOrder.NAME_DESC -> {
				val map = linkedMapOf<String, MutableList<AppInstalled>>()
				for (app in filteredApps) {
					val firstChar = app.name.trim().firstOrNull()?.uppercaseChar() ?: '#'
					val key = if (firstChar in 'A'..'Z') firstChar.toString() else "#"
					map.getOrPut(key) { mutableListOf() }.add(app)
				}
				map
			}
			AppsSortOrder.SOURCE -> {
				val map = linkedMapOf<String, MutableList<AppInstalled>>()
				for (app in filteredApps) {
					val key = when (app.installOrigin) {
						InstallOrigin.PLAY_STORE -> sourceLabelPlay
						InstallOrigin.FDROID -> sourceLabelFdroid
						InstallOrigin.APKMIRROR -> sourceLabelApkMirror
						InstallOrigin.REVANCED -> sourceLabelRevanced
						InstallOrigin.SIDELOAD -> sourceLabelSideload
						InstallOrigin.SYSTEM -> sourceLabelSystem
					}
					map.getOrPut(key) { mutableListOf() }.add(app)
				}
				map
			}
			AppsSortOrder.LAST_UPDATED -> emptyMap()
		}
	}

	val listState = rememberLazyListState()
	val gridState = rememberLazyGridState()
	val coroutineScope = rememberCoroutineScope()
	var lastScrollResetKey by remember {
		mutableStateOf(Triple(sortOrder, quickFilter, scopeTab to searchQuery.trim()))
	}
	LaunchedEffect(sortOrder, quickFilter, scopeTab, searchQuery, viewMode) {
		val currentKey = Triple(sortOrder, quickFilter, scopeTab to searchQuery.trim())
		if (lastScrollResetKey != currentKey) {
			lastScrollResetKey = currentKey
			if (viewMode == "list") {
				listState.scrollToItem(0)
			} else {
				gridState.scrollToItem(0)
			}
		}
	}
	var isScrollingDown by remember { mutableStateOf(true) }
	var prevIndex by remember { mutableIntStateOf(0) }
	var prevOffset by remember { mutableIntStateOf(0) }

	// Scroll tracker for floating button
	LaunchedEffect(viewMode, listState, gridState) {
		if (viewMode == "list") {
			snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
				.collect { (index, offset) ->
					val movedDown = index > prevIndex || (index == prevIndex && offset > prevOffset + 6)
					val movedUp = index < prevIndex || (index == prevIndex && offset < prevOffset - 6)
					when {
						!listState.canScrollForward && listState.canScrollBackward -> isScrollingDown = false
						!listState.canScrollBackward && listState.canScrollForward -> isScrollingDown = true
						movedDown && listState.canScrollForward -> isScrollingDown = true
						movedUp && listState.canScrollBackward -> isScrollingDown = false
					}
					prevIndex = index
					prevOffset = offset
				}
		} else {
			snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
				.collect { (index, offset) ->
					val movedDown = index > prevIndex || (index == prevIndex && offset > prevOffset + 6)
					val movedUp = index < prevIndex || (index == prevIndex && offset < prevOffset - 6)
					when {
						!gridState.canScrollForward && gridState.canScrollBackward -> isScrollingDown = false
						!gridState.canScrollBackward && gridState.canScrollForward -> isScrollingDown = true
						movedDown && gridState.canScrollForward -> isScrollingDown = true
						movedUp && gridState.canScrollBackward -> isScrollingDown = false
					}
					prevIndex = index
					prevOffset = offset
				}
		}
	}

	val showFastScrollFab by remember(filteredApps.size, viewMode) {
		derivedStateOf {
			if (viewMode == "list") {
				filteredApps.size > 12 &&
					(listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 20)
			} else {
				filteredApps.size > 12 &&
					(gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 20)
			}
		}
	}

	val isAccessibleUi = ApkUpTheme.isAccessibleUi
	val effectiveViewMode = if (isAccessibleUi) "list" else viewMode
	val collapsibleHeaderState = rememberCollapsibleHeaderState(
		pinned = isAccessibleUi || searchExpanded || searchQuery.isNotBlank() || filteredApps.isEmpty()
	)

	// 1. Top Bar
	val shareContext = LocalContext.current
	val shareScope = rememberCoroutineScope()
	val shareChooserTitle = stringResource(R.string.apps_share_list_action)
	AppsTopBar(
		totalCount = filteredApps.size,
		searchExpanded = searchExpanded || searchQuery.isNotBlank(),
		onToggleSearch = {
			if (searchExpanded && searchQuery.isNotBlank()) {
				viewModel.setSearchQuery("")
			}
			searchExpanded = !searchExpanded
		},
		onAddWatchApp = { showWatchAppSheet = true },
		onShareApps = if (state.apps.isNotEmpty()) {
			{ showShareScopeDialog = true }
		} else null,
		sortOrder = sortOrder,
		onSortOrderChange = { newOrder -> sortOrder = newOrder }
	)

	// 2. Docked Search Bar
	AnimatedVisibility(
		visible = searchExpanded || searchQuery.isNotBlank(),
		enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
		exit = shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut()
	) {
		DockedAppsSearchBar(
			query = searchQuery,
			onQueryChange = { viewModel.setSearchQuery(it) },
			onClear = {
				viewModel.setSearchQuery("")
				searchExpanded = false
			}
		)
	}

	// 3. Einklappbarer Bereich: Segmented Control Tabs + Sekundäre Filterchips + Info-Banner
	CollapsibleScrollHeader(state = collapsibleHeaderState) {
		Column(modifier = Modifier.fillMaxWidth()) {
			if (showSystemApps) {
				Surface(
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = 14.dp, vertical = 4.dp),
					color = Color.Transparent
				) {
					SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
						SegmentedButton(
							selected = scopeTab == ScopeTab.USER,
							onClick = { viewModel.setScopeTab(ScopeTab.USER) },
							shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
							colors = SegmentedButtonDefaults.colors(
								activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
								activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
								inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
								inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant
							)
						) {
							Text(
								text = "${stringResource(R.string.apps_filter_user)} ($userAppsCount)",
								style = MaterialTheme.typography.labelMedium,
								fontWeight = if (scopeTab == ScopeTab.USER) FontWeight.Bold else FontWeight.Medium
							)
						}
						SegmentedButton(
							selected = scopeTab == ScopeTab.SYSTEM,
							onClick = { viewModel.setScopeTab(ScopeTab.SYSTEM) },
							shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
							colors = SegmentedButtonDefaults.colors(
								activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
								activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
								inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
								inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant
							)
						) {
							Text(
								text = "${stringResource(R.string.apps_filter_system)} ($systemAppsCount)",
								style = MaterialTheme.typography.labelMedium,
								fontWeight = if (scopeTab == ScopeTab.SYSTEM) FontWeight.Bold else FontWeight.Medium
							)
						}
					}
				}
			}

			// 4. Sekundäre Filterchips
			AppsQuickFilterRow(
				selectedFilter = quickFilter,
				onSelectFilter = { viewModel.setQuickFilter(it) }
			)

			// Information banner when Recently Updated filter is active
			AnimatedVisibility(
				visible = quickFilter == AppsQuickFilter.RECENTLY_UPDATED,
				enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
				exit = shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut()
			) {
				RecentFilterSystemInfoRow(
					onOpenHistory = { showHistorySheet = true }
				)
			}
		}
	}

	// 5. Toolbar & Ansichts-Umschalter (Sub-Header)
	val activeSortLabel = when (sortOrder) {
		AppsSortOrder.NAME_ASC -> stringResource(R.string.apps_sort_name_asc)
		AppsSortOrder.NAME_DESC -> stringResource(R.string.apps_sort_name_desc)
		AppsSortOrder.SOURCE -> stringResource(R.string.apps_sort_source)
		AppsSortOrder.LAST_UPDATED -> stringResource(R.string.apps_sort_recent)
	}
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 4.dp),
		horizontalArrangement = Arrangement.SpaceBetween,
		verticalAlignment = Alignment.CenterVertically
	) {
		Text(
			text = stringResource(R.string.apps_sort_count_label, filteredApps.size, activeSortLabel),
			style = MaterialTheme.typography.labelMedium,
			fontWeight = FontWeight.SemiBold,
			color = MaterialTheme.colorScheme.onSurfaceVariant
		)

		if (isAccessibleUi) {
			Text(
				text = stringResource(R.string.a11y_apps_list_active_hint),
				style = MaterialTheme.typography.labelSmall,
				fontWeight = FontWeight.SemiBold,
				color = MaterialTheme.colorScheme.primary
			)
		} else {
			Surface(
				shape = CircleShape,
				color = MaterialTheme.colorScheme.surfaceContainerLow
			) {
				Row(
					modifier = Modifier
						.padding(2.dp)
						.selectableGroup(),
					horizontalArrangement = Arrangement.spacedBy(2.dp),
					verticalAlignment = Alignment.CenterVertically
				) {
					val isList = effectiveViewMode == "list"
					Surface(
						onClick = { viewModel.setViewMode("list") },
						shape = CircleShape,
						color = if (isList) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
						modifier = Modifier.semantics {
							role = Role.Tab
							selected = isList
						}
					) {
						Icon(
							imageVector = Icons.AutoMirrored.Rounded.List,
							contentDescription = stringResource(R.string.apps_view_list),
							tint = if (isList) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
							modifier = Modifier
								.padding(horizontal = 8.dp, vertical = 4.dp)
								.size(18.dp)
						)
					}
					Surface(
						onClick = { viewModel.setViewMode("grid") },
						shape = CircleShape,
						color = if (!isList) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
						modifier = Modifier.semantics {
							role = Role.Tab
							selected = !isList
						}
					) {
						Icon(
							imageVector = Icons.Rounded.GridView,
							contentDescription = stringResource(R.string.apps_view_grid),
							tint = if (!isList) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
							modifier = Modifier
								.padding(horizontal = 8.dp, vertical = 4.dp)
								.size(18.dp)
						)
					}
				}
			}
		}
	}

	// 6. Content: 1-Spalten-Liste vs 2-Spalten-Grid
	if (filteredApps.isEmpty()) {
		AppEmptyState(
			title = stringResource(R.string.apps_search_empty_title),
			message = stringResource(R.string.apps_search_empty_message),
			icon = Icons.Filled.Search,
			modifier = Modifier.weight(1f)
		)
	} else {
		Box(
			modifier = Modifier
				.weight(1f)
				.nestedScroll(collapsibleHeaderState.nestedScrollConnection)
		) {
			val isReducedMotionApps = ApkUpTheme.isReducedMotion
			AnimatedContent(
				targetState = effectiveViewMode,
				transitionSpec = {
					if (isReducedMotionApps) androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
					else fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) togetherWith fadeOut()
				},
				label = "AppsViewModeTransition"
			) { mode ->
				if (mode == "list") {
					// 1-Spalten-Listenansicht mit gruppierten Headern oder flacher Sortierung
					LazyColumn(
						state = listState,
						modifier = Modifier.fillMaxSize(),
						contentPadding = PaddingValues(start = 14.dp, top = 4.dp, end = 14.dp, bottom = if (isAccessibleUi) 24.dp else 140.dp),
						verticalArrangement = Arrangement.spacedBy(if (isAccessibleUi) 12.dp else 8.dp)
					) {
						if (sortOrder == AppsSortOrder.LAST_UPDATED) {
							items(filteredApps, key = { it.packageName }) { app ->
								Box(modifier = Modifier.animateItem()) {
									AppListItem(
										app = app,
										pinnedSourceKey = pinnedAppSources[app.packageName],
										onOpenPinSource = { pinTargetApp = app },
										onIgnore = { pkg ->
											viewModel.ignore(pkg)
											updatesViewModel.syncIgnoredFromPrefs()
										},
										onRemoveWatched = { pkg ->
											viewModel.removeWatchedApp(pkg)
											snackBar.snackBar(
												coroutineScope,
												TextSnack(resources.getString(R.string.apps_watch_removed_snack, app.name.ifBlank { pkg }), visualType = com.apkup.data.snack.SnackVisualType.SUCCESS)
											)
										}
									)
								}
							}
						} else {
							groupedApps.forEach { (initial, appsInGroup) ->
								stickyHeader(key = "header_$initial") {
									Surface(
										modifier = Modifier.fillMaxWidth(),
										color = MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
									) {
										Row(
											modifier = Modifier
												.fillMaxWidth()
												.padding(vertical = if (isAccessibleUi) 8.dp else 4.dp)
												.semantics { heading() },
											verticalAlignment = Alignment.CenterVertically,
											horizontalArrangement = Arrangement.spacedBy(8.dp)
										) {
											if (isAccessibleUi) {
												Text(
													text = stringResource(R.string.a11y_apps_section_header, initial, appsInGroup.size),
													style = MaterialTheme.typography.titleMedium,
													fontWeight = FontWeight.Bold,
													color = MaterialTheme.colorScheme.primary
												)
												HorizontalDivider(
													modifier = Modifier.weight(1f),
													color = MaterialTheme.colorScheme.outlineVariant,
													thickness = 1.5.dp
												)
											} else {
												Text(
													text = initial,
													style = MaterialTheme.typography.titleMedium,
													fontWeight = FontWeight.Bold,
													color = MaterialTheme.colorScheme.primary
												)
												HorizontalDivider(
													modifier = Modifier.weight(1f),
													color = MaterialTheme.colorScheme.surfaceContainerHigh,
													thickness = 1.dp
												)
												Text(
													text = "${appsInGroup.size}",
													style = MaterialTheme.typography.labelSmall,
													color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
												)
											}
										}
									}
								}
								items(appsInGroup, key = { it.packageName }) { app ->
									Box(modifier = Modifier.animateItem()) {
										AppListItem(
											app = app,
											pinnedSourceKey = pinnedAppSources[app.packageName],
											onOpenPinSource = { pinTargetApp = app },
											onIgnore = { pkg ->
												viewModel.ignore(pkg)
												updatesViewModel.syncIgnoredFromPrefs()
											},
											onRemoveWatched = { pkg ->
												viewModel.removeWatchedApp(pkg)
												snackBar.snackBar(
													coroutineScope,
													TextSnack(resources.getString(R.string.apps_watch_removed_snack, app.name.ifBlank { pkg }), visualType = com.apkup.data.snack.SnackVisualType.SUCCESS)
												)
											}
										)
									}
								}
							}
						}
					}
				} else {
					// Konfigurierbares Raster: 2 große Spalten (48dp) oder kompakt 3 Spalten
					val highContrast = ApkUpTheme.isHighContrast
					BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
						val columns = installedAppsGridColumnCount(
							availableWidthDp = maxWidth.value,
							preferredPortraitColumns = configuredGridColumns,
							forceAccessibleGrid = false
						)
						val useLargeTouchTargets = highContrast || columns <= 2
						LazyVerticalGrid(
							columns = GridCells.Fixed(columns),
							state = gridState,
							modifier = Modifier.fillMaxSize(),
							contentPadding = PaddingValues(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 140.dp),
							verticalArrangement = Arrangement.spacedBy(8.dp),
							horizontalArrangement = Arrangement.spacedBy(8.dp)
						) {
							if (sortOrder == AppsSortOrder.LAST_UPDATED) {
								items(filteredApps, key = { it.packageName }) { app ->
									Box(modifier = Modifier.animateItem()) {
										AppGridItem(
											app = app,
											pinnedSourceKey = pinnedAppSources[app.packageName],
											enlargedTouchTargets = useLargeTouchTargets,
											onOpenPinSource = { pinTargetApp = app },
											onIgnore = { pkg ->
												viewModel.ignore(pkg)
												updatesViewModel.syncIgnoredFromPrefs()
											},
											onRemoveWatched = { pkg ->
												viewModel.removeWatchedApp(pkg)
												snackBar.snackBar(
													coroutineScope,
													TextSnack(resources.getString(R.string.apps_watch_removed_snack, app.name.ifBlank { pkg }), visualType = com.apkup.data.snack.SnackVisualType.SUCCESS)
												)
											}
										)
									}
								}
							} else {
								groupedApps.forEach { (initial, appsInGroup) ->
									item(key = "grid_header_$initial", span = { GridItemSpan(columns) }) {
										Row(
											modifier = Modifier
												.fillMaxWidth()
												.padding(top = 8.dp, bottom = 2.dp),
											verticalAlignment = Alignment.CenterVertically,
											horizontalArrangement = Arrangement.spacedBy(8.dp)
										) {
											Text(
												text = initial,
												style = MaterialTheme.typography.titleMedium,
												fontWeight = FontWeight.Bold,
												color = MaterialTheme.colorScheme.primary
											)
											HorizontalDivider(
												modifier = Modifier.weight(1f),
												color = MaterialTheme.colorScheme.surfaceContainerHigh,
												thickness = 1.dp
											)
											Text(
												text = "${appsInGroup.size}",
												style = MaterialTheme.typography.labelSmall,
												color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
											)
										}
									}
									items(appsInGroup, key = { it.packageName }) { app ->
										Box(modifier = Modifier.animateItem()) {
											AppGridItem(
												app = app,
												pinnedSourceKey = pinnedAppSources[app.packageName],
												enlargedTouchTargets = useLargeTouchTargets,
												onOpenPinSource = { pinTargetApp = app },
												onIgnore = { pkg ->
													viewModel.ignore(pkg)
													updatesViewModel.syncIgnoredFromPrefs()
												},
												onRemoveWatched = { pkg ->
													viewModel.removeWatchedApp(pkg)
													snackBar.snackBar(
														coroutineScope,
														TextSnack(resources.getString(R.string.apps_watch_removed_snack, app.name.ifBlank { pkg }), visualType = com.apkup.data.snack.SnackVisualType.SUCCESS)
													)
												}
											)
										}
									}
								}
							}
						}
					}
				}
			}

			// Schneller Scroll-FAB
			androidx.compose.animation.AnimatedVisibility(
				visible = showFastScrollFab,
				modifier = Modifier
					.align(Alignment.BottomEnd)
					.padding(16.dp),
				enter = if (ApkUpTheme.isReducedMotion) fadeIn() else fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
					scaleIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
				exit = if (ApkUpTheme.isReducedMotion) fadeOut() else fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
					scaleOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
			) {
				val isReducedMotionFab = ApkUpTheme.isReducedMotion
				val scrollAction = {
					coroutineScope.launch {
						if (effectiveViewMode == "list") {
							if (isScrollingDown) {
								val lastIndex = (groupedApps.size + filteredApps.size - 1).coerceAtLeast(0)
								if (isReducedMotionFab) listState.scrollToItem(lastIndex) else listState.animateScrollToItem(lastIndex)
								isScrollingDown = false
							} else {
								if (isReducedMotionFab) listState.scrollToItem(0) else listState.animateScrollToItem(0)
								isScrollingDown = true
							}
						} else {
							if (isScrollingDown) {
								val lastIndex = (groupedApps.size + filteredApps.size - 1).coerceAtLeast(0)
								gridState.animateScrollToItem(lastIndex)
								isScrollingDown = false
							} else {
								gridState.animateScrollToItem(0)
								isScrollingDown = true
							}
						}
					}
				}
				if (isAccessibleUi) {
					Button(
						onClick = { scrollAction() },
						shape = CircleShape,
						border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
						modifier = Modifier.heightIn(min = 48.dp)
					) {
						Icon(
							imageVector = if (isScrollingDown) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
							contentDescription = null,
							modifier = Modifier.size(18.dp)
						)
						androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
						Text(
							text = stringResource(
								if (isScrollingDown) R.string.apps_scroll_to_bottom else R.string.apps_scroll_to_top
							)
						)
					}
				} else {
					FloatingActionButton(
						onClick = { scrollAction() },
						shape = CircleShape,
						containerColor = MaterialTheme.colorScheme.primaryContainer,
						contentColor = MaterialTheme.colorScheme.onPrimaryContainer
					) {
						Icon(
							imageVector = if (isScrollingDown) {
								Icons.Filled.KeyboardArrowDown
							} else {
								Icons.Filled.KeyboardArrowUp
							},
							contentDescription = stringResource(
								if (isScrollingDown) R.string.apps_scroll_to_bottom else R.string.apps_scroll_to_top
							)
						)
					}
				}
			}
		}
	}

	if (showShareScopeDialog) {
		val shareScopeItems = listOf(
			AppsViewModel.ShareAppsScope.USER to stringResource(R.string.apps_filter_user),
			AppsViewModel.ShareAppsScope.SYSTEM to stringResource(R.string.apps_filter_system),
			AppsViewModel.ShareAppsScope.ALL to stringResource(R.string.apps_filter_all)
		)
		AlertDialog(
			onDismissRequest = { showShareScopeDialog = false },
			title = {
				Text(
					text = stringResource(R.string.apps_share_list_action),
					modifier = Modifier.semantics { heading() }
				)
			},
			text = {
				Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
					Text(
						text = stringResource(R.string.apps_share_scope_message),
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
					shareScopeItems.forEach { (scope, label) ->
						FilledTonalButton(
							onClick = {
								showShareScopeDialog = false
								shareScope.launch {
									val appsToShare = viewModel.loadAppsForShare(scope, sortOrder)
									if (appsToShare.isEmpty()) return@launch
									val repoUrls = appsToShare.associate { app ->
										app.packageName to (customBindings[app.packageName]?.webUrl.orEmpty())
									}
									val shareText = com.apkup.util.formatShareableAppList(
										apps = appsToShare,
										repoUrls = repoUrls,
										scopeLabel = label
									)
									runCatching {
										if (shareText.length <= 4_000) {
											val sendIntent = Intent(Intent.ACTION_SEND).apply {
												type = "text/plain"
												putExtra(Intent.EXTRA_TEXT, shareText)
											}
											shareContext.startActivity(
												Intent.createChooser(sendIntent, shareChooserTitle)
											)
										} else {
											val uri = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
												val exportsDir = java.io.File(shareContext.cacheDir, "exports").apply { mkdirs() }
												val exportFile = java.io.File(exportsDir, "apkup-app-list.txt")
												exportFile.writeText(shareText, Charsets.UTF_8)
												FileProvider.getUriForFile(
													shareContext,
													"${shareContext.packageName}.fileprovider",
													exportFile
												)
											}
											val sendIntent = Intent(Intent.ACTION_SEND).apply {
												type = "text/plain"
												putExtra(Intent.EXTRA_STREAM, uri)
												putExtra(Intent.EXTRA_SUBJECT, shareChooserTitle)
												clipData = ClipData.newRawUri("ApkUp App List", uri)
												addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
											}
											shareContext.startActivity(
												Intent.createChooser(sendIntent, shareChooserTitle)
													.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
											)
										}
									}
								}
							},
							modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
						) {
							Text(label)
						}
					}
				}
			},
			confirmButton = {},
			dismissButton = {
				TextButton(
					onClick = { showShareScopeDialog = false },
					modifier = Modifier.heightIn(min = 48.dp)
				) {
					Text(stringResource(R.string.action_cancel))
				}
			}
		)
	}

	if (showHistorySheet) {
		UpdateHistoryBottomSheet(onDismiss = { showHistorySheet = false })
	}

	if (showWatchAppSheet) {
		WatchAppBottomSheet(
			onDismiss = { showWatchAppSheet = false },
			onConfirmWatch = { pkg, name, repo ->
				val result = viewModel.addWatchedAppDetailed(pkg, name, repo)
				if (result == AppsViewModel.WatchAppAddResult.SUCCESS) {
					snackBar.snackBar(
						coroutineScope,
						TextSnack(resources.getString(R.string.apps_watch_added_snack, name.ifBlank { pkg }), visualType = com.apkup.data.snack.SnackVisualType.SUCCESS)
					)
					showWatchAppSheet = false
				}
				result
			}
		)
	}

	pinTargetApp?.let { target ->
		PinAppSourceBottomSheet(
			appName = target.name.ifBlank { target.packageName },
			currentPinnedKey = pinnedAppSources[target.packageName],
			onSelectSourceKey = { selectedKey ->
				viewModel.pinAppSource(target.packageName, selectedKey)
			},
			onDismiss = { pinTargetApp = null },
			currentPreReleaseOverride = viewModel.getAppPreReleaseOverride(target.packageName),
			onSelectPreReleaseOverride = { mode ->
				viewModel.setAppPreReleaseOverride(target.packageName, mode)
			}
		)
	}
}

@Composable
internal fun CopyableFingerprintPill(
	fingerprint: String,
	modifier: Modifier = Modifier
) {
	@Suppress("DEPRECATION")
	val clipboard = LocalClipboardManager.current
	val haptics = LocalHapticFeedback.current
	val isReducedMotion = ApkUpTheme.isReducedMotion
	var copied by remember(fingerprint) { mutableStateOf(false) }
	LaunchedEffect(copied) {
		if (copied) {
			kotlinx.coroutines.delay(1500L)
			copied = false
		}
	}
	val shortFp = remember(fingerprint) {
		val clean = fingerprint.trim()
		if (clean.length > 20) "${clean.take(10)}…${clean.takeLast(8)}" else clean
	}
	Surface(
		onClick = {
			clipboard.setText(AnnotatedString(fingerprint.trim()))
			haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
			copied = true
		},
		shape = RoundedCornerShape(10.dp),
		color = if (copied) ApkUpTheme.colors.successContainer else MaterialTheme.colorScheme.surfaceContainer,
		border = BorderStroke(
			1.dp,
			if (copied) ApkUpTheme.colors.success.copy(alpha = 0.45f)
			else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
		),
		modifier = modifier
	) {
		AnimatedContent(
			targetState = copied,
			transitionSpec = {
				if (isReducedMotion) {
					fadeIn(tween(80)) togetherWith fadeOut(tween(80))
				} else {
					(scaleIn(initialScale = 0.92f) + fadeIn(tween(160))) togetherWith
						(scaleOut(targetScale = 0.92f) + fadeOut(tween(120)))
				}
			},
			label = "FingerprintCopyMorph"
		) { isCopied ->
			Row(
				modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(6.dp)
			) {
				if (isCopied) {
					Icon(
						imageVector = Icons.Filled.Check,
						contentDescription = null,
						tint = ApkUpTheme.colors.onSuccessContainer,
						modifier = Modifier.size(14.dp)
					)
					Text(
						text = stringResource(R.string.apps_fingerprint_copied),
						style = MaterialTheme.typography.labelMedium,
						fontWeight = FontWeight.SemiBold,
						color = ApkUpTheme.colors.onSuccessContainer
					)
				} else {
					Text(
						text = shortFp,
						style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)
				}
			}
		}
	}
}

@Composable
private fun NewlyUpdatedPulseDot(
	lastUpdateTime: Long,
	modifier: Modifier = Modifier
) {
	val isRecent = remember(lastUpdateTime) {
		lastUpdateTime > 0L && (System.currentTimeMillis() - lastUpdateTime) <= 24L * 60L * 60L * 1000L
	}
	if (!isRecent) return
	val isReducedMotion = ApkUpTheme.isReducedMotion
	val pulseScale: Float
	val pulseAlpha: Float
	if (isReducedMotion) {
		pulseScale = 1f
		pulseAlpha = 0.9f
	} else {
		val transition = rememberInfiniteTransition(label = "NewDotPulse")
		pulseScale = transition.animateFloat(
			initialValue = 0.85f,
			targetValue = 1.15f,
			animationSpec = infiniteRepeatable(
				animation = tween(durationMillis = 1200, easing = LinearEasing),
				repeatMode = RepeatMode.Reverse
			),
			label = "NewDotScale"
		).value
		pulseAlpha = transition.animateFloat(
			initialValue = 0.55f,
			targetValue = 1f,
			animationSpec = infiniteRepeatable(
				animation = tween(durationMillis = 1200, easing = LinearEasing),
				repeatMode = RepeatMode.Reverse
			),
			label = "NewDotAlpha"
		).value
	}
	Box(
		modifier = modifier
			.size(7.dp)
			.graphicsLayer {
				scaleX = pulseScale
				scaleY = pulseScale
				alpha = pulseAlpha
			}
			.clip(CircleShape)
			.background(Color(0xFFF59E0B))
	)
}

// -------------------------------------------------------------
// 1-Spalten-Listenzeile („Die Symbiose“)
// -------------------------------------------------------------
@Composable
private fun rememberRepoLinkGlowBorder(
	repoBinding: RepoBinding?,
	isDialogOpen: Boolean
): BorderStroke {
	val haptics = LocalHapticFeedback.current
	var wasDialogOpen by remember { mutableStateOf(false) }
	var previousRepo by remember { mutableStateOf(repoBinding?.repo) }
	var isGlowing by remember { mutableStateOf(false) }

	LaunchedEffect(isDialogOpen, repoBinding?.repo) {
		val currentRepo = repoBinding?.repo
		if (wasDialogOpen && !isDialogOpen && currentRepo != null && currentRepo != previousRepo) {
			isGlowing = true
			haptics.performHapticFeedback(HapticFeedbackType.LongPress)
			kotlinx.coroutines.delay(1400L)
			isGlowing = false
		}
		wasDialogOpen = isDialogOpen
		previousRepo = currentRepo
	}

	val highContrast = ApkUpTheme.isHighContrast
	val borderColor by androidx.compose.animation.animateColorAsState(
		targetValue = when {
			isGlowing -> MaterialTheme.colorScheme.primary
			highContrast -> MaterialTheme.colorScheme.outlineVariant
			else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
		},
		animationSpec = ApkUpTheme.motion.fastEffectsSpec(),
		label = "RepoLinkGlowBorderColor"
	)
	val borderWidth by androidx.compose.animation.core.animateDpAsState(
		targetValue = when {
			isGlowing -> 2.dp
			highContrast -> 1.5.dp
			else -> 1.dp
		},
		animationSpec = ApkUpTheme.motion.fastSpatialSpec(),
		label = "RepoLinkGlowBorderWidth"
	)
	return BorderStroke(borderWidth, borderColor)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppListItem(
	app: AppInstalled,
	pinnedSourceKey: String? = null,
	onOpenPinSource: () -> Unit = {},
	repoMappingManager: RepoMappingManager = koinInject(),
	onIgnore: (String) -> Unit,
	onRemoveWatched: ((String) -> Unit)? = null
) {
	val context = LocalContext.current
	val haptics = LocalHapticFeedback.current
	var showActionsSheet by remember { mutableStateOf(false) }
	var showLinkRepoDialog by remember { mutableStateOf(false) }
	val repoBinding = remember(app.packageName, showLinkRepoDialog) {
		repoMappingManager.getBinding(app.packageName)
	}
	val cardBorder = rememberRepoLinkGlowBorder(repoBinding = repoBinding, isDialogOpen = showLinkRepoDialog)

	val interactionSource = remember { MutableInteractionSource() }
	val isPressed by interactionSource.collectIsPressedAsState()
	val scale by animateFloatAsState(
		targetValue = if (isPressed && !ApkUpTheme.isReducedMotion) 0.97f else 1f,
		animationSpec = ApkUpTheme.motion.fastSpatialSpec(),
		label = "AppListItemPressScale"
	)

	val isAccessibleUi = ApkUpTheme.isAccessibleUi
	val cardShape = MaterialTheme.shapes.large
	Surface(
		modifier = Modifier
			.fillMaxWidth()
			.graphicsLayer {
				scaleX = scale
				scaleY = scale
			}
			.clip(cardShape)
			.combinedClickable(
				interactionSource = interactionSource,
				indication = null,
				onClick = {
					haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
					showActionsSheet = true
				},
				onLongClick = {
					haptics.performHapticFeedback(HapticFeedbackType.LongPress)
					showActionsSheet = true
				}
			)
			.animateContentSize(animationSpec = ApkUpTheme.motion.defaultSpatialSpec()),
		shape = cardShape,
		color = MaterialTheme.colorScheme.surfaceContainerLow,
		border = cardBorder
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(if (isAccessibleUi) 18.dp else 12.dp),
			verticalArrangement = Arrangement.spacedBy(10.dp)
		) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(12.dp)
			) {
				// 46dp App-Icon
				Box(
					modifier = Modifier
						.size(46.dp)
						.clip(RoundedCornerShape(13.dp))
						.background(MaterialTheme.colorScheme.surfaceContainerHigh),
					contentAlignment = Alignment.Center
				) {
					LoadingImageApp(app.packageName, Modifier.fillMaxSize())
				}

				// Flexible Middle Content
				Column(
					modifier = Modifier.weight(1f),
					verticalArrangement = Arrangement.spacedBy(3.dp)
				) {
					Row(
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(6.dp)
					) {
						Text(
							text = app.name.ifBlank { app.packageName },
							style = MaterialTheme.typography.titleMedium.copy(fontSize = if (isAccessibleUi) 16.sp else 14.sp),
							fontWeight = FontWeight.Bold,
							color = MaterialTheme.colorScheme.onSurface,
							maxLines = if (isAccessibleUi) 3 else 1,
							overflow = TextOverflow.Ellipsis,
							modifier = Modifier.weight(1f, fill = false)
						)
						NewlyUpdatedPulseDot(lastUpdateTime = app.lastUpdateTime)
					}
					Text(
						text = app.packageName,
						style = MaterialTheme.typography.bodySmall.copy(
							fontSize = if (isAccessibleUi) 12.5.sp else 11.sp,
							fontFamily = FontFamily.Monospace
						),
						color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isAccessibleUi) 1f else 0.75f),
						maxLines = if (isAccessibleUi) 2 else 1,
						overflow = TextOverflow.Ellipsis
					)
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(6.dp)
				) {
					if (app.isWatchedOnly) {
						Surface(
							shape = RoundedCornerShape(6.dp),
							color = MaterialTheme.colorScheme.secondaryContainer
						) {
							Text(
								text = stringResource(R.string.apps_watched_badge),
								style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold),
								color = MaterialTheme.colorScheme.onSecondaryContainer,
								modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
								maxLines = 1,
								overflow = TextOverflow.Ellipsis
							)
						}
					} else {
						Surface(
							shape = RoundedCornerShape(6.dp),
							color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
						) {
							Text(
								text = "v${app.version.ifBlank { "—" }}",
								style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold),
								color = MaterialTheme.colorScheme.onPrimaryContainer,
								modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
								maxLines = 1,
								overflow = TextOverflow.Ellipsis
							)
						}
						Surface(
							shape = RoundedCornerShape(6.dp),
							color = MaterialTheme.colorScheme.surfaceContainer
						) {
							Row(
								modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(4.dp)
							) {
								val (originIcon, originLabel) = resolveOriginDisplay(app.installOrigin)
								Icon(
									painter = painterResource(originIcon),
									contentDescription = null,
									tint = MaterialTheme.colorScheme.onSurfaceVariant,
									modifier = Modifier.size(10.dp)
								)
								Text(
									text = originLabel,
									style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
									color = MaterialTheme.colorScheme.onSurfaceVariant,
									maxLines = 1
								)
							}
						}
					}
					if (pinnedSourceKey != null) {
						Icon(
							imageVector = Icons.Rounded.PushPin,
							contentDescription = stringResource(R.string.pin_source_chip_label),
							tint = MaterialTheme.colorScheme.primary,
							modifier = Modifier.size(13.dp)
						)
					}
					if (app.ignored) {
						Surface(
							shape = RoundedCornerShape(6.dp),
							color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
						) {
							Text(
								text = stringResource(R.string.apps_filter_ignored),
								style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold),
								color = MaterialTheme.colorScheme.onErrorContainer,
								modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
								maxLines = 1
							)
						}
					}
				}
			}

			// Right Actions: Relative Date + Quick Launch Button + 3-Dots Menu (in Standard mode)
			if (!isAccessibleUi) {
				Column(
					horizontalAlignment = Alignment.End,
					verticalArrangement = Arrangement.spacedBy(4.dp)
				) {
					if (app.lastUpdateTime > 0L) {
						val relativeTime = DateUtils.getRelativeTimeSpanString(
							app.lastUpdateTime,
							System.currentTimeMillis(),
							DateUtils.MINUTE_IN_MILLIS
						).toString()
						Text(
							text = relativeTime,
							style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
							color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
							maxLines = 1
						)
					}
					Row(
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(4.dp)
					) {
						val appDisplayName = app.name.ifBlank { app.packageName }
						if (!app.isWatchedOnly) {
							Box(
								modifier = Modifier
									.minimumInteractiveComponentSize()
									.clip(CircleShape)
									.clickable(
										role = Role.Button,
										onClick = { launchAppOrSettings(context, app.packageName) }
									),
								contentAlignment = Alignment.Center
							) {
								Box(
									modifier = Modifier
										.size(32.dp)
										.clip(CircleShape)
										.background(MaterialTheme.colorScheme.surfaceContainerHigh),
									contentAlignment = Alignment.Center
								) {
									Icon(
										imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
										contentDescription = "${stringResource(R.string.apps_launch_action)} $appDisplayName",
										tint = MaterialTheme.colorScheme.primary,
										modifier = Modifier.size(16.dp)
									)
								}
							}
						}

						Box(
							modifier = Modifier
								.minimumInteractiveComponentSize()
								.clip(CircleShape)
								.clickable(
									role = Role.Button,
									onClick = { showActionsSheet = true }
								),
							contentAlignment = Alignment.Center
						) {
							Icon(
								imageVector = Icons.Rounded.MoreVert,
								contentDescription = "${stringResource(R.string.updates_detail_view)} $appDisplayName",
								tint = MaterialTheme.colorScheme.onSurfaceVariant,
								modifier = Modifier.size(18.dp)
							)
						}
					}
				}
			}
			}
			if (isAccessibleUi) {
				val appDisplayName = app.name.ifBlank { app.packageName }
				val launchActionText = stringResource(R.string.apps_launch_action)
				val optionsActionText = stringResource(R.string.a11y_options_action)
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.spacedBy(8.dp),
					verticalAlignment = Alignment.CenterVertically
				) {
					if (!app.isWatchedOnly) {
						FilledTonalButton(
							onClick = { launchAppOrSettings(context, app.packageName) },
							shape = CircleShape,
							modifier = Modifier
								.weight(1f)
								.heightIn(min = 48.dp)
								.semantics {
									contentDescription = "$launchActionText $appDisplayName"
								}
						) {
							Text(
								text = launchActionText,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis
							)
						}
					}
					androidx.compose.material3.OutlinedButton(
						onClick = { showActionsSheet = true },
						shape = CircleShape,
						border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
						modifier = Modifier
							.weight(1f)
							.heightIn(min = 48.dp)
							.semantics {
								contentDescription = "$optionsActionText $appDisplayName"
							}
					) {
						Text(
							text = optionsActionText,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis
						)
					}
				}
			}
		}
	}

	if (showActionsSheet) {
		AppActionsBottomSheet(
			onDismiss = { showActionsSheet = false },
			app = app,
			repoBinding = repoBinding,
			pinnedSourceKey = pinnedSourceKey,
			onOpenLinkRepoDialog = { showLinkRepoDialog = true },
			onOpenPinSource = onOpenPinSource,
			onIgnore = onIgnore,
			onRemoveWatched = onRemoveWatched
		)
	}

	if (showLinkRepoDialog) {
		LinkRepoDialog(
			packageName = app.packageName,
			appName = app.name.ifBlank { app.packageName },
			onDismiss = { showLinkRepoDialog = false }
		)
	}
}

// -------------------------------------------------------------
// 2-Spalten-Grid-Kachel („Die Symbiose“)
// -------------------------------------------------------------
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppGridItem(
	app: AppInstalled,
	pinnedSourceKey: String? = null,
	enlargedTouchTargets: Boolean = false,
	onOpenPinSource: () -> Unit = {},
	repoMappingManager: RepoMappingManager = koinInject(),
	onIgnore: (String) -> Unit,
	onRemoveWatched: ((String) -> Unit)? = null
) {
	val context = LocalContext.current
	val haptics = LocalHapticFeedback.current
	var showActionsSheet by remember { mutableStateOf(false) }
	var showLinkRepoDialog by remember { mutableStateOf(false) }
	val repoBinding = remember(app.packageName, showLinkRepoDialog) {
		repoMappingManager.getBinding(app.packageName)
	}
	val cardBorder = rememberRepoLinkGlowBorder(repoBinding = repoBinding, isDialogOpen = showLinkRepoDialog)

	val interactionSource = remember { MutableInteractionSource() }
	val isPressed by interactionSource.collectIsPressedAsState()
	val scale by animateFloatAsState(
		targetValue = if (isPressed && !ApkUpTheme.isReducedMotion) 0.97f else 1f,
		animationSpec = ApkUpTheme.motion.fastSpatialSpec(),
		label = "AppGridItemPressScale"
	)

	val cardShape = MaterialTheme.shapes.medium
	Surface(
		modifier = Modifier
			.fillMaxWidth()
			.graphicsLayer {
				scaleX = scale
				scaleY = scale
			}
			.clip(cardShape)
			.combinedClickable(
				interactionSource = interactionSource,
				indication = null,
				onClick = {
					haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
					showActionsSheet = true
				},
				onLongClick = {
					haptics.performHapticFeedback(HapticFeedbackType.LongPress)
					showActionsSheet = true
				}
			)
			.animateContentSize(animationSpec = ApkUpTheme.motion.defaultSpatialSpec()),
		shape = cardShape,
		color = MaterialTheme.colorScheme.surfaceContainerLow,
		border = cardBorder
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(if (enlargedTouchTargets) 10.dp else 12.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp)
		) {
			// Top Row: App Icon + Launch Button + Menu
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween,
				verticalAlignment = Alignment.Top
			) {
				Box(
					modifier = Modifier
						.size(if (enlargedTouchTargets) 40.dp else 44.dp)
						.clip(RoundedCornerShape(12.dp))
						.background(MaterialTheme.colorScheme.surfaceContainerHigh),
					contentAlignment = Alignment.Center
				) {
					LoadingImageApp(app.packageName, Modifier.fillMaxSize())
				}
				Row(
					horizontalArrangement = Arrangement.spacedBy(if (enlargedTouchTargets) 0.dp else 2.dp),
					verticalAlignment = Alignment.CenterVertically
				) {
					val appDisplayName = app.name.ifBlank { app.packageName }
					if (!app.isWatchedOnly) {
						Box(
							modifier = (if (enlargedTouchTargets) Modifier.minimumInteractiveComponentSize() else Modifier)
								.clip(CircleShape)
								.clickable(
									role = Role.Button,
									onClick = { launchAppOrSettings(context, app.packageName) }
								),
							contentAlignment = Alignment.Center
						) {
							Surface(
								shape = CircleShape,
								color = MaterialTheme.colorScheme.surfaceContainerHigh,
								modifier = Modifier.size(30.dp)
							) {
								Box(contentAlignment = Alignment.Center) {
									Icon(
										imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
										contentDescription = "${stringResource(R.string.apps_launch_action)} $appDisplayName",
										tint = MaterialTheme.colorScheme.primary,
										modifier = Modifier.size(15.dp)
									)
								}
							}
						}
					}
					Box(
						modifier = (if (enlargedTouchTargets) Modifier.minimumInteractiveComponentSize() else Modifier)
							.clip(CircleShape)
							.clickable(
								role = Role.Button,
								onClick = { showActionsSheet = true }
							),
						contentAlignment = Alignment.Center
					) {
						Surface(
							shape = CircleShape,
							color = Color.Transparent,
							modifier = Modifier.size(28.dp)
						) {
							Box(contentAlignment = Alignment.Center) {
								Icon(
									imageVector = Icons.Rounded.MoreVert,
									contentDescription = "${stringResource(R.string.updates_detail_view)} $appDisplayName",
									tint = MaterialTheme.colorScheme.onSurfaceVariant,
									modifier = Modifier.size(18.dp)
								)
							}
						}
					}
				}
			}

			// App Name & Package Name
			Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
				Row(
					verticalAlignment = Alignment.Top,
					horizontalArrangement = Arrangement.spacedBy(5.dp)
				) {
					Text(
						text = app.name.ifBlank { app.packageName },
						style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.5.sp),
						fontWeight = FontWeight.Bold,
						color = MaterialTheme.colorScheme.onSurface,
						maxLines = 2,
						minLines = 2,
						overflow = TextOverflow.Ellipsis,
						modifier = Modifier.weight(1f)
					)
					NewlyUpdatedPulseDot(
						lastUpdateTime = app.lastUpdateTime,
						modifier = Modifier.padding(top = 5.dp)
					)
				}
				Text(
					text = app.packageName,
					style = MaterialTheme.typography.bodySmall.copy(
						fontSize = 10.sp,
						fontFamily = FontFamily.Monospace
					),
					color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
					maxLines = 1,
					overflow = TextOverflow.Ellipsis
				)
			}

			// Footer: Version Pill + Source Badge + Relative Date
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween,
				verticalAlignment = Alignment.CenterVertically
			) {
				Row(
					horizontalArrangement = Arrangement.spacedBy(4.dp),
					verticalAlignment = Alignment.CenterVertically,
					modifier = Modifier.weight(1f, fill = false)
				) {
					if (app.isWatchedOnly) {
						Surface(
							shape = RoundedCornerShape(6.dp),
							color = MaterialTheme.colorScheme.secondaryContainer
						) {
							Text(
								text = stringResource(R.string.apps_watched_badge),
								style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
								color = MaterialTheme.colorScheme.onSecondaryContainer,
								modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
								maxLines = 1,
								overflow = TextOverflow.Ellipsis
							)
						}
					} else {
						Surface(
							shape = RoundedCornerShape(6.dp),
							color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
						) {
							Text(
								text = "v${app.version.ifBlank { "—" }}",
								style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
								color = MaterialTheme.colorScheme.onPrimaryContainer,
								modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
								maxLines = 1,
								overflow = TextOverflow.Ellipsis
							)
						}
						val (originIcon, _) = resolveOriginDisplay(app.installOrigin)
						Icon(
							painter = painterResource(originIcon),
							contentDescription = null,
							tint = MaterialTheme.colorScheme.onSurfaceVariant,
							modifier = Modifier.size(13.dp)
						)
						if (pinnedSourceKey != null) {
							Icon(
								imageVector = Icons.Rounded.PushPin,
								contentDescription = stringResource(R.string.pin_source_chip_label),
								tint = MaterialTheme.colorScheme.primary,
								modifier = Modifier.size(12.dp)
							)
						}
					}
				}
				if (app.lastUpdateTime > 0L) {
					val relativeTime = DateUtils.getRelativeTimeSpanString(
						app.lastUpdateTime,
						System.currentTimeMillis(),
						DateUtils.MINUTE_IN_MILLIS
					).toString()
					Text(
						text = relativeTime,
						style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
						color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
						maxLines = 1
					)
				}
			}
		}
	}

	if (showActionsSheet) {
		AppActionsBottomSheet(
			onDismiss = { showActionsSheet = false },
			app = app,
			repoBinding = repoBinding,
			pinnedSourceKey = pinnedSourceKey,
			onOpenLinkRepoDialog = { showLinkRepoDialog = true },
			onOpenPinSource = onOpenPinSource,
			onIgnore = onIgnore,
			onRemoveWatched = onRemoveWatched
		)
	}

	if (showLinkRepoDialog) {
		LinkRepoDialog(
			packageName = app.packageName,
			appName = app.name.ifBlank { app.packageName },
			onDismiss = { showLinkRepoDialog = false }
		)
	}
}

// -------------------------------------------------------------
// Unified ModalBottomSheet for App Details & Context Actions
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AppActionsBottomSheet(
	onDismiss: () -> Unit,
	app: AppInstalled,
	repoBinding: RepoBinding?,
	pinnedSourceKey: String? = null,
	onOpenLinkRepoDialog: () -> Unit,
	onOpenPinSource: () -> Unit = {},
	onIgnore: (String) -> Unit,
	onRemoveWatched: ((String) -> Unit)? = null
) {
	val context = LocalContext.current
	val sheetState = rememberExpandedModalSheetState()
	val lastUpdatedFormatted = remember(app.lastUpdateTime) {
		if (app.lastUpdateTime > 0L) {
			DateUtils.getRelativeTimeSpanString(
				app.lastUpdateTime,
				System.currentTimeMillis(),
				DateUtils.MINUTE_IN_MILLIS
			).toString()
		} else {
			"—"
		}
	}

	val isAccessibleSheet = ApkUpTheme.isAccessibleUi
	ModalBottomSheet(
		onDismissRequest = onDismiss,
		sheetState = sheetState,
		containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
		contentColor = MaterialTheme.colorScheme.onSurface,
		dragHandle = if (isAccessibleSheet) null else {
			{
				Box(
					modifier = Modifier
						.padding(vertical = 12.dp)
						.width(36.dp)
						.height(4.dp)
						.clip(CircleShape)
						.background(MaterialTheme.colorScheme.outlineVariant)
				)
			}
		}
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.verticalScroll(rememberScrollState())
				.navigationBarsPadding()
				.padding(horizontal = 20.dp)
				.padding(top = if (isAccessibleSheet) 18.dp else 0.dp, bottom = 24.dp),
			verticalArrangement = Arrangement.spacedBy(12.dp)
		) {
			// Top Header: Icon, Name, Package Name
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(12.dp)
			) {
				Box(
					modifier = Modifier
						.size(48.dp)
						.clip(RoundedCornerShape(14.dp))
						.background(MaterialTheme.colorScheme.surfaceContainerHigh),
					contentAlignment = Alignment.Center
				) {
					LoadingImageApp(app.packageName, Modifier.fillMaxSize())
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
							text = app.name.ifBlank { app.packageName },
							style = MaterialTheme.typography.titleMedium,
							fontWeight = FontWeight.Bold,
							color = MaterialTheme.colorScheme.onSurface,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
							modifier = Modifier
								.weight(1f, fill = false)
								.semantics { heading() }
						)
						NewlyUpdatedPulseDot(lastUpdateTime = app.lastUpdateTime)
					}
					Text(
						text = app.packageName,
						style = MaterialTheme.typography.bodySmall.copy(
							fontSize = 11.5.sp,
							fontFamily = FontFamily.Monospace
						),
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)
				}
			}

			// Metadata Pills: Version, Install Origin, Last Updated
			FlowRow(
				horizontalArrangement = Arrangement.spacedBy(6.dp),
				verticalArrangement = Arrangement.spacedBy(6.dp)
			) {
				if (app.isWatchedOnly) {
					Surface(
						shape = RoundedCornerShape(8.dp),
						color = MaterialTheme.colorScheme.secondaryContainer
					) {
						Text(
							text = stringResource(R.string.apps_watched_badge),
							style = MaterialTheme.typography.labelMedium,
							fontWeight = FontWeight.SemiBold,
							color = MaterialTheme.colorScheme.onSecondaryContainer,
							modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
						)
					}
				} else {
					if (app.version.isNotBlank()) {
						Surface(
							shape = RoundedCornerShape(8.dp),
							color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
						) {
							Text(
								text = com.apkup.util.formatVersionWithShortHash(
									version = "v${app.version}",
									versionCode = app.versionCode,
									shortSha256 = app.shortSha256
								),
								style = MaterialTheme.typography.labelMedium,
								fontWeight = FontWeight.SemiBold,
								color = MaterialTheme.colorScheme.onPrimaryContainer,
								modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
							)
						}
					}
					val (originIcon, originLabel) = resolveOriginDisplay(app.installOrigin)
					Surface(
						shape = RoundedCornerShape(8.dp),
						color = MaterialTheme.colorScheme.surfaceContainerHigh
					) {
						Row(
							modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(5.dp)
						) {
							Icon(
								painter = painterResource(originIcon),
								contentDescription = null,
								tint = MaterialTheme.colorScheme.onSurfaceVariant,
								modifier = Modifier.size(13.dp)
							)
							Text(
								text = originLabel,
								style = MaterialTheme.typography.labelMedium,
								color = MaterialTheme.colorScheme.onSurfaceVariant
							)
						}
					}
				}

				if (app.lastUpdateTime > 0L) {
					Surface(
						shape = RoundedCornerShape(8.dp),
						color = MaterialTheme.colorScheme.surfaceContainerHigh
					) {
						Text(
							text = "${stringResource(R.string.apps_peek_last_updated)}: $lastUpdatedFormatted",
							style = MaterialTheme.typography.labelMedium,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
							modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
						)
					}
				}
			}

			// Tappable SHA-256 Signer Fingerprint Chip
			if (app.signatureSha256.isNotBlank()) {
				Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
					Text(
						text = stringResource(R.string.apps_peek_signature),
						style = MaterialTheme.typography.labelSmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
					CopyableFingerprintPill(fingerprint = app.signatureSha256)
				}
			}

			HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

			// Action Rows
			Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
				// 1. Open App (if installed)
				if (!app.isWatchedOnly) {
					AppSheetActionRow(
						title = stringResource(R.string.apps_launch_action),
						icon = {
							Icon(
								imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
								contentDescription = null,
								tint = MaterialTheme.colorScheme.primary,
								modifier = Modifier.size(20.dp)
							)
						},
						onClick = {
							onDismiss()
							launchAppOrSettings(context, app.packageName)
						}
					)

					// 2. App Info in Android Settings
					AppSheetActionRow(
						title = stringResource(R.string.apps_context_details),
						icon = {
							Icon(
								imageVector = Icons.Filled.Info,
								contentDescription = null,
								tint = MaterialTheme.colorScheme.primary,
								modifier = Modifier.size(20.dp)
							)
						},
						onClick = {
							onDismiss()
							context.openAppSystemSettings(app.packageName)
						}
					)
				}

				// 3. View on GitHub / GitLab / Codeberg (or Play Store)
				if (repoBinding != null) {
					AppSheetActionRow(
						title = stringResource(
							when {
								repoBinding.isCodeberg -> R.string.repo_view_on_codeberg
								repoBinding.isGitLab -> R.string.repo_view_on_gitlab
								else -> R.string.repo_view_on_github
							}
						),
						subtitle = repoBinding.webUrl,
						icon = {
							Icon(
								painter = painterResource(
									when {
										repoBinding.isCodeberg -> R.drawable.ic_codeberg
										repoBinding.isGitLab -> R.drawable.ic_gitlab
										else -> R.drawable.ic_github
									}
								),
								contentDescription = null,
								tint = MaterialTheme.colorScheme.primary,
								modifier = Modifier.size(20.dp)
							)
						},
						onClick = {
							onDismiss()
							context.openUrl(repoBinding.webUrl)
						}
					)
				} else if (!app.isWatchedOnly) {
					val isPlayStore = app.installOrigin == InstallOrigin.PLAY_STORE
					AppSheetActionRow(
						title = stringResource(R.string.apps_context_store),
						enabled = isPlayStore,
						icon = {
							Icon(
								imageVector = Icons.Outlined.PlayArrow,
								contentDescription = null,
								tint = if (isPlayStore) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
								modifier = Modifier.size(20.dp)
							)
						},
						onClick = {
							onDismiss()
							if (isPlayStore) {
								context.openAppInPlayStore(app.packageName)
							}
						}
					)
				}

				// 4. Link / Edit Repository
				val providerLabel = when {
					repoBinding?.isCodeberg == true -> "Codeberg"
					repoBinding?.isGitLab == true -> "GitLab"
					else -> "GitHub"
				}
				AppSheetActionRow(
					title = stringResource(
						if (repoBinding != null) R.string.repo_menu_linked
						else R.string.repo_menu_link
					),
					subtitle = repoBinding?.let { "$providerLabel: ${it.repo}" },
					icon = {
						Icon(
							painter = painterResource(
								when {
									repoBinding?.isCodeberg == true -> R.drawable.ic_codeberg
									repoBinding?.isGitLab == true -> R.drawable.ic_gitlab
									else -> R.drawable.ic_github
								}
							),
							contentDescription = null,
							tint = if (repoBinding != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
							modifier = Modifier.size(20.dp)
						)
					},
					onClick = {
						onDismiss()
						onOpenLinkRepoDialog()
					}
				)

				// 5. Pin Update Source
				AppSheetActionRow(
					title = stringResource(R.string.pin_source_menu_action),
					subtitle = pinnedSourceKey?.let {
						stringResource(R.string.pin_source_menu_pinned, sourcePriorityLabel(it))
					},
					subtitleColor = MaterialTheme.colorScheme.primary,
					icon = {
						Icon(
							imageVector = Icons.Rounded.PushPin,
							contentDescription = null,
							tint = if (pinnedSourceKey != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
							modifier = Modifier.size(20.dp)
						)
					},
					onClick = {
						onDismiss()
						onOpenPinSource()
					}
				)

				// 6. Remove from Watchlist or Ignore / Unignore App
				if (app.isWatchedOnly && onRemoveWatched != null) {
					AppSheetActionRow(
						title = stringResource(R.string.apps_watch_remove_action),
						titleColor = MaterialTheme.colorScheme.error,
						icon = {
							Icon(
								imageVector = Icons.Filled.Delete,
								contentDescription = null,
								tint = MaterialTheme.colorScheme.error,
								modifier = Modifier.size(20.dp)
							)
						},
						onClick = {
							onDismiss()
							onRemoveWatched(app.packageName)
						}
					)
				} else {
					AppSheetActionRow(
						title = stringResource(
							if (app.ignored) R.string.apps_context_unignore
							else R.string.apps_context_ignore
						),
						icon = {
							Icon(
								painter = painterResource(if (app.ignored) R.drawable.ic_visible else R.drawable.ic_visible_off),
								contentDescription = null,
								tint = if (app.ignored) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
								modifier = Modifier.size(20.dp)
							)
						},
						onClick = {
							onDismiss()
							onIgnore(app.packageName)
						}
					)
				}
				if (ApkUpTheme.isAccessibleUi) {
					androidx.compose.material3.OutlinedButton(
						onClick = onDismiss,
						shape = CircleShape,
						border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
						modifier = Modifier
							.fillMaxWidth()
							.padding(top = 6.dp)
							.heightIn(min = 48.dp)
					) {
						Text(stringResource(R.string.a11y_close_action))
					}
				}
			}
		}
	}
}

@Composable
private fun AppSheetActionRow(
	title: String,
	subtitle: String? = null,
	enabled: Boolean = true,
	titleColor: Color = MaterialTheme.colorScheme.onSurface,
	subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
	icon: @Composable () -> Unit,
	onClick: () -> Unit
) {
	Surface(
		onClick = onClick,
		enabled = enabled,
		shape = RoundedCornerShape(14.dp),
		color = MaterialTheme.colorScheme.surfaceContainer,
		modifier = Modifier.fillMaxWidth()
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 14.dp, vertical = 12.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(12.dp)
		) {
			icon()
			Column(
				modifier = Modifier.weight(1f),
				verticalArrangement = Arrangement.spacedBy(2.dp)
			) {
				Text(
					text = title,
					style = MaterialTheme.typography.bodyMedium,
					fontWeight = FontWeight.Medium,
					color = if (enabled) titleColor else titleColor.copy(alpha = 0.45f),
					maxLines = 1,
					overflow = TextOverflow.Ellipsis
				)
				if (!subtitle.isNullOrBlank()) {
					Text(
						text = subtitle,
						style = MaterialTheme.typography.labelSmall,
						color = if (enabled) subtitleColor else subtitleColor.copy(alpha = 0.45f),
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)
				}
			}
		}
	}
}

private fun launchAppOrSettings(context: Context, packageName: String) {
	val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
	if (launchIntent != null) {
		runCatching { context.startActivity(launchIntent) }
	} else {
		runCatching {
			val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
				data = Uri.fromParts("package", packageName, null)
				addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
			}
			context.startActivity(intent)
		}
	}
}

private fun resolveOriginDisplay(origin: InstallOrigin): Pair<Int, String> = when (origin) {
	InstallOrigin.PLAY_STORE -> R.drawable.ic_play to "Google Play"
	InstallOrigin.FDROID -> R.drawable.ic_izzy to "F-Droid"
	InstallOrigin.APKMIRROR -> R.drawable.ic_apkmirror to "APKMirror"
	InstallOrigin.REVANCED -> R.drawable.ic_system to "ReVanced"
	InstallOrigin.SIDELOAD -> R.drawable.ic_install to "Sideload"
	InstallOrigin.SYSTEM -> R.drawable.ic_system to "System"
}

// -------------------------------------------------------------
// Quick Filter Chips Row
// -------------------------------------------------------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppsQuickFilterRow(
	selectedFilter: AppsQuickFilter,
	onSelectFilter: (AppsQuickFilter) -> Unit
) {
	val isAccessibleUi = ApkUpTheme.isAccessibleUi
	val isReducedMotion = ApkUpTheme.isReducedMotion
	val filters = listOf(
		AppsQuickFilter.ALL to stringResource(R.string.apps_filter_all),
		AppsQuickFilter.SIDELOAD_ONLY to stringResource(R.string.apps_filter_sideload),
		AppsQuickFilter.WATCHED to stringResource(R.string.apps_filter_watched),
		AppsQuickFilter.RECENTLY_UPDATED to stringResource(R.string.apps_filter_recent),
		AppsQuickFilter.IGNORED_ONLY to stringResource(R.string.apps_filter_ignored)
	)
	val chipContent: @Composable () -> Unit = {
		filters.forEach { (filter, label) ->
			val selected = selectedFilter == filter
			val chipScale = remember { Animatable(1f) }
			val iconRotation = remember { Animatable(0f) }
			var wasSelected by remember { mutableStateOf(selected) }
			LaunchedEffect(selected) {
				if (selected && !wasSelected && !isReducedMotion) {
					chipScale.snapTo(0.94f)
					iconRotation.snapTo(-28f)
					kotlinx.coroutines.coroutineScope {
						launch {
							chipScale.animateTo(
								targetValue = 1f,
								animationSpec = spring(dampingRatio = 0.52f, stiffness = 560f)
							)
						}
						launch {
							iconRotation.animateTo(
								targetValue = 0f,
								animationSpec = spring(dampingRatio = 0.65f, stiffness = 480f)
							)
						}
					}
				}
				wasSelected = selected
			}
			FilterChip(
				selected = selected,
				onClick = { onSelectFilter(filter) },
				modifier = Modifier
					.then(if (isAccessibleUi) Modifier.heightIn(min = 48.dp) else Modifier)
					.graphicsLayer {
						scaleX = chipScale.value
						scaleY = chipScale.value
					},
				label = {
					Text(
						text = label,
						style = if (isAccessibleUi) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
						fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
					)
				},
				leadingIcon = if (selected) {
					{
						Icon(
							imageVector = Icons.Filled.Check,
							contentDescription = null,
							modifier = Modifier
								.size(FilterChipDefaults.IconSize)
								.graphicsLayer { rotationZ = iconRotation.value }
						)
					}
				} else null,
				shape = CircleShape,
				border = if (isAccessibleUi) {
					FilterChipDefaults.filterChipBorder(
						enabled = true,
						selected = selected,
						borderColor = MaterialTheme.colorScheme.outline,
						selectedBorderColor = MaterialTheme.colorScheme.primary,
						borderWidth = 1.5.dp,
						selectedBorderWidth = 1.5.dp
					)
				} else null,
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
	if (isAccessibleUi) {
		FlowRow(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 14.dp, vertical = 4.dp),
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp)
		) {
			chipContent()
		}
	} else {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.horizontalScroll(rememberScrollState())
				.padding(horizontal = 14.dp, vertical = 2.dp),
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			chipContent()
		}
	}
}

@Composable
private fun RecentFilterSystemInfoRow(
	onOpenHistory: () -> Unit
) {
	Surface(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 14.dp, vertical = 4.dp),
			shape = RoundedCornerShape(14.dp),
		color = MaterialTheme.colorScheme.surfaceContainerLow
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 10.dp, vertical = 7.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(8.dp)
		) {
			Icon(
				imageVector = Icons.Filled.Info,
				contentDescription = null,
				tint = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.size(14.dp)
			)
			Text(
				text = stringResource(R.string.apps_recent_filter_hint),
				style = MaterialTheme.typography.labelSmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.weight(1f)
			)
			Surface(
				onClick = onOpenHistory,
				shape = CircleShape,
				color = MaterialTheme.colorScheme.secondaryContainer
			) {
				Row(
					modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(4.dp)
				) {
					Icon(
						imageVector = Icons.Rounded.History,
						contentDescription = null,
						tint = MaterialTheme.colorScheme.onSecondaryContainer,
						modifier = Modifier.size(12.dp)
					)
					Text(
						text = stringResource(R.string.apps_open_apkup_history),
						style = MaterialTheme.typography.labelSmall,
						fontWeight = FontWeight.SemiBold,
						color = MaterialTheme.colorScheme.onSecondaryContainer,
						maxLines = 1
					)
					Icon(
						imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
						contentDescription = null,
						tint = MaterialTheme.colorScheme.onSecondaryContainer,
						modifier = Modifier.size(11.dp)
					)
				}
			}
		}
	}
}

@Composable
private fun DockedAppsSearchBar(
	query: String,
	onValueChange: (String) -> Unit = {},
	onQueryChange: (String) -> Unit = onValueChange,
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
				text = stringResource(R.string.apps_search_placeholder),
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
						contentDescription = stringResource(R.string.a11y_clear_search),
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
fun AppsScreenLoading() = Column(Modifier.fillMaxSize()) {
	AppsTopBar()
	LoadingGrid()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsTopBar(
	totalCount: Int? = null,
	searchExpanded: Boolean = false,
	onToggleSearch: (() -> Unit)? = null,
	onAddWatchApp: (() -> Unit)? = null,
	onShareApps: (() -> Unit)? = null,
	sortOrder: AppsSortOrder = AppsSortOrder.NAME_ASC,
	onSortOrderChange: ((AppsSortOrder) -> Unit)? = null
) = TopAppBar(
	title = {
		Row(
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(8.dp)
		) {
			Text(
				text = stringResource(R.string.tab_apps),
				style = MaterialTheme.typography.headlineMedium,
				fontWeight = FontWeight.Bold,
				modifier = Modifier.semantics { heading() }
			)
			if (totalCount != null) {
				Surface(
					shape = CircleShape,
					color = MaterialTheme.colorScheme.primaryContainer
				) {
					Text(
						text = totalCount.toString(),
						modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
						style = MaterialTheme.typography.labelMedium,
						fontWeight = FontWeight.Bold,
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
	windowInsets = WindowInsets(0),
	actions = {
		if (onShareApps != null) {
			IconButton(onClick = onShareApps) {
				Icon(
					imageVector = Icons.Filled.Share,
					contentDescription = stringResource(R.string.apps_share_list_action),
					tint = MaterialTheme.colorScheme.onSurfaceVariant
				)
			}
		}
		if (onAddWatchApp != null) {
			IconButton(onClick = onAddWatchApp) {
				Icon(
					imageVector = Icons.Filled.Add,
					contentDescription = stringResource(R.string.apps_watch_add_action),
					tint = MaterialTheme.colorScheme.onSurfaceVariant
				)
			}
		}
		if (onToggleSearch != null) {
			IconButton(onClick = onToggleSearch) {
				Icon(
					imageVector = if (searchExpanded) Icons.Filled.Clear else Icons.Filled.Search,
					contentDescription = stringResource(
						if (searchExpanded) R.string.a11y_close_search else R.string.apps_search_placeholder
					)
				)
			}
		}
		if (onSortOrderChange != null) {
			var sortMenuExpanded by remember { mutableStateOf(false) }
			Box {
				IconButton(onClick = { sortMenuExpanded = true }) {
					Icon(
						imageVector = Icons.AutoMirrored.Rounded.Sort,
						contentDescription = stringResource(R.string.apps_sort_action),
						tint = if (sortOrder != AppsSortOrder.NAME_ASC) {
							MaterialTheme.colorScheme.primary
						} else {
							MaterialTheme.colorScheme.onSurfaceVariant
						}
					)
				}
				DropdownMenu(
					expanded = sortMenuExpanded,
					onDismissRequest = { sortMenuExpanded = false },
					shape = RoundedCornerShape(18.dp),
					containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
				) {
					listOf(
						AppsSortOrder.NAME_ASC to R.string.apps_sort_name_asc,
						AppsSortOrder.NAME_DESC to R.string.apps_sort_name_desc,
						AppsSortOrder.SOURCE to R.string.apps_sort_source,
						AppsSortOrder.LAST_UPDATED to R.string.apps_sort_recent
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
	},
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
				imageVector = Icons.Outlined.Home,
				contentDescription = null,
				tint = MaterialTheme.colorScheme.primary,
				modifier = Modifier.size(18.dp)
			)
		}
	}
)

@Composable
fun AppsScreenError() = AppEmptyState(
	title = stringResource(R.string.something_went_wrong),
	message = stringResource(R.string.apps_error_message),
	icon = Icons.Filled.Info
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WatchAppBottomSheet(
	onDismiss: () -> Unit,
	onConfirmWatch: (packageName: String, appName: String, repoUrl: String) -> AppsViewModel.WatchAppAddResult
) {
	val sheetState = rememberExpandedModalSheetState()
	var packageNameInput by rememberSaveable { mutableStateOf("") }
	var appNameInput by rememberSaveable { mutableStateOf("") }
	var repoUrlInput by rememberSaveable { mutableStateOf("") }
	var showInvalidPkgError by remember { mutableStateOf(false) }
	var showInvalidRepoError by remember { mutableStateOf(false) }

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
						imageVector = Icons.Filled.Add,
						contentDescription = null,
						tint = MaterialTheme.colorScheme.primary,
						modifier = Modifier.size(22.dp)
					)
				}
				Text(
					text = stringResource(R.string.watch_sheet_title),
					style = MaterialTheme.typography.titleMedium,
					fontWeight = FontWeight.Bold
				)
			}

			Text(
				text = stringResource(R.string.watch_sheet_desc),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)

			OutlinedTextField(
				value = packageNameInput,
				onValueChange = {
					packageNameInput = it
					showInvalidPkgError = false
				},
				label = { Text(stringResource(R.string.watch_sheet_pkg_label)) },
				placeholder = { Text(stringResource(R.string.watch_sheet_pkg_hint)) },
				isError = showInvalidPkgError,
				supportingText = if (showInvalidPkgError) {
					{ Text(stringResource(R.string.watch_sheet_pkg_invalid)) }
				} else null,
				singleLine = true,
				modifier = Modifier.fillMaxWidth()
			)

			OutlinedTextField(
				value = appNameInput,
				onValueChange = { appNameInput = it },
				label = { Text(stringResource(R.string.watch_sheet_name_label)) },
				placeholder = { Text(stringResource(R.string.watch_sheet_name_hint)) },
				singleLine = true,
				modifier = Modifier.fillMaxWidth()
			)

			OutlinedTextField(
				value = repoUrlInput,
				onValueChange = {
					repoUrlInput = it
					showInvalidRepoError = false
				},
				label = { Text(stringResource(R.string.watch_sheet_repo_label)) },
				placeholder = { Text(stringResource(R.string.watch_sheet_repo_hint)) },
				isError = showInvalidRepoError,
				supportingText = if (showInvalidRepoError) {
					{ Text(stringResource(R.string.watch_sheet_repo_invalid)) }
				} else null,
				singleLine = true,
				modifier = Modifier.fillMaxWidth()
			)

			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.spacedBy(10.dp)
			) {
				FilledTonalButton(
					onClick = onDismiss,
					modifier = Modifier.weight(1f),
					shape = CircleShape
				) {
					Text(stringResource(android.R.string.cancel))
				}
				Button(
					onClick = {
						when (onConfirmWatch(packageNameInput, appNameInput, repoUrlInput)) {
							AppsViewModel.WatchAppAddResult.SUCCESS -> Unit
							AppsViewModel.WatchAppAddResult.INVALID_PACKAGE -> showInvalidPkgError = true
							AppsViewModel.WatchAppAddResult.INVALID_REPO -> showInvalidRepoError = true
						}
					},
					enabled = packageNameInput.isNotBlank(),
					modifier = Modifier.weight(1f),
					shape = CircleShape
				) {
					Text(stringResource(R.string.watch_sheet_btn_add))
				}
			}
		}
	}
}
