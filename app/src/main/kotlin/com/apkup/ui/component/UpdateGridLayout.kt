package com.apkup.ui.component

import com.apkup.data.ui.AppUpdate
import com.apkup.data.ui.Source
import kotlin.math.floor

internal const val PHONE_PORTRAIT_MIN_WIDTH_DP = 360f
internal const val TABLET_MIN_WIDTH_DP = 600f
internal const val NORMAL_PHONE_COLUMNS = 2
internal const val COMPACT_PHONE_COLUMNS = 4

internal object NormalUpdateCardLayout {
	const val gridHorizontalPaddingDp = 8f
	const val gridVerticalPaddingDp = 6f
	const val gridTopPaddingDp = 10f
	const val gridBottomPaddingDp = 16f
	const val gridSpacingDp = 10f
	const val cardCornerRadiusDp = 16f
	const val cardPaddingDp = 12f
	const val cardPaddingHorizontalDp = 12f
	const val cardPaddingVerticalDp = 12f
	const val minCardHeightDp = 168f
	const val sectionSpacingDp = 5f
	const val iconSizeDp = 44f
	const val appNameLineHeightSp = 16f
	const val packageIdFontSizeSp = 10f
	const val packageIdLineHeightSp = 12f
	const val actionButtonHeightDp = 28f
	const val actionIconSizeDp = 14f
	const val sourceIconSizeDp = 12f
}

internal object CompactUpdateCardLayout {
	const val gridHorizontalPaddingDp = 10f
	const val gridVerticalPaddingDp = 8f
	const val gridTopPaddingDp = 10f
	const val gridBottomPaddingDp = 16f
	const val gridSpacingDp = 6f
	const val cardCornerRadiusDp = 16f
	const val iconSizeDp = 36f
	const val iconRequestSizeDp = 52f
	const val horizontalPaddingDp = 8f
	const val verticalPaddingDp = 8f
	const val itemSpacingDp = 2f
	const val versionRowSpacingDp = 1f
	const val appNameFontSizeSp = 12f
	const val appNameLineHeightDp = 14f
	const val metaFontSizeSp = 10f
	const val metaLineHeightDp = 12f
	const val sourceIconSizeDp = 10f
	const val progressHeightDp = 3f
	const val minCardHeightDp = 108f

	/**
	 * Natural single-card height in dp without clipping or rigid height constraints.
	 * Uses a vertical tile structure: top app icon (36 dp), 1-line app name (14 dp),
	 * 2-line version block (25 dp), and 1-line source row (12 dp).
	 */
	fun estimatedCardHeightDp(
		isInstalling: Boolean = false,
		fontScale: Float = 1f
	): Float {
		val scaledAppNameLineDp = appNameLineHeightDp * fontScale
		val scaledMetaLineDp = metaLineHeightDp * fontScale
		val iconRowHeightDp = iconSizeDp
		val versionsHeightDp = scaledMetaLineDp * 2f + versionRowSpacingDp
		val sourceHeightDp = maxOf(sourceIconSizeDp, scaledMetaLineDp)
		val naturalHeightDp = verticalPaddingDp * 2f +
			iconRowHeightDp +
			scaledAppNameLineDp +
			versionsHeightDp +
			sourceHeightDp +
			itemSpacingDp * 3f
		val baseHeightDp = maxOf(minCardHeightDp, naturalHeightDp)
		return if (isInstalling) {
			baseHeightDp + itemSpacingDp + progressHeightDp
		} else {
			baseHeightDp
		}
	}

	/**
	 * Number of complete compact-card rows simultaneously visible in the viewport after
	 * accounting for status bar, TopAppBar, update progress bar, and bottom navigation bar.
	 */
	fun visibleRowCount(
		screenHeightDp: Float,
		statusBarHeightDp: Float = 48f,
		topAppBarHeightDp: Float = 64f,
		progressBarHeightDp: Float = 48f,
		bottomBarHeightDp: Float = 104f,
		isInstalling: Boolean = false,
		fontScale: Float = 1f
	): Int {
		val viewportHeightDp = (
			screenHeightDp -
				statusBarHeightDp -
				topAppBarHeightDp -
				progressBarHeightDp -
				bottomBarHeightDp
			).coerceAtLeast(0f)
		val contentHeightDp = (viewportHeightDp - gridVerticalPaddingDp * 2f).coerceAtLeast(0f)
		val rowHeightDp = estimatedCardHeightDp(isInstalling = isInstalling, fontScale = fontScale)
		return floor((contentHeightDp + gridSpacingDp) / (rowHeightDp + gridSpacingDp))
			.toInt()
			.coerceAtLeast(0)
	}
}

/** Number of update cards that fit after horizontal content padding and grid spacing. */
internal fun updateGridColumnCount(
	availableWidthDp: Float,
	compact: Boolean,
	horizontalPaddingDp: Float = if (compact) {
		CompactUpdateCardLayout.gridHorizontalPaddingDp
	} else {
		NormalUpdateCardLayout.gridHorizontalPaddingDp
	},
	spacingDp: Float = if (compact) {
		CompactUpdateCardLayout.gridSpacingDp
	} else {
		NormalUpdateCardLayout.gridSpacingDp
	}
): Int {
	val minimumCellWidthDp = if (compact) 78f else 160f
	val contentWidthDp = (availableWidthDp - horizontalPaddingDp * 2).coerceAtLeast(0f)
	val fittedColumns = floor((contentWidthDp + spacingDp) / (minimumCellWidthDp + spacingDp))
		.toInt()
		.coerceAtLeast(1)

	return if (compact) {
		when {
			availableWidthDp < PHONE_PORTRAIT_MIN_WIDTH_DP ->
				fittedColumns.coerceIn(1, COMPACT_PHONE_COLUMNS - 1)
			availableWidthDp < TABLET_MIN_WIDTH_DP ->
				COMPACT_PHONE_COLUMNS
			else ->
				fittedColumns.coerceAtLeast(COMPACT_PHONE_COLUMNS + 1)
		}
	} else {
		when {
			availableWidthDp < PHONE_PORTRAIT_MIN_WIDTH_DP ->
				fittedColumns.coerceIn(1, NORMAL_PHONE_COLUMNS)
			availableWidthDp < TABLET_MIN_WIDTH_DP ->
				NORMAL_PHONE_COLUMNS
			else ->
				fittedColumns.coerceAtLeast(NORMAL_PHONE_COLUMNS + 1)
		}
	}
}

/** Width of each grid cell after outer padding and the gaps between cells. */
internal fun updateGridCellWidthDp(
	availableWidthDp: Float,
	columns: Int,
	horizontalPaddingDp: Float = CompactUpdateCardLayout.gridHorizontalPaddingDp,
	spacingDp: Float = CompactUpdateCardLayout.gridSpacingDp
): Float {
	if (columns < 1) return 0f
	val contentWidthDp = (availableWidthDp - horizontalPaddingDp * 2).coerceAtLeast(0f)
	return ((contentWidthDp - spacingDp * (columns - 1)) / columns).coerceAtLeast(0f)
}

/** UI data kept visible in both update-card layouts; source and version values remain unmodified. */
internal data class UpdateCardContent(
	val appName: String,
	val packageName: String,
	val installedVersion: String,
	val availableVersion: String,
	val source: Source,
	val isInstalling: Boolean,
	val additionalSourcesCount: Int = 0
) {
	val canInstall: Boolean get() = !isInstalling

	fun formatSourceWithCount(sourceLabel: String): String =
		if (additionalSourcesCount > 0) "$sourceLabel +$additionalSourcesCount" else sourceLabel
}

internal fun AppUpdate.toUpdateCardContent(resolvedAppName: String) = UpdateCardContent(
	appName = resolvedAppName.ifBlank { packageName },
	packageName = packageName,
	installedVersion = oldVersion,
	availableVersion = version,
	source = source,
	isInstalling = isInstalling,
	additionalSourcesCount = (alternativeUpdates.size - 1).coerceAtLeast(0)
)
