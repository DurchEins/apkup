package com.apkup.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import com.apkup.ui.theme.ApkUpTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.apkup.R
import com.apkup.data.ui.SourcePriorityKeys

@Composable
fun sourcePriorityLabel(key: String): String = when (key) {
    SourcePriorityKeys.PLAY -> stringResource(R.string.source_play)
    SourcePriorityKeys.GITHUB -> stringResource(R.string.source_github)
    SourcePriorityKeys.CODEBERG -> stringResource(R.string.source_codeberg)
    SourcePriorityKeys.FDROID -> stringResource(R.string.source_fdroid_izzyondroid)
    SourcePriorityKeys.CUSTOM_FDROID -> stringResource(R.string.source_custom_fdroid)
    SourcePriorityKeys.GITLAB -> stringResource(R.string.source_gitlab)
    SourcePriorityKeys.APKMIRROR -> stringResource(R.string.source_apkmirror)
    else -> key
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcePriorityBottomSheet(
    order: List<String>,
    onMove: (index: Int, delta: Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberExpandedModalSheetState()
    val isAccessibleSheet = ApkUpTheme.isAccessibleUi
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = if (isAccessibleSheet) null else {
            { androidx.compose.material3.BottomSheetDefaults.DragHandle() }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.settings_source_priority_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(onClick = onReset) {
                    Icon(
                        imageVector = Icons.Rounded.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.settings_source_priority_reset))
                }
            }

            Text(
                text = stringResource(R.string.settings_source_priority_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val haptics = LocalHapticFeedback.current
            val spacingPx = with(LocalDensity.current) { 8.dp.toPx() }
            val currentOrder by rememberUpdatedState(order)
            var draggingKey by remember { mutableStateOf<String?>(null) }
            var dragOffsetY by remember { mutableFloatStateOf(0f) }
            val itemHeights = remember { mutableStateMapOf<String, Int>() }

            val isAccessibleUi = com.apkup.ui.theme.ApkUpTheme.isAccessibleUi
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                order.forEachIndexed { index, key ->
                    key(key) {
                        val isDragging = !isAccessibleUi && draggingKey == key
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isDragging) {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            shadowElevation = if (isDragging) 8.dp else 0.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(if (isDragging) 1f else 0f)
                                .onSizeChanged { itemHeights[key] = it.height }
                                .graphicsLayer {
                                    translationY = if (isDragging) dragOffsetY else 0f
                                    scaleX = if (isDragging) 1.02f else 1f
                                    scaleY = if (isDragging) 1.02f else 1f
                                }
                                .let { mod ->
                                    if (isAccessibleUi) {
                                        mod
                                    } else {
                                        mod.pointerInput(key) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    draggingKey = key
                                                    dragOffsetY = 0f
                                                },
                                                onDragEnd = {
                                                    draggingKey = null
                                                    dragOffsetY = 0f
                                                },
                                                onDragCancel = {
                                                    draggingKey = null
                                                    dragOffsetY = 0f
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    dragOffsetY += dragAmount.y
                                                    val latest = currentOrder
                                                    val curIdx = latest.indexOf(key)
                                                    if (curIdx < 0) return@detectDragGesturesAfterLongPress
                                                    val curHeight = (itemHeights[key] ?: 120).toFloat()
                                                    if (dragOffsetY > (curHeight + spacingPx) / 2f && curIdx < latest.lastIndex) {
                                                        val nextHeight = (itemHeights[latest[curIdx + 1]] ?: curHeight.toInt()).toFloat()
                                                        onMove(curIdx, 1)
                                                        dragOffsetY -= (nextHeight + spacingPx)
                                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    } else if (dragOffsetY < -(curHeight + spacingPx) / 2f && curIdx > 0) {
                                                        val prevHeight = (itemHeights[latest[curIdx - 1]] ?: curHeight.toInt()).toFloat()
                                                        onMove(curIdx, -1)
                                                        dragOffsetY += (prevHeight + spacingPx)
                                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (!isAccessibleUi) {
                                        Icon(
                                            imageVector = Icons.Rounded.DragIndicator,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${index + 1}",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Icon(
                                        painter = painterResource(SourcePriorityKeys.iconForKey(key)),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    val sourceTitle = sourcePriorityLabel(key)
                                    Text(
                                        text = sourceTitle,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = if (isAccessibleUi) 2 else 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (!isAccessibleUi) {
                                        IconButton(
                                            onClick = { onMove(index, -1) },
                                            enabled = index > 0,
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.ArrowUpward,
                                                contentDescription = "${stringResource(R.string.settings_source_priority_move_up)}: $sourceTitle",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { onMove(index, 1) },
                                            enabled = index < order.lastIndex,
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.ArrowDownward,
                                                contentDescription = "${stringResource(R.string.settings_source_priority_move_down)}: $sourceTitle",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                                if (isAccessibleUi) {
                                    val sourceTitle = sourcePriorityLabel(key)
                                    val moveUpCd = "${stringResource(R.string.settings_source_priority_move_up)}: $sourceTitle"
                                    val moveDownCd = "${stringResource(R.string.settings_source_priority_move_down)}: $sourceTitle"
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        androidx.compose.material3.OutlinedButton(
                                            onClick = { onMove(index, -1) },
                                            enabled = index > 0,
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp)
                                                .semantics {
                                                    contentDescription = moveUpCd
                                                }
                                        ) {
                                            Text(stringResource(R.string.a11y_move_up))
                                        }
                                        androidx.compose.material3.OutlinedButton(
                                            onClick = { onMove(index, 1) },
                                            enabled = index < order.lastIndex,
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp)
                                                .semantics {
                                                    contentDescription = moveDownCd
                                                }
                                        ) {
                                            Text(stringResource(R.string.a11y_move_down))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (ApkUpTheme.isAccessibleUi) {
                androidx.compose.material3.OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.a11y_close_action))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinAppSourceBottomSheet(
    appName: String,
    currentPinnedKey: String?,
    onSelectSourceKey: (String?) -> Unit,
    onDismiss: () -> Unit,
    currentPreReleaseOverride: String = com.apkup.data.ui.PreReleaseOverride.DEFAULT,
    onSelectPreReleaseOverride: ((String) -> Unit)? = null
) {
    val sheetState = rememberExpandedModalSheetState()
    var selectedPreRelease by remember(currentPreReleaseOverride) { mutableStateOf(currentPreReleaseOverride) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PushPin,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.pin_source_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = stringResource(R.string.pin_source_desc, appName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onSelectPreReleaseOverride != null) {
                    Text(
                        text = stringResource(R.string.prerelease_override_section_title),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectableGroup(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val options = listOf(
                            com.apkup.data.ui.PreReleaseOverride.DEFAULT to stringResource(R.string.prerelease_override_default),
                            com.apkup.data.ui.PreReleaseOverride.ALLOW to stringResource(R.string.prerelease_override_allow),
                            com.apkup.data.ui.PreReleaseOverride.BLOCK to stringResource(R.string.prerelease_override_block)
                        )
                        options.forEach { (modeKey, modeLabel) ->
                            val isSelected = selectedPreRelease == modeKey
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .selectable(
                                        selected = isSelected,
                                        role = Role.RadioButton
                                    ) {
                                        selectedPreRelease = modeKey
                                        onSelectPreReleaseOverride(modeKey)
                                    }
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = modeLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                PinSourceOptionRow(
                    label = stringResource(R.string.pin_source_auto),
                    selected = currentPinnedKey == null,
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    onClick = {
                        onSelectSourceKey(null)
                        onDismiss()
                    }
                )

                SourcePriorityKeys.DEFAULT_ORDER.forEach { key ->
                    PinSourceOptionRow(
                        label = sourcePriorityLabel(key),
                        selected = currentPinnedKey == key,
                        leadingContent = {
                            Icon(
                                painter = painterResource(SourcePriorityKeys.iconForKey(key)),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = {
                            onSelectSourceKey(key)
                            onDismiss()
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun PinSourceOptionRow(
    label: String,
    selected: Boolean,
    leadingContent: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leadingContent()
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceHealthBottomSheet(
    onDismiss: () -> Unit
) {
    val sheetState = rememberExpandedModalSheetState()
    val entries = remember { com.apkup.util.SourceHealthTracker.snapshot() }
    val nowMillis = remember { System.currentTimeMillis() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_source_health_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.settings_source_health_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (entries.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.source_health_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    entries.forEach { entry ->
                        val cooldownSec = entry.remainingCooldownSeconds(nowMillis)
                        val statusText = when {
                            cooldownSec > 0L -> stringResource(R.string.source_health_status_cooldown, cooldownSec)
                            entry.hasRecentError -> stringResource(R.string.source_health_status_error, entry.lastErrorMessage)
                            else -> stringResource(R.string.source_health_status_ok, entry.lastFoundUpdates, entry.lastDurationMillis)
                        }
                        val statusColor = when {
                            cooldownSec > 0L -> MaterialTheme.colorScheme.tertiary
                            entry.hasRecentError -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.primary
                        }
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    painter = painterResource(resolveScanSourceIcon(entry.sourceName)),
                                    contentDescription = null,
                                    tint = statusColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.sourceName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = statusText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = statusColor,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreReleaseBottomSheet(
    enabled: Boolean,
    selectedChannels: Set<String>,
    onToggleEnabled: (Boolean) -> Unit,
    onToggleChannel: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberExpandedModalSheetState()
    val isAccessibleSheet = ApkUpTheme.isAccessibleUi
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = if (isAccessibleSheet) null else {
            { androidx.compose.material3.BottomSheetDefaults.DragHandle() }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.prerelease_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.prerelease_sheet_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .toggleable(
                            value = enabled,
                            role = Role.Switch,
                            onValueChange = onToggleEnabled
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_include_prereleases_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = enabled, onCheckedChange = null)
                }
            }

            val channels = listOf(
                Triple(
                    com.apkup.data.repomapping.ReleaseAssetFilter.PreReleaseChannel.BETA,
                    stringResource(R.string.prerelease_channel_beta),
                    stringResource(R.string.prerelease_channel_beta_desc)
                ),
                Triple(
                    com.apkup.data.repomapping.ReleaseAssetFilter.PreReleaseChannel.RC,
                    stringResource(R.string.prerelease_channel_rc),
                    stringResource(R.string.prerelease_channel_rc_desc)
                ),
                Triple(
                    com.apkup.data.repomapping.ReleaseAssetFilter.PreReleaseChannel.ALPHA,
                    stringResource(R.string.prerelease_channel_alpha),
                    stringResource(R.string.prerelease_channel_alpha_desc)
                ),
                Triple(
                    com.apkup.data.repomapping.ReleaseAssetFilter.PreReleaseChannel.NIGHTLY,
                    stringResource(R.string.prerelease_channel_nightly),
                    stringResource(R.string.prerelease_channel_nightly_desc)
                )
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                channels.forEach { (channelKey, title, desc) ->
                    val checked = channelKey in selectedChannels
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .toggleable(
                                value = checked,
                                enabled = enabled,
                                role = Role.Switch,
                                onValueChange = { onToggleChannel(channelKey, it) }
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                )
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                )
                            }
                            Switch(
                                checked = checked,
                                enabled = enabled,
                                onCheckedChange = null
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateWindowBottomSheet(
    enabled: Boolean,
    mode: String,
    startMinutes: Int,
    endMinutes: Int,
    onToggleEnabled: (Boolean) -> Unit,
    onModeChanged: (String) -> Unit,
    onStartMinutesChanged: (Int) -> Unit,
    onEndMinutesChanged: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberExpandedModalSheetState()
    val context = androidx.compose.ui.platform.LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_update_window_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.settings_update_window_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .toggleable(
                            value = enabled,
                            role = Role.Switch,
                            onValueChange = onToggleEnabled
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_update_window_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = enabled, onCheckedChange = null)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val modes = listOf(
                    com.apkup.data.ui.UpdateWindowMode.QUIET_PAUSE to stringResource(R.string.settings_update_window_mode_quiet),
                    com.apkup.data.ui.UpdateWindowMode.ALLOW_ONLY to stringResource(R.string.settings_update_window_mode_allow)
                )
                modes.forEach { (mKey, mLabel) ->
                    val selected = mode == mKey
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected && enabled) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .selectable(
                                selected = selected,
                                enabled = enabled,
                                role = Role.RadioButton
                            ) { onModeChanged(mKey) }
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = mLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = when {
                                    !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                    selected -> MaterialTheme.colorScheme.onPrimaryContainer
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val startFormatted = com.apkup.data.ui.UpdateWindowEvaluator.formatMinutesOfDay(startMinutes)
                val endFormatted = com.apkup.data.ui.UpdateWindowEvaluator.formatMinutesOfDay(endMinutes)
                OutlinedButton(
                    onClick = {
                        android.app.TimePickerDialog(
                            context,
                            { _, h, m -> onStartMinutesChanged(h * 60 + m) },
                            startMinutes / 60,
                            startMinutes % 60,
                            true
                        ).show()
                    },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(stringResource(R.string.settings_update_window_from, startFormatted))
                }
                OutlinedButton(
                    onClick = {
                        android.app.TimePickerDialog(
                            context,
                            { _, h, m -> onEndMinutesChanged(h * 60 + m) },
                            endMinutes / 60,
                            endMinutes % 60,
                            true
                        ).show()
                    },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(stringResource(R.string.settings_update_window_until, endFormatted))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
fun rollbackBudgetLabel(budgetMb: Int): String = when (budgetMb) {
    256 -> "256 MB"
    512 -> "512 MB"
    1024 -> "1 GB"
    2048 -> "2 GB"
    else -> "${budgetMb} MB"
}

@Composable
fun rollbackMaxAgeLabel(maxAgeDays: Int): String = when (maxAgeDays) {
    7 -> stringResource(R.string.rollback_ttl_7_days)
    14 -> stringResource(R.string.rollback_ttl_14_days)
    30 -> stringResource(R.string.rollback_ttl_30_days)
    else -> stringResource(R.string.rollback_ttl_never)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RollbackSettingsBottomSheet(
    enabled: Boolean,
    budgetMb: Int,
    maxAgeDays: Int,
    backupCount: Int,
    backupTotalBytes: Long,
    onToggleEnabled: (Boolean) -> Unit,
    onBudgetSelected: (Int) -> Unit,
    onMaxAgeSelected: (Int) -> Unit,
    onClearAllBackups: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberExpandedModalSheetState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val formattedSize = remember(context, backupTotalBytes) {
        android.text.format.Formatter.formatShortFileSize(context, backupTotalBytes)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_rollback_backups_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.settings_rollback_sheet_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .toggleable(
                            value = enabled,
                            role = Role.Switch,
                            onValueChange = onToggleEnabled
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_rollback_backups_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = enabled, onCheckedChange = null)
                }
            }

            Text(
                text = stringResource(R.string.settings_rollback_budget_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                com.apkup.util.RollbackBackupManager.BUDGET_OPTIONS_MB.forEach { optionMb ->
                    val selected = budgetMb == optionMb
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected && enabled) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .selectable(
                                selected = selected,
                                enabled = enabled,
                                role = Role.RadioButton
                            ) { onBudgetSelected(optionMb) }
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = rollbackBudgetLabel(optionMb),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = when {
                                    !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                    selected -> MaterialTheme.colorScheme.onPrimaryContainer
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Text(
                text = stringResource(R.string.settings_rollback_ttl_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                com.apkup.util.RollbackBackupManager.MAX_AGE_OPTIONS_DAYS.forEach { optionDays ->
                    val selected = maxAgeDays == optionDays
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected && enabled) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .selectable(
                                selected = selected,
                                enabled = enabled,
                                role = Role.RadioButton
                            ) { onMaxAgeSelected(optionDays) }
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = rollbackMaxAgeLabel(optionDays),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = when {
                                    !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                    selected -> MaterialTheme.colorScheme.onPrimaryContainer
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (backupCount > 0) {
                            stringResource(R.string.settings_rollback_usage_summary, backupCount, formattedSize)
                        } else {
                            stringResource(R.string.settings_rollback_usage_empty)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = onClearAllBackups,
                        enabled = backupCount > 0,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (backupCount > 0) {
                                MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            }
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.settings_rollback_clear_all_btn),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
