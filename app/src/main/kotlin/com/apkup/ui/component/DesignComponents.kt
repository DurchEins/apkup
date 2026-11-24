package com.apkup.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.apkup.R
import com.apkup.ui.theme.ApkUpTheme
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AppEmptyState(
	title: String,
	message: String,
	icon: ImageVector,
	modifier: Modifier = Modifier,
	actionLabel: String? = null,
	onAction: (() -> Unit)? = null
) = Box(modifier.fillMaxSize()) {
	LazyColumn(Modifier.fillMaxSize()) {}
	Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
		Column(
			modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.Center
		) {
			Surface(
				shape = MaterialTheme.shapes.extraLarge,
				color = MaterialTheme.colorScheme.primaryContainer,
				modifier = Modifier.size(76.dp)
			) {
				Box(contentAlignment = Alignment.Center) {
					Icon(
						imageVector = icon,
						contentDescription = null,
						tint = MaterialTheme.colorScheme.onPrimaryContainer,
						modifier = Modifier.size(32.dp)
					)
				}
			}
			Spacer(Modifier.height(20.dp))
			Text(
				text = title,
				style = MaterialTheme.typography.headlineSmall,
				fontWeight = FontWeight.SemiBold,
				textAlign = TextAlign.Center
			)
			Spacer(Modifier.height(8.dp))
			Text(
				text = message,
				style = MaterialTheme.typography.bodyLarge,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				textAlign = TextAlign.Center
			)
			if (actionLabel != null && onAction != null) {
				Spacer(Modifier.height(20.dp))
				Button(onClick = onAction, shape = CircleShape, modifier = Modifier.height(52.dp)) {
					Text(actionLabel)
				}
			}
		}
	}
}

@Composable
fun SettingsSection(
	title: String,
	modifier: Modifier = Modifier,
	expanded: Boolean? = null,
	onExpandedChange: ((Boolean) -> Unit)? = null,
	showCollapsedWarningBadge: Boolean = false,
	content: @Composable ColumnScope.() -> Unit
) {
	val shape = MaterialTheme.shapes.large
	val isCollapsible = expanded != null && onExpandedChange != null
	val isReducedMotion = ApkUpTheme.isReducedMotion
	val motion = ApkUpTheme.motion
	var previousExpanded by remember { mutableStateOf(expanded) }
	val gearRotation = remember { Animatable(0f) }
	val shimmerProgress = remember { Animatable(0f) }
	var isShimmerActive by remember { mutableStateOf(false) }

	LaunchedEffect(expanded, isReducedMotion) {
		if (expanded == true && previousExpanded == false && !isReducedMotion) {
			isShimmerActive = true
			launch {
				gearRotation.snapTo(0f)
				gearRotation.animateTo(
					targetValue = 180f,
					animationSpec = motion.slowSpatialSpec()
				)
			}
			launch {
				shimmerProgress.snapTo(0f)
				shimmerProgress.animateTo(
					targetValue = 1f,
					animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing)
				)
				isShimmerActive = false
			}
		} else if (expanded == false || isReducedMotion) {
			isShimmerActive = false
			gearRotation.snapTo(0f)
			shimmerProgress.snapTo(0f)
		}
		previousExpanded = expanded
	}

	val primaryColor = MaterialTheme.colorScheme.primary
	val cardModifier = modifier
		.fillMaxWidth()
		.clip(shape)
		.then(
			if (!isCollapsible) {
				Modifier.animateContentSize(animationSpec = motion.defaultSpatialSpec())
			} else {
				Modifier.drawWithContent {
					drawContent()
					if (isShimmerActive && expanded == true) {
						val progress = shimmerProgress.value
						val bandWidth = size.width * 0.65f
						val startX = -bandWidth + (size.width + bandWidth * 2f) * progress
						val fadeAlpha = (1f - kotlin.math.abs(progress - 0.5f) * 1.6f).coerceIn(0f, 1f)
						val shimmerBrush = Brush.linearGradient(
							colors = listOf(
								Color.Transparent,
								primaryColor.copy(alpha = 0.14f * fadeAlpha),
								Color.Transparent
							),
							start = Offset(startX, 0f),
							end = Offset(startX + bandWidth, size.height)
						)
						drawRect(brush = shimmerBrush)
					}
				}
			}
		)

	ElevatedCard(
		modifier = cardModifier.border(
			BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
			shape
		),
		shape = shape,
		colors = CardDefaults.elevatedCardColors(
			containerColor = MaterialTheme.colorScheme.surfaceContainerLow
		),
		elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(
					horizontal = 18.dp,
					vertical = if (isCollapsible && expanded == false) 14.dp else 18.dp
				),
			verticalArrangement = Arrangement.spacedBy(10.dp)
		) {
			if (isCollapsible) {
				val headerStateLabel = stringResource(
					if (expanded == true) R.string.a11y_state_expanded else R.string.a11y_state_collapsed
				)
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.clip(MaterialTheme.shapes.small)
						.clickable(
							interactionSource = remember { MutableInteractionSource() },
							indication = null,
							role = Role.Button
						) {
							onExpandedChange(expanded != true)
						}
						.semantics(mergeDescendants = true) {
							heading()
							stateDescription = headerStateLabel
						},
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(8.dp)
				) {
					Icon(
						imageVector = Icons.Default.Settings,
						contentDescription = null,
						tint = MaterialTheme.colorScheme.primary,
						modifier = Modifier
							.size(16.dp)
							.graphicsLayer { rotationZ = gearRotation.value }
					)
					Text(
						text = title,
						style = MaterialTheme.typography.titleMedium,
						fontWeight = FontWeight.Bold,
						color = MaterialTheme.colorScheme.primary,
						modifier = Modifier.weight(1f)
					)
					if (expanded == false && showCollapsedWarningBadge) {
						Surface(
							shape = CircleShape,
							color = MaterialTheme.colorScheme.errorContainer
						) {
							Row(
								modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
								verticalAlignment = Alignment.CenterVertically
							) {
								Icon(
									imageVector = Icons.Default.Warning,
									contentDescription = null,
									tint = MaterialTheme.colorScheme.onErrorContainer,
									modifier = Modifier.size(12.dp)
								)
							}
						}
					}
					Icon(
						imageVector = if (expanded == true) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
						contentDescription = null,
						tint = MaterialTheme.colorScheme.onSurfaceVariant,
						modifier = Modifier.size(20.dp)
					)
				}
				AnimatedVisibility(
					visible = expanded == true,
					enter = expandVertically(
						animationSpec = motion.defaultSpatialSpec()
					) + fadeIn(animationSpec = motion.defaultEffectsSpec()),
					exit = shrinkVertically(
						animationSpec = motion.fastSpatialSpec()
					) + fadeOut(animationSpec = motion.fastEffectsSpec())
				) {
					Column(
						modifier = Modifier.fillMaxWidth(),
						verticalArrangement = Arrangement.spacedBy(10.dp)
					) {
						content()
					}
				}
			} else {
				Text(
					text = title,
					style = MaterialTheme.typography.titleMedium,
					fontWeight = FontWeight.Bold,
					color = MaterialTheme.colorScheme.primary,
					modifier = Modifier.semantics { heading() }
				)
				content()
			}
		}
	}
}

/**
 * Material 3 Expressive-inspired wavy linear progress indicator.
 * Falls back to a standard [LinearProgressIndicator] when reduced motion is enabled.
 */
@Composable
fun ApkUpWavyLinearProgressIndicator(
	progress: (() -> Float)? = null,
	modifier: Modifier = Modifier,
	color: Color = MaterialTheme.colorScheme.primary,
	trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest
) {
	if (ApkUpTheme.isReducedMotion) {
		if (progress != null) {
			LinearProgressIndicator(
				progress = { progress().coerceIn(0f, 1f) },
				modifier = modifier.height(5.dp).clip(CircleShape),
				color = color,
				trackColor = trackColor
			)
		} else {
			LinearProgressIndicator(
				modifier = modifier.height(5.dp).clip(CircleShape),
				color = color,
				trackColor = trackColor
			)
		}
		return
	}

	val infiniteTransition = rememberInfiniteTransition(label = "WavyProgress")
	val phase by infiniteTransition.animateFloat(
		initialValue = 0f,
		targetValue = (2f * PI).toFloat(),
		animationSpec = infiniteRepeatable(
			animation = tween(durationMillis = 900, easing = LinearEasing),
			repeatMode = RepeatMode.Restart
		),
		label = "WavyPhase"
	)
	val indeterminateOffset by infiniteTransition.animateFloat(
		initialValue = -0.35f,
		targetValue = 1.0f,
		animationSpec = infiniteRepeatable(
			animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
			repeatMode = RepeatMode.Restart
		),
		label = "WavyIndeterminate"
	)

	Canvas(
		modifier = modifier
			.fillMaxWidth()
			.height(10.dp)
	) {
		val strokePx = 3.5.dp.toPx()
		val centerY = size.height / 2f
		val amplitudePx = ((size.height - strokePx) / 2f).coerceAtMost(2.5.dp.toPx())
		val wavelengthPx = 22.dp.toPx()
		val halfStroke = strokePx / 2f
		val usableWidth = (size.width - strokePx).coerceAtLeast(1f)

		// Track
		drawLine(
			color = trackColor,
			start = Offset(halfStroke, centerY),
			end = Offset(size.width - halfStroke, centerY),
			strokeWidth = strokePx,
			cap = StrokeCap.Round
		)

		val startX: Float
		val endX: Float
		if (progress != null) {
			val fraction = progress().coerceIn(0f, 1f)
			if (fraction <= 0.005f) return@Canvas
			startX = halfStroke
			endX = halfStroke + usableWidth * fraction
		} else {
			val segStart = indeterminateOffset.coerceIn(0f, 1f)
			val segEnd = (indeterminateOffset + 0.35f).coerceIn(0f, 1f)
			if (segEnd <= segStart) return@Canvas
			startX = halfStroke + usableWidth * segStart
			endX = halfStroke + usableWidth * segEnd
		}

		val wavePath = Path()
		val stepPx = 3f
		var x = startX
		var first = true
		while (x <= endX) {
			val angle = ((x / wavelengthPx) * 2f * PI.toFloat()) - phase
			val y = centerY + sin(angle) * amplitudePx
			if (first) {
				wavePath.moveTo(x, y)
				first = false
			} else {
				wavePath.lineTo(x, y)
			}
			x += stepPx
		}
		if (!first) {
			val endAngle = ((endX / wavelengthPx) * 2f * PI.toFloat()) - phase
			wavePath.lineTo(endX, centerY + sin(endAngle) * amplitudePx)
			drawPath(
				path = wavePath,
				color = color,
				style = Stroke(width = strokePx, cap = StrokeCap.Round)
			)
		}
	}
}

/**
 * Material 3 Expressive-inspired contained loading indicator with morphing petal geometry.
 * Falls back to a calm [CircularProgressIndicator] when reduced motion is enabled.
 */
@Composable
fun ApkUpExpressiveLoadingIndicator(
	modifier: Modifier = Modifier.size(36.dp),
	containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
	indicatorColor: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
	Surface(
		shape = CircleShape,
		color = containerColor,
		modifier = modifier
	) {
		Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
			if (ApkUpTheme.isReducedMotion) {
				CircularProgressIndicator(
					modifier = Modifier.size(18.dp),
					strokeWidth = 2.dp,
					color = indicatorColor
				)
			} else {
				val infiniteTransition = rememberInfiniteTransition(label = "ExpressiveLoader")
				val rotation by infiniteTransition.animateFloat(
					initialValue = 0f,
					targetValue = 360f,
					animationSpec = infiniteRepeatable(
						animation = tween(durationMillis = 2200, easing = LinearEasing),
						repeatMode = RepeatMode.Restart
					),
					label = "ExpressiveLoaderRotation"
				)
				val morphPulse by infiniteTransition.animateFloat(
					initialValue = 0.88f,
					targetValue = 1.0f,
					animationSpec = infiniteRepeatable(
						animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
						repeatMode = RepeatMode.Reverse
					),
					label = "ExpressiveLoaderPulse"
				)
				Canvas(modifier = Modifier.fillMaxSize(0.55f)) {
					val radius = (size.minDimension / 2f) * morphPulse
					val center = Offset(size.width / 2f, size.height / 2f)
					val path = Path()
					val points = 36
					for (i in 0..points) {
						val theta = (i.toFloat() / points) * (2f * PI.toFloat())
						val r = radius * (0.82f + 0.18f * cos(4f * theta))
						val px = center.x + r * cos(theta)
						val py = center.y + r * sin(theta)
						if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
					}
					path.close()
					rotate(rotation, pivot = center) {
						drawPath(path = path, color = indicatorColor)
					}
				}
			}
		}
	}
}

/**
 * Material 3 Expressive split action button: primary pill action + connected dropdown trigger.
 */
@Composable
fun ApkUpSplitActionButton(
	label: String,
	iconRes: Int,
	onClick: () -> Unit,
	menuContent: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
	modifier: Modifier = Modifier,
	enabled: Boolean = true,
	menuContentDescription: String? = null
) {
	var expanded by remember { mutableStateOf(false) }
	val leadingShape = RoundedCornerShape(
		topStart = 24.dp,
		bottomStart = 24.dp,
		topEnd = 6.dp,
		bottomEnd = 6.dp
	)
	val trailingShape = RoundedCornerShape(
		topStart = 6.dp,
		bottomStart = 6.dp,
		topEnd = 24.dp,
		bottomEnd = 24.dp
	)

	Box(modifier = modifier) {
		Row(
			horizontalArrangement = Arrangement.spacedBy(2.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			Button(
				onClick = onClick,
				enabled = enabled,
				shape = leadingShape,
				colors = ButtonDefaults.buttonColors(
					containerColor = MaterialTheme.colorScheme.primary,
					contentColor = MaterialTheme.colorScheme.onPrimary
				),
				contentPadding = PaddingValues(start = 14.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
				modifier = Modifier.height(40.dp)
			) {
				Icon(
					painter = painterResource(iconRes),
					contentDescription = null,
					modifier = Modifier.size(16.dp)
				)
				Spacer(Modifier.width(6.dp))
				Text(
					text = label,
					style = MaterialTheme.typography.labelMedium,
					fontWeight = FontWeight.Bold,
					maxLines = 1
				)
			}

			Button(
				onClick = { expanded = true },
				enabled = enabled,
				shape = trailingShape,
				colors = ButtonDefaults.buttonColors(
					containerColor = MaterialTheme.colorScheme.primary,
					contentColor = MaterialTheme.colorScheme.onPrimary
				),
				contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
				modifier = Modifier
					.minimumInteractiveComponentSize()
					.height(40.dp)
					.then(
						if (menuContentDescription != null) {
							Modifier.semantics { contentDescription = menuContentDescription }
						} else {
							Modifier
						}
					)
			) {
				Icon(
					imageVector = Icons.Default.KeyboardArrowDown,
					contentDescription = null,
					modifier = Modifier.size(18.dp)
				)
			}
		}

		DropdownMenu(
			expanded = expanded,
			onDismissRequest = { expanded = false }
		) {
			menuContent { expanded = false }
		}
	}
}

/**
 * Shared Material 3 sheet state helper matching skip-partially-expanded modal sheet behavior
 * without relying on deprecated [androidx.compose.material3.rememberModalBottomSheetState].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberExpandedModalSheetState(): SheetState =
	rememberBottomSheetState(
		initialValue = SheetValue.Hidden,
		enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
	)
