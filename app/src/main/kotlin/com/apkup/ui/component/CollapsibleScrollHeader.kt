package com.apkup.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Berechnet die Zielposition beim Loslassen des Fingers:
 * - Innerhalb von 45 % bis 55 % (Mitte +/- 5 %) bleibt der Header als kleines Gimmick exakt bei 50 %.
 * - Darunter rastet er sanft auf 0 % (komplett offen) ein.
 * - Darüber rastet er sanft auf 100 % (komplett eingeklappt) ein.
 */
internal fun computeCollapsibleSnapTarget(collapsedPx: Float, maxHeightPx: Float): Float {
	if (maxHeightPx <= 0f) return 0f
	val fraction = (collapsedPx / maxHeightPx).coerceIn(0f, 1f)
	return when {
		fraction in 0.45f..0.55f -> maxHeightPx * 0.5f
		fraction < 0.45f -> 0f
		else -> maxHeightPx
	}
}

@Stable
class CollapsibleHeaderState(
	private val scope: CoroutineScope,
	pinned: Boolean = false
) {
	var maxHeightPx by mutableFloatStateOf(0f)
		internal set

	var collapsedPx by mutableFloatStateOf(0f)
		internal set

	var isPinned by mutableFloatStateOf(if (pinned) 1f else 0f)
		private set

	private var snapJob: Job? = null

	fun updatePinned(pinned: Boolean) {
		val newFlag = if (pinned) 1f else 0f
		if (isPinned == newFlag) return
		isPinned = newFlag
		if (pinned && collapsedPx > 0f) {
			animateTo(0f)
		}
	}

	private fun animateTo(targetPx: Float) {
		snapJob?.cancel()
		snapJob = scope.launch {
			val animatable = Animatable(collapsedPx)
			animatable.animateTo(
				targetValue = targetPx.coerceIn(0f, maxHeightPx),
				animationSpec = spring(
					dampingRatio = Spring.DampingRatioNoBouncy,
					stiffness = Spring.StiffnessMediumLow
				)
			) {
				collapsedPx = value
			}
		}
	}

	val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
		override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
			if (isPinned > 0.5f || maxHeightPx <= 0f) return Offset.Zero
			snapJob?.cancel()
			val deltaY = -available.y
			if (deltaY == 0f) return Offset.Zero
			val newCollapsed = (collapsedPx + deltaY).coerceIn(0f, maxHeightPx)
			val consumed = newCollapsed - collapsedPx
			if (consumed == 0f) return Offset.Zero
			collapsedPx = newCollapsed
			return Offset(0f, -consumed)
		}

		override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
			if (isPinned > 0.5f || maxHeightPx <= 0f) return Velocity.Zero
			val target = computeCollapsibleSnapTarget(collapsedPx, maxHeightPx)
			if (kotlin.math.abs(target - collapsedPx) > 0.5f) {
				animateTo(target)
			} else {
				collapsedPx = target
			}
			return Velocity.Zero
		}
	}
}

@Composable
fun rememberCollapsibleHeaderState(pinned: Boolean = false): CollapsibleHeaderState {
	val scope = rememberCoroutineScope()
	val state = remember(scope) { CollapsibleHeaderState(scope = scope, pinned = pinned) }
	LaunchedEffect(pinned) {
		state.updatePinned(pinned)
	}
	return state
}

@Composable
fun CollapsibleScrollHeader(
	state: CollapsibleHeaderState,
	modifier: Modifier = Modifier,
	content: @Composable () -> Unit
) {
	Box(
		modifier = modifier
			.clipToBounds()
			.layout { measurable, constraints ->
				val placeable = measurable.measure(constraints)
				val fullHeight = placeable.height
				state.maxHeightPx = fullHeight.toFloat()
				val clampedCollapsed = state.collapsedPx.coerceIn(0f, fullHeight.toFloat())
				if (clampedCollapsed != state.collapsedPx) {
					state.collapsedPx = clampedCollapsed
				}
				val collapsedInt = clampedCollapsed.roundToInt()
				val visibleHeight = (fullHeight - collapsedInt).coerceIn(0, fullHeight)
				layout(placeable.width, visibleHeight) {
					placeable.placeRelative(0, -collapsedInt)
				}
			}
	) {
		content()
	}
}
