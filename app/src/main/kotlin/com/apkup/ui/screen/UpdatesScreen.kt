package com.apkup.ui.screen

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apkup.R
import com.apkup.data.ui.ApkMirrorSource
import com.apkup.data.ui.AppUpdate
import com.apkup.data.ui.GitHubSource
import com.apkup.data.ui.GitLabSource
import com.apkup.data.ui.InstallOrigin
import com.apkup.data.ui.IzzyOnDroidSource
import com.apkup.data.ui.PlaySource
import com.apkup.data.ui.Source
import com.apkup.data.ui.hasPotentialSignatureConflict
import com.apkup.prefs.Prefs
import com.apkup.ui.component.AppEmptyState
import com.apkup.ui.component.CompactUpdateCardLayout
import com.apkup.ui.component.DownloadIcon
import com.apkup.ui.component.LoadingImageApp
import com.apkup.ui.component.NormalUpdateCardLayout
import com.apkup.ui.component.RefreshIcon
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
fun UpdatesScreen(viewModel: UpdatesViewModel) {
	viewModel.state().collectAsStateWithLifecycle().value.onLoading {
		UpdatesScreenLoading(viewModel)
	}.onError {
		UpdatesScreenError(viewModel)
	}.onSuccess {
		UpdatesScreenSuccess(
			viewModel = viewModel,
			updates = it.updates,
			hasSourceErrors = it.hasSourceErrors,
			isChecking = it.isChecking,
			checkedApps = it.checkedApps,
			totalApps = it.totalApps,
			activeSourceName = it.activeSourceName,
			sourceErrors = it.sourceErrors,
			lastCheckedAtMillis = it.lastCheckedAtMillis
		)
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesTopBar(
	viewModel: UpdatesViewModel,
	updatesCount: Int? = null,
	hasInstallableUpdates: Boolean = false
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
		if (hasInstallableUpdates) {
			IconButton(onClick = { viewModel.installAll() }) {
				DownloadIcon(stringResource(R.string.install_all))
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
fun UpdatesScreenLoading(viewModel: UpdatesViewModel) = Column(Modifier.fillMaxSize()) {
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
	UpdatesTopBar(viewModel)
	UpdateCheckProgressBar(activeSourceName = initialSource)
	UpdatesLoadingList(Modifier.weight(1f))
}

@Composable
fun UpdatesScreenError(viewModel: UpdatesViewModel) = Column(Modifier.fillMaxSize()) {
	UpdatesTopBar(viewModel)
	UpdatesMessage(
		title = stringResource(R.string.updates_error_title),
		message = stringResource(R.string.updates_error_message),
		icon = Icons.Filled.Info,
		modifier = Modifier.weight(1f)
	)
}

@Composable
fun UpdatesScreenSuccess(
	viewModel: UpdatesViewModel,
	updates: List<AppUpdate>,
	hasSourceErrors: Boolean = false,
	isChecking: Boolean = false,
	checkedApps: Int? = null,
	totalApps: Int? = null,
	activeSourceName: String? = null,
	sourceErrors: Map<String, String> = emptyMap(),
	lastCheckedAtMillis: Long? = null
) = Column(Modifier.fillMaxSize()) {
	val handler = LocalUriHandler.current
	val prefs = koinInject<Prefs>()
	val displayMode = prefs.updatesDisplayMode.get()
	val view = LocalView.current
	DisposableEffect(isChecking) {
		view.keepScreenOn = isChecking
		onDispose { view.keepScreenOn = false }
	}

	var selectedSourceFilter by rememberSaveable { mutableStateOf<String?>(null) }

	val sourceCounts = remember(updates) {
		val map = linkedMapOf<Source, Int>()
		for (update in updates) {
			map[update.source] = (map[update.source] ?: 0) + 1
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
			updates.filter { it.source.name == selectedSourceFilter }
		}
	}

	val activeSources = buildList {
		if (prefs.useApkMirror.get()) add("APKMirror")
		if (prefs.usePlay.get()) add("Google Play")
	}
	val localeLanguage = LocalConfiguration.current.locales[0]?.language.orEmpty()
	val isGerman = localeLanguage.startsWith("de", ignoreCase = true)
	val hasInstallable = displayedUpdates.any {
		it.isDirectInstallable || it.alternativeUpdates.any(AppUpdate::isDirectInstallable)
	}

	UpdatesTopBar(
		viewModel = viewModel,
		updatesCount = displayedUpdates.size,
		hasInstallableUpdates = hasInstallable
	)

	if (updates.isNotEmpty() && sourceCounts.isNotEmpty()) {
		UpdatesSourceFilterRow(
			totalCount = updates.size,
			sourceCounts = sourceCounts,
			selectedSource = selectedSourceFilter,
			onSelectSource = { selectedSourceFilter = it }
		)
	}

	if (isChecking) {
		UpdateCheckProgressBar(checkedApps, totalApps, activeSourceName)
	} else if (lastCheckedAtMillis != null && lastCheckedAtMillis > 0L) {
		LastCheckedStatusBanner(lastCheckedAtMillis = lastCheckedAtMillis)
	}

	if (hasSourceErrors && updates.isNotEmpty()) {
		val bannerText = formatSourceErrorBanner(
			sourceErrors = sourceErrors,
			activeSources = activeSources,
			isGerman = isGerman
		)
		Surface(
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
					style = MaterialTheme.typography.labelMedium
				)
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
			modifier = Modifier.weight(1f)
		)
		updates.isEmpty() -> UpdatesMessage(
			title = stringResource(R.string.updates_empty_title),
			message = stringResource(R.string.updates_empty_message),
			modifier = Modifier.weight(1f)
		)
		displayedUpdates.isEmpty() -> UpdatesMessage(
			title = stringResource(R.string.updates_empty_title),
			message = stringResource(R.string.updates_empty_source_filter_message),
			modifier = Modifier.weight(1f)
		)
		displayMode == 1 -> CompactUpdatesGrid(viewModel, displayedUpdates, handler, Modifier.weight(1f))
		else -> UpdatesList(viewModel, displayedUpdates, handler, Modifier.weight(1f))
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
	CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.horizontalScroll(rememberScrollState())
				.padding(horizontal = 12.dp, vertical = 3.dp),
			horizontalArrangement = Arrangement.spacedBy(6.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			val allSelected = selectedSource == null
			FilterChip(
				selected = allSelected,
				onClick = { onSelectSource(null) },
				modifier = Modifier.height(32.dp),
				label = {
					Text(
						text = "${stringResource(R.string.apps_filter_all)} ($totalCount)",
						style = MaterialTheme.typography.labelMedium,
						fontWeight = if (allSelected) FontWeight.SemiBold else FontWeight.Medium
					)
				},
				leadingIcon = if (allSelected) {
					{
						Icon(
							imageVector = Icons.Filled.Check,
							contentDescription = null,
							modifier = Modifier.size(16.dp)
						)
					}
				} else null,
				shape = CircleShape,
				border = FilterChipDefaults.filterChipBorder(
					enabled = true,
					selected = allSelected,
					borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
				),
				colors = FilterChipDefaults.filterChipColors(
					selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
					selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
					selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer
				)
			)

			sourceCounts.forEach { (source, count) ->
				if (count > 0) {
					val isSelected = selectedSource == source.name
					val labelText = "${sourceName(source)} ($count)"
					FilterChip(
						selected = isSelected,
						onClick = { onSelectSource(if (isSelected) null else source.name) },
						modifier = Modifier.height(32.dp),
						label = {
							Text(
								text = labelText,
								style = MaterialTheme.typography.labelMedium,
								fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
							)
						},
						leadingIcon = if (isSelected) {
							{
								Icon(
									imageVector = Icons.Filled.Check,
									contentDescription = null,
									modifier = Modifier.size(16.dp)
								)
							}
						} else {
							{
								Icon(
									painter = painterResource(source.resourceId),
									contentDescription = null,
									modifier = Modifier.size(16.dp),
									tint = MaterialTheme.colorScheme.onSurfaceVariant
								)
							}
						},
						shape = CircleShape,
						border = FilterChipDefaults.filterChipBorder(
							enabled = true,
							selected = isSelected,
							borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
						),
						colors = FilterChipDefaults.filterChipColors(
							selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
							selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
							selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer
						)
					)
				}
			}
		}
	}
}

@Composable
private fun LastCheckedStatusBanner(lastCheckedAtMillis: Long) {
	val now = System.currentTimeMillis()
	val elapsedMinutes = ((now - lastCheckedAtMillis).coerceAtLeast(0L)) / 60_000L
	val statusText = when {
		elapsedMinutes < 1L -> stringResource(R.string.updates_last_checked_just_now)
		elapsedMinutes < 60L -> stringResource(R.string.updates_last_checked_minutes, elapsedMinutes.toInt())
		elapsedMinutes < 1440L -> stringResource(R.string.updates_last_checked_hours, (elapsedMinutes / 60L).toInt())
		else -> stringResource(R.string.updates_last_checked_days, (elapsedMinutes / 1440L).toInt())
	}
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 14.dp, vertical = 2.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Surface(
			shape = CircleShape,
			color = MaterialTheme.colorScheme.surfaceContainerHigh
		) {
			Text(
				text = statusText,
				modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
				style = MaterialTheme.typography.labelSmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}
	}
}

@Composable
private fun UpdateCheckProgressBar(
	checkedApps: Int? = null,
	totalApps: Int? = null,
	activeSourceName: String? = null
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
		}
	}
}

@Composable
private fun UpdatesList(
	viewModel: UpdatesViewModel,
	updates: List<AppUpdate>,
	handler: UriHandler,
	modifier: Modifier
) {
	val appsViewModel: AppsViewModel = koinViewModel()
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
				UpdateCard(
					update = update,
					onSelect = { selectedPackageName = update.packageName },
					onInstall = { viewModel.install(update, handler) }
				)
			}
		}
	}

	selectedUpdate?.let { update ->
		UpdateDetailsSheet(
			update = update,
			onDismiss = { selectedPackageName = null },
			onInstallVariant = { chosen ->
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
	onSelect: () -> Unit,
	onInstall: () -> Unit
) {
	val context = LocalContext.current
	val appName = update.name.ifBlank {
		context.getAppName(update.packageName).ifBlank { update.packageName }
	}
	val card = update.toUpdateCardContent(appName)
	val sourceName = sourceName(card.source)

	val cardShape = RoundedCornerShape(NormalUpdateCardLayout.cardCornerRadiusDp.dp)
	ElevatedCard(
		onClick = onSelect,
		modifier = Modifier
			.fillMaxWidth()
			.border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)), cardShape)
			.animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
		shape = cardShape,
		colors = CardDefaults.elevatedCardColors(
			containerColor = MaterialTheme.colorScheme.surfaceContainerLow
		),
		elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.heightIn(min = NormalUpdateCardLayout.minCardHeightDp.dp)
				.padding(
					horizontal = NormalUpdateCardLayout.cardPaddingHorizontalDp.dp,
					vertical = NormalUpdateCardLayout.cardPaddingVerticalDp.dp
				),
			verticalArrangement = Arrangement.spacedBy(NormalUpdateCardLayout.sectionSpacingDp.dp)
		) {
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(8.dp)
			) {
				Box(
					modifier = Modifier
						.size(NormalUpdateCardLayout.iconSizeDp.dp)
						.clip(RoundedCornerShape(12.dp))
						.background(MaterialTheme.colorScheme.surfaceContainerHigh),
					contentAlignment = Alignment.Center
				) {
					LoadingImageApp(
						card.packageName,
						Modifier.requiredSize((NormalUpdateCardLayout.iconSizeDp + 16f).dp)
					)
				}
				Column(
					modifier = Modifier.weight(1f),
					verticalArrangement = Arrangement.spacedBy(1.dp)
				) {
					Text(
						text = card.appName,
						style = MaterialTheme.typography.titleSmall.copy(
							lineHeight = NormalUpdateCardLayout.appNameLineHeightSp.sp
						),
						fontWeight = FontWeight.SemiBold,
						color = MaterialTheme.colorScheme.onSurface,
						minLines = 2,
						maxLines = 2,
						overflow = TextOverflow.Ellipsis
					)
					Text(
						text = card.packageName,
						style = MaterialTheme.typography.labelSmall.copy(
							fontSize = NormalUpdateCardLayout.packageIdFontSizeSp.sp,
							lineHeight = NormalUpdateCardLayout.packageIdLineHeightSp.sp
						),
						color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)
				}
			}

			Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
				Surface(
					shape = CircleShape,
					color = MaterialTheme.colorScheme.surfaceContainerHigh
				) {
					Text(
						text = "${stringResource(R.string.installed_short)} ${card.installedVersion.ifBlank { "—" }}",
						modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
						style = MaterialTheme.typography.labelSmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)
				}
				Surface(
					shape = CircleShape,
					color = MaterialTheme.colorScheme.primaryContainer
				) {
					Text(
						text = "${stringResource(R.string.available_short)} ${card.availableVersion.ifBlank { "—" }}",
						modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
						style = MaterialTheme.typography.labelSmall,
						fontWeight = FontWeight.SemiBold,
						color = MaterialTheme.colorScheme.onPrimaryContainer,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)
				}
			}

			if (card.isInstalling || update.isQueued) {
				Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
					Text(
						text = stringResource(if (update.isQueued && !card.isInstalling) R.string.queued_update else R.string.installing_update),
						style = MaterialTheme.typography.labelSmall,
						color = MaterialTheme.colorScheme.primary,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)
					if (card.isInstalling && update.total > 0L) {
						LinearProgressIndicator(
							progress = { (update.progress.toFloat() / update.total).coerceIn(0f, 1f) },
							modifier = Modifier.fillMaxWidth().height(4.dp),
							color = MaterialTheme.colorScheme.primary,
							trackColor = MaterialTheme.colorScheme.secondaryContainer
						)
					} else {
						LinearProgressIndicator(
							modifier = Modifier.fillMaxWidth().height(4.dp),
							color = MaterialTheme.colorScheme.primary,
							trackColor = MaterialTheme.colorScheme.secondaryContainer
						)
					}
				}
			}

			Spacer(modifier = Modifier.weight(1f))

			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.SpaceBetween
			) {
				Row(
					modifier = Modifier.weight(1f, fill = false).padding(end = 6.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(4.dp)
				) {
					Icon(
						painter = painterResource(update.source.resourceId),
						contentDescription = null,
						tint = MaterialTheme.colorScheme.onSurfaceVariant,
						modifier = Modifier.size(NormalUpdateCardLayout.sourceIconSizeDp.dp)
					)
					Text(
						text = sourceName,
						modifier = Modifier.weight(1f, fill = false),
						style = MaterialTheme.typography.labelSmall,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
					if (card.additionalSourcesCount > 0) {
						Text(
							text = "+${card.additionalSourcesCount}",
							style = MaterialTheme.typography.labelSmall,
							fontWeight = FontWeight.SemiBold,
							maxLines = 1,
							color = MaterialTheme.colorScheme.primary
						)
					}
				}
				if (card.canInstall && !update.isQueued) {
					CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
						FilledTonalButton(
							onClick = onInstall,
							modifier = Modifier.height(NormalUpdateCardLayout.actionButtonHeightDp.dp),
							shape = CircleShape,
							contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
						) {
							Icon(
								painter = painterResource(R.drawable.ic_install),
								contentDescription = null,
								modifier = Modifier.size(NormalUpdateCardLayout.actionIconSizeDp.dp)
							)
							Spacer(Modifier.width(4.dp))
							Text(
								text = stringResource(R.string.update_action),
								style = MaterialTheme.typography.labelSmall,
								fontWeight = FontWeight.SemiBold,
								maxLines = 1
							)
						}
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
	updates: List<AppUpdate>,
	handler: UriHandler,
	modifier: Modifier
) {
	val appsViewModel: AppsViewModel = koinViewModel()
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
				val cardShape = RoundedCornerShape(CompactUpdateCardLayout.cardCornerRadiusDp.dp)
				ElevatedCard(
					onClick = { selectedPackageName = update.packageName },
					modifier = Modifier
						.fillMaxWidth()
						.heightIn(min = CompactUpdateCardLayout.minCardHeightDp.dp)
						.border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)), cardShape)
						.animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
					shape = cardShape,
					colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
					elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
				) {
					Column(
						modifier = Modifier
							.fillMaxWidth()
							.padding(
								start = CompactUpdateCardLayout.horizontalPaddingDp.dp,
								end = CompactUpdateCardLayout.horizontalPaddingDp.dp,
								top = 12.dp,
								bottom = CompactUpdateCardLayout.verticalPaddingDp.dp
							),
						verticalArrangement = Arrangement.spacedBy(CompactUpdateCardLayout.itemSpacingDp.dp)
					) {
						Box(
							modifier = Modifier
								.fillMaxWidth()
								.padding(bottom = 8.dp),
							contentAlignment = Alignment.Center
						) {
							Box(
								modifier = Modifier
									.size(CompactUpdateCardLayout.iconSizeDp.dp)
									.clip(RoundedCornerShape(10.dp))
									.background(MaterialTheme.colorScheme.surfaceContainerHigh),
								contentAlignment = Alignment.Center
							) {
								LoadingImageApp(
									card.packageName,
									Modifier.requiredSize(CompactUpdateCardLayout.iconRequestSizeDp.dp)
								)
							}
						}
						Text(
							text = card.appName,
							style = MaterialTheme.typography.labelMedium.copy(
								fontSize = CompactUpdateCardLayout.appNameFontSizeSp.sp,
								lineHeight = CompactUpdateCardLayout.appNameLineHeightDp.sp
							),
							fontWeight = FontWeight.SemiBold,
							color = MaterialTheme.colorScheme.onSurface,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis
						)
						Column(
							Modifier.fillMaxWidth(),
							verticalArrangement = Arrangement.spacedBy(CompactUpdateCardLayout.versionRowSpacingDp.dp)
						) {
							Text(
								text = card.installedVersion.ifBlank { "—" },
								style = MaterialTheme.typography.labelSmall.copy(
									fontSize = CompactUpdateCardLayout.metaFontSizeSp.sp,
									lineHeight = CompactUpdateCardLayout.metaLineHeightDp.sp
								),
								color = MaterialTheme.colorScheme.onSurfaceVariant,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis
							)
							Text(
								text = "→ ${card.availableVersion.ifBlank { "—" }}",
								style = MaterialTheme.typography.labelSmall.copy(
									fontSize = CompactUpdateCardLayout.metaFontSizeSp.sp,
									lineHeight = CompactUpdateCardLayout.metaLineHeightDp.sp
								),
								fontWeight = FontWeight.SemiBold,
								color = MaterialTheme.colorScheme.primary,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis
							)
						}
						Row(
							modifier = Modifier.fillMaxWidth(),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(2.dp)
						) {
							val resolvedSourceLabel = sourceName(card.source)
							Icon(
								painter = painterResource(card.source.resourceId),
								contentDescription = card.formatSourceWithCount(resolvedSourceLabel),
								tint = MaterialTheme.colorScheme.onSurfaceVariant,
								modifier = Modifier.size(CompactUpdateCardLayout.sourceIconSizeDp.dp)
							)
							Text(
								text = resolvedSourceLabel,
								modifier = Modifier.weight(1f, fill = false),
								style = MaterialTheme.typography.labelSmall.copy(
									fontSize = CompactUpdateCardLayout.metaFontSizeSp.sp,
									lineHeight = CompactUpdateCardLayout.metaLineHeightDp.sp
								),
								color = MaterialTheme.colorScheme.onSurfaceVariant,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis
							)
							if (card.additionalSourcesCount > 0) {
								Text(
									text = "+${card.additionalSourcesCount}",
									style = MaterialTheme.typography.labelSmall.copy(
										fontSize = CompactUpdateCardLayout.metaFontSizeSp.sp,
										lineHeight = CompactUpdateCardLayout.metaLineHeightDp.sp
									),
									fontWeight = FontWeight.SemiBold,
									color = MaterialTheme.colorScheme.primary,
									maxLines = 1
								)
							}
						}
						if (card.isInstalling || update.isQueued) {
							if (card.isInstalling && update.total > 0L) {
								LinearProgressIndicator(
									progress = { (update.progress.toFloat() / update.total).coerceIn(0f, 1f) },
									modifier = Modifier.fillMaxWidth().height(CompactUpdateCardLayout.progressHeightDp.dp),
									color = MaterialTheme.colorScheme.primary,
									trackColor = MaterialTheme.colorScheme.secondaryContainer
								)
							} else {
								LinearProgressIndicator(
									modifier = Modifier.fillMaxWidth().height(CompactUpdateCardLayout.progressHeightDp.dp)
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
			onDismiss = { selectedPackageName = null },
			onInstallVariant = { chosen ->
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
	onDismiss: () -> Unit,
	onInstallVariant: (AppUpdate) -> Unit,
	onIgnoreVersion: (Int) -> Unit,
	onIgnoreAppPermanently: (String) -> Unit
) {
	val context = LocalContext.current
	val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

	val sourceVariants = remember(update.packageName, update.alternativeUpdates.size) {
		update.alternativeUpdates.takeIf { it.size > 1 } ?: listOf(update)
	}
	var selectedSourceIndex by remember(update.packageName) {
		val idx = sourceVariants.indexOfFirst { it.source == update.source }.coerceAtLeast(0)
		mutableIntStateOf(idx)
	}
	val rawActiveVariant = sourceVariants.getOrElse(selectedSourceIndex) { update }
	// Mirror live installation, queue, and byte progress state from the parent update item
	val activeVariant = rawActiveVariant.copy(
		isInstalling = update.isInstalling,
		isQueued = update.isQueued,
		progress = update.progress,
		total = update.total
	)

	val appName = activeVariant.name.ifBlank {
		context.getAppName(activeVariant.packageName).ifBlank { activeVariant.packageName }
	}
	val card = activeVariant.toUpdateCardContent(appName)
	val originText = installOriginLabel(activeVariant.installOrigin)
	val showSignatureWarning = hasPotentialSignatureConflict(activeVariant.installOrigin, activeVariant.source)
	val isUpdateBusy = update.isInstalling || update.isQueued

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
								border = androidx.compose.foundation.BorderStroke(
									width = 1.dp,
									color = if (isSelected) {
										MaterialTheme.colorScheme.primary
									} else {
										MaterialTheme.colorScheme.outlineVariant
									}
								)
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
							color = MaterialTheme.colorScheme.onTertiaryContainer
						)
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
								text = stringResource(
									if (update.isQueued && !update.isInstalling) R.string.queued_update else R.string.installing_update
								),
								style = MaterialTheme.typography.labelLarge,
								fontWeight = FontWeight.SemiBold,
								color = MaterialTheme.colorScheme.primary
							)
							if (update.isInstalling && update.total > 0L) {
								val fraction = (update.progress.toFloat() / update.total).coerceIn(0f, 1f)
								val pct = (fraction * 100).toInt()
								Text(
									text = "$pct% (${formatSizeMegabytes(update.progress)} / ${formatSizeMegabytes(update.total)})",
									style = MaterialTheme.typography.labelMedium,
									color = MaterialTheme.colorScheme.onSurfaceVariant
								)
							}
						}
						if (update.isInstalling && update.total > 0L) {
							LinearProgressIndicator(
								progress = { (update.progress.toFloat() / update.total).coerceIn(0f, 1f) },
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
						update.isQueued && !update.isInstalling -> stringResource(R.string.queued_update)
						update.isInstalling -> stringResource(R.string.installing_update)
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
	icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Rounded.Update
) = AppEmptyState(title, message, icon, modifier)

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
