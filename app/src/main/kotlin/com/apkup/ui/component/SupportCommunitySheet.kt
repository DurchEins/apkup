package com.apkup.ui.component

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MonetizationOn
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.apkup.R

object SupportLinks {
	const val BUY_ME_A_COFFEE = "https://buymeacoffee.com/durcheins"
	const val COINDROP = "https://coindrop.to/durcheins"
	const val GITHUB = "https://github.com/DurchEins/apkup"
	const val GITLAB = "https://gitlab.com/DurchEins1/apkup"
	const val ALTERNATIVETO = "https://alternativeto.net/software/apkup/about/"
	const val KOMI_STORE = "https://komistore.app"
	const val ZAPSTORE = "https://zapstore.dev/apps/com.apkup"
	const val APKPURE = "https://apkpure.com/apkup/com.apkup"
	const val ITCH_IO = "https://durcheins.itch.io/apkup"
	const val APPTEKA = "https://appteka.store/app/c0cr341070"
}

private data class CommunityPlatformLink(
	val label: String,
	val url: String
)

private val communityPlatforms = listOf(
	CommunityPlatformLink("⭐ GitHub", SupportLinks.GITHUB),
	CommunityPlatformLink("🦊 GitLab", SupportLinks.GITLAB),
	CommunityPlatformLink("🔵 AlternativeTo", SupportLinks.ALTERNATIVETO),
	CommunityPlatformLink("🛍️ Komi Store", SupportLinks.KOMI_STORE),
	CommunityPlatformLink("⚡ Zapstore", SupportLinks.ZAPSTORE),
	CommunityPlatformLink("▶️ APKPure", SupportLinks.APKPURE),
	CommunityPlatformLink("🎮 itch.io", SupportLinks.ITCH_IO),
	CommunityPlatformLink("💎 Appteka", SupportLinks.APPTEKA)
)

/**
 * Opens [url] in a themed Chrome Custom Tab so the user stays in-app while keeping browser
 * logins, passkeys, and Google Pay available, falling back to a standard VIEW intent if needed.
 */
fun openInCustomTab(
	context: Context,
	url: String,
	toolbarColorArgb: Int
) {
	if (!com.apkup.util.isSafeHttpsUrl(url)) return
	val uri = runCatching { Uri.parse(url.trim()) }.getOrNull() ?: return
	val launchedCustomTab = runCatching {
		val colorParams = CustomTabColorSchemeParams.Builder()
			.setToolbarColor(toolbarColorArgb)
			.build()
		val customTabsIntent = CustomTabsIntent.Builder()
			.setDefaultColorSchemeParams(colorParams)
			.setShowTitle(true)
			.setUrlBarHidingEnabled(true)
			.build()
		customTabsIntent.launchUrl(context, uri)
	}.isSuccess
	if (!launchedCustomTab) {
		runCatching {
			context.startActivity(
				Intent(Intent.ACTION_VIEW, uri).apply {
					addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
				}
			)
		}
	}
}

@Composable
fun rememberCustomTabOpener(): (String) -> Unit {
	val context = LocalContext.current
	val toolbarColorArgb = MaterialTheme.colorScheme.surfaceContainerHigh.toArgb()
	return remember(context, toolbarColorArgb) {
		{ url -> openInCustomTab(context, url, toolbarColorArgb) }
	}
}

@Composable
fun rememberCustomTabUriHandler(): androidx.compose.ui.platform.UriHandler {
	val openCustomTab = rememberCustomTabOpener()
	return remember(openCustomTab) {
		object : androidx.compose.ui.platform.UriHandler {
			override fun openUri(uri: String) = openCustomTab(uri)
		}
	}
}

/**
 * Shared Material 3 Expressive bottom sheet presenting voluntary funding links (Buy Me a Coffee & Coindrop)
 * alongside free community support links (GitHub/GitLab stars and store reviews).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SupportCommunityBottomSheet(
	onDismiss: () -> Unit
) {
	val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
	val openUrl = rememberCustomTabOpener()
	val bmacTitle = stringResource(R.string.support_bmac_title)
	val coindropTitle = stringResource(R.string.support_coindrop_title)

	ModalBottomSheet(
		onDismissRequest = onDismiss,
		sheetState = sheetState,
		containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
		shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.verticalScroll(rememberScrollState())
				.padding(horizontal = 20.dp)
				.padding(bottom = 24.dp)
				.navigationBarsPadding(),
			verticalArrangement = Arrangement.spacedBy(16.dp)
		) {
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(12.dp)
			) {
				Box(
					modifier = Modifier
						.size(40.dp)
						.clip(RoundedCornerShape(12.dp))
						.background(MaterialTheme.colorScheme.primaryContainer),
					contentAlignment = Alignment.Center
				) {
					Icon(
						imageVector = Icons.Rounded.Favorite,
						contentDescription = null,
						tint = MaterialTheme.colorScheme.primary,
						modifier = Modifier.size(22.dp)
					)
				}
				Text(
					text = stringResource(R.string.support_sheet_title),
					style = MaterialTheme.typography.titleLarge,
					fontWeight = FontWeight.Bold,
					color = MaterialTheme.colorScheme.onSurface
				)
			}

			Text(
				text = stringResource(R.string.support_sheet_description),
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)

			Text(
				text = stringResource(R.string.support_section_direct),
				style = MaterialTheme.typography.labelLarge,
				fontWeight = FontWeight.SemiBold,
				color = MaterialTheme.colorScheme.primary
			)

			Row(
				modifier = Modifier
					.fillMaxWidth()
					.height(IntrinsicSize.Min),
				horizontalArrangement = Arrangement.spacedBy(10.dp)
			) {
				SupportDonationCard(
					title = bmacTitle,
					icon = Icons.Rounded.Coffee,
					modifier = Modifier
						.weight(1f)
						.fillMaxHeight(),
					onClick = { openUrl(SupportLinks.BUY_ME_A_COFFEE) }
				)
				SupportDonationCard(
					title = coindropTitle,
					icon = Icons.Rounded.MonetizationOn,
					modifier = Modifier
						.weight(1f)
						.fillMaxHeight(),
					onClick = { openUrl(SupportLinks.COINDROP) }
				)
			}

			HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

			Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
				Text(
					text = stringResource(R.string.support_section_free),
					style = MaterialTheme.typography.labelLarge,
					fontWeight = FontWeight.SemiBold,
					color = MaterialTheme.colorScheme.primary
				)
				Text(
					text = stringResource(R.string.support_section_free_desc),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
				FlowRow(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.spacedBy(8.dp),
					verticalArrangement = Arrangement.spacedBy(8.dp)
				) {
					communityPlatforms.forEach { platform ->
						Surface(
							onClick = { openUrl(platform.url) },
							shape = RoundedCornerShape(10.dp),
							color = MaterialTheme.colorScheme.surfaceContainerHigh,
							border = BorderStroke(
								1.dp,
								MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
							)
						) {
							Row(
								modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(5.dp)
							) {
								Text(
									text = platform.label,
									style = MaterialTheme.typography.labelMedium,
									fontWeight = FontWeight.Medium,
									color = MaterialTheme.colorScheme.onSurface
								)
								Icon(
									painter = painterResource(R.drawable.ic_open_in_new),
									contentDescription = null,
									tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
									modifier = Modifier.size(12.dp)
								)
							}
						}
					}
				}
			}
		}
	}
}

@Composable
private fun SupportDonationCard(
	title: String,
	icon: ImageVector,
	modifier: Modifier = Modifier,
	onClick: () -> Unit
) {
	Surface(
		onClick = onClick,
		modifier = modifier,
		shape = RoundedCornerShape(14.dp),
		color = MaterialTheme.colorScheme.surfaceContainerHigh,
		border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 12.dp, vertical = 12.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(8.dp)
		) {
			Box(
				modifier = Modifier
					.size(30.dp)
					.clip(RoundedCornerShape(8.dp))
					.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)),
				contentAlignment = Alignment.Center
			) {
				Icon(
					imageVector = icon,
					contentDescription = null,
					tint = MaterialTheme.colorScheme.primary,
					modifier = Modifier.size(17.dp)
				)
			}
			Text(
				text = title,
				style = MaterialTheme.typography.labelLarge,
				fontWeight = FontWeight.SemiBold,
				color = MaterialTheme.colorScheme.onSurface,
				maxLines = 2,
				overflow = TextOverflow.Ellipsis,
				modifier = Modifier.weight(1f)
			)
			Icon(
				painter = painterResource(R.drawable.ic_open_in_new),
				contentDescription = null,
				tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
				modifier = Modifier.size(13.dp)
			)
		}
	}
}

/**
 * Gentle, one-time reminder dialog shown after 5 days of usage or 5 successful updates.
 */
@Composable
fun SupportReminderDialog(
	onStarOnGitHub: () -> Unit,
	onOpenSupportSheet: () -> Unit,
	onSnooze: () -> Unit,
	onDismissPermanently: () -> Unit
) {
	AlertDialog(
		onDismissRequest = onSnooze,
		icon = {
			Icon(
				imageVector = Icons.Rounded.Star,
				contentDescription = null,
				tint = MaterialTheme.colorScheme.primary,
				modifier = Modifier.size(28.dp)
			)
		},
		title = {
			Text(
				text = stringResource(R.string.support_prompt_title),
				fontWeight = FontWeight.Bold
			)
		},
		text = {
			Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
				Text(
					text = stringResource(R.string.support_prompt_message),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
				Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
					Button(
						onClick = onStarOnGitHub,
						modifier = Modifier.fillMaxWidth(),
						shape = RoundedCornerShape(12.dp)
					) {
						Icon(
							imageVector = Icons.Rounded.Star,
							contentDescription = null,
							modifier = Modifier.size(18.dp)
						)
						Spacer(modifier = Modifier.width(8.dp))
						Text(text = stringResource(R.string.support_prompt_cta_github))
					}
					FilledTonalButton(
						onClick = onOpenSupportSheet,
						modifier = Modifier.fillMaxWidth(),
						shape = RoundedCornerShape(12.dp)
					) {
						Icon(
							imageVector = Icons.Rounded.Favorite,
							contentDescription = null,
							modifier = Modifier.size(18.dp)
						)
						Spacer(modifier = Modifier.width(8.dp))
						Text(text = stringResource(R.string.support_prompt_cta_options))
					}
				}
			}
		},
		confirmButton = {
			TextButton(onClick = onDismissPermanently) {
				Text(text = stringResource(R.string.support_prompt_never))
			}
		},
		dismissButton = {
			TextButton(onClick = onSnooze) {
				Text(text = stringResource(R.string.support_prompt_later))
			}
		}
	)
}
