package com.apkup.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.apkup.R


@Composable
fun IgnoreIcon(ignored: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) =
	CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
		IconButton(
			onClick = onClick,
			modifier = modifier
		) {
			Icon(
				painter = painterResource(if (ignored) R.drawable.ic_visible_off else R.drawable.ic_visible),
				contentDescription = stringResource(if (ignored) R.string.unignore_cd else R.string.ignore_cd),
				tint = if (ignored) {
					MaterialTheme.colorScheme.primary
				} else {
					MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
				},
				modifier = Modifier.size(18.dp)
			)
		}
	}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshIcon(
    text: String,
    modifier: Modifier = Modifier
) = TooltipBox(
    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
    state = rememberTooltipState(),
    tooltip = { PlainTooltip { Text(text) } }
) {
    Icon(
        painter = painterResource(id = R.drawable.ic_refresh),
        contentDescription = text,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadIcon(
    text: String,
    modifier: Modifier = Modifier
) = TooltipBox(
    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
    state = rememberTooltipState(),
    tooltip = { PlainTooltip { Text(text) } }
) {
    Icon(
        painter = painterResource(id = R.drawable.ic_download),
        contentDescription = text,
        modifier = modifier
    )
}
