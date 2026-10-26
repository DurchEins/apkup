package com.apkup.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

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
				shape = RoundedCornerShape(28.dp),
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
	val shape = RoundedCornerShape(24.dp)
	val isCollapsible = expanded != null && onExpandedChange != null
	var previousExpanded by remember { mutableStateOf(expanded) }
	val gearRotation = remember { Animatable(0f) }
	val shimmerProgress = remember { Animatable(0f) }
	var isShimmerActive by remember { mutableStateOf(false) }

	LaunchedEffect(expanded) {
		if (expanded == true && previousExpanded == false) {
			isShimmerActive = true
			launch {
				gearRotation.snapTo(0f)
				gearRotation.animateTo(
					targetValue = 180f,
					animationSpec = spring(
						dampingRatio = Spring.DampingRatioLowBouncy,
						stiffness = Spring.StiffnessLow
					)
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
		} else if (expanded == false) {
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
		.border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)), shape)
		.then(
			if (!isCollapsible) {
				Modifier.animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
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
		modifier = cardModifier,
		shape = shape,
		colors = CardDefaults.elevatedCardColors(
			containerColor = MaterialTheme.colorScheme.surfaceContainerLow
		),
		elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
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
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.clip(RoundedCornerShape(12.dp))
						.clickable(
							interactionSource = remember { MutableInteractionSource() },
							indication = null
						) {
							onExpandedChange?.invoke(expanded != true)
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
						animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
					) + fadeIn(animationSpec = tween(durationMillis = 200)),
					exit = shrinkVertically(
						animationSpec = tween(durationMillis = 180)
					) + fadeOut(animationSpec = tween(durationMillis = 120))
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
					color = MaterialTheme.colorScheme.primary
				)
				content()
			}
		}
	}
}
