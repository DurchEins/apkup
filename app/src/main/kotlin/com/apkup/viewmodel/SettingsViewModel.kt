package com.apkup.viewmodel

import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.apkup.BuildConfig
import com.apkup.R
import com.apkup.data.fdroid.FDroidRepoConfig
import com.apkup.data.obtainium.ObtainiumImportPreview
import com.apkup.data.obtainium.ObtainiumManager
import com.apkup.data.repomapping.ReleaseAssetFilter
import com.apkup.data.repomapping.RepoBinding
import com.apkup.data.snack.TextSnack
import com.apkup.data.ui.ApkUpBackupConfig
import com.apkup.data.ui.BackgroundCheckInterval
import com.apkup.data.ui.CustomFDroidRepo
import com.apkup.data.ui.IgnoredVersionEntry
import com.apkup.data.ui.ShizukuSetupStatus
import com.apkup.data.ui.SourcePriorityKeys
import com.apkup.data.ui.WatchedAppEntry
import com.apkup.prefs.Prefs
import com.apkup.repository.RepoHostValidator
import com.apkup.repository.RepoMappingManager
import com.apkup.ui.theme.isDarkTheme
import com.apkup.util.ApkIntegrityVerifier
import com.apkup.util.AppJournal
import com.apkup.util.SessionInstaller
import com.apkup.util.SnackBar
import com.apkup.util.Stringer
import com.apkup.util.Themer
import com.apkup.util.UpdatesNotification
import com.apkup.worker.ShizukuAutoUpdateWorker
import com.apkup.worker.UpdatesWorker
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.Reader

sealed class TokenValidationResult {
	data class Valid(
		val username: String,
		val rateLimitRemaining: String? = null,
		val rateLimitTotal: String? = null
	) : TokenValidationResult()

	data class Invalid(val httpCode: Int) : TokenValidationResult()
	data class Error(val message: String) : TokenValidationResult()
}

enum class CustomFDroidAddResult {
	SUCCESS,
	INVALID_URL,
	DISALLOWED_HOST,
	INVALID_FINGERPRINT
}

data class BackupImportResult(
	val success: Boolean,
	val rootResetForSecurity: Boolean = false
)

class SettingsViewModel(
	private val prefs: Prefs,
	@Suppress("UNUSED_PARAMETER")
	notification: UpdatesNotification? = null,
	private val workManager: WorkManager? = null,
	private val themer: Themer? = null,
	private val sessionInstaller: SessionInstaller? = null,
	private val snackBar: SnackBar? = null,
	private val stringer: Stringer? = null,
	private val gson: Gson = GsonBuilder().setPrettyPrinting().create(),
	private val okHttpClient: OkHttpClient = OkHttpClient(),
	private val repoMappingManager: RepoMappingManager? = null,
	private val appJournal: AppJournal? = null,
	private val rollbackBackupManager: com.apkup.util.RollbackBackupManager? = null
) : ViewModel() {

	companion object {
		const val MAX_BACKUP_JSON_CHARS = 10 * 1024 * 1024
		private const val MAX_AUTH_RESPONSE_CHARS = 65_536
	}

	private val ignoredVersionsFlow = MutableStateFlow(buildIgnoredVersionEntries())
	private val ignoredAppsFlow = MutableStateFlow(prefs.ignoredApps.get())
	private val shizukuSetupStatusFlow = MutableStateFlow(
		sessionInstaller?.getShizukuSetupStatus() ?: ShizukuSetupStatus()
	)
	private val shizukuEnabledFlow = MutableStateFlow(
		prefs.useShizukuInstaller.get() && shizukuSetupStatusFlow.value.isReady
	)
	private val rootEnabledFlow = MutableStateFlow(prefs.useRootInstaller.get())

	val sourcePriorityOrderFlow: StateFlow<List<String>> = prefs.sourcePriorityOrderFlow

	fun getSourcePriorityOrder(): List<String> = prefs.readSourcePriorityOrderSafe()

	fun moveSourcePriority(index: Int, delta: Int) {
		val current = prefs.readSourcePriorityOrderSafe().toMutableList()
		val target = index + delta
		if (index !in current.indices || target !in current.indices) return
		val item = current.removeAt(index)
		current.add(target, item)
		prefs.writeSourcePriorityOrder(current)
	}

	fun resetSourcePriorityOrder() {
		prefs.writeSourcePriorityOrder(SourcePriorityKeys.DEFAULT_ORDER)
	}
	private val rootAvailableFlow = MutableStateFlow<Boolean?>(null)
	private val customDownloadFolderUriFlow = MutableStateFlow(prefs.customDownloadFolderUri.get())
	private val showShizukuSetupSheetFlow = MutableStateFlow(false)
	private var pendingEnableOnReady = false
	private var unregisterShizukuListeners: (() -> Unit)? = null

	val fdroidReposFlow: StateFlow<List<FDroidRepoConfig>> = prefs.fdroidReposFlow

	init {
		if (!shizukuSetupStatusFlow.value.isReady && prefs.useShizukuInstaller.get()) {
			prefs.useShizukuInstaller.put(false)
		}
		unregisterShizukuListeners = sessionInstaller?.observeShizukuEvents(
			onPermissionResult = { granted ->
				val latest = sessionInstaller.getShizukuSetupStatus()
				shizukuSetupStatusFlow.value = latest
				if (granted && latest.isReady) {
					prefs.useShizukuInstaller.put(true)
					shizukuEnabledFlow.value = true
					pendingEnableOnReady = false
					showShizukuSetupSheetFlow.value = false
				}
			},
			onBinderChanged = {
				refreshShizukuStatus()
			}
		)
	}

	override fun onCleared() {
		unregisterShizukuListeners?.invoke()
		unregisterShizukuListeners = null
		super.onCleared()
	}

	fun ignoredVersions(): StateFlow<List<IgnoredVersionEntry>> = ignoredVersionsFlow
	fun shizukuEnabled(): StateFlow<Boolean> = shizukuEnabledFlow
	fun rootEnabled(): StateFlow<Boolean> = rootEnabledFlow
	fun rootAvailable(): StateFlow<Boolean?> = rootAvailableFlow
	fun customDownloadFolderUri(): StateFlow<String> = customDownloadFolderUriFlow
	fun shizukuSetupStatus(): StateFlow<ShizukuSetupStatus> = shizukuSetupStatusFlow
	fun showShizukuSetupSheet(): StateFlow<Boolean> = showShizukuSetupSheetFlow

	fun getUseRootInstaller(): Boolean = rootEnabledFlow.value

	fun setUseRootInstaller(enabled: Boolean) {
		if (!enabled) {
			prefs.useRootInstaller.put(false)
			rootEnabledFlow.value = false
			if (!getUseShizukuInstaller()) {
				workManager?.let { ShizukuAutoUpdateWorker.cancel(it) }
			}
			return
		}
		viewModelScope.launch(Dispatchers.IO) {
			val available = sessionInstaller?.testRootAvailability() ?: false
			rootAvailableFlow.value = available
			if (available) {
				prefs.useRootInstaller.put(true)
				rootEnabledFlow.value = true
				notifySnack(R.string.root_installer_enabled_ok)
			} else {
				prefs.useRootInstaller.put(false)
				rootEnabledFlow.value = false
				notifySnack(R.string.root_installer_unavailable)
			}
		}
	}

	fun refreshRootStatus() {
		viewModelScope.launch(Dispatchers.IO) {
			val available = sessionInstaller?.testRootAvailability() ?: false
			rootAvailableFlow.value = available
			if (!available && prefs.useRootInstaller.get()) {
				prefs.useRootInstaller.put(false)
				rootEnabledFlow.value = false
			}
		}
	}

	fun getCustomDownloadFolderUri(): String = customDownloadFolderUriFlow.value

	fun setCustomDownloadFolderUri(uriString: String) {
		val cleaned = uriString.trim()
		prefs.customDownloadFolderUri.put(cleaned)
		customDownloadFolderUriFlow.value = cleaned
	}

	fun refreshShizukuStatus() {
		val status = sessionInstaller?.getShizukuSetupStatus() ?: ShizukuSetupStatus()
		shizukuSetupStatusFlow.value = status
		if (pendingEnableOnReady && status.isReady) {
			prefs.useShizukuInstaller.put(true)
			shizukuEnabledFlow.value = true
			pendingEnableOnReady = false
			showShizukuSetupSheetFlow.value = false
		} else if (!status.isReady) {
			prefs.useShizukuInstaller.put(false)
			shizukuEnabledFlow.value = false
		} else {
			shizukuEnabledFlow.value = prefs.useShizukuInstaller.get()
		}
	}

	fun getUseShizukuInstaller() = shizukuEnabledFlow.value

	fun setUseShizukuInstaller(requested: Boolean) {
		val status = sessionInstaller?.getShizukuSetupStatus() ?: ShizukuSetupStatus()
		shizukuSetupStatusFlow.value = status
		if (!status.isReady) {
			prefs.useShizukuInstaller.put(false)
			shizukuEnabledFlow.value = false
			pendingEnableOnReady = true
			showShizukuSetupSheetFlow.value = true
			return
		}
		if (!requested) {
			pendingEnableOnReady = false
			prefs.useShizukuInstaller.put(false)
			shizukuEnabledFlow.value = false
			if (!getUseRootInstaller()) {
				workManager?.let { ShizukuAutoUpdateWorker.cancel(it) }
			}
			return
		}
		prefs.useShizukuInstaller.put(true)
		shizukuEnabledFlow.value = true
		pendingEnableOnReady = false
		showShizukuSetupSheetFlow.value = false
	}

	fun requestShizukuPermissionFromSheet() {
		pendingEnableOnReady = true
		val alreadyGranted = sessionInstaller?.requestShizukuPermission() == true
		if (alreadyGranted) {
			refreshShizukuStatus()
		}
	}

	fun openShizukuSetupSheet() {
		showShizukuSetupSheetFlow.value = true
	}

	fun dismissShizukuSetupSheet() {
		showShizukuSetupSheetFlow.value = false
	}

	fun refreshIgnoredVersions() {
		ignoredVersionsFlow.value = buildIgnoredVersionEntries()
	}

	private fun buildIgnoredVersionEntries(): List<IgnoredVersionEntry> {
		val ids = prefs.ignoredVersions.get()
		val storedEntries = prefs.readIgnoredVersionEntriesSafe()
		val byId = storedEntries.associateBy { it.id }
		val mappedLegacy = ids.map { id ->
			byId[id] ?: IgnoredVersionEntry(
				id = id,
				packageName = "",
				appName = "ID #$id",
				version = "",
				versionCode = 0L,
				sourceName = ""
			)
		}
		val extraEntries = storedEntries.filter { it.id !in byId.keys || it.id !in ids.toSet() }
		return (mappedLegacy + extraEntries).distinctBy { "${it.id}_${it.packageName}_${it.versionCode}" }
	}

	fun unignoreVersion(id: Int) {
		val ids = prefs.ignoredVersions.get().toMutableList()
		ids.remove(id)
		prefs.ignoredVersions.put(ids)

		val entries = prefs.readIgnoredVersionEntriesSafe().toMutableList()
		entries.removeAll { it.id == id }
		prefs.ignoredVersionEntries.put(entries)

		refreshIgnoredVersions()
	}

	fun clearAllIgnoredVersions() {
		prefs.clearAllIgnoredVersions()
		refreshIgnoredVersions()
	}

	fun ignoredApps(): StateFlow<List<String>> = ignoredAppsFlow

	fun getIgnoreAppsFilterActive(): Boolean = prefs.ignoreAppsFilterActive.get()
	fun setIgnoreAppsFilterActive(active: Boolean, appsViewModel: AppsViewModel, updatesViewModel: UpdatesViewModel) {
		prefs.ignoreAppsFilterActive.put(active)
		refreshIgnoredApps()
		appsViewModel.refresh(false)
		updatesViewModel.syncIgnoredFromPrefs()
	}

	fun getIgnoreVersionsFilterActive(): Boolean = prefs.ignoreVersionsFilterActive.get()
	fun setIgnoreVersionsFilterActive(active: Boolean, updatesViewModel: UpdatesViewModel) {
		prefs.ignoreVersionsFilterActive.put(active)
		refreshIgnoredVersions()
		updatesViewModel.syncIgnoredFromPrefs()
	}

	fun refreshIgnoredApps() {
		ignoredAppsFlow.value = prefs.ignoredApps.get()
	}

	fun unignoreApps(
		packagesToRestore: Set<String>,
		appsViewModel: AppsViewModel,
		updatesViewModel: UpdatesViewModel
	) {
		val current = prefs.ignoredApps.get().toMutableList()
		current.removeAll(packagesToRestore)
		prefs.ignoredApps.put(current)
		refreshIgnoredApps()
		appsViewModel.refresh(false)
		updatesViewModel.syncIgnoredFromPrefs()
	}

	fun ignoreApps(
		packagesToIgnore: Set<String>,
		appsViewModel: AppsViewModel,
		updatesViewModel: UpdatesViewModel
	) {
		val current = prefs.ignoredApps.get().toMutableList()
		current.addAll(packagesToIgnore)
		prefs.ignoredApps.put(current.distinct())
		refreshIgnoredApps()
		appsViewModel.refresh(false)
		updatesViewModel.syncIgnoredFromPrefs()
	}

	fun canRequestPackageInstalls(): Boolean =
		sessionInstaller?.canRequestPackageInstalls() ?: false

	fun getShowSystemApps() = prefs.showSystemApps.get()
	fun setShowSystemApps(show: Boolean) = prefs.setShowSystemApps(show)

	fun onShowSystemAppsChanged(
		show: Boolean,
		appsViewModel: AppsViewModel,
		updatesViewModel: UpdatesViewModel
	) {
		prefs.setShowSystemApps(show)
		viewModelScope.launch(Dispatchers.IO) {
			val updatedApps = appsViewModel.refreshAndGet(false)
			updatesViewModel.refresh(
				load = true,
				installedAppsSnapshot = updatedApps,
				filterRetainedBySnapshot = true
			)
		}
	}

	fun getUpdatesDisplayMode() = prefs.updatesDisplayMode.get()
	fun setUpdatesDisplayMode(mode: Int) = prefs.updatesDisplayMode.put(mode)
	fun getUseApkMirror() = prefs.useApkMirror.get()
	fun setUseApkMirror(b: Boolean) = prefs.useApkMirror.put(b)
	fun getDirectApkMirrorDownloads(): Boolean = prefs.directApkMirrorDownloads.get()
	fun setDirectApkMirrorDownloads(b: Boolean) = prefs.directApkMirrorDownloads.put(b)

	fun getApkMirrorCustomApiUser(): String = prefs.apkMirrorCustomApiUser.get()
	fun getApkMirrorCustomApiKey(): String = prefs.apkMirrorCustomApiKey.get()
	fun setApkMirrorCustomCredentials(user: String, key: String) {
		prefs.apkMirrorCustomApiUser.put(user.trim().filter { it in '!'..'~' })
		prefs.apkMirrorCustomApiKey.put(key.trim().filter { it in '!'..'~' })
	}

	fun getUsePlay() = prefs.usePlay.get()
	fun setUsePlay(b: Boolean) = prefs.usePlay.put(b)
	fun getUseIzzyOnDroid() = prefs.useIzzyOnDroid.get()
	fun setUseIzzyOnDroid(b: Boolean) = prefs.useIzzyOnDroid.put(b)

	fun getFDroidRepos(): List<FDroidRepoConfig> = prefs.getFDroidRepos()

	fun setFDroidRepoEnabled(repoId: String, enabled: Boolean) {
		prefs.setFDroidRepoEnabled(repoId, enabled)
		if (enabled && !prefs.useIzzyOnDroid.get()) {
			prefs.useIzzyOnDroid.put(true)
		}
	}

	fun addFDroidRepoConfig(
		rawUrl: String,
		explicitName: String = "",
		explicitFingerprint: String = ""
	): Result<FDroidRepoConfig> {
		val parsedResult = FDroidRepoConfig.parseShareInput(
			rawUrl = rawUrl,
			explicitName = explicitName,
			explicitFingerprint = explicitFingerprint
		)
		val repo = parsedResult.getOrElse { return Result.failure(it) }
		val host = repo.url.toHttpUrlOrNull()?.host.orEmpty()
		if (!RepoHostValidator.isAllowedRepoHost(host, allowLanHosts = true)) {
			return Result.failure(IllegalArgumentException("Disallowed repository host: $host"))
		}
		if (!prefs.addCustomFDroidRepo(repo)) {
			return Result.failure(IllegalArgumentException("Repository URL is already configured"))
		}
		if (!prefs.useIzzyOnDroid.get()) {
			prefs.useIzzyOnDroid.put(true)
		}
		return Result.success(repo)
	}

	fun removeFDroidRepoConfig(repoId: String) {
		prefs.removeCustomFDroidRepo(repoId)
	}

	fun getUseGitHub() = prefs.useGitHub.get()
	fun setUseGitHub(b: Boolean) = prefs.useGitHub.put(b)
	fun getUseGitLab() = prefs.useGitLab.get()
	fun setUseGitLab(b: Boolean) = prefs.useGitLab.put(b)
	fun getUseCodeberg() = prefs.useCodeberg.get()
	fun setUseCodeberg(b: Boolean) = prefs.useCodeberg.put(b)
	fun getUseCustomFDroidRepos() = prefs.useCustomFDroidRepos.get()
	fun setUseCustomFDroidRepos(b: Boolean) = prefs.useCustomFDroidRepos.put(b)
	fun customFDroidRepos(): StateFlow<List<CustomFDroidRepo>> = prefs.customFDroidReposFlow
	fun getCustomFDroidRepos(): List<CustomFDroidRepo> = prefs.readCustomFDroidReposSafe()

	fun addCustomFDroidRepo(name: String, rawUrl: String, fingerprint: String = ""): Boolean =
		addCustomFDroidRepoDetailed(name, rawUrl, fingerprint) == CustomFDroidAddResult.SUCCESS

	fun addCustomFDroidRepoDetailed(
		name: String,
		rawUrl: String,
		fingerprint: String = "",
		allowLanHosts: Boolean = true
	): CustomFDroidAddResult {
		val trimmed = rawUrl.trim()
		if (trimmed.isBlank()) return CustomFDroidAddResult.INVALID_URL
		val candidateUrl = if (trimmed.startsWith("fdroidrepo://", ignoreCase = true)) {
			"https://" + trimmed.substringAfter("://")
		} else {
			trimmed
		}
		val parsed = candidateUrl.toHttpUrlOrNull() ?: return CustomFDroidAddResult.INVALID_URL
		if (!parsed.scheme.equals("https", ignoreCase = true)) {
			return CustomFDroidAddResult.INVALID_URL
		}
		if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) {
			return CustomFDroidAddResult.INVALID_URL
		}
		if (!RepoHostValidator.isAllowedRepoHost(parsed.host, allowLanHosts = allowLanHosts)) {
			return CustomFDroidAddResult.DISALLOWED_HOST
		}

		val queryFingerprint = parsed.queryParameter("fingerprint")
			?: parsed.queryParameter("FINGERPRINT")
		val rawFingerprint = fingerprint.trim().ifBlank { queryFingerprint.orEmpty().trim() }
		val normalizedFingerprint = FDroidRepoConfig.normalizeFingerprint(rawFingerprint)
		if (normalizedFingerprint.isNotEmpty() && !FDroidRepoConfig.isValidSha256Fingerprint(normalizedFingerprint)) {
			return CustomFDroidAddResult.INVALID_FINGERPRINT
		}

		val cleanBaseUrl = parsed.newBuilder()
			.query(null)
			.fragment(null)
			.build()
			.toString()
			.trimEnd('/')
		val finalName = name.trim().ifBlank { parsed.host.removePrefix("www.") }
		val current = prefs.readCustomFDroidReposSafe().toMutableList()
		val existingIdx = current.indexOfFirst { it.baseUrl.equals(cleanBaseUrl, ignoreCase = true) }
		val updated = CustomFDroidRepo(
			name = finalName,
			baseUrl = cleanBaseUrl,
			fingerprint = normalizedFingerprint,
			enabled = true
		)
		if (existingIdx >= 0) {
			current[existingIdx] = updated
		} else {
			current.add(updated)
		}
		prefs.writeCustomFDroidRepos(current)
		if (!prefs.useCustomFDroidRepos.get()) {
			prefs.useCustomFDroidRepos.put(true)
		}
		return CustomFDroidAddResult.SUCCESS
	}

	fun toggleCustomFDroidRepo(baseUrl: String, enabled: Boolean) {
		val current = prefs.readCustomFDroidReposSafe().map { repo ->
			if (repo.baseUrl.equals(baseUrl, ignoreCase = true)) repo.copy(enabled = enabled) else repo
		}
		prefs.writeCustomFDroidRepos(current)
	}

	fun removeCustomFDroidRepo(baseUrl: String) {
		val current = prefs.readCustomFDroidReposSafe().filterNot {
			it.baseUrl.equals(baseUrl, ignoreCase = true)
		}
		prefs.writeCustomFDroidRepos(current)
	}

	fun getGitHubToken(): String = prefs.githubToken.get()
	fun setGitHubToken(token: String) = prefs.githubToken.put(token.trim().filter { it in '!'..'~' })
	fun getGitLabToken(): String = prefs.gitlabToken.get()
	fun setGitLabToken(token: String) = prefs.gitlabToken.put(token.trim().filter { it in '!'..'~' })
	fun getCodebergToken(): String = prefs.codebergToken.get()
	fun setCodebergToken(token: String) = prefs.codebergToken.put(token.trim().filter { it in '!'..'~' })

	private fun readBoundedBody(reader: Reader, maxChars: Int = MAX_AUTH_RESPONSE_CHARS): String {
		val sb = StringBuilder()
		val buf = CharArray(4096)
		var total = 0
		while (total < maxChars) {
			val read = reader.read(buf, 0, minOf(buf.size, maxChars - total))
			if (read == -1) break
			sb.append(buf, 0, read)
			total += read
		}
		return sb.toString()
	}

	suspend fun validateGitHubToken(rawToken: String): TokenValidationResult = withContext(Dispatchers.IO) {
		val cleanToken = rawToken.trim().filter { it in '!'..'~' }
		if (cleanToken.isBlank()) {
			return@withContext TokenValidationResult.Invalid(401)
		}
		try {
			val request = Request.Builder()
				.url("https://api.github.com/user")
				.cacheControl(CacheControl.FORCE_NETWORK)
				.header("Accept", "application/vnd.github.v3+json")
				.header("User-Agent", "ApkUp/${BuildConfig.VERSION_NAME}")
				.header("Authorization", "Bearer $cleanToken")
				.build()
			okHttpClient.newCall(request).execute().use { response ->
				when {
					response.isSuccessful -> {
						val body = response.body.charStream().use { readBoundedBody(it) }
						val login = runCatching {
							JsonParser.parseString(body).asJsonObject.get("login")?.asString
						}.getOrNull().orEmpty().ifBlank { "GitHub" }
						val remaining = response.header("x-ratelimit-remaining")
						val limit = response.header("x-ratelimit-limit")
						TokenValidationResult.Valid(
							username = login,
							rateLimitRemaining = remaining,
							rateLimitTotal = limit
						)
					}
					response.code == 401 || response.code == 403 -> {
						TokenValidationResult.Invalid(response.code)
					}
					else -> {
						TokenValidationResult.Error("HTTP ${response.code}")
					}
				}
			}
		} catch (e: CancellationException) {
			throw e
		} catch (error: Throwable) {
			TokenValidationResult.Error(error.message ?: "Network error")
		}
	}

	suspend fun validateGitLabToken(rawToken: String): TokenValidationResult = withContext(Dispatchers.IO) {
		val cleanToken = rawToken.trim().filter { it in '!'..'~' }
		if (cleanToken.isBlank()) {
			return@withContext TokenValidationResult.Invalid(401)
		}
		try {
			val request = Request.Builder()
				.url("https://gitlab.com/api/v4/user")
				.cacheControl(CacheControl.FORCE_NETWORK)
				.header("Accept", "application/json")
				.header("User-Agent", "ApkUp/${BuildConfig.VERSION_NAME}")
				.header("Authorization", "Bearer $cleanToken")
				.build()
			okHttpClient.newCall(request).execute().use { response ->
				when {
					response.isSuccessful -> {
						val body = response.body.charStream().use { readBoundedBody(it) }
						val username = runCatching {
							JsonParser.parseString(body).asJsonObject.get("username")?.asString
						}.getOrNull().orEmpty().ifBlank { "GitLab" }
						val remaining = response.header("RateLimit-Remaining")
						val limit = response.header("RateLimit-Limit")
						TokenValidationResult.Valid(
							username = username,
							rateLimitRemaining = remaining,
							rateLimitTotal = limit
						)
					}
					response.code == 401 || response.code == 403 -> {
						TokenValidationResult.Invalid(response.code)
					}
					else -> {
						TokenValidationResult.Error("HTTP ${response.code}")
					}
				}
			}
		} catch (e: CancellationException) {
			throw e
		} catch (error: Throwable) {
			TokenValidationResult.Error(error.message ?: "Network error")
		}
	}

	suspend fun validateCodebergToken(rawToken: String): TokenValidationResult = withContext(Dispatchers.IO) {
		val cleanToken = rawToken.trim().filter { it in '!'..'~' }
		if (cleanToken.isBlank()) {
			return@withContext TokenValidationResult.Invalid(401)
		}
		try {
			val request = Request.Builder()
				.url("https://codeberg.org/api/v1/user")
				.cacheControl(CacheControl.FORCE_NETWORK)
				.header("Accept", "application/json")
				.header("User-Agent", "ApkUp/${BuildConfig.VERSION_NAME}")
				.header("Authorization", "token $cleanToken")
				.build()
			okHttpClient.newCall(request).execute().use { response ->
				when {
					response.isSuccessful -> {
						val body = response.body.charStream().use { readBoundedBody(it) }
						val jsonObj = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull()
						val username = jsonObj?.get("login")?.asString?.takeIf { it.isNotBlank() }
							?: jsonObj?.get("username")?.asString?.takeIf { it.isNotBlank() }
							?: "Codeberg"
						TokenValidationResult.Valid(username = username)
					}
					response.code == 401 || response.code == 403 -> {
						TokenValidationResult.Invalid(response.code)
					}
					else -> {
						TokenValidationResult.Error("HTTP ${response.code}")
					}
				}
			}
		} catch (e: CancellationException) {
			throw e
		} catch (error: Throwable) {
			TokenValidationResult.Error(error.message ?: "Network error")
		}
	}

	fun notifyTextSnack(
		message: String,
		visualType: com.apkup.data.snack.SnackVisualType = com.apkup.data.snack.SnackVisualType.INFO
	) {
		val snack = snackBar ?: return
		snack.snackBar(viewModelScope, TextSnack(message, visualType = visualType))
	}

	fun getDownloadWifiOnly(): Boolean = prefs.downloadWifiOnly.get()
	fun setDownloadWifiOnly(wifiOnly: Boolean) = prefs.downloadWifiOnly.put(wifiOnly)

	fun getDownloadChargingOnly(): Boolean = prefs.downloadChargingOnly.get()
	fun setDownloadChargingOnly(chargingOnly: Boolean) = prefs.downloadChargingOnly.put(chargingOnly)

	fun getBackgroundCheckWifiOnly(): Boolean = prefs.backgroundCheckWifiOnly.get()
	fun setBackgroundCheckWifiOnly(wifiOnly: Boolean) {
		prefs.backgroundCheckWifiOnly.put(wifiOnly)
		if (getBackgroundChecksEnabled()) {
			workManager?.let { UpdatesWorker.launch(it, replaceExisting = true) }
		}
	}

	fun getAutoInstallUpdates(): Boolean = prefs.autoInstallUpdates.get()

	fun setAutoInstallUpdates(enabled: Boolean) {
		prefs.autoInstallUpdates.put(enabled)
		val wm = workManager ?: return
		if (enabled && (getUseShizukuInstaller() || getUseRootInstaller())) {
			if (prefs.readCachedSnapshotSafe().updates.orEmpty().isNotEmpty()) {
				ShizukuAutoUpdateWorker.enqueue(
					workManager = wm,
					wifiOnly = prefs.autoInstallWifiOnly.get(),
					chargingOnly = prefs.autoInstallChargingOnly.get()
				)
			}
		} else {
			ShizukuAutoUpdateWorker.cancel(wm)
		}
	}

	fun getAutoInstallWifiOnly(): Boolean = prefs.autoInstallWifiOnly.get()

	fun setAutoInstallWifiOnly(wifiOnly: Boolean) {
		prefs.autoInstallWifiOnly.put(wifiOnly)
		if (prefs.autoInstallUpdates.get() && (getUseShizukuInstaller() || getUseRootInstaller()) && prefs.readCachedSnapshotSafe().updates.orEmpty().isNotEmpty()) {
			workManager?.let { wm ->
				ShizukuAutoUpdateWorker.enqueue(
					workManager = wm,
					wifiOnly = wifiOnly,
					chargingOnly = prefs.autoInstallChargingOnly.get()
				)
			}
		}
	}

	fun getAutoInstallChargingOnly(): Boolean = prefs.autoInstallChargingOnly.get()

	fun setAutoInstallChargingOnly(chargingOnly: Boolean) {
		prefs.autoInstallChargingOnly.put(chargingOnly)
		if (prefs.autoInstallUpdates.get() && (getUseShizukuInstaller() || getUseRootInstaller()) && prefs.readCachedSnapshotSafe().updates.orEmpty().isNotEmpty()) {
			workManager?.let { wm ->
				ShizukuAutoUpdateWorker.enqueue(
					workManager = wm,
					wifiOnly = prefs.autoInstallWifiOnly.get(),
					chargingOnly = chargingOnly
				)
			}
		}
	}

	fun getBackgroundChecksEnabled() = prefs.enableBackgroundChecks.get()
	fun getBackgroundCheckIntervalHours(): Int =
		BackgroundCheckInterval.fromHours(prefs.backgroundCheckIntervalHours.get()).hours

	fun setBackgroundCheckIntervalHours(hours: Int) {
		val normalized = BackgroundCheckInterval.fromHours(hours).hours
		prefs.backgroundCheckIntervalHours.put(normalized)
		if (prefs.enableBackgroundChecks.get()) {
			workManager?.let { UpdatesWorker.launch(it, replaceExisting = true) }
		}
	}

	fun getTheme() = prefs.theme.get()

	fun setTheme(theme: Int) {
		prefs.theme.put(theme)
		themer?.setTheme(isDarkTheme(theme))
	}

	fun getDynamicColor(): Boolean = prefs.dynamicColor.get()

	fun setDynamicColor(enabled: Boolean) {
		prefs.dynamicColor.put(enabled)
		themer?.setDynamicColor(enabled)
	}

	fun setBackgroundChecksEnabled(
		b: Boolean,
		@Suppress("UNUSED_PARAMETER")
		launcher: ManagedActivityResultLauncher<String, Boolean>? = null
	) {
		prefs.enableBackgroundChecks.put(b)
		if (b) {
			workManager?.let { UpdatesWorker.launch(it, replaceExisting = true) }
		} else {
			workManager?.let { UpdatesWorker.cancel(it) }
		}
	}

	fun exportBackupJson(): String {
		val config = ApkUpBackupConfig(
			schemaVersion = ApkUpBackupConfig.CURRENT_SCHEMA_VERSION,
			exportedAtMillis = System.currentTimeMillis(),
			ignoredApps = prefs.ignoredApps.get(),
			ignoredVersions = prefs.ignoredVersions.get(),
			ignoredVersionEntries = prefs.readIgnoredVersionEntriesSafe(),
			showSystemApps = prefs.showSystemApps.get(),
			updatesDisplayMode = prefs.updatesDisplayMode.get(),
			useApkMirror = prefs.useApkMirror.get(),
			directApkMirrorDownloads = prefs.directApkMirrorDownloads.get(),
			usePlay = prefs.usePlay.get(),
			useIzzyOnDroid = prefs.useIzzyOnDroid.get(),
			useGitHub = prefs.useGitHub.get(),
			useGitLab = prefs.useGitLab.get(),
			useCodeberg = prefs.useCodeberg.get(),
			useCustomFDroidRepos = prefs.useCustomFDroidRepos.get(),
			customFDroidRepos = prefs.readCustomFDroidReposSafe(),
			watchedApps = prefs.readWatchedAppsSafe(),
			customRepoBindings = prefs.readCustomRepoBindingsSafe(),
			fdroidRepos = prefs.getFDroidRepos(),
			enableBackgroundChecks = prefs.enableBackgroundChecks.get(),
			backgroundCheckIntervalHours = getBackgroundCheckIntervalHours(),
			theme = prefs.theme.get(),
			useShizukuInstaller = prefs.useShizukuInstaller.get(),
			useRootInstaller = prefs.useRootInstaller.get(),
			autoInstallUpdates = prefs.autoInstallUpdates.get(),
			autoInstallWifiOnly = prefs.autoInstallWifiOnly.get(),
			autoInstallChargingOnly = prefs.autoInstallChargingOnly.get(),
			downloadWifiOnly = prefs.downloadWifiOnly.get(),
			downloadChargingOnly = prefs.downloadChargingOnly.get(),
			backgroundCheckWifiOnly = prefs.backgroundCheckWifiOnly.get(),
			customDownloadFolderUri = prefs.customDownloadFolderUri.get(),
			updateHistory = prefs.readUpdateHistorySafe(),
			sourcePriorityOrder = prefs.readSourcePriorityOrderSafe(),
			pinnedAppSources = prefs.readPinnedAppSourcesSafe(),
			includePreReleasesGlobal = prefs.includePreReleasesGlobal.get(),
			preReleaseChannels = prefs.readAllowedPreReleaseChannelsSafe().toList(),
			appPreReleaseOverrides = prefs.readAppPreReleaseOverridesSafe(),
			autoUpdateWindowEnabled = prefs.autoUpdateWindowEnabled.get(),
			autoUpdateWindowMode = prefs.autoUpdateWindowMode.get(),
			autoUpdateWindowStartMinutes = prefs.autoUpdateWindowStartMinutes.get(),
			autoUpdateWindowEndMinutes = prefs.autoUpdateWindowEndMinutes.get(),
			enableRollbackBackups = prefs.enableRollbackBackups.get(),
			rollbackMaxBudgetMb = getRollbackMaxBudgetMb(),
			rollbackMaxAgeDays = getRollbackMaxAgeDays(),
			includeSelfUpdatePreReleases = prefs.includeSelfUpdatePreReleases.get()
		)
		return gson.toJson(config)
	}

	fun importBackupJson(rawJson: String): Boolean = importBackupJsonDetailed(rawJson).success

	@Suppress("SENSELESS_COMPARISON", "USELESS_CAST")
	fun importBackupJsonDetailed(rawJson: String): BackupImportResult {
		if (rawJson.length > MAX_BACKUP_JSON_CHARS) {
			notifySnack(R.string.backup_import_error)
			return BackupImportResult(success = false)
		}

		if (ObtainiumManager.isObtainiumJson(rawJson)) {
			val result = ObtainiumManager.importObtainium(
				rawJson = rawJson,
				repoMappingManager = repoMappingManager,
				prefs = prefs
			)
			return if (result.importedBindingsCount > 0) {
				val baseStr = stringer?.get(R.string.backup_obtainium_import_success)
				val baseMessage = if (baseStr != null) {
					runCatching { baseStr.format(result.importedBindingsCount, result.totalEntries) }.getOrDefault(baseStr)
				} else {
					"Imported ${result.importedBindingsCount}/${result.totalEntries} apps from Obtainium"
				}
				val finalMessage = if (result.unsupportedDomains.isNotEmpty()) {
					val domainsText = result.unsupportedDomains.take(3).joinToString(", ")
					val warnStr = stringer?.get(R.string.backup_obtainium_skipped_domains)
					val warnFormatted = if (warnStr != null) {
						runCatching { warnStr.format(result.skippedCount, domainsText) }.getOrDefault(warnStr)
					} else {
						"Skipped ${result.skippedCount} unsupported entries ($domainsText)"
					}
					"$baseMessage • $warnFormatted"
				} else {
					baseMessage
				}
				notifyTextSnack(finalMessage, com.apkup.data.snack.SnackVisualType.SUCCESS)
				BackupImportResult(success = true)
			} else {
				if (result.unsupportedDomains.isNotEmpty()) {
					val domainsText = result.unsupportedDomains.take(3).joinToString(", ")
					val warnStr = stringer?.get(R.string.backup_obtainium_skipped_domains)
					val warnFormatted = if (warnStr != null) {
						runCatching { warnStr.format(result.skippedCount, domainsText) }.getOrDefault(warnStr)
					} else {
						"Skipped ${result.skippedCount} unsupported entries ($domainsText)"
					}
					notifyTextSnack(warnFormatted, com.apkup.data.snack.SnackVisualType.WARNING)
				} else {
					notifySnack(R.string.backup_import_error, com.apkup.data.snack.SnackVisualType.ERROR)
				}
				BackupImportResult(success = false)
			}
		}

		return runCatching {
			val config = gson.fromJson(rawJson, ApkUpBackupConfig::class.java)
				?: throw IllegalArgumentException("Empty backup JSON")
			if (!config.isSupportedSchemaVersion) {
				throw IllegalArgumentException("Unsupported backup schemaVersion=${config.schemaVersion}")
			}

			val sanitizedIgnoredApps = (config.ignoredApps as List<String>?).orEmpty()
				.mapNotNull { (it as String?)?.trim()?.takeIf { pkg -> ApkIntegrityVerifier.isValidPackageName(pkg) } }
				.distinct()
			val sanitizedIgnoredVersions = (config.ignoredVersions as List<Int>?).orEmpty().distinct()
			val sanitizedEntries = (config.ignoredVersionEntries as List<IgnoredVersionEntry>?).orEmpty()
				.mapNotNull { candidate ->
					val entry = candidate as IgnoredVersionEntry? ?: return@mapNotNull null
					val safePkg = (entry.packageName as String?).orEmpty()
					entry.copy(
						packageName = safePkg,
						appName = (entry.appName as String?).orEmpty(),
						version = (entry.version as String?).orEmpty(),
						sourceName = (entry.sourceName as String?).orEmpty()
					)
				}
				.distinctBy { "${it.id}_${it.packageName}_${it.versionCode}" }

			prefs.ignoredApps.put(sanitizedIgnoredApps)
			prefs.ignoredVersions.put(sanitizedIgnoredVersions)
			prefs.ignoredVersionEntries.put(sanitizedEntries)
			prefs.setShowSystemApps(config.showSystemApps ?: false)
			prefs.updatesDisplayMode.put((config.updatesDisplayMode ?: 0).coerceIn(0, 1))
			prefs.useApkMirror.put(config.useApkMirror ?: true)
			prefs.directApkMirrorDownloads.put(config.directApkMirrorDownloads ?: true)
			prefs.usePlay.put(config.usePlay ?: true)
			prefs.useIzzyOnDroid.put(config.useIzzyOnDroid ?: true)
			prefs.useGitHub.put(config.useGitHub ?: true)
			prefs.useGitLab.put(config.useGitLab ?: true)
			config.useCodeberg?.let { prefs.useCodeberg.put(it) }
			config.useCustomFDroidRepos?.let { prefs.useCustomFDroidRepos.put(it) }
			config.customFDroidRepos?.let { repos ->
				val validatedCustomFDroid = repos.mapNotNull { candidate ->
					val repo = candidate as CustomFDroidRepo? ?: return@mapNotNull null
					val safeBaseUrl = (repo.baseUrl as String?).orEmpty()
					val cleanBase = FDroidRepoConfig.normalizeRepoBaseUrl(safeBaseUrl) ?: return@mapNotNull null
					val host = cleanBase.toHttpUrlOrNull()?.host ?: return@mapNotNull null
					if (!RepoHostValidator.isAllowedRepoHost(host, allowLanHosts = false)) return@mapNotNull null
					val safeFp = (repo.fingerprint as String?).orEmpty()
					val cleanFp = FDroidRepoConfig.normalizeFingerprint(safeFp)
					if (cleanFp.isNotEmpty() && !FDroidRepoConfig.isValidSha256Fingerprint(cleanFp)) return@mapNotNull null
					val safeName = (repo.name as String?)?.trim().orEmpty().ifBlank { host.removePrefix("www.") }
					val safeId = (repo.id as String?).orEmpty()
					repo.copy(
						id = safeId,
						name = safeName,
						baseUrl = cleanBase,
						fingerprint = cleanFp
					)
				}
				prefs.writeCustomFDroidRepos(validatedCustomFDroid)
			}
			config.watchedApps?.let { watched ->
				val sanitizedWatched = watched.mapNotNull { candidate ->
					val entry = candidate as WatchedAppEntry? ?: return@mapNotNull null
					val safePkg = (entry.packageName as String?)?.trim().orEmpty()
					if (!ApkIntegrityVerifier.isValidPackageName(safePkg)) return@mapNotNull null
					val safeName = (entry.appName as String?)?.trim().orEmpty().ifBlank { safePkg }
					entry.copy(packageName = safePkg, appName = safeName)
				}.distinctBy { it.packageName }
				prefs.writeWatchedApps(sanitizedWatched)
			}

			val rawBindings = (config.customRepoBindings as Map<String, RepoBinding>?).orEmpty()
			val sanitizedBindings = LinkedHashMap<String, RepoBinding>()
			for ((rawPkg, rawBinding) in rawBindings) {
				val pkg = (rawPkg as String?)?.trim().orEmpty()
				val binding = rawBinding as RepoBinding?
				if (!ApkIntegrityVerifier.isValidPackageName(pkg) || binding == null) continue
				val normalizedType = (binding.type as String?)?.trim()?.lowercase().orEmpty()
				if (normalizedType !in setOf("github", "gitlab", "codeberg")) continue
				val rawParts = (binding.repo as String?)?.trim()?.trim('/')?.split('/')?.filter { it.isNotBlank() }.orEmpty()
				val cleanParts = rawParts.mapIndexed { idx, seg ->
					if (idx == rawParts.lastIndex) seg.removeSuffix(".git") else seg
				}
				val validArity = if (normalizedType == "gitlab") cleanParts.size >= 2 else cleanParts.size == 2
				if (!validArity || !RepoHostValidator.isValidRepoSlugSegments(cleanParts)) continue
				val cleanRegex = (binding.assetFilterRegex as String?)?.trim()?.takeIf { it.isNotEmpty() }
				if (cleanRegex != null && !ReleaseAssetFilter.isValidRegex(cleanRegex)) continue
				val cleanCandidate = binding.copy(
					type = normalizedType,
					repo = cleanParts.joinToString("/"),
					host = (binding.host as String?)?.trim()?.takeIf { it.isNotEmpty() },
					assetFilterRegex = cleanRegex
				)
				if (!RepoHostValidator.isAllowedRepoHost(cleanCandidate.resolvedHost, allowLanHosts = false)) continue
				sanitizedBindings[pkg] = cleanCandidate
			}
			if (sanitizedBindings.isNotEmpty()) {
				if (repoMappingManager != null) {
					repoMappingManager.setCustomBindings(sanitizedBindings)
				} else {
					prefs.writeCustomRepoBindings(sanitizedBindings)
				}
			}

			config.sourcePriorityOrder?.let { importedOrder ->
				val safeOrder = importedOrder.filterIsInstance<String>()
				prefs.writeSourcePriorityOrder(safeOrder)
			}
			config.pinnedAppSources?.let { rawPinned ->
				val safePinned = rawPinned.entries.mapNotNull { (pkg, key) ->
					val cleanPkg = (pkg as String?)?.trim().orEmpty()
					val cleanKey = (key as String?)?.trim().orEmpty()
					if (cleanPkg.isNotBlank() && cleanKey in SourcePriorityKeys.DEFAULT_ORDER) {
						cleanPkg to cleanKey
					} else null
				}.toMap()
				prefs.pinnedAppSources.put(safePinned)
			}
			config.includePreReleasesGlobal?.let { prefs.includePreReleasesGlobal.put(it) }
			config.preReleaseChannels?.let { channels ->
				val valid = com.apkup.data.repomapping.ReleaseAssetFilter.PreReleaseChannel.ALL
				prefs.preReleaseChannels.put(channels.filter { it in valid }.distinct())
			}
			config.appPreReleaseOverrides?.let { rawOverrides ->
				val safeOverrides = rawOverrides.entries.mapNotNull { (pkg, mode) ->
					val cleanPkg = (pkg as String?)?.trim().orEmpty()
					val cleanMode = (mode as String?)?.trim().orEmpty()
					if (cleanPkg.isNotBlank() && (cleanMode == com.apkup.data.ui.PreReleaseOverride.ALLOW || cleanMode == com.apkup.data.ui.PreReleaseOverride.BLOCK)) {
						cleanPkg to cleanMode
					} else null
				}.toMap()
				prefs.appPreReleaseOverrides.put(safeOverrides)
			}
			config.autoUpdateWindowEnabled?.let { prefs.autoUpdateWindowEnabled.put(it) }
			config.autoUpdateWindowMode?.let { mode ->
				if (mode == com.apkup.data.ui.UpdateWindowMode.ALLOW_ONLY || mode == com.apkup.data.ui.UpdateWindowMode.QUIET_PAUSE) {
					prefs.autoUpdateWindowMode.put(mode)
				}
			}
			config.autoUpdateWindowStartMinutes?.let { prefs.autoUpdateWindowStartMinutes.put(com.apkup.data.ui.UpdateWindowEvaluator.normalizeMinutes(it)) }
			config.autoUpdateWindowEndMinutes?.let { prefs.autoUpdateWindowEndMinutes.put(com.apkup.data.ui.UpdateWindowEvaluator.normalizeMinutes(it)) }
			config.enableRollbackBackups?.let { prefs.enableRollbackBackups.put(it) }
			config.rollbackMaxBudgetMb?.let {
				prefs.rollbackMaxBudgetMb.put(com.apkup.util.RollbackBackupManager.normalizeBudgetMb(it))
			}
			config.rollbackMaxAgeDays?.let {
				prefs.rollbackMaxAgeDays.put(com.apkup.util.RollbackBackupManager.normalizeMaxAgeDays(it))
			}
			prefs.includeSelfUpdatePreReleases.put(config.includeSelfUpdatePreReleases ?: false)
			config.fdroidRepos?.let { importedRepos ->
				val presetsById = FDroidRepoConfig.defaultPresets().associateBy { it.id }
				val validatedRepos = importedRepos.mapNotNull { candidate ->
					if (candidate == null) return@mapNotNull null
					val safeId = (candidate.id as String?).orEmpty()
					val preset = presetsById[safeId]
					if (preset != null) {
						preset.copy(enabled = candidate.enabled)
					} else {
						val safeUrl = (candidate.url as String?).orEmpty()
						val cleanBase = FDroidRepoConfig.normalizeRepoBaseUrl(safeUrl) ?: return@mapNotNull null
						val host = cleanBase.toHttpUrlOrNull()?.host ?: return@mapNotNull null
						if (!RepoHostValidator.isAllowedRepoHost(host, allowLanHosts = false)) return@mapNotNull null
						val safeFp = (candidate.fingerprint as String?).orEmpty()
						val cleanFp = FDroidRepoConfig.normalizeFingerprint(safeFp)
						if (cleanFp.isNotEmpty() && !FDroidRepoConfig.isValidSha256Fingerprint(cleanFp)) return@mapNotNull null
						val safeName = (candidate.name as String?)?.trim().orEmpty().ifBlank { host.removePrefix("www.") }
						candidate.copy(
							id = safeId.ifBlank {
								"custom_" + cleanBase.lowercase(java.util.Locale.ROOT).hashCode().toUInt().toString(16)
							},
							name = safeName,
							url = cleanBase,
							fingerprint = cleanFp,
							isBuiltIn = false,
							isTofuPinned = candidate.isTofuPinned && cleanFp.isNotEmpty()
						)
					}
				}
				if (validatedRepos.isNotEmpty()) {
					prefs.fdroidRepos.put(validatedRepos)
				}
			}
			// Also surface any valid legacy customFDroidRepos inside the unified fdroidRepos list
			// while preserving prefs.customFDroidRepos for legacy compatibility.
			val legacyValidRepos = prefs.readCustomFDroidReposSafe()
			if (legacyValidRepos.isNotEmpty()) {
				val useLegacy = config.useCustomFDroidRepos == true
				val currentFDroid = prefs.readFDroidReposSafe().toMutableList()
				var changedFDroid = false
				for (legacy in legacyValidRepos) {
					val cleanBase = FDroidRepoConfig.normalizeRepoBaseUrl(legacy.baseUrl) ?: continue
					val cleanFp = FDroidRepoConfig.normalizeFingerprint((legacy.fingerprint as String?).orEmpty())
					if (cleanFp.isNotEmpty() && !FDroidRepoConfig.isValidSha256Fingerprint(cleanFp)) continue
					val legacyActive = legacy.enabled && useLegacy
					val existingIdx = currentFDroid.indexOfFirst { it.normalizedUrl.equals(cleanBase, ignoreCase = true) }
					if (existingIdx >= 0) {
						val existing = currentFDroid[existingIdx]
						if (legacyActive && !existing.enabled) {
							currentFDroid[existingIdx] = existing.copy(enabled = true)
							changedFDroid = true
						}
					} else {
						val safeId = legacy.id.ifBlank {
							"custom_" + cleanBase.lowercase(java.util.Locale.ROOT).hashCode().toUInt().toString(16)
						}
						currentFDroid.add(
							FDroidRepoConfig(
								id = safeId,
								name = legacy.name.trim().ifBlank { cleanBase },
								url = cleanBase,
								fingerprint = cleanFp,
								enabled = legacyActive,
								isBuiltIn = false,
								isTofuPinned = legacy.isTofuPinned && cleanFp.isNotEmpty()
							)
						)
						changedFDroid = true
					}
				}
				if (changedFDroid) {
					prefs.fdroidRepos.put(currentFDroid)
				}
			}

			val bgChecksEnabled = config.enableBackgroundChecks ?: true
			prefs.enableBackgroundChecks.put(bgChecksEnabled)
			prefs.backgroundCheckIntervalHours.put(
				BackgroundCheckInterval.fromHours(config.backgroundCheckIntervalHours ?: 24).hours
			)
			setTheme((config.theme ?: 0).coerceIn(0, 2))
			val canEnableShizuku = (config.useShizukuInstaller ?: false) &&
				(sessionInstaller?.getShizukuSetupStatus()?.isReady == true)
			prefs.useShizukuInstaller.put(canEnableShizuku)
			shizukuEnabledFlow.value = canEnableShizuku
			// Never auto-enable root installer from imported backup JSON (Q-01d).
			val requestedRootFromBackup = config.useRootInstaller == true
			val useRoot = false
			prefs.useRootInstaller.put(useRoot)
			rootEnabledFlow.value = useRoot
			val autoInstallEnabled = config.autoInstallUpdates ?: false
			prefs.autoInstallUpdates.put(autoInstallEnabled)
			prefs.autoInstallWifiOnly.put(config.autoInstallWifiOnly ?: true)
			prefs.autoInstallChargingOnly.put(config.autoInstallChargingOnly ?: true)
			if ((!canEnableShizuku && !useRoot) || !autoInstallEnabled) {
				workManager?.let { ShizukuAutoUpdateWorker.cancel(it) }
			}
			prefs.downloadWifiOnly.put(config.downloadWifiOnly ?: false)
			prefs.downloadChargingOnly.put(config.downloadChargingOnly ?: false)
			prefs.backgroundCheckWifiOnly.put(config.backgroundCheckWifiOnly ?: false)
			val customFolderUri = (config.customDownloadFolderUri as String?).orEmpty()
			prefs.customDownloadFolderUri.put(customFolderUri)
			customDownloadFolderUriFlow.value = customFolderUri
			config.updateHistory?.let { importedHistory ->
				val sanitizedHistory = importedHistory.mapNotNull { candidate ->
					val entry = candidate as com.apkup.data.ui.UpdateHistoryEntry? ?: return@mapNotNull null
					val safePkg = (entry.packageName as String?).orEmpty()
					if (safePkg.isBlank()) return@mapNotNull null
					entry.copy(
						packageName = safePkg,
						appName = (entry.appName as String?).orEmpty().ifBlank { safePkg },
						oldVersion = (entry.oldVersion as String?).orEmpty(),
						newVersion = (entry.newVersion as String?).orEmpty(),
						sourceName = (entry.sourceName as String?).orEmpty(),
						installModeName = (entry.installModeName as String?).orEmpty(),
						previousVersionUrl = (entry.previousVersionUrl as String?).orEmpty()
					)
				}
				prefs.replaceUpdateHistory(sanitizedHistory)
			}
			if (bgChecksEnabled) {
				workManager?.let { UpdatesWorker.launch(it, replaceExisting = true) }
			} else {
				workManager?.let { UpdatesWorker.cancel(it) }
			}
			refreshIgnoredVersions()
			refreshIgnoredApps()
			notifySnack(
				if (requestedRootFromBackup) R.string.backup_import_success_root_disabled
				else R.string.backup_import_success,
				if (requestedRootFromBackup) com.apkup.data.snack.SnackVisualType.WARNING
				else com.apkup.data.snack.SnackVisualType.SUCCESS
			)
			BackupImportResult(success = true, rootResetForSecurity = requestedRootFromBackup)
		}.getOrElse {
			notifySnack(R.string.backup_import_error, com.apkup.data.snack.SnackVisualType.ERROR)
			BackupImportResult(success = false)
		}
	}

	fun exportObtainiumJson(appNamesByPackage: Map<String, String> = emptyMap()): String {
		val bindings = prefs.readCustomRepoBindingsSafe()
		return ObtainiumManager.exportObtainium(bindings, appNamesByPackage)
	}

	fun parseObtainiumImportPreview(
		rawJson: String,
		installedPackageNames: Set<String>
	): ObtainiumImportPreview? {
		if (rawJson.length > MAX_BACKUP_JSON_CHARS) return null
		if (!ObtainiumManager.isObtainiumJson(rawJson)) return null
		return ObtainiumManager.buildImportPreview(rawJson, installedPackageNames)
	}

	fun applyObtainiumImport(
		preview: ObtainiumImportPreview,
		importInstalledMappings: Boolean,
		importFDroidRepos: Boolean,
		selectedWatchedPackages: Set<String>
	): Triple<Int, Int, Int> {
		var importedMappingsCount = 0
		var importedReposCount = 0
		var importedWatchedCount = 0

		val newBindings = mutableMapOf<String, RepoBinding>()
		if (importInstalledMappings && preview.matchedInstalledBindings.isNotEmpty()) {
			newBindings.putAll(preview.matchedInstalledBindings)
			importedMappingsCount += preview.matchedInstalledBindings.size
		}

		val selectedWatched = preview.uninstalledCandidateApps.filter { it.packageName in selectedWatchedPackages }
		for (candidate in selectedWatched) {
			prefs.addWatchedApp(
				WatchedAppEntry(
					packageName = candidate.packageName,
					appName = candidate.appName,
					addedAtMillis = System.currentTimeMillis()
				)
			)
			newBindings[candidate.packageName] = candidate.binding
			importedWatchedCount++
		}

		if (newBindings.isNotEmpty()) {
			if (repoMappingManager != null) {
				repoMappingManager.setCustomBindings(newBindings)
			} else {
				val merged = prefs.readCustomRepoBindingsSafe().toMutableMap()
				merged.putAll(newBindings)
				prefs.writeCustomRepoBindings(merged)
			}
			if (newBindings.values.any { it.isCodeberg } && !prefs.useCodeberg.get()) {
				prefs.useCodeberg.put(true)
			}
			if (newBindings.values.any { it.isGitLab } && !prefs.useGitLab.get()) {
				prefs.useGitLab.put(true)
			}
			if (newBindings.values.any { it.isGitHub } && !prefs.useGitHub.get()) {
				prefs.useGitHub.put(true)
			}
		}

		if (importFDroidRepos && preview.detectedFDroidRepos.isNotEmpty()) {
			for (repo in preview.detectedFDroidRepos) {
				if (addCustomFDroidRepoDetailed(repo.name, repo.baseUrl, repo.fingerprint, allowLanHosts = false) == CustomFDroidAddResult.SUCCESS) {
					importedReposCount++
				}
			}
		}

		notifySnack(R.string.obtainium_import_success, com.apkup.data.snack.SnackVisualType.SUCCESS)
		return Triple(importedMappingsCount, importedReposCount, importedWatchedCount)
	}

	fun notifyObtainiumImportInvalid() {
		notifySnack(R.string.backup_import_error, com.apkup.data.snack.SnackVisualType.ERROR)
	}

	fun notifyObtainiumExported(success: Boolean) {
		notifySnack(
			if (success) R.string.backup_obtainium_export_success else R.string.backup_import_error,
			if (success) com.apkup.data.snack.SnackVisualType.SUCCESS else com.apkup.data.snack.SnackVisualType.ERROR
		)
	}

	fun notifyBackupExported(success: Boolean) {
		notifySnack(
			if (success) R.string.backup_export_success else R.string.backup_import_error,
			if (success) com.apkup.data.snack.SnackVisualType.SUCCESS else com.apkup.data.snack.SnackVisualType.ERROR
		)
	}

	fun buildDiagnosticLogReport(includeLogcat: Boolean = false): String {
		val journal = appJournal ?: AppJournal(journalFile = null)
		val activeSources = buildList {
			if (prefs.useApkMirror.get()) add("ApkMirror")
			if (prefs.usePlay.get()) add("Google Play")
			if (prefs.useIzzyOnDroid.get()) add("IzzyOnDroid")
			if (prefs.useGitHub.get()) add("GitHub")
			if (prefs.useGitLab.get()) add("GitLab")
			if (prefs.useCodeberg.get()) add("Codeberg")
			if (prefs.useCustomFDroidRepos.get()) {
				val activeCustom = prefs.readCustomFDroidReposSafe().count { it.enabled }
				if (activeCustom > 0) {
					add("Custom F-Droid ($activeCustom)")
				}
			}
		}
		// Logcat is strictly opt-in (Q-04d).
		return journal.buildDiagnosticReport(
			appVersionName = BuildConfig.VERSION_NAME,
			appVersionCode = BuildConfig.VERSION_CODE,
			packageName = BuildConfig.APPLICATION_ID,
			shizukuStatus = sessionInstaller?.getShizukuSetupStatus(),
			activeSources = activeSources,
			includeLogcat = includeLogcat
		)
	}

	fun notifyDiagnosticLogExported(success: Boolean) {
		notifySnack(
			if (success) R.string.diagnostic_log_export_success else R.string.diagnostic_log_export_error,
			if (success) com.apkup.data.snack.SnackVisualType.SUCCESS else com.apkup.data.snack.SnackVisualType.ERROR
		)
	}

	fun getIncludePreReleasesGlobal(): Boolean = prefs.includePreReleasesGlobal.get()
	fun setIncludePreReleasesGlobal(enabled: Boolean) {
		prefs.includePreReleasesGlobal.put(enabled)
	}

	fun getIncludeSelfUpdatePreReleases(): Boolean = prefs.includeSelfUpdatePreReleases.get()
	fun setIncludeSelfUpdatePreReleases(enabled: Boolean) {
		prefs.includeSelfUpdatePreReleases.put(enabled)
	}

	fun getAllowedPreReleaseChannels(): Set<String> = prefs.readAllowedPreReleaseChannelsSafe()
	fun setPreReleaseChannelEnabled(channel: String, enabled: Boolean) {
		prefs.setPreReleaseChannelEnabled(channel, enabled)
	}

	fun getAutoUpdateWindowEnabled(): Boolean = prefs.autoUpdateWindowEnabled.get()
	fun setAutoUpdateWindowEnabled(enabled: Boolean) {
		prefs.autoUpdateWindowEnabled.put(enabled)
	}

	fun getAutoUpdateWindowMode(): String = prefs.autoUpdateWindowMode.get()
	fun setAutoUpdateWindowMode(mode: String) {
		if (mode == com.apkup.data.ui.UpdateWindowMode.ALLOW_ONLY || mode == com.apkup.data.ui.UpdateWindowMode.QUIET_PAUSE) {
			prefs.autoUpdateWindowMode.put(mode)
		}
	}

	fun getAutoUpdateWindowStartMinutes(): Int = prefs.autoUpdateWindowStartMinutes.get()
	fun setAutoUpdateWindowStartMinutes(minutes: Int) {
		prefs.autoUpdateWindowStartMinutes.put(com.apkup.data.ui.UpdateWindowEvaluator.normalizeMinutes(minutes))
	}

	fun getAutoUpdateWindowEndMinutes(): Int = prefs.autoUpdateWindowEndMinutes.get()
	fun setAutoUpdateWindowEndMinutes(minutes: Int) {
		prefs.autoUpdateWindowEndMinutes.put(com.apkup.data.ui.UpdateWindowEvaluator.normalizeMinutes(minutes))
	}

	fun getEnableRollbackBackups(): Boolean = prefs.enableRollbackBackups.get()
	fun setEnableRollbackBackups(enabled: Boolean) {
		prefs.enableRollbackBackups.put(enabled)
	}

	fun getRollbackMaxBudgetMb(): Int =
		com.apkup.util.RollbackBackupManager.normalizeBudgetMb(prefs.rollbackMaxBudgetMb.get())

	fun setRollbackMaxBudgetMb(budgetMb: Int, onApplied: ((Int, Long) -> Unit)? = null) {
		val normalized = com.apkup.util.RollbackBackupManager.normalizeBudgetMb(budgetMb)
		prefs.rollbackMaxBudgetMb.put(normalized)
		applyRollbackRetentionNow(onApplied)
	}

	fun getRollbackMaxAgeDays(): Int =
		com.apkup.util.RollbackBackupManager.normalizeMaxAgeDays(prefs.rollbackMaxAgeDays.get())

	fun setRollbackMaxAgeDays(maxAgeDays: Int, onApplied: ((Int, Long) -> Unit)? = null) {
		val normalized = com.apkup.util.RollbackBackupManager.normalizeMaxAgeDays(maxAgeDays)
		prefs.rollbackMaxAgeDays.put(normalized)
		applyRollbackRetentionNow(onApplied)
	}

	fun getRollbackBackupStats(): Pair<Int, Long> =
		rollbackBackupManager?.getBackupStats() ?: (0 to 0L)

	fun applyRollbackRetentionNow(onApplied: ((Int, Long) -> Unit)? = null) {
		val manager = rollbackBackupManager ?: return
		val budgetBytes = com.apkup.util.RollbackBackupManager.budgetMbToBytes(getRollbackMaxBudgetMb())
		val maxAgeDays = getRollbackMaxAgeDays()
		viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
			runCatching { manager.cleanExpiredBackups(maxAgeDays = maxAgeDays, maxTotalBytes = budgetBytes) }
			val updated = runCatching { manager.getBackupStats() }.getOrDefault(0 to 0L)
			kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
				onApplied?.invoke(updated.first, updated.second)
			}
		}
	}

	fun clearAllRollbackBackups(onFinished: ((Int, Long) -> Unit)? = null) {
		val manager = rollbackBackupManager ?: return
		viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
			val (oldCount, _) = runCatching { manager.getBackupStats() }.getOrDefault(0 to 0L)
			runCatching { manager.clearAllBackups() }
			val updated = runCatching { manager.getBackupStats() }.getOrDefault(0 to 0L)
			if (oldCount > 0) {
				notifySnack(R.string.rollback_backups_cleared_snack, com.apkup.data.snack.SnackVisualType.SUCCESS)
			}
			kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
				onFinished?.invoke(updated.first, updated.second)
			}
		}
	}

	fun importAppWatcherJson(
		rawJson: String,
		installedPackages: Set<String>
	): com.apkup.util.AppWatcherImportSummary? {
		val summary = com.apkup.util.AppWatcherImporter.parseExportJson(
			rawJson = rawJson,
			installedPackages = installedPackages,
			existingWatched = prefs.readWatchedAppsSafe()
		)
		if (summary == null) {
			notifySnack(R.string.app_watcher_import_error, com.apkup.data.snack.SnackVisualType.ERROR)
			return null
		}
		summary.addedEntries.forEach { entry ->
			prefs.addWatchedApp(entry)
		}
		val msg = stringer?.let { str ->
			runCatching {
				str.get(
					R.string.app_watcher_import_success,
					summary.addedEntries.size,
					summary.skippedInstalledCount
				)
			}.getOrNull()
		} ?: "App Watcher: ${summary.addedEntries.size} imported (${summary.skippedInstalledCount} installed skipped)"
		notifyTextSnack(msg, com.apkup.data.snack.SnackVisualType.SUCCESS)
		return summary
	}

	private fun notifySnack(
		resId: Int,
		visualType: com.apkup.data.snack.SnackVisualType = com.apkup.data.snack.SnackVisualType.INFO
	) {
		val snack = snackBar ?: return
		val str = stringer ?: return
		snack.snackBar(viewModelScope, TextSnack(str.get(resId), visualType = visualType))
	}

}
