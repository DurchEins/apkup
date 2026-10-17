package com.apkup.ui.component

import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow


@Composable
fun MediumText(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    color: Color = MaterialTheme.colorScheme.onSurface
) = Text(
    text = text,
    style = MaterialTheme.typography.bodyMedium,
    color = color,
    maxLines = maxLines,
    modifier = modifier,
    overflow = TextOverflow.Ellipsis
)

@Composable
fun MediumTitle(text: String, modifier: Modifier = Modifier) = Text(
    text = text,
    style = MaterialTheme.typography.titleMedium,
    fontWeight = FontWeight.Bold,
    maxLines = 2,
    overflow = TextOverflow.Ellipsis,
    modifier = modifier
)

@Composable
fun LargeTitle(text: String, modifier: Modifier = Modifier) = Text(
    text = text,
    style = MaterialTheme.typography.titleLarge,
    fontWeight = FontWeight.Bold,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
    modifier = modifier
)

@Composable
fun BadgeText(number: String) {
    if (number.isNotEmpty()) {
        Badge {
            Text(number)
        }
    }
}
