package com.apkup.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apkup.R
import com.apkup.data.repomapping.RepoBinding
import com.apkup.data.ui.AppInstalled
import com.apkup.data.ui.AppsQuickFilter
import com.apkup.data.ui.AppsUiState
import com.apkup.data.ui.InstallOrigin
import com.apkup.data.ui.filterInstalledApps
import com.apkup.repository.RepoMappingManager
import com.apkup.util.openAppInPlayStore
import com.apkup.util.openAppSystemSettings
import com.apkup.util.openUrl
import com.apkup.transform.resolveInstallerPackageName
import com.apkup.ui.component.AppEmptyState
import com.apkup.ui.component.LinkRepoDialog
import com.apkup.ui.component.LoadingGrid
import com.apkup.ui.component.LoadingImageApp
import com.apkup.ui.component.UpdateHistoryBottomSheet
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
) = Column(Modifier.fillMaxSize()) {
	val context = LocalContext.current
	val searchQuery by viewModel.searchQuery().collectAsStateWithLifecycle()
	val quickFilter by viewModel.quickFilter().collectAsStateWithLifecycle()
	val scopeTab by viewModel.scopeTab().collectAsStateWithLifecycle()
	val viewMode by viewModel.viewMode().collectAsStateWithLifecycle()
	val showSystemApps by viewModel.showSystemAppsFlow.collectAsStateWithLifecycle()
	var searchExpanded by rememberSaveable { mutableStateOf(false) }
	var showHistorySheet by rememberSaveable { mutableStateOf(false) }

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

	// Secondary filter chips & search
	val filteredApps = remember(scopedApps, searchQuery, quickFilter) {
		filterInstalledApps(
			apps = scopedApps,
			searchQuery = searchQuery,
			quickFilter = quickFilter
		)
	}

	// Group alphabetically A-Z
	val groupedApps = remember(filteredApps) {
		filteredApps.groupBy { app ->
			val firstChar = app.name.trim().firstOrNull()?.uppercaseChar() ?: '#'
			if (firstChar in 'A'..'Z') firstChar.toString() else "#"
		}.toSortedMap()
	}

	val listState = rememberLazyListState()
	val gridState = rememberLazyGridState()
	val coroutineScope = rememberCoroutineScope()
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

	// 1. Top Bar
	AppsTopBar(
		totalCount = filteredApps.size,
		searchExpanded = searchExpanded || searchQuery.isNotBlank(),
		onToggleSearch = {
			if (searchExpanded && searchQuery.isNotBlank()) {
				viewModel.setSearchQuery("")
			}
			searchExpanded = !searchExpanded
		}
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

	// 3. Segmented Control Tabs oben (Benutzer vs. System) - nur sichtbar, wenn in Einstellungen aktiviert
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

	// 5. Toolbar & Ansichts-Umschalter (Sub-Header)
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 4.dp),
		horizontalArrangement = Arrangement.SpaceBetween,
		verticalAlignment = Alignment.CenterVertically
	) {
		Text(
			text = stringResource(R.string.apps_sort_count_label, filteredApps.size),
			style = MaterialTheme.typography.labelMedium,
			fontWeight = FontWeight.SemiBold,
			color = MaterialTheme.colorScheme.onSurfaceVariant
		)

		Surface(
			shape = CircleShape,
			color = MaterialTheme.colorScheme.surfaceContainerLow
		) {
			Row(
				modifier = Modifier.padding(2.dp),
				horizontalArrangement = Arrangement.spacedBy(2.dp),
				verticalAlignment = Alignment.CenterVertically
			) {
				val isList = viewMode == "list"
				Surface(
					onClick = { viewModel.setViewMode("list") },
					shape = CircleShape,
					color = if (isList) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
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
					color = if (!isList) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
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

	// 6. Content: 1-Spalten-Liste vs 2-Spalten-Grid
	if (filteredApps.isEmpty()) {
		AppEmptyState(
			title = stringResource(R.string.apps_search_empty_title),
			message = stringResource(R.string.apps_search_empty_message),
			icon = Icons.Filled.Search,
			modifier = Modifier.weight(1f)
		)
	} else {
		Box(Modifier.weight(1f)) {
			AnimatedContent(
				targetState = viewMode,
				transitionSpec = { fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) togetherWith fadeOut() },
				label = "AppsViewModeTransition"
			) { mode ->
				if (mode == "list") {
					// 1-Spalten-Listenansicht mit Sticky A-Z Headern
					LazyColumn(
						state = listState,
						modifier = Modifier.fillMaxSize(),
						contentPadding = PaddingValues(start = 14.dp, top = 4.dp, end = 14.dp, bottom = 140.dp),
						verticalArrangement = Arrangement.spacedBy(8.dp)
					) {
						groupedApps.forEach { (initial, appsInGroup) ->
							stickyHeader(key = "header_$initial") {
								Surface(
									modifier = Modifier.fillMaxWidth(),
									color = MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
								) {
									Row(
										modifier = Modifier
											.fillMaxWidth()
											.padding(vertical = 4.dp),
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
							}
							items(appsInGroup, key = { it.packageName }) { app ->
								AppListItem(
									app = app,
									onIgnore = { pkg ->
										viewModel.ignore(pkg)
										updatesViewModel.syncIgnoredFromPrefs()
									}
								)
							}
						}
					}
				} else {
					// 2-Spalten-Grid (exakt 2 Spalten im Hochformat)
					BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
						val columns = if (maxWidth < 600.dp) 2 else (maxWidth / 180.dp).toInt().coerceIn(2, 5)
						LazyVerticalGrid(
							columns = GridCells.Fixed(columns),
							state = gridState,
							modifier = Modifier.fillMaxSize(),
							contentPadding = PaddingValues(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 140.dp),
							verticalArrangement = Arrangement.spacedBy(8.dp),
							horizontalArrangement = Arrangement.spacedBy(8.dp)
						) {
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
									AppGridItem(
										app = app,
										onIgnore = { pkg ->
											viewModel.ignore(pkg)
											updatesViewModel.syncIgnoredFromPrefs()
										}
									)
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
				enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
					scaleIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
				exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
					scaleOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
			) {
				FloatingActionButton(
					onClick = {
						coroutineScope.launch {
							if (viewMode == "list") {
								if (isScrollingDown) {
									val lastIndex = (filteredApps.size - 1).coerceAtLeast(0)
									listState.animateScrollToItem(lastIndex)
									isScrollingDown = false
								} else {
									listState.animateScrollToItem(0)
									isScrollingDown = true
								}
							} else {
								if (isScrollingDown) {
									val lastIndex = (filteredApps.size - 1).coerceAtLeast(0)
									gridState.animateScrollToItem(lastIndex)
									isScrollingDown = false
								} else {
									gridState.animateScrollToItem(0)
									isScrollingDown = true
								}
							}
						}
					},
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

	if (showHistorySheet) {
		UpdateHistoryBottomSheet(onDismiss = { showHistorySheet = false })
	}
}

// -------------------------------------------------------------
// 1-Spalten-Listenzeile („Die Symbiose“)
// -------------------------------------------------------------
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppListItem(
	app: AppInstalled,
	repoMappingManager: RepoMappingManager = koinInject(),
	onIgnore: (String) -> Unit
) {
	val context = LocalContext.current
	val haptics = LocalHapticFeedback.current
	var showMenu by remember { mutableStateOf(false) }
	var showLinkRepoDialog by remember { mutableStateOf(false) }
	val repoBinding = remember(app.packageName, showLinkRepoDialog) {
		repoMappingManager.getBinding(app.packageName)
	}

	val interactionSource = remember { MutableInteractionSource() }
	val isPressed by interactionSource.collectIsPressedAsState()
	val scale by animateFloatAsState(
		targetValue = if (isPressed) 0.97f else 1f,
		animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy)
	)

	val cardShape = RoundedCornerShape(20.dp)
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
					showMenu = true
				},
				onLongClick = {
					haptics.performHapticFeedback(HapticFeedbackType.LongPress)
					showMenu = true
				}
			)
			.animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
		shape = cardShape,
		color = MaterialTheme.colorScheme.surfaceContainerLow
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(12.dp),
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
				Text(
					text = app.name.ifBlank { app.packageName },
					style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
					fontWeight = FontWeight.Bold,
					color = MaterialTheme.colorScheme.onSurface,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis
				)
				Text(
					text = app.packageName,
					style = MaterialTheme.typography.bodySmall.copy(
						fontSize = 11.sp,
						fontFamily = FontFamily.Monospace
					),
					color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
					maxLines = 1,
					overflow = TextOverflow.Ellipsis
				)
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(6.dp)
				) {
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

			// Right Actions: Relative Date + Quick Launch Button + 3-Dots Menu
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
					// 32dp Quick Launch Button
					Surface(
						onClick = { launchAppOrSettings(context, app.packageName) },
						shape = CircleShape,
						color = MaterialTheme.colorScheme.surfaceContainerHigh,
						modifier = Modifier.size(32.dp)
					) {
						Box(contentAlignment = Alignment.Center) {
							Icon(
								imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
								contentDescription = stringResource(R.string.apps_launch_action),
								tint = MaterialTheme.colorScheme.primary,
								modifier = Modifier.size(16.dp)
							)
						}
					}

					// 28dp Context Menu Trigger
					Box {
						Surface(
							onClick = { showMenu = true },
							shape = CircleShape,
							color = Color.Transparent,
							modifier = Modifier.size(28.dp)
						) {
							Box(contentAlignment = Alignment.Center) {
								Icon(
									imageVector = Icons.Rounded.MoreVert,
									contentDescription = null,
									tint = MaterialTheme.colorScheme.onSurfaceVariant,
									modifier = Modifier.size(18.dp)
								)
							}
						}

						AppItemDropdownMenu(
							expanded = showMenu,
							onDismiss = { showMenu = false },
							app = app,
							repoBinding = repoBinding,
							onOpenLinkRepoDialog = { showLinkRepoDialog = true },
							onIgnore = onIgnore
						)
					}
				}
			}
		}
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
	repoMappingManager: RepoMappingManager = koinInject(),
	onIgnore: (String) -> Unit
) {
	val context = LocalContext.current
	val haptics = LocalHapticFeedback.current
	var showMenu by remember { mutableStateOf(false) }
	var showLinkRepoDialog by remember { mutableStateOf(false) }
	val repoBinding = remember(app.packageName, showLinkRepoDialog) {
		repoMappingManager.getBinding(app.packageName)
	}

	val interactionSource = remember { MutableInteractionSource() }
	val isPressed by interactionSource.collectIsPressedAsState()
	val scale by animateFloatAsState(
		targetValue = if (isPressed) 0.97f else 1f,
		animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy)
	)

	val cardShape = RoundedCornerShape(18.dp)
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
					showMenu = true
				},
				onLongClick = {
					haptics.performHapticFeedback(HapticFeedbackType.LongPress)
					showMenu = true
				}
			)
			.animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
		shape = cardShape,
		color = MaterialTheme.colorScheme.surfaceContainerLow
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(12.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp)
		) {
			// Top Row: 44dp App Icon + Launch Button + Menu
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween,
				verticalAlignment = Alignment.Top
			) {
				Box(
					modifier = Modifier
						.size(44.dp)
						.clip(RoundedCornerShape(12.dp))
						.background(MaterialTheme.colorScheme.surfaceContainerHigh),
					contentAlignment = Alignment.Center
				) {
					LoadingImageApp(app.packageName, Modifier.fillMaxSize())
				}
				Row(
					horizontalArrangement = Arrangement.spacedBy(2.dp),
					verticalAlignment = Alignment.CenterVertically
				) {
					Surface(
						onClick = { launchAppOrSettings(context, app.packageName) },
						shape = CircleShape,
						color = MaterialTheme.colorScheme.surfaceContainerHigh,
						modifier = Modifier.size(30.dp)
					) {
						Box(contentAlignment = Alignment.Center) {
							Icon(
								imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
								contentDescription = stringResource(R.string.apps_launch_action),
								tint = MaterialTheme.colorScheme.primary,
								modifier = Modifier.size(15.dp)
							)
						}
					}
					Box {
						Surface(
							onClick = { showMenu = true },
							shape = CircleShape,
							color = Color.Transparent,
							modifier = Modifier.size(28.dp)
						) {
							Box(contentAlignment = Alignment.Center) {
								Icon(
									imageVector = Icons.Rounded.MoreVert,
									contentDescription = null,
									tint = MaterialTheme.colorScheme.onSurfaceVariant,
									modifier = Modifier.size(18.dp)
								)
							}
						}
						AppItemDropdownMenu(
							expanded = showMenu,
							onDismiss = { showMenu = false },
							app = app,
							repoBinding = repoBinding,
							onOpenLinkRepoDialog = { showLinkRepoDialog = true },
							onIgnore = onIgnore
						)
					}
				}
			}

			// App Name & Package Name
			Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
				Text(
					text = app.name.ifBlank { app.packageName },
					style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.5.sp),
					fontWeight = FontWeight.Bold,
					color = MaterialTheme.colorScheme.onSurface,
					maxLines = 2,
					minLines = 2,
					overflow = TextOverflow.Ellipsis
				)
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

	if (showLinkRepoDialog) {
		LinkRepoDialog(
			packageName = app.packageName,
			appName = app.name.ifBlank { app.packageName },
			onDismiss = { showLinkRepoDialog = false }
		)
	}
}

// -------------------------------------------------------------
// Shared Dropdown Menu for Context Actions
// -------------------------------------------------------------
@Composable
private fun AppItemDropdownMenu(
	expanded: Boolean,
	onDismiss: () -> Unit,
	app: AppInstalled,
	repoBinding: RepoBinding?,
	onOpenLinkRepoDialog: () -> Unit,
	onIgnore: (String) -> Unit
) {
	val context = LocalContext.current
	DropdownMenu(
		expanded = expanded,
		onDismissRequest = onDismiss,
		shape = RoundedCornerShape(24.dp),
		containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
	) {
		// 1. App Info in Android Settings
		DropdownMenuItem(
			text = { Text(stringResource(R.string.apps_context_details)) },
			leadingIcon = {
				Icon(
					imageVector = Icons.Filled.Info,
					contentDescription = null,
					tint = MaterialTheme.colorScheme.primary
				)
			},
			onClick = {
				onDismiss()
				context.openAppSystemSettings(app.packageName)
			}
		)

		// 2. Play Store or Repo link
		if (repoBinding != null) {
			DropdownMenuItem(
				text = {
					Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
						Text(
							stringResource(
								if (repoBinding.isGitLab) R.string.repo_view_on_gitlab
								else R.string.repo_view_on_github
							)
						)
						Text(
							text = repoBinding.webUrl,
							style = MaterialTheme.typography.labelSmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
							maxLines = 1,
							overflow = TextOverflow.Ellipsis
						)
					}
				},
				leadingIcon = {
					Icon(
						painter = painterResource(
							if (repoBinding.isGitLab) R.drawable.ic_gitlab else R.drawable.ic_github
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
		} else {
			val isPlayStore = app.installOrigin == InstallOrigin.PLAY_STORE
			DropdownMenuItem(
				enabled = isPlayStore,
				text = { Text(stringResource(R.string.apps_context_store)) },
				leadingIcon = {
					Icon(
						imageVector = Icons.Outlined.PlayArrow,
						contentDescription = null,
						tint = if (isPlayStore) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
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

		// 3. Link Repository
		DropdownMenuItem(
			text = {
				Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
					Text(
						stringResource(
							if (repoBinding != null) R.string.repo_menu_linked
							else R.string.repo_menu_link
						)
					)
					if (repoBinding != null) {
						Text(
							text = "${if (repoBinding.isGitLab) "GitLab" else "GitHub"}: ${repoBinding.repo}",
							style = MaterialTheme.typography.labelSmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
							maxLines = 1,
							overflow = TextOverflow.Ellipsis
						)
					}
				}
			},
			leadingIcon = {
				Icon(
					painter = painterResource(
						if (repoBinding?.isGitLab == true) R.drawable.ic_gitlab else R.drawable.ic_github
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

		// 4. Ignore / Unignore App
		DropdownMenuItem(
			text = {
				Text(
					stringResource(
						if (app.ignored) R.string.apps_context_unignore
						else R.string.apps_context_ignore
					)
				)
			},
			leadingIcon = {
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
}

private fun launchAppOrSettings(context: Context, packageName: String) {
	val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
	if (launchIntent != null) {
		runCatching { context.startActivity(launchIntent) }
	} else {
		runCatching {
			val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
				data = Uri.parse("package:$packageName")
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
@Composable
private fun AppsQuickFilterRow(
	selectedFilter: AppsQuickFilter,
	onSelectFilter: (AppsQuickFilter) -> Unit
) {
	val filters = listOf(
		AppsQuickFilter.ALL to stringResource(R.string.apps_filter_all),
		AppsQuickFilter.RECENTLY_UPDATED to stringResource(R.string.apps_filter_recent),
		AppsQuickFilter.IGNORED_ONLY to stringResource(R.string.apps_filter_ignored)
	)
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.horizontalScroll(rememberScrollState())
			.padding(horizontal = 14.dp, vertical = 2.dp),
		horizontalArrangement = Arrangement.spacedBy(8.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		filters.forEach { (filter, label) ->
			val selected = selectedFilter == filter
			FilterChip(
				selected = selected,
				onClick = { onSelectFilter(filter) },
				label = {
					Text(
						text = label,
						style = MaterialTheme.typography.labelMedium,
						fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
					)
				},
				leadingIcon = if (selected) {
					{
						Icon(
							imageVector = Icons.Filled.Check,
							contentDescription = null,
							modifier = Modifier.size(FilterChipDefaults.IconSize)
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
fun AppsScreenLoading() = Column(Modifier.fillMaxSize()) {
	AppsTopBar()
	LoadingGrid()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsTopBar(
	totalCount: Int? = null,
	searchExpanded: Boolean = false,
	onToggleSearch: (() -> Unit)? = null
) = TopAppBar(
	title = {
		Row(
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(8.dp)
		) {
			Text(
				text = stringResource(R.string.tab_apps),
				style = MaterialTheme.typography.headlineMedium,
				fontWeight = FontWeight.Bold
			)
			if (totalCount != null) {
				Surface(
					shape = CircleShape,
					color = MaterialTheme.colorScheme.secondaryContainer
				) {
					Text(
						text = totalCount.toString(),
						modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
						style = MaterialTheme.typography.labelMedium,
						fontWeight = FontWeight.SemiBold,
						color = MaterialTheme.colorScheme.onSecondaryContainer
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
		if (onToggleSearch != null) {
			IconButton(onClick = onToggleSearch) {
				Icon(
					imageVector = if (searchExpanded) Icons.Filled.Clear else Icons.Filled.Search,
					contentDescription = stringResource(R.string.apps_search_placeholder)
				)
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
				contentDescription = stringResource(R.string.tab_apps),
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
