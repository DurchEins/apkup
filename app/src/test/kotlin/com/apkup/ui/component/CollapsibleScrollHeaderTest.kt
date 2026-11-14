package com.apkup.ui.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class CollapsibleScrollHeaderTest {

	@Test
	fun snapTarget_below45Percent_snapsToZero() {
		assertEquals(0f, computeCollapsibleSnapTarget(collapsedPx = 0f, maxHeightPx = 200f), 0.001f)
		assertEquals(0f, computeCollapsibleSnapTarget(collapsedPx = 89.9f, maxHeightPx = 200f), 0.001f)
	}

	@Test
	fun snapTarget_between45And55Percent_snapsToExactHalfGimmick() {
		assertEquals(100f, computeCollapsibleSnapTarget(collapsedPx = 90f, maxHeightPx = 200f), 0.001f)
		assertEquals(100f, computeCollapsibleSnapTarget(collapsedPx = 100f, maxHeightPx = 200f), 0.001f)
		assertEquals(100f, computeCollapsibleSnapTarget(collapsedPx = 110f, maxHeightPx = 200f), 0.001f)
	}

	@Test
	fun snapTarget_above55Percent_snapsToFullCollapsed() {
		assertEquals(200f, computeCollapsibleSnapTarget(collapsedPx = 110.1f, maxHeightPx = 200f), 0.001f)
		assertEquals(200f, computeCollapsibleSnapTarget(collapsedPx = 200f, maxHeightPx = 200f), 0.001f)
	}

	@Test
	fun preScroll_collapsesAndExpandsPixelByPixelWhenNotPinned() = runBlocking {
		val state = CollapsibleHeaderState(scope = this, pinned = false).apply {
			maxHeightPx = 200f
		}

		// Scroll down (content moves up -> negative available.y)
		val consumedDown = state.nestedScrollConnection.onPreScroll(
			available = Offset(0f, -80f),
			source = NestedScrollSource.UserInput
		)
		assertEquals(-80f, consumedDown.y, 0.001f)
		assertEquals(80f, state.collapsedPx, 0.001f)

		// Scroll further down past maxHeightPx
		val consumedPastMax = state.nestedScrollConnection.onPreScroll(
			available = Offset(0f, -150f),
			source = NestedScrollSource.UserInput
		)
		assertEquals(-120f, consumedPastMax.y, 0.001f)
		assertEquals(200f, state.collapsedPx, 0.001f)

		// Scroll up -> expands immediately
		val consumedUp = state.nestedScrollConnection.onPreScroll(
			available = Offset(0f, 50f),
			source = NestedScrollSource.UserInput
		)
		assertEquals(50f, consumedUp.y, 0.001f)
		assertEquals(150f, state.collapsedPx, 0.001f)
	}

	@Test
	fun preScroll_ignoresScrollWhenPinned() = runBlocking {
		val state = CollapsibleHeaderState(scope = this, pinned = true).apply {
			maxHeightPx = 200f
		}

		val consumed = state.nestedScrollConnection.onPreScroll(
			available = Offset(0f, -100f),
			source = NestedScrollSource.UserInput
		)
		assertEquals(0f, consumed.y, 0.001f)
		assertEquals(0f, state.collapsedPx, 0.001f)
	}
}
