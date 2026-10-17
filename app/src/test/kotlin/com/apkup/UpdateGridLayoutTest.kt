package com.apkup

import com.apkup.data.ui.AppUpdate
import com.apkup.data.ui.PlaySource
import com.apkup.ui.component.CompactUpdateCardLayout
import com.apkup.ui.component.NormalUpdateCardLayout
import com.apkup.ui.component.toUpdateCardContent
import com.apkup.ui.component.updateGridCellWidthDp
import com.apkup.ui.component.updateGridColumnCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateGridLayoutTest {
	@Test
	fun typicalPortraitWidthShowsTwoNormalCardsPerRowAndFourCompactCards() {
		val portraitPhoneWidths = listOf(360f, 384f, 393f, 411f, 412f, 427f, 430f, 448f, 480f, 540f, 599f)
		for (widthDp in portraitPhoneWidths) {
			assertEquals("Normal columns at ${widthDp}dp", 2, updateGridColumnCount(widthDp, compact = false))
			assertEquals("Compact columns at ${widthDp}dp", 4, updateGridColumnCount(widthDp, compact = true))
		}
	}

	@Test
	fun compactColumnsAdaptOnNarrowerAndTabletDisplays() {
		assertEquals(3, updateGridColumnCount(320f, compact = true))
		assertEquals(3, updateGridColumnCount(359f, compact = true))
		assertEquals(4, updateGridColumnCount(360f, compact = true))
		assertEquals(4, updateGridColumnCount(411f, compact = true))
		assertEquals(4, updateGridColumnCount(448f, compact = true))
		assertEquals(4, updateGridColumnCount(599f, compact = true))
		assertTrue(updateGridColumnCount(600f, compact = true) >= 5)
		assertTrue(updateGridColumnCount(600f, compact = false) >= 3)
	}

	@Test
	fun typicalPhoneUsesFourColumnsWithCellWidthCalculatedFromRealSpacing() {
		val columns = updateGridColumnCount(360f, compact = true)
		val cellWidth = updateGridCellWidthDp(
			availableWidthDp = 360f,
			columns = columns,
			horizontalPaddingDp = CompactUpdateCardLayout.gridHorizontalPaddingDp,
			spacingDp = CompactUpdateCardLayout.gridSpacingDp
		)

		assertEquals(4, columns)
		assertEquals(80.5f, cellWidth, 0.01f)
		assertTrue(cellWidth - CompactUpdateCardLayout.horizontalPaddingDp * 2 >= 64f)
	}

	@Test
	fun compactCardUsesBalancedTileMetricsAndFitsFourToFiveRowsInPortraitViewport() {
		assertEquals(36f, CompactUpdateCardLayout.iconSizeDp, 0.01f)
		assertEquals(52f, CompactUpdateCardLayout.iconRequestSizeDp, 0.01f)
		// BaseLoadingImage applies 10.dp internal padding on each side (20.dp total):
		assertTrue(CompactUpdateCardLayout.iconRequestSizeDp - 20f >= 32f)
		assertEquals(8f, CompactUpdateCardLayout.horizontalPaddingDp, 0.01f)
		assertEquals(8f, CompactUpdateCardLayout.verticalPaddingDp, 0.01f)
		assertEquals(6f, CompactUpdateCardLayout.gridSpacingDp, 0.01f)

		val idleCardHeightDp = CompactUpdateCardLayout.estimatedCardHeightDp(isInstalling = false)
		val installingCardHeightDp = CompactUpdateCardLayout.estimatedCardHeightDp(isInstalling = true)
		assertEquals(109f, idleCardHeightDp, 0.01f)
		assertEquals(114f, installingCardHeightDp, 0.01f)
		assertTrue(idleCardHeightDp in 105f..120f)
		assertTrue(installingCardHeightDp in 105f..120f)

		// Typical portrait smartphone heights (780..890 dp) with status bar (48 dp),
		// TopAppBar (64 dp), update progress bar (48 dp or 0 dp), and bottom NavigationBar (104 dp):
		val portraitPhoneHeights = listOf(780f, 800f, 825f, 850f, 890f)
		val columns = updateGridColumnCount(411f, compact = true)
		for (screenHeightDp in portraitPhoneHeights) {
			val visibleCheckingRows = CompactUpdateCardLayout.visibleRowCount(
				screenHeightDp = screenHeightDp,
				progressBarHeightDp = 48f,
				isInstalling = false
			)
			val visibleSettledRows = CompactUpdateCardLayout.visibleRowCount(
				screenHeightDp = screenHeightDp,
				progressBarHeightDp = 0f,
				isInstalling = false
			)

			assertTrue(
				"Expected 4..5 visible rows while checking at ${screenHeightDp}dp height, got $visibleCheckingRows",
				visibleCheckingRows in 4..5
			)
			assertTrue(
				"Expected 4..5 visible rows after check at ${screenHeightDp}dp height, got $visibleSettledRows",
				visibleSettledRows in 4..5
			)
			assertTrue(
				"Expected 16..20 visible compact cards at ${screenHeightDp}dp height",
				(visibleSettledRows * columns) in 16..20
			)
		}
	}

	@Test
	fun normalCardLayoutPrioritizesAppIconAndCompactActionButton() {
		assertTrue(NormalUpdateCardLayout.iconSizeDp >= 44f)
		assertTrue(NormalUpdateCardLayout.actionButtonHeightDp <= 32f)
		assertEquals(12f, NormalUpdateCardLayout.cardPaddingDp, 0.01f)
		assertEquals(10f, NormalUpdateCardLayout.gridSpacingDp, 0.01f)
		assertEquals(168f, NormalUpdateCardLayout.minCardHeightDp, 0.01f)
		assertEquals(16f, NormalUpdateCardLayout.appNameLineHeightSp, 0.01f)
		assertTrue(NormalUpdateCardLayout.packageIdFontSizeSp < 12f)
	}

	@Test
	fun cardContentKeepsAppVersionsSourceAndCorrectActionState() {
		val update = AppUpdate(
			name = "Example App",
			packageName = "example.app",
			version = "2.4.1",
			oldVersion = "2.3.8",
			versionCode = 24L,
			oldVersionCode = 23L,
			source = PlaySource
		)

		val card = update.toUpdateCardContent("Example App")

		assertEquals("Example App", card.appName)
		assertEquals("example.app", card.packageName)
		assertEquals("2.3.8", card.installedVersion)
		assertEquals("2.4.1", card.availableVersion)
		assertEquals(PlaySource, card.source)
		assertTrue(card.canInstall)

		val installingCard = update.copy(isInstalling = true).toUpdateCardContent("Example App")
		assertTrue(installingCard.isInstalling)
		assertTrue(!installingCard.canInstall)
	}

	@Test
	fun layoutAlwaysKeepsAtLeastOneColumnWhenWidthIsVerySmall() {
		assertEquals(1, updateGridColumnCount(100f, compact = false))
		assertEquals(1, updateGridColumnCount(100f, compact = true))
	}

	@Test
	fun updateGridsMaintainGenerousTopAndBottomContentPaddingToPreventClipping() {
		assertEquals(10f, NormalUpdateCardLayout.gridTopPaddingDp, 0.01f)
		assertEquals(16f, NormalUpdateCardLayout.gridBottomPaddingDp, 0.01f)
		assertEquals(10f, CompactUpdateCardLayout.gridTopPaddingDp, 0.01f)
		assertEquals(16f, CompactUpdateCardLayout.gridBottomPaddingDp, 0.01f)
	}
}
