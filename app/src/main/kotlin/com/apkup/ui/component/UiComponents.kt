package com.apkup.ui.component

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.apkup.R
import com.apkup.data.repomapping.RepoBinding
import com.apkup.data.ui.AppInstalled
import com.apkup.data.ui.InstallOrigin
import com.apkup.data.ui.resolveInstallOrigin
import com.apkup.repository.RepoMappingManager
import com.apkup.transform.resolveInstallerPackageName
import org.koin.compose.koinInject


@Composable
fun AppImage(app: AppInstalled, onIgnore: (String) -> Unit = {}) = Row(
	modifier = Modifier.fillMaxWidth(),
	verticalAlignment = Alignment.Top,
	horizontalArrangement = Arrangement.SpaceBetween
) {
	Box(
		modifier = Modifier
			.size(40.dp)
			.clip(RoundedCornerShape(12.dp))
			.background(MaterialTheme.colorScheme.surfaceContainerHigh),
		contentAlignment = Alignment.Center
	) {
		LoadingImageApp(app.packageName, Modifier.requiredSize(56.dp))
	}
	IgnoreIcon(
		ignored = app.ignored,
		onClick = { onIgnore(app.packageName) },
		modifier = Modifier.size(30.dp)
	)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InstalledItem(
	app: AppInstalled,
	repoMappingManager: RepoMappingManager = koinInject(),
	onIgnore: (String) -> Unit = {}
) {
	val context = LocalContext.current
	val haptics = LocalHapticFeedback.current
	var showContextMenu by remember { mutableStateOf(false) }
	var showLinkRepoDialog by remember { mutableStateOf(false) }
	val repoBinding = remember(app.packageName, showLinkRepoDialog) {
		repoMappingManager.getBinding(app.packageName)
	}
	val isPlayStoreApp = remember(app.packageName, app.installerPackageName, app.isSystemApp) {
		app.installOrigin == InstallOrigin.PLAY_STORE ||
			resolveInstallOrigin(
				context.resolveInstallerPackageName(app.packageName),
				app.isSystemApp
			) == InstallOrigin.PLAY_STORE
	}

	Box(modifier = Modifier.fillMaxWidth()) {
		val cardShape = RoundedCornerShape(24.dp)
		ElevatedCard(
			modifier = Modifier
				.fillMaxWidth()
				.clip(cardShape)
				.border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)), cardShape)
				.combinedClickable(
					onClick = { showContextMenu = true },
					onLongClick = {
						haptics.performHapticFeedback(HapticFeedbackType.LongPress)
						showContextMenu = true
					}
				)
				.animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
			shape = cardShape,
			colors = CardDefaults.elevatedCardColors(
				containerColor = if (app.ignored) {
					MaterialTheme.colorScheme.surfaceContainer
				} else {
					MaterialTheme.colorScheme.surfaceContainerLow
				}
			),
			elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp, pressedElevation = 3.dp)
		) {
			Column(
				modifier = Modifier.fillMaxWidth().padding(10.dp),
				verticalArrangement = Arrangement.spacedBy(6.dp)
			) {
				AppImage(app, onIgnore)
				Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
					Text(
						text = app.name.ifBlank { app.packageName },
						style = MaterialTheme.typography.labelLarge.copy(
							fontSize = 13.sp,
							lineHeight = 16.sp,
							hyphens = Hyphens.Auto,
							lineBreak = LineBreak.Paragraph
						),
						fontWeight = FontWeight.SemiBold,
						color = MaterialTheme.colorScheme.onSurface,
						maxLines = 2,
						minLines = 2,
						overflow = TextOverflow.Ellipsis
					)
					Text(
						text = app.packageName,
						style = MaterialTheme.typography.labelSmall.copy(
							fontSize = 10.sp,
							lineHeight = 12.sp
						),
						color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)
				}
				Row(
					modifier = Modifier.fillMaxWidth(),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(4.dp)
				) {
					Surface(
						modifier = Modifier.weight(1f, fill = false),
						shape = CircleShape,
						color = MaterialTheme.colorScheme.primaryContainer
					) {
						Text(
							text = app.version.ifBlank { "—" },
							modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
							style = MaterialTheme.typography.labelSmall.copy(
								fontSize = 10.sp,
								lineHeight = 12.sp
							),
							fontWeight = FontWeight.Medium,
							color = MaterialTheme.colorScheme.onPrimaryContainer,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis
						)
					}
					if (!app.enabled) {
						Surface(
							shape = CircleShape,
							color = MaterialTheme.colorScheme.errorContainer
						) {
							Text(
								text = stringResource(R.string.app_disabled),
								modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
								style = MaterialTheme.typography.labelSmall.copy(
									fontSize = 10.sp,
									lineHeight = 12.sp
								),
								color = MaterialTheme.colorScheme.onErrorContainer,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis
							)
						}
					}
				}
			}
		}

		DropdownMenu(
			expanded = showContextMenu,
			onDismissRequest = { showContextMenu = false },
			shape = RoundedCornerShape(24.dp),
			containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
		) {
			DropdownMenuItem(
				text = { Text(stringResource(R.string.app_action_system_details)) },
				leadingIcon = {
					Icon(
						imageVector = Icons.Filled.Info,
						contentDescription = null,
						tint = MaterialTheme.colorScheme.primary
					)
				},
				onClick = {
					showContextMenu = false
					context.openAppSystemSettings(app.packageName)
				}
			)
			if (repoBinding != null) {
				DropdownMenuItem(
					text = {
						Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
							Text(
								stringResource(
									if (repoBinding.isGitLab) R.string.repo_view_on_gitlab
									else R.string.repo_view_on_github
								)
							)
							Text(
								text = repoBinding.webUrl,
								style = MaterialTheme.typography.labelSmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
								maxLines = 1,
								overflow = TextOverflow.Ellipsis
							)
						}
					},
					leadingIcon = {
						Icon(
							painter = painterResource(
								if (repoBinding.isGitLab) R.drawable.ic_gitlab else R.drawable.ic_github
							),
							contentDescription = null,
							tint = MaterialTheme.colorScheme.primary,
							modifier = Modifier.size(20.dp)
						)
					},
					onClick = {
						showContextMenu = false
						context.openUrl(repoBinding.webUrl)
					}
				)
			} else {
				DropdownMenuItem(
					enabled = isPlayStoreApp,
					text = {
						Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
							Text(stringResource(R.string.app_action_open_play_store))
							if (!isPlayStoreApp) {
								Text(
									text = stringResource(R.string.app_action_open_play_store_unavailable),
									style = MaterialTheme.typography.labelSmall,
									color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
								)
							}
						}
					},
					leadingIcon = {
						Icon(
							imageVector = Icons.Outlined.PlayArrow,
							contentDescription = null,
							tint = if (isPlayStoreApp) {
								MaterialTheme.colorScheme.primary
							} else {
								MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
							}
						)
					},
					onClick = {
						showContextMenu = false
						if (isPlayStoreApp) {
							context.openAppInPlayStore(app.packageName)
						}
					}
				)
			}
			DropdownMenuItem(
				text = {
					Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
						Text(
							stringResource(
								if (repoBinding != null) R.string.repo_menu_linked
								else R.string.repo_menu_link
							)
						)
						if (repoBinding != null) {
							Text(
								text = "${if (repoBinding.isGitLab) "GitLab" else "GitHub"}: ${repoBinding.repo}",
								style = MaterialTheme.typography.labelSmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
								maxLines = 1,
								overflow = TextOverflow.Ellipsis
							)
						}
					}
				},
				leadingIcon = {
					Icon(
						painter = painterResource(
							if (repoBinding?.isGitLab == true) R.drawable.ic_gitlab else R.drawable.ic_github
						),
						contentDescription = null,
						tint = if (repoBinding != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
						modifier = Modifier.size(20.dp)
					)
				},
				onClick = {
					showContextMenu = false
					showLinkRepoDialog = true
				}
			)
			DropdownMenuItem(
				text = { Text(stringResource(R.string.app_action_toggle_ignore)) },
				leadingIcon = {
					Icon(
						painter = painterResource(if (app.ignored) R.drawable.ic_visible else R.drawable.ic_visible_off),
						contentDescription = null,
						tint = MaterialTheme.colorScheme.primary,
						modifier = Modifier.size(20.dp)
					)
				},
				onClick = {
					showContextMenu = false
					onIgnore(app.packageName)
				}
			)
			if (!app.isSystemApp) {
				HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
				DropdownMenuItem(
					text = {
						Text(
							text = stringResource(R.string.app_action_uninstall),
							color = MaterialTheme.colorScheme.error
						)
					},
					leadingIcon = {
						Icon(
							imageVector = Icons.Filled.Delete,
							contentDescription = null,
							tint = MaterialTheme.colorScheme.error
						)
					},
					onClick = {
						showContextMenu = false
						context.requestAppUninstall(app.packageName)
					}
				)
			}
		}
	}

	if (showLinkRepoDialog) {
		LinkRepoDialog(
			packageName = app.packageName,
			appName = app.name.ifBlank { app.packageName },
			onDismiss = { showLinkRepoDialog = false }
		)
	}
}

private fun Context.openAppSystemSettings(packageName: String) {
	runCatching {
		val intent = Intent(
			Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
			"package:$packageName".toUri()
		).apply {
			addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
		}
		startActivity(intent)
	}
}

private fun Context.openAppInPlayStore(packageName: String) {
	val marketOpened = runCatching {
		val intent = Intent(
			Intent.ACTION_VIEW,
			"market://details?id=$packageName".toUri()
		).apply {
			addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
		}
		startActivity(intent)
		true
	}.getOrDefault(false)

	if (!marketOpened) {
		runCatching {
			val webIntent = Intent(
				Intent.ACTION_VIEW,
				"https://play.google.com/store/apps/details?id=$packageName".toUri()
			).apply {
				addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
			}
			startActivity(webIntent)
		}
	}
}

private fun Context.requestAppUninstall(packageName: String) {
	runCatching {
		val intent = Intent(
			Intent.ACTION_DELETE,
			"package:$packageName".toUri()
		).apply {
			addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
		}
		startActivity(intent)
	}
}

private fun Context.openUrl(url: String) {
	runCatching {
		val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
			addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
		}
		startActivity(intent)
	}
}

