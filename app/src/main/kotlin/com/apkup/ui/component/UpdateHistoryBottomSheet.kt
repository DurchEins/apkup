package com.apkup.ui.component

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.apkup.ui.component.rememberExpandedModalSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.core.net.toUri
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apkup.R
import com.apkup.ui.theme.ApkUpTheme
import com.apkup.data.ui.ApkMirrorSource
import com.apkup.data.ui.CodebergSource
import com.apkup.data.ui.CustomFDroidSource
import com.apkup.data.ui.FDroidSource
import com.apkup.data.ui.GitHubSource
import com.apkup.data.ui.GitLabSource
import com.apkup.data.ui.IzzyOnDroidSource
import com.apkup.data.ui.PlaySource
import com.apkup.data.ui.UpdateHistoryEntry
import com.apkup.data.ui.UpdateInstallMode
import com.apkup.repository.RepoMappingManager
import com.apkup.util.UpdateHistoryFilterMode
import com.apkup.util.effectivePreviousVersionUrl
import com.apkup.util.filterUpdateHistoryEntries
import com.apkup.util.getAppName
import com.apkup.util.openSafeHttpsUri
import com.apkup.viewmodel.UpdatesViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateHistoryBottomSheet(
    onDismiss: () -> Unit,
    updatesViewModel: UpdatesViewModel = koinViewModel(),
    repoMappingManager: RepoMappingManager = koinInject()
) {
    val entries by updatesViewModel.updateHistoryFlow.collectAsStateWithLifecycle()
    val sheetState = rememberExpandedModalSheetState()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val rollbackManager = remember(context) { com.apkup.util.RollbackBackupManager(context) }
    var selectedRollbackPair by remember { mutableStateOf<Pair<String, com.apkup.util.RollbackBackupMeta>?>(null) }
    var rollbackStatusHint by remember { mutableStateOf<String?>(null) }
    var pendingExportPkg by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf(UpdateHistoryFilterMode.ALL) }
    var selectedSourceName by remember { mutableStateOf<String?>(null) }

    val availableSources = remember(entries) {
        entries.map { it.sourceName }.filter { it.isNotBlank() }.distinct()
    }
    val filteredEntries = remember(entries, searchQuery, filterMode, selectedSourceName) {
        filterUpdateHistoryEntries(
            entries = entries,
            query = searchQuery,
            filterMode = filterMode,
            selectedSourceName = selectedSourceName,
            hasBackup = { pkg -> rollbackManager.getBackupMeta(pkg) != null },
            resolveAppName = { entry ->
                entry.appName.ifBlank { context.getAppName(entry.packageName).ifBlank { entry.packageName } }
            }
        )
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
    ) { destUri ->
        val pkg = pendingExportPkg
        pendingExportPkg = null
        if (destUri != null && pkg != null) {
            val ok = rollbackManager.exportBackupToUri(pkg, destUri)
            val toastText = if (ok) {
                resources.getString(R.string.apk_save_success, destUri.lastPathSegment ?: "APK")
            } else {
                resources.getString(R.string.apk_save_error)
            }
            Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
        }
    }

    val openCustomTab = rememberCustomTabOpener()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.update_history_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (entries.isNotEmpty()) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = entries.size.toString(),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = stringResource(R.string.update_history_subtitle),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (entries.isNotEmpty()) {
                    TextButton(
                        onClick = { updatesViewModel.clearUpdateHistory() },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteSweep,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.update_history_clear),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (entries.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.update_history_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = stringResource(R.string.update_history_empty_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    placeholder = {
                        Text(
                            text = stringResource(R.string.update_history_search_hint),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.update_history_filter_reset),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else null
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = filterMode == UpdateHistoryFilterMode.ALL && selectedSourceName == null,
                        onClick = {
                            filterMode = UpdateHistoryFilterMode.ALL
                            selectedSourceName = null
                        },
                        label = { Text(stringResource(R.string.update_history_filter_all)) }
                    )
                    FilterChip(
                        selected = filterMode == UpdateHistoryFilterMode.WITH_BACKUP,
                        onClick = {
                            filterMode = if (filterMode == UpdateHistoryFilterMode.WITH_BACKUP) {
                                UpdateHistoryFilterMode.ALL
                            } else {
                                UpdateHistoryFilterMode.WITH_BACKUP
                            }
                        },
                        label = { Text(stringResource(R.string.update_history_filter_backup)) }
                    )
                    availableSources.forEach { sourceName ->
                        val (sourceLabel, _) = resolveHistorySourceDisplay(sourceName)
                        FilterChip(
                            selected = selectedSourceName.equals(sourceName, ignoreCase = true),
                            onClick = {
                                selectedSourceName = if (selectedSourceName.equals(sourceName, ignoreCase = true)) {
                                    null
                                } else {
                                    sourceName
                                }
                            },
                            label = { Text(sourceLabel) }
                        )
                    }
                }

                if (filteredEntries.isEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.update_history_filter_empty_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = stringResource(R.string.update_history_filter_empty_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            TextButton(
                                onClick = {
                                    searchQuery = ""
                                    filterMode = UpdateHistoryFilterMode.ALL
                                    selectedSourceName = null
                                }
                            ) {
                                Text(stringResource(R.string.update_history_filter_reset))
                            }
                        }
                    }
                } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(
                        items = filteredEntries,
                        key = { index, item -> "${item.packageName}_${item.installedAtMillis}_$index" }
                    ) { _, entry ->
                        val displayAppName = remember(entry.appName, entry.packageName) {
                            entry.appName.ifBlank {
                                context.getAppName(entry.packageName).ifBlank { entry.packageName }
                            }
                        }
                        val prevUrl = remember(entry) {
                            val binding = repoMappingManager.getBinding(entry.packageName)
                                ?: updatesViewModel.getCustomRepoBinding(entry.packageName)
                            entry.effectivePreviousVersionUrl(binding)
                        }
                        val backupMeta = remember(entry.packageName, entries.size) {
                            rollbackManager.getBackupMeta(entry.packageName)
                        }
                        UpdateHistoryCard(
                            entry = entry,
                            displayAppName = displayAppName,
                            previousVersionUrl = prevUrl,
                            onOpenPreviousVersion = { url ->
                                openCustomTab(url)
                            },
                            backupMeta = backupMeta,
                            onRequestRollback = if (backupMeta != null) {
                                {
                                    rollbackStatusHint = null
                                    selectedRollbackPair = displayAppName to backupMeta
                                }
                            } else null,
                            onExportBackup = if (backupMeta != null) {
                                {
                                    pendingExportPkg = entry.packageName
                                    val ext = if (backupMeta.partCount > 1) "apks" else "apk"
                                    exportBackupLauncher.launch("${entry.packageName}-v${backupMeta.versionName}.$ext")
                                }
                            } else null
                        )
                    }
                }
                }
            }
        }
    }

    selectedRollbackPair?.let { (appLabel, meta) ->
        val isRootActive = updatesViewModel.isRootReady()
        val currentVersionLabel = remember(entries, meta.packageName) {
            entries.firstOrNull { it.packageName == meta.packageName }?.newVersion.orEmpty()
        }
        val isReducedMotion = ApkUpTheme.isReducedMotion
        val timeTravelProgress = remember(meta.packageName) { Animatable(if (isReducedMotion) 1f else 0f) }
        LaunchedEffect(meta.packageName, isReducedMotion) {
            if (!isReducedMotion) {
                timeTravelProgress.snapTo(0f)
                timeTravelProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
                )
            }
        }
        AlertDialog(
            onDismissRequest = { selectedRollbackPair = null },
            title = {
                Text(
                    text = stringResource(R.string.rollback_dialog_title, appLabel, meta.versionName),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (currentVersionLabel.isNotBlank()) {
                                Text(
                                    text = "v$currentVersionLabel",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(16.dp)
                                    .graphicsLayer {
                                        val p = timeTravelProgress.value
                                        rotationZ = if (isReducedMotion) 0f else (1f - p) * 180f
                                        translationX = if (isReducedMotion) 0f else (1f - p) * 10.dp.toPx()
                                    }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "v${meta.versionName}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .graphicsLayer {
                                        val p = timeTravelProgress.value
                                        alpha = if (isReducedMotion) 1f else 0.4f + 0.6f * p
                                    }
                            )
                        }
                    }
                    Text(
                        text = if (isRootActive) {
                            stringResource(R.string.rollback_dialog_root_desc)
                        } else {
                            stringResource(R.string.rollback_dialog_non_root_desc, meta.versionName)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    rollbackStatusHint?.let { hint ->
                        Text(
                            text = hint,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (isRootActive) {
                        Button(
                            onClick = {
                                val target = rollbackManager.getBackupTargetFileOrDir(meta.packageName) ?: return@Button
                                scope.launch {
                                    val res = updatesViewModel.executeRollback(meta.packageName, target, cleanReinstallWithRoot = false)
                                    if (res.success) {
                                        Toast.makeText(
                                            context,
                                            resources.getString(R.string.rollback_success_snack, appLabel, meta.versionName),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        selectedRollbackPair = null
                                    } else {
                                        rollbackStatusHint = resources.getString(R.string.rollback_failed_root_hint)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.rollback_btn_root_inplace))
                        }
                        OutlinedButton(
                            onClick = {
                                val target = rollbackManager.getBackupTargetFileOrDir(meta.packageName) ?: return@OutlinedButton
                                scope.launch {
                                    val res = updatesViewModel.executeRollback(meta.packageName, target, cleanReinstallWithRoot = true)
                                    if (res.success) {
                                        Toast.makeText(
                                            context,
                                            resources.getString(R.string.rollback_success_snack, appLabel, meta.versionName),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        selectedRollbackPair = null
                                    } else {
                                        rollbackStatusHint = resources.getString(R.string.rollback_failed_generic)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.rollback_btn_root_clean))
                        }
                    } else {
                        FilledTonalButton(
                            onClick = {
                                val uninstallIntent = Intent(Intent.ACTION_DELETE, "package:${meta.packageName}".toUri()).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                runCatching { context.startActivity(uninstallIntent) }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.rollback_btn_step1_uninstall))
                        }
                        Button(
                            onClick = {
                                val target = rollbackManager.getBackupTargetFileOrDir(meta.packageName) ?: return@Button
                                scope.launch {
                                    val res = updatesViewModel.executeRollback(meta.packageName, target, cleanReinstallWithRoot = false)
                                    if (res.success) {
                                        Toast.makeText(
                                            context,
                                            resources.getString(R.string.rollback_success_snack, appLabel, meta.versionName),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        selectedRollbackPair = null
                                    } else {
                                        rollbackStatusHint = resources.getString(R.string.rollback_failed_generic)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.rollback_btn_step2_install, meta.versionName))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedRollbackPair = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UpdateHistoryCard(
    entry: UpdateHistoryEntry,
    displayAppName: String,
    previousVersionUrl: String,
    onOpenPreviousVersion: (String) -> Unit,
    backupMeta: com.apkup.util.RollbackBackupMeta? = null,
    onRequestRollback: (() -> Unit)? = null,
    onExportBackup: (() -> Unit)? = null
) {
    val formattedTimestamp = remember(entry.installedAtMillis) {
        if (entry.installedAtMillis > 0L) {
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(Date(entry.installedAtMillis))
        } else {
            ""
        }
    }

    val (modeLabel, modeBgColor, modeTextColor) = when (entry.installMode) {
        UpdateInstallMode.AUTO_SHIZUKU -> Triple(
            stringResource(R.string.update_history_mode_auto_shizuku),
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        UpdateInstallMode.MANUAL_SHIZUKU -> Triple(
            stringResource(R.string.update_history_mode_manual_shizuku),
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        UpdateInstallMode.AUTO_ROOT -> Triple(
            stringResource(R.string.update_history_mode_auto_root),
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        UpdateInstallMode.MANUAL_ROOT -> Triple(
            stringResource(R.string.update_history_mode_manual_root),
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        UpdateInstallMode.MANUAL -> Triple(
            stringResource(R.string.update_history_mode_manual),
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    val (sourceLabel, sourceIconRes) = resolveHistorySourceDisplay(entry.sourceName)

    val versionTransition = remember(entry.oldVersion, entry.newVersion, entry.oldVersionCode, entry.newVersionCode) {
        val fromVer = entry.oldVersion.ifBlank {
            entry.oldVersionCode.takeIf { it > 0L }?.toString().orEmpty()
        }
        val toVer = entry.newVersion.ifBlank {
            entry.newVersionCode.takeIf { it > 0L }?.toString().orEmpty()
        }
        when {
            fromVer.isNotBlank() && toVer.isNotBlank() -> "$fromVer → $toVer"
            toVer.isNotBlank() -> toVer
            else -> ""
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingImageApp(entry.packageName, Modifier.requiredSize(48.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayAppName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (formattedTimestamp.isNotBlank()) {
                        Text(
                            text = formattedTimestamp,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = modeBgColor
                ) {
                    Text(
                        text = modeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = modeTextColor,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (versionTransition.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = versionTransition,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                if (sourceLabel.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            if (sourceIconRes != null) {
                                Icon(
                                    painter = painterResource(sourceIconRes),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            Text(
                                text = sourceLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            if (backupMeta != null && onRequestRollback != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = onRequestRollback,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Restore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.rollback_action_button, backupMeta.versionName),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (onExportBackup != null) {
                        Surface(
                            onClick = onExportBackup,
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = stringResource(R.string.rollback_export_button),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            if (backupMeta == null && previousVersionUrl.isNotBlank()) {
                val actionLabel = if (entry.oldVersion.isNotBlank()) {
                    stringResource(R.string.update_history_previous_version_action, entry.oldVersion)
                } else {
                    stringResource(R.string.update_history_older_versions_action)
                }
                Surface(
                    onClick = { onOpenPreviousVersion(previousVersionUrl) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Restore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = actionLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun resolveHistorySourceDisplay(sourceName: String): Pair<String, Int?> = when {
    sourceName.equals(ApkMirrorSource.name, ignoreCase = true) ->
        stringResource(R.string.source_apkmirror) to R.drawable.ic_apkmirror
    sourceName.equals(PlaySource.name, ignoreCase = true) ->
        stringResource(R.string.source_play) to R.drawable.ic_play
    sourceName.equals(IzzyOnDroidSource.name, ignoreCase = true) ->
        stringResource(R.string.source_izzyondroid) to R.drawable.ic_izzy
    sourceName.equals(FDroidSource.name, ignoreCase = true) ->
        stringResource(R.string.source_fdroid) to R.drawable.ic_izzy
    sourceName.equals(GitHubSource.name, ignoreCase = true) ->
        stringResource(R.string.source_github) to R.drawable.ic_github
    sourceName.equals(GitLabSource.name, ignoreCase = true) ->
        stringResource(R.string.source_gitlab) to R.drawable.ic_gitlab
    sourceName.equals(CodebergSource.name, ignoreCase = true) ->
        stringResource(R.string.source_codeberg) to R.drawable.ic_codeberg
    sourceName.equals(CustomFDroidSource.name, ignoreCase = true) ->
        stringResource(R.string.source_custom_fdroid) to R.drawable.ic_izzy
    else -> sourceName to null
}
