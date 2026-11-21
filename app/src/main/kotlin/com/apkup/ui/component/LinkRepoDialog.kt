package com.apkup.ui.component

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.apkup.R
import com.apkup.ui.theme.ApkUpTheme
import com.apkup.data.repomapping.ReleaseAssetFilter
import com.apkup.data.repomapping.RepoBinding
import com.apkup.data.snack.TextSnack
import com.apkup.repository.CodebergRepository
import com.apkup.repository.GitHubRepository
import com.apkup.repository.GitLabRepository
import com.apkup.repository.RepoHostValidator
import com.apkup.repository.RepoMappingManager
import com.apkup.util.SnackBar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkRepoDialog(
    packageName: String,
    appName: String,
    onDismiss: () -> Unit,
    repoMappingManager: RepoMappingManager = koinInject(),
    gitHubRepository: GitHubRepository = koinInject(),
    gitLabRepository: GitLabRepository = koinInject(),
    codebergRepository: CodebergRepository = koinInject(),
    snackBar: SnackBar = koinInject()
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val coroutineScope = rememberCoroutineScope()
    val existingBinding = remember(packageName) { repoMappingManager.getBinding(packageName) }
    val isCustom = remember(packageName) { repoMappingManager.getCustomBindings().containsKey(packageName) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedType by remember { mutableStateOf(existingBinding?.type ?: "github") }
    var repoInput by remember { mutableStateOf(existingBinding?.repo.orEmpty()) }
    var hostInput by remember { mutableStateOf(existingBinding?.host.orEmpty()) }
    var assetFilterInput by remember { mutableStateOf(existingBinding?.assetFilterRegex.orEmpty()) }
    val isRegexValid = remember(assetFilterInput) { ReleaseAssetFilter.isValidRegex(assetFilterInput) }

    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<TestResult?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(
                            when (selectedType) {
                                "gitlab" -> R.drawable.ic_gitlab
                                "codeberg" -> R.drawable.ic_codeberg
                                else -> R.drawable.ic_github
                            }
                        ),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.link_repo_dialog_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = appName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Text(
                text = stringResource(R.string.link_repo_dialog_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.link_repo_platform),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = selectedType == "github",
                            onClick = {
                                selectedType = "github"
                                testResult = null
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_github),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        ) {
                            Text("GitHub", maxLines = 1)
                        }
                        SegmentedButton(
                            selected = selectedType == "gitlab",
                            onClick = {
                                selectedType = "gitlab"
                                testResult = null
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_gitlab),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        ) {
                            Text("GitLab", maxLines = 1)
                        }
                        SegmentedButton(
                            selected = selectedType == "codeberg",
                            onClick = {
                                selectedType = "codeberg"
                                testResult = null
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_codeberg),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        ) {
                            Text("Codeberg", maxLines = 1)
                        }
                    }

                    OutlinedTextField(
                        value = repoInput,
                        onValueChange = {
                            repoInput = it
                            testResult = null
                            val pastedUrl = normalizePastedRepoUrlOrNull(it)
                            if (pastedUrl != null) {
                                val parsed = repoMappingManager.parseRepoUrl(pastedUrl, allowLanHosts = true, hintSource = selectedType)
                                if (parsed != null) {
                                    selectedType = parsed.type
                                    repoInput = parsed.repo
                                    if (!parsed.host.isNullOrBlank()) {
                                        hostInput = parsed.host
                                    }
                                }
                            }
                        },
                        label = { Text(stringResource(R.string.link_repo_input_label)) },
                        placeholder = { Text(stringResource(R.string.link_repo_input_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    val previewBinding = remember(repoInput, selectedType, hostInput) {
                        buildBinding(repoMappingManager, selectedType, repoInput, hostInput, "")
                    }
                    val isReducedMotion = ApkUpTheme.isReducedMotion
                    AnimatedVisibility(
                        visible = previewBinding != null,
                        enter = if (isReducedMotion) fadeIn() else fadeIn() + expandVertically(),
                        exit = if (isReducedMotion) fadeOut() else fadeOut() + shrinkVertically()
                    ) {
                        previewBinding?.let { parsed ->
                            val providerIcon = when (parsed.type) {
                                "gitlab" -> R.drawable.ic_gitlab
                                "codeberg" -> R.drawable.ic_codeberg
                                else -> R.drawable.ic_github
                            }
                            val providerName = when (parsed.type) {
                                "gitlab" -> "GitLab"
                                "codeberg" -> "Codeberg"
                                else -> "GitHub"
                            }
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(providerIcon),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.link_repo_preview_label),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f)
                                    )
                                    Text(
                                        text = "$providerName · ${parsed.repo}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    if (selectedType == "gitlab" || selectedType == "codeberg") {
                        OutlinedTextField(
                            value = hostInput,
                            onValueChange = {
                                hostInput = it
                                testResult = null
                            },
                            label = { Text(stringResource(R.string.link_repo_host_label)) },
                            placeholder = {
                                Text(
                                    stringResource(
                                        if (selectedType == "codeberg") R.string.link_repo_host_hint_codeberg
                                        else R.string.link_repo_host_hint
                                    )
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    OutlinedTextField(
                        value = assetFilterInput,
                        onValueChange = {
                            assetFilterInput = it
                            testResult = null
                        },
                        label = { Text(stringResource(R.string.link_repo_regex_label)) },
                        placeholder = { Text(stringResource(R.string.link_repo_regex_hint)) },
                        isError = !isRegexValid,
                        supportingText = if (!isRegexValid) {
                            { Text(stringResource(R.string.link_repo_regex_invalid)) }
                        } else null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedButton(
                        onClick = {
                            val binding = buildBinding(repoMappingManager, selectedType, repoInput, hostInput, assetFilterInput)
                            if (binding == null) {
                                testResult = TestResult.Error(resources.getString(R.string.link_repo_invalid_format))
                                return@OutlinedButton
                            }
                            isTesting = true
                            testResult = null
                            coroutineScope.launch {
                                val res = runTest(context, binding, gitHubRepository, gitLabRepository, codebergRepository)
                                testResult = res
                                isTesting = false
                            }
                        },
                        enabled = !isTesting && repoInput.isNotBlank() && isRegexValid,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.link_repo_testing))
                        } else {
                            Text(stringResource(R.string.link_repo_btn_test))
                        }
                    }

                    testResult?.let { result ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = when (result) {
                                is TestResult.Success -> MaterialTheme.colorScheme.primaryContainer
                                is TestResult.Warning -> MaterialTheme.colorScheme.tertiaryContainer
                                is TestResult.Error -> MaterialTheme.colorScheme.errorContainer
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = when (result) {
                                        is TestResult.Success -> Icons.Filled.CheckCircle
                                        is TestResult.Warning -> Icons.Filled.Warning
                                        is TestResult.Error -> Icons.Filled.Warning
                                    },
                                    contentDescription = null,
                                    tint = when (result) {
                                        is TestResult.Success -> MaterialTheme.colorScheme.onPrimaryContainer
                                        is TestResult.Warning -> MaterialTheme.colorScheme.onTertiaryContainer
                                        is TestResult.Error -> MaterialTheme.colorScheme.onErrorContainer
                                    },
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = result.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when (result) {
                                        is TestResult.Success -> MaterialTheme.colorScheme.onPrimaryContainer
                                        is TestResult.Warning -> MaterialTheme.colorScheme.onTertiaryContainer
                                        is TestResult.Error -> MaterialTheme.colorScheme.onErrorContainer
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
                Button(
                    onClick = {
                        val binding = buildBinding(repoMappingManager, selectedType, repoInput, hostInput, assetFilterInput)
                        if (binding != null) {
                            repoMappingManager.setCustomBinding(packageName, binding)
                            snackBar.snackBar(
                                coroutineScope,
                                TextSnack(
                                    message = resources.getString(R.string.link_repo_saved_snack, appName),
                                    visualType = com.apkup.data.snack.SnackVisualType.SUCCESS
                                )
                            )
                            onDismiss()
                        } else {
                            testResult = TestResult.Error(resources.getString(R.string.link_repo_invalid_format))
                        }
                    },
                    enabled = repoInput.isNotBlank() && isRegexValid,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(stringResource(R.string.link_repo_btn_save))
                }
            }

            if (isCustom || existingBinding != null) {
                TextButton(
                    onClick = {
                        repoMappingManager.setCustomBinding(packageName, null)
                        snackBar.snackBar(
                            coroutineScope,
                            TextSnack(
                                message = resources.getString(R.string.link_repo_unlinked_snack, appName),
                                visualType = com.apkup.data.snack.SnackVisualType.SUCCESS
                            )
                        )
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.link_repo_btn_unlink),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

private sealed class TestResult(val message: String) {
    class Success(message: String) : TestResult(message)
    class Warning(message: String) : TestResult(message)
    class Error(message: String) : TestResult(message)
}

internal fun normalizePastedRepoUrlOrNull(rawInput: String): String? {
    val trimmed = rawInput.trim()
    return when {
        trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
        trimmed.startsWith("github.com/", ignoreCase = true) ||
            trimmed.startsWith("www.github.com/", ignoreCase = true) ||
            trimmed.startsWith("gitlab.com/", ignoreCase = true) ||
            trimmed.startsWith("www.gitlab.com/", ignoreCase = true) ||
            trimmed.startsWith("codeberg.org/", ignoreCase = true) ||
            trimmed.startsWith("www.codeberg.org/", ignoreCase = true) -> "https://$trimmed"
        else -> null
    }
}

internal fun buildBinding(
    manager: RepoMappingManager,
    type: String,
    repoInput: String,
    hostInput: String,
    assetFilterInput: String = ""
): RepoBinding? {
    val cleanRegex = assetFilterInput.trim().takeIf { it.isNotEmpty() }
    if (cleanRegex != null && !ReleaseAssetFilter.isValidRegex(cleanRegex)) {
        return null
    }
    val supportsCustomHost = type == "gitlab" || type == "codeberg"
    val normalizedInput = normalizePastedRepoUrlOrNull(repoInput) ?: repoInput
    val parsed = manager.parseRepoUrl(normalizedInput, allowLanHosts = true, hintSource = type)
    if (parsed != null) {
        val parsedParts = parsed.repo.split("/").filter { it.isNotBlank() }
        val validTargetArity = if (type == "gitlab") parsedParts.size >= 2 else parsedParts.size == 2
        if (!validTargetArity || !RepoHostValidator.isValidRepoSlugSegments(parsedParts)) {
            return null
        }
        val candidate = if (supportsCustomHost && hostInput.isNotBlank()) {
            parsed.copy(type = type, host = hostInput.trim(), assetFilterRegex = cleanRegex)
        } else {
            parsed.copy(type = type, host = if (supportsCustomHost) parsed.host else null, assetFilterRegex = cleanRegex)
        }
        return candidate.takeIf { RepoHostValidator.isAllowedRepoHost(it.resolvedHost, allowLanHosts = true) }
    }
    val trimmed = repoInput.trim()
    if (trimmed.any { it.isWhitespace() || it in "?#@:" }) return null
    val parts = trimmed.split("/").filter { it.isNotBlank() }
    val validArity = if (type == "gitlab") parts.size >= 2 else parts.size == 2
    if (validArity) {
        val cleanedParts = parts.mapIndexed { idx, seg ->
            if (idx == parts.lastIndex) seg.removeSuffix(".git") else seg
        }
        if (!RepoHostValidator.isValidRepoSlugSegments(cleanedParts)) return null
        val candidate = RepoBinding(
            type = type,
            repo = cleanedParts.joinToString("/"),
            host = if (supportsCustomHost && hostInput.isNotBlank()) hostInput.trim() else null,
            assetFilterRegex = cleanRegex
        )
        return candidate.takeIf { RepoHostValidator.isAllowedRepoHost(it.resolvedHost, allowLanHosts = true) }
    }
    return null
}

private suspend fun runTest(
    context: android.content.Context,
    binding: RepoBinding,
    gitHubRepository: GitHubRepository,
    gitLabRepository: GitLabRepository,
    codebergRepository: CodebergRepository
): TestResult = withContext(Dispatchers.IO) {
    try {
        if (binding.isGitLab) {
            val release = gitLabRepository.fetchLatestRelease(binding, allowLanHost = true)
                ?: return@withContext TestResult.Error(context.getString(R.string.link_repo_test_failed, "HTTP 404 / Release not found"))
            val tag = release.safeTagName.ifBlank { release.safeName }
            val apk = gitLabRepository.extractBestApkLink(release, assetFilterRegex = binding.assetFilterRegex)
            if (apk != null) {
                TestResult.Success(context.getString(R.string.link_repo_test_success, tag, apk.safeName))
            } else {
                TestResult.Warning(context.getString(R.string.link_repo_test_no_apk, tag))
            }
        } else {
            val release = if (binding.isCodeberg) {
                codebergRepository.fetchLatestRelease(binding, allowLanHost = true)
            } else {
                gitHubRepository.fetchLatestRelease(binding.repo)
            } ?: return@withContext TestResult.Error(context.getString(R.string.link_repo_test_failed, "HTTP 404 or Rate Limit"))
            val tag = release.safeTagName.ifBlank { release.safeName }
            val apk = ReleaseAssetFilter.findBestApk(
                assets = release.safeAssets,
                getFilename = { it.safeName },
                assetFilterRegex = binding.assetFilterRegex.orEmpty()
            )
            if (apk != null) {
                val sizeStr = Formatter.formatFileSize(context, apk.size)
                TestResult.Success(context.getString(R.string.link_repo_test_success, tag, "${apk.safeName} ($sizeStr)"))
            } else {
                TestResult.Warning(context.getString(R.string.link_repo_test_no_apk, tag))
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        TestResult.Error(context.getString(R.string.link_repo_test_failed, e.message ?: e.javaClass.simpleName))
    }
}
