package com.apkup.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apkup.R
import com.apkup.ui.theme.ApkUpTheme

data class ScanSourceStatus(
    val name: String,
    val isScanning: Boolean,
    val updateCount: Int = 0,
    val isError: Boolean = false
)

fun resolveScanSourceIcon(name: String): Int = when {
    name.contains("apkmirror", ignoreCase = true) -> R.drawable.ic_apkmirror
    name.contains("play", ignoreCase = true) || name.contains("google", ignoreCase = true) -> R.drawable.ic_play
    name.contains("codeberg", ignoreCase = true) || name.contains("forgejo", ignoreCase = true) -> R.drawable.ic_codeberg
    name.contains("gitlab", ignoreCase = true) -> R.drawable.ic_gitlab
    name.contains("github", ignoreCase = true) -> R.drawable.ic_github
    name.contains("newpipe", ignoreCase = true) -> R.drawable.ic_newpipe
    name.contains("microg", ignoreCase = true) -> R.drawable.ic_microg
    name.contains("guardian", ignoreCase = true) -> R.drawable.ic_guardian
    name.contains("izzy", ignoreCase = true) -> R.drawable.ic_izzy
    name.contains("f-droid", ignoreCase = true) || name.contains("fdroid", ignoreCase = true) -> R.drawable.ic_fdroid
    else -> R.drawable.ic_izzy
}

fun isFDroidFamilySource(name: String): Boolean = when {
    name.contains("apkmirror", ignoreCase = true) -> false
    name.contains("play", ignoreCase = true) || name.contains("google", ignoreCase = true) -> false
    name.contains("codeberg", ignoreCase = true) || name.contains("forgejo", ignoreCase = true) -> false
    name.contains("gitlab", ignoreCase = true) -> false
    name.contains("github", ignoreCase = true) -> false
    else -> true
}

data class ScanTrayItem(
    val status: ScanSourceStatus,
    val iconRes: Int
)

fun groupScanTrayStatuses(statuses: List<ScanSourceStatus>): List<ScanTrayItem> {
    if (statuses.isEmpty()) return emptyList()
    val fdroidSources = statuses.filter { isFDroidFamilySource(it.name) }
    if (fdroidSources.size <= 1) {
        return statuses.map { ScanTrayItem(it, resolveScanSourceIcon(it.name)) }
    }

    val result = mutableListOf<ScanTrayItem>()
    var fdroidInserted = false
    for (source in statuses) {
        if (!isFDroidFamilySource(source.name)) {
            result.add(ScanTrayItem(source, resolveScanSourceIcon(source.name)))
        } else if (!fdroidInserted) {
            fdroidInserted = true
            val activeIndex = fdroidSources.indexOfFirst { it.isScanning }
            val combinedStatus = if (activeIndex >= 0) {
                val active = fdroidSources[activeIndex]
                ScanSourceStatus(
                    name = "${active.name} (${activeIndex + 1}/${fdroidSources.size})",
                    isScanning = true,
                    updateCount = fdroidSources.sumOf { it.updateCount },
                    isError = fdroidSources.any { it.isError }
                )
            } else {
                ScanSourceStatus(
                    name = "F-Droid (${fdroidSources.size})",
                    isScanning = false,
                    updateCount = fdroidSources.sumOf { it.updateCount },
                    isError = fdroidSources.any { it.isError }
                )
            }
            val iconRes = if (activeIndex >= 0) {
                resolveScanSourceIcon(fdroidSources[activeIndex].name)
            } else if (fdroidSources.any { it.name.contains("f-droid", ignoreCase = true) || it.name.contains("fdroid", ignoreCase = true) }) {
                R.drawable.ic_fdroid
            } else {
                resolveScanSourceIcon(fdroidSources.first().name)
            }
            result.add(ScanTrayItem(combinedStatus, iconRes))
        }
    }
    return result
}

@Composable
fun MultiSourceScanTray(
    statuses: List<ScanSourceStatus>,
    modifier: Modifier = Modifier
) {
    val trayItems = groupScanTrayStatuses(statuses)
    if (trayItems.isEmpty()) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        trayItems.forEach { item ->
            val source = item.status
            val iconRes = item.iconRes

            if (source.isScanning) {
                // Active accordion state: Expanded with icon, spinning indicator and source name
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .height(34.dp)
                        .animateContentSize(
                            animationSpec = ApkUpTheme.motion.defaultSpatialSpec()
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .height(34.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(13.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = source.name,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = source.name,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            } else {
                // Compact state: 34dp outer Box containing 32dp circular surface + unclipped floating badge
                Box(
                    modifier = Modifier.size(34.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.size(32.dp),
                        shape = CircleShape,
                        color = when {
                            source.updateCount > 0 -> MaterialTheme.colorScheme.secondaryContainer
                            source.isError -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                            else -> MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        border = BorderStroke(
                            1.dp,
                            if (source.updateCount > 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(iconRes),
                                contentDescription = source.name,
                                modifier = Modifier.size(16.dp),
                                tint = when {
                                    source.updateCount > 0 -> MaterialTheme.colorScheme.primary
                                    source.isError -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                                }
                            )
                        }
                    }

                    // Floating micro-badges positioned outside the clipped surface so they are NEVER truncated
                    if (source.updateCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(13.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .border(1.5.dp, MaterialTheme.colorScheme.surfaceContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${source.updateCount}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    } else if (source.isError) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error)
                                .border(1.5.dp, MaterialTheme.colorScheme.surfaceContainer, CircleShape)
                        )
                    } else {
                        // Completed without errors or pending updates: fully round, crisp green checkmark badge
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF34A853))
                                .border(1.5.dp, MaterialTheme.colorScheme.surfaceContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
