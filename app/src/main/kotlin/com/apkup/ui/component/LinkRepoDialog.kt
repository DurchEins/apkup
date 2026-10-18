package com.apkup.ui.component

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.apkup.R
import com.apkup.data.repomapping.ReleaseAssetFilter
import com.apkup.data.repomapping.RepoBinding
import com.apkup.data.snack.TextSnack
import com.apkup.repository.GitHubRepository
import com.apkup.repository.GitLabRepository
import com.apkup.repository.RepoMappingManager
import com.apkup.util.SnackBar
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
    snackBar: SnackBar = koinInject()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val existingBinding = remember(packageName) { repoMappingManager.getBinding(packageName) }
    val isCustom = remember(packageName) { repoMappingManager.getCustomBindings().containsKey(packageName) }

    var selectedType by remember { mutableStateOf(existingBinding?.type ?: "github") }
    var repoInput by remember { mutableStateOf(existingBinding?.repo.orEmpty()) }
    var hostInput by remember { mutableStateOf(existingBinding?.host.orEmpty()) }

    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<TestResult?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(
                        if (selectedType == "gitlab") R.drawable.ic_gitlab else R.drawable.ic_github
                    ),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.link_repo_dialog_title),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = appName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.link_repo_dialog_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Platform selection
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = selectedType == "github",
                        onClick = {
                            selectedType = "github"
                            testResult = null
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_github),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    ) {
                        Text("GitHub")
                    }
                    SegmentedButton(
                        selected = selectedType == "gitlab",
                        onClick = {
                            selectedType = "gitlab"
                            testResult = null
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_gitlab),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    ) {
                        Text("GitLab")
                    }
                }

                // Repo input
                OutlinedTextField(
                    value = repoInput,
                    onValueChange = {
                        repoInput = it
                        testResult = null
                        val parsed = repoMappingManager.parseRepoUrl(it)
                        if (parsed != null) {
                            selectedType = parsed.type
                            repoInput = parsed.repo
                            if (!parsed.host.isNullOrBlank()) {
                                hostInput = parsed.host
                            }
                        }
                    },
                    label = { Text(stringResource(R.string.link_repo_input_label)) },
                    placeholder = { Text(stringResource(R.string.link_repo_input_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Host input (if GitLab)
                if (selectedType == "gitlab") {
                    OutlinedTextField(
                        value = hostInput,
                        onValueChange = {
                            hostInput = it
                            testResult = null
                        },
                        label = { Text(stringResource(R.string.link_repo_host_label)) },
                        placeholder = { Text(stringResource(R.string.link_repo_host_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Test Connection Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = {
                            val binding = buildBinding(repoMappingManager, selectedType, repoInput, hostInput)
                            if (binding == null) {
                                testResult = TestResult.Error("Bitte Repository im Format 'owner/repo' angeben")
                                return@FilledTonalButton
                            }
                            isTesting = true
                            testResult = null
                            coroutineScope.launch {
                                val res = runTest(context, binding, gitHubRepository, gitLabRepository)
                                testResult = res
                                isTesting = false
                            }
                        },
                        enabled = !isTesting && repoInput.isNotBlank()
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.link_repo_testing))
                        } else {
                            Text(stringResource(R.string.link_repo_btn_test))
                        }
                    }
                }

                // Test Result Display
                testResult?.let { result ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = when (result) {
                            is TestResult.Success -> Color(0xFFE8F5E9)
                            is TestResult.Warning -> Color(0xFFFFF3E0)
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
                                    is TestResult.Success -> Color(0xFF2E7D32)
                                    is TestResult.Warning -> Color(0xFFE65100)
                                    is TestResult.Error -> MaterialTheme.colorScheme.onErrorContainer
                                },
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = result.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = when (result) {
                                    is TestResult.Success -> Color(0xFF2E7D32)
                                    is TestResult.Warning -> Color(0xFFE65100)
                                    is TestResult.Error -> MaterialTheme.colorScheme.onErrorContainer
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val binding = buildBinding(repoMappingManager, selectedType, repoInput, hostInput)
                    if (binding != null) {
                        repoMappingManager.setCustomBinding(packageName, binding)
                        snackBar.snackBar(
                            coroutineScope,
                            TextSnack(context.getString(R.string.link_repo_saved_snack, appName))
                        )
                    }
                    onDismiss()
                },
                enabled = repoInput.isNotBlank()
            ) {
                Text(stringResource(R.string.link_repo_btn_save))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isCustom || existingBinding != null) {
                    TextButton(
                        onClick = {
                            repoMappingManager.setCustomBinding(packageName, null)
                            snackBar.snackBar(
                                coroutineScope,
                                TextSnack(context.getString(R.string.link_repo_unlinked_snack, appName))
                            )
                            onDismiss()
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.link_repo_btn_unlink),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        }
    )
}

private sealed class TestResult(val message: String) {
    class Success(message: String) : TestResult(message)
    class Warning(message: String) : TestResult(message)
    class Error(message: String) : TestResult(message)
}

private fun buildBinding(
    manager: RepoMappingManager,
    type: String,
    repoInput: String,
    hostInput: String
): RepoBinding? {
    val parsed = manager.parseRepoUrl(repoInput)
    if (parsed != null) {
        return if (type == "gitlab" && hostInput.isNotBlank()) {
            parsed.copy(type = "gitlab", host = hostInput.trim())
        } else {
            parsed.copy(type = type)
        }
    }
    val trimmed = repoInput.trim()
    val parts = trimmed.split("/").filter { it.isNotBlank() }
    if (parts.size >= 2) {
        val cleanRepo = "${parts[0]}/${parts.drop(1).joinToString("/").removeSuffix(".git")}"
        return RepoBinding(
            type = type,
            repo = cleanRepo,
            host = if (type == "gitlab" && hostInput.isNotBlank()) hostInput.trim() else null
        )
    }
    return null
}

private suspend fun runTest(
    context: android.content.Context,
    binding: RepoBinding,
    gitHubRepository: GitHubRepository,
    gitLabRepository: GitLabRepository
): TestResult = withContext(Dispatchers.IO) {
    runCatching {
        if (binding.type.equals("gitlab", ignoreCase = true)) {
            val release = gitLabRepository.fetchLatestRelease(binding)
                ?: return@withContext TestResult.Error(context.getString(R.string.link_repo_test_failed, "HTTP 404 / Release not found"))
            val tag = release.tag_name.ifBlank { release.name }
            val apk = gitLabRepository.extractBestApkLink(release)
            if (apk != null) {
                TestResult.Success(context.getString(R.string.link_repo_test_success, tag, apk.name))
            } else {
                TestResult.Warning(context.getString(R.string.link_repo_test_no_apk, tag))
            }
        } else {
            val release = gitHubRepository.fetchLatestRelease(binding.repo)
                ?: return@withContext TestResult.Error(context.getString(R.string.link_repo_test_failed, "HTTP 404 or Rate Limit"))
            val tag = release.tag_name.ifBlank { release.name }
            val apk = ReleaseAssetFilter.findBestApk(release.assets, { it.name })
            if (apk != null) {
                val sizeStr = Formatter.formatFileSize(context, apk.size)
                TestResult.Success(context.getString(R.string.link_repo_test_success, tag, "${apk.name} ($sizeStr)"))
            } else {
                TestResult.Warning(context.getString(R.string.link_repo_test_no_apk, tag))
            }
        }
    }.getOrElse { e ->
        TestResult.Error(context.getString(R.string.link_repo_test_failed, e.message ?: e.javaClass.simpleName))
    }
}
