package com.apkup.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apkup.R
import com.apkup.data.ui.AppsQuickFilter
import com.apkup.data.ui.AppsUiState
import com.apkup.data.ui.filterInstalledApps
import com.apkup.ui.component.AppEmptyState
import com.apkup.ui.component.InstalledGrid
import com.apkup.ui.component.InstalledItem
import com.apkup.ui.component.LoadingGrid
import com.apkup.viewmodel.AppsViewModel
import com.apkup.viewmodel.UpdatesViewModel
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel


@Composable
fun AppsScreen(
	viewModel: AppsViewModel = koinViewModel()
) {
	viewModel.state().collectAsStateWithLifecycle().value.onLoading {
		AppsScreenLoading()
	}.onError {
		AppsScreenError()
	}.onSuccess {
		AppsScreenSuccess(viewModel, it)
	}
}

@Composable
fun AppsScreenSuccess(viewModel: AppsViewModel, state: AppsUiState.Success) = Column(Modifier.fillMaxSize()) {
	val updatesViewModel: UpdatesViewModel = koinViewModel()
	val searchQuery by viewModel.searchQuery().collectAsStateWithLifecycle()
	val quickFilter by viewModel.quickFilter().collectAsStateWithLifecycle()
	var searchExpanded by rememberSaveable { mutableStateOf(false) }

	val filteredApps = remember(state.apps, searchQuery, quickFilter) {
		filterInstalledApps(
			apps = state.apps,
			searchQuery = searchQuery,
			quickFilter = quickFilter
		)
	}

	val gridState = rememberLazyGridState()
	val coroutineScope = rememberCoroutineScope()
	var isScrollingDown by remember { mutableStateOf(true) }
	var prevIndex by remember { mutableIntStateOf(0) }
	var prevOffset by remember { mutableIntStateOf(0) }

	LaunchedEffect(gridState) {
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

	val showFastScrollFab by remember(filteredApps.size) {
		derivedStateOf {
			filteredApps.size > 12 &&
				(gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 20)
		}
	}

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

	AppsQuickFilterRow(
		selectedFilter = quickFilter,
		onSelectFilter = { viewModel.setQuickFilter(it) }
	)

	if (filteredApps.isEmpty()) {
		AppEmptyState(
			title = stringResource(R.string.apps_search_empty_title),
			message = stringResource(R.string.apps_search_empty_message),
			icon = Icons.Filled.Search,
			modifier = Modifier.weight(1f)
		)
	} else {
		Box(Modifier.weight(1f)) {
			InstalledGrid(state = gridState) {
				items(filteredApps, key = { it.packageName }) { app ->
					InstalledItem(app) { packageName ->
						viewModel.ignore(packageName)
						updatesViewModel.syncIgnoredFromPrefs()
					}
				}
			}

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
							if (isScrollingDown) {
								val lastIndex = (filteredApps.size - 1).coerceAtLeast(0)
								if (lastIndex - gridState.firstVisibleItemIndex > 30) {
									gridState.scrollToItem((lastIndex - 18).coerceAtLeast(0))
								}
								gridState.animateScrollToItem(lastIndex)
								isScrollingDown = false
							} else {
								if (gridState.firstVisibleItemIndex > 30) {
									gridState.scrollToItem(18)
								}
								gridState.animateScrollToItem(0)
								isScrollingDown = true
							}
						}
					},
					shape = RoundedCornerShape(18.dp),
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

@Composable
private fun DockedAppsSearchBar(
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
			.padding(horizontal = 12.dp, vertical = 4.dp)
			.height(52.dp)
			.clip(RoundedCornerShape(26.dp)),
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
		},
		singleLine = true,
		textStyle = MaterialTheme.typography.bodyMedium,
		keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
		keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
		shape = RoundedCornerShape(26.dp),
		colors = TextFieldDefaults.colors(
			focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
			unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
			focusedIndicatorColor = Color.Transparent,
			unfocusedIndicatorColor = Color.Transparent,
			disabledIndicatorColor = Color.Transparent
		)
	)
}

@Composable
private fun AppsQuickFilterRow(
	selectedFilter: AppsQuickFilter,
	onSelectFilter: (AppsQuickFilter) -> Unit
) {
	val filters = listOf(
		AppsQuickFilter.ALL to stringResource(R.string.apps_filter_all),
		AppsQuickFilter.USER_ONLY to stringResource(R.string.apps_filter_user),
		AppsQuickFilter.IGNORED_ONLY to stringResource(R.string.apps_filter_ignored),
		AppsQuickFilter.RECENTLY_UPDATED to stringResource(R.string.apps_filter_recent)
	)
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.horizontalScroll(rememberScrollState())
			.padding(horizontal = 10.dp, vertical = 2.dp),
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
				shape = RoundedCornerShape(14.dp),
				colors = FilterChipDefaults.filterChipColors(
					selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
					selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
					selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer
				)
			)
		}
	}
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
					shape = RoundedCornerShape(12.dp),
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
