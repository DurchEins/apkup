package com.apkup.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal const val INSTALLED_APPS_COMPACT_BREAKPOINT_DP = 600f
internal const val INSTALLED_APPS_PORTRAIT_COLUMNS = 3
internal const val INSTALLED_APPS_MIN_TILE_WIDTH_DP = 110f
internal const val INSTALLED_APPS_GRID_HORIZONTAL_PADDING_DP = 10f
internal const val INSTALLED_APPS_GRID_SPACING_DP = 8f

internal fun installedAppsGridColumnCount(availableWidthDp: Float): Int {
    if (availableWidthDp < INSTALLED_APPS_COMPACT_BREAKPOINT_DP) {
        return INSTALLED_APPS_PORTRAIT_COLUMNS
    }
    val contentWidthDp = (availableWidthDp - (INSTALLED_APPS_GRID_HORIZONTAL_PADDING_DP * 2f))
        .coerceAtLeast(INSTALLED_APPS_MIN_TILE_WIDTH_DP)
    val rawColumns = ((contentWidthDp + INSTALLED_APPS_GRID_SPACING_DP) /
        (INSTALLED_APPS_MIN_TILE_WIDTH_DP + INSTALLED_APPS_GRID_SPACING_DP)).toInt()
    return rawColumns.coerceIn(4, 6)
}

@Composable
fun LoadingGrid() = InstalledLoadingGrid()

@Composable
fun InstalledLoadingGrid() = InstalledGrid(false) {
    items(16) {
        val cardShape = RoundedCornerShape(24.dp)
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .height(134.dp)
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)), cardShape),
            shape = cardShape,
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(40.dp).shimmering(true), contentAlignment = Alignment.Center) {}
                Box(Modifier.fillMaxWidth(0.78f).height(14.dp).shimmering(true))
                Box(Modifier.fillMaxWidth(0.56f).height(10.dp).shimmering(true))
                Spacer(Modifier.weight(1f))
                Box(Modifier.fillMaxWidth(0.42f).height(16.dp).shimmering(true))
            }
        }
    }
}

@Composable
fun InstalledGrid(
    scroll: Boolean = true,
    state: LazyGridState = rememberLazyGridState(),
    content: LazyGridScope.() -> Unit
) = BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val columns = installedAppsGridColumnCount(maxWidth.value)
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = state,
        contentPadding = PaddingValues(
            start = INSTALLED_APPS_GRID_HORIZONTAL_PADDING_DP.dp,
            top = 12.dp,
            end = INSTALLED_APPS_GRID_HORIZONTAL_PADDING_DP.dp,
            bottom = 100.dp
        ),
        verticalArrangement = Arrangement.spacedBy(INSTALLED_APPS_GRID_SPACING_DP.dp),
        horizontalArrangement = Arrangement.spacedBy(INSTALLED_APPS_GRID_SPACING_DP.dp),
        content = content,
        userScrollEnabled = scroll,
        modifier = Modifier.fillMaxSize()
    )
}
