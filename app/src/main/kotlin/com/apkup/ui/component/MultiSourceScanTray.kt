package com.apkup.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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

data class ScanSourceStatus(
    val name: String,
    val isScanning: Boolean,
    val updateCount: Int = 0,
    val isError: Boolean = false
)

fun resolveScanSourceIcon(name: String): Int = when {
    name.contains("apkmirror", ignoreCase = true) -> R.drawable.ic_apkmirror
    name.contains("play", ignoreCase = true) || name.contains("google", ignoreCase = true) -> R.drawable.ic_play
    name.contains("izzy", ignoreCase = true) || name.contains("f-droid", ignoreCase = true) || name.contains("fdroid", ignoreCase = true) -> R.drawable.ic_izzy
    name.contains("gitlab", ignoreCase = true) -> R.drawable.ic_gitlab
    name.contains("github", ignoreCase = true) -> R.drawable.ic_github
    else -> R.drawable.ic_refresh
}

@Composable
fun MultiSourceScanTray(
    statuses: List<ScanSourceStatus>,
    modifier: Modifier = Modifier
) {
    if (statuses.isEmpty()) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        statuses.forEach { source ->
            val iconRes = resolveScanSourceIcon(source.name)

            Surface(
                shape = if (source.isScanning) RoundedCornerShape(18.dp) else CircleShape,
                color = when {
                    source.isScanning -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
                    source.updateCount > 0 -> MaterialTheme.colorScheme.secondaryContainer
                    source.isError -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                },
                border = BorderStroke(
                    1.dp,
                    if (source.isScanning) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    else if (source.updateCount > 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                ),
                modifier = Modifier
                    .height(34.dp)
                    .animateContentSize(
                        animationSpec = spring(
                            stiffness = Spring.StiffnessMediumLow,
                            dampingRatio = Spring.DampingRatioLowBouncy
                        )
                    )
            ) {
                if (source.isScanning) {
                    // Active accordion state: Expanded with icon, spinning indicator and source name
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
                } else {
                    // Compact state: 34dp circle with source icon and overlay indicator badge
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape),
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

                        // Top-right or bottom-right small badge
                        if (source.updateCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 2.dp, end = 2.dp)
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
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
                                    .padding(top = 2.dp, end = 2.dp)
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error)
                            )
                        } else {
                            // Completed without errors or pending updates: small subtle green checkmark badge
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(bottom = 2.dp, end = 2.dp)
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF34A853)),
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
}
