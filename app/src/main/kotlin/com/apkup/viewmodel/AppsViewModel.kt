package com.apkup.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apkup.data.repomapping.RepoBinding
import com.apkup.data.repomapping.RepoUrlParser
import com.apkup.data.ui.AppInstalled
import com.apkup.data.ui.AppsQuickFilter
import com.apkup.data.ui.AppsSortOrder
import com.apkup.data.ui.AppsUiState
import com.apkup.data.ui.WatchedAppEntry
import com.apkup.data.ui.filterAndSortInstalledApps
import com.apkup.prefs.Prefs
import com.apkup.repository.AppsRepository
import com.apkup.repository.RepoMappingManager
import com.apkup.util.ApkIntegrityVerifier
import com.apkup.util.Badger
import com.apkup.util.launchWithMutex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AppsViewModel(
	private val repository: AppsRepository,
	private val prefs: Prefs,
	private val badger: Badger,
	private val repoMappingManager: RepoMappingManager? = null
) : ViewModel() {

	enum class ScopeTab {
		USER,
		SYSTEM
	}

	enum class ShareAppsScope {
		USER,
		SYSTEM,
		ALL
	}

	enum class WatchAppAddResult {
		SUCCESS,
		INVALID_PACKAGE,
		INVALID_REPO
	}

	private val mutex = Mutex()
	private val state = MutableStateFlow<AppsUiState>(buildLoadingState())
	private val searchQueryState = MutableStateFlow("")
	private val quickFilterState = MutableStateFlow(AppsQuickFilter.ALL)
	private val scopeTabState = MutableStateFlow(ScopeTab.USER)
	private val viewModeState = MutableStateFlow(prefs.appsViewMode.get().ifBlank { "list" })

	val showSystemAppsFlow: StateFlow<Boolean> = prefs.showSystemAppsFlow
	val customRepoBindingsFlow: StateFlow<Map<String, RepoBinding>> = prefs.customRepoBindingsFlow

	init {
		viewModelScope.launch {
			prefs.showSystemAppsFlow.collect {
				refresh(false)
			}
		}
	}

	fun state(): StateFlow<AppsUiState> = state
	fun searchQuery(): StateFlow<String> = searchQueryState
	fun quickFilter(): StateFlow<AppsQuickFilter> = quickFilterState
	fun scopeTab(): StateFlow<ScopeTab> = scopeTabState
	fun viewMode(): StateFlow<String> = viewModeState
	fun watchedApps(): StateFlow<List<WatchedAppEntry>> = prefs.watchedAppsFlow
	fun pinnedAppSources(): StateFlow<Map<String, String>> = prefs.pinnedAppSourcesFlow
	fun appPreReleaseOverrides(): StateFlow<Map<String, String>> = prefs.appPreReleaseOverridesFlow

	fun getPinnedAppSource(packageName: String): String? = prefs.getPinnedAppSource(packageName)

	fun pinAppSource(packageName: String, sourceKey: String?) {
		prefs.pinAppSource(packageName, sourceKey)
	}

	fun getAppPreReleaseOverride(packageName: String): String = prefs.getAppPreReleaseOverride(packageName)

	fun setAppPreReleaseOverride(packageName: String, mode: String?) {
		prefs.setAppPreReleaseOverride(packageName, mode)
	}

	fun getAppsSortOrder(): AppsSortOrder = runCatching {
		AppsSortOrder.valueOf(prefs.appsSortOrder.get())
	}.getOrDefault(AppsSortOrder.NAME_ASC)

	fun setAppsSortOrder(order: AppsSortOrder) {
		prefs.appsSortOrder.put(order.name)
	}

	fun addWatchedApp(
		packageName: String,
		appName: String = "",
		repoUrlOrSlug: String = "",
		platformType: String = ""
	): Boolean = addWatchedAppDetailed(
		packageName = packageName,
		appName = appName,
		repoUrlOrSlug = repoUrlOrSlug,
		platformType = platformType
	) == WatchAppAddResult.SUCCESS

	fun addWatchedAppDetailed(
		packageName: String,
		appName: String = "",
		repoUrlOrSlug: String = "",
		platformType: String = ""
	): WatchAppAddResult {
		val pkg = packageName.trim()
		if (!ApkIntegrityVerifier.isValidPackageName(pkg)) {
			return WatchAppAddResult.INVALID_PACKAGE
		}
		val repoInput = repoUrlOrSlug.trim()
		if (repoInput.isNotBlank()) {
			val binding = RepoUrlParser.parseRepoUrl(
				input = repoInput,
				hintSource = platformType.ifBlank { null },
				allowLanHosts = true
			) ?: return WatchAppAddResult.INVALID_REPO
			if (repoMappingManager != null) {
				repoMappingManager.setCustomBinding(pkg, binding)
			} else {
				val bindings = prefs.readCustomRepoBindingsSafe().toMutableMap()
				bindings[pkg] = binding
				prefs.writeCustomRepoBindings(bindings)
			}
		}
		prefs.addWatchedApp(
			WatchedAppEntry(
				packageName = pkg,
				appName = appName.trim().ifBlank { pkg },
				addedAtMillis = System.currentTimeMillis()
			)
		)
		refresh(false)
		return WatchAppAddResult.SUCCESS
	}

	fun removeWatchedApp(packageName: String) {
		prefs.removeWatchedApp(packageName)
		refresh(false)
	}

	fun getMappedPackages(): Set<String> =
		repoMappingManager?.getMappedPackages().orEmpty() + prefs.readCustomRepoBindingsSafe().keys

	fun setSearchQuery(query: String) {
		searchQueryState.value = query
	}

	fun setQuickFilter(filter: AppsQuickFilter) {
		quickFilterState.value = filter
	}

	fun setScopeTab(tab: ScopeTab) {
		scopeTabState.value = tab
	}

	fun setViewMode(mode: String) {
		viewModeState.value = mode
		prefs.appsViewMode.put(mode)
	}

	fun refresh(load: Boolean = true) = viewModelScope.launchWithMutex(mutex, Dispatchers.IO) {
		refreshAndGetLocked(load)
	}

	suspend fun refreshAndGet(load: Boolean = false) = withContext(Dispatchers.IO) {
		mutex.withLock { refreshAndGetLocked(load) }
	}

	@Suppress("SENSELESS_COMPARISON")
	private suspend fun refreshAndGetLocked(load: Boolean): Result<List<AppInstalled>> {
		if (load) state.value = buildLoadingState()
		badger.changeAppsBadge("")
		val result = repository.getApps().map { installedApps ->
			val installedPackages = installedApps.mapTo(HashSet()) { it.packageName }
			val watchedOnlyApps = prefs.readWatchedAppsSafe()
				.mapNotNull { entry ->
					val safePkg = (entry.packageName as String?)?.trim().orEmpty()
					if (safePkg.isBlank() || safePkg in installedPackages) return@mapNotNull null
					val safeName = (entry.appName as String?)?.trim().orEmpty()
					AppInstalled(
						name = safeName.ifBlank { safePkg },
						packageName = safePkg,
						version = "",
						versionCode = -1L,
						signatureSha256 = "",
						isWatchedOnly = true
					)
				}
			installedApps + watchedOnlyApps
		}
		result.onSuccess { apps ->
			state.value = AppsUiState.Success(apps)
			badger.changeAppsBadge("")
		}.onFailure { ex ->
			state.value = AppsUiState.Error
			badger.changeAppsBadge("!")
			Log.e("InstalledViewModel", "Could not load installed apps (${ex.javaClass.simpleName})")
		}
		return result
	}

	fun ignore(packageName: String) = viewModelScope.launchWithMutex(mutex, Dispatchers.IO) {
		prefs.toggleIgnoredApp(packageName)
		refreshAndGetLocked(false)
	}

	suspend fun loadAppsForShare(
		scope: ShareAppsScope,
		sortOrder: AppsSortOrder
	): List<AppInstalled> = withContext(Dispatchers.IO) {
		val installed = repository.getApps(includeSystem = scope != ShareAppsScope.USER)
			.getOrDefault(emptyList())
		val scoped = when (scope) {
			ShareAppsScope.USER -> installed.filterNot { it.isSystemApp }
			ShareAppsScope.SYSTEM -> installed.filter { it.isSystemApp }
			ShareAppsScope.ALL -> installed
		}
		filterAndSortInstalledApps(
			apps = scoped,
			searchQuery = "",
			quickFilter = AppsQuickFilter.ALL,
			sortOrder = sortOrder,
			mappedPackages = getMappedPackages()
		)
	}

	private fun buildLoadingState() = AppsUiState.Loading

}
