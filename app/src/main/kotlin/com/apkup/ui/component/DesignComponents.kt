package com.apkup.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

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
				Button(onClick = onAction, modifier = Modifier.height(52.dp)) {
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
	content: @Composable ColumnScope.() -> Unit
) = ElevatedCard(
	modifier = modifier
		.fillMaxWidth()
		.animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
	shape = RoundedCornerShape(26.dp),
	colors = CardDefaults.elevatedCardColors(
		containerColor = MaterialTheme.colorScheme.surfaceContainer
	),
	elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
) {
	Column(
		modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp),
		verticalArrangement = Arrangement.spacedBy(10.dp)
	) {
		Text(
			text = title,
			style = MaterialTheme.typography.titleMedium,
			fontWeight = FontWeight.Bold,
			color = MaterialTheme.colorScheme.primary
		)
		content()
	}
}
