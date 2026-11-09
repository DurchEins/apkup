package com.apkup.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apkup.data.ui.AppInstalled
import com.apkup.data.ui.AppsQuickFilter
import com.apkup.data.ui.AppsUiState
import com.apkup.prefs.Prefs
import com.apkup.repository.AppsRepository
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
	private val badger: Badger
) : ViewModel() {

	enum class ScopeTab {
		USER,
		SYSTEM
	}

	private val mutex = Mutex()
	private val state = MutableStateFlow<AppsUiState>(buildLoadingState())
	private val searchQueryState = MutableStateFlow("")
	private val quickFilterState = MutableStateFlow(AppsQuickFilter.ALL)
	private val scopeTabState = MutableStateFlow(ScopeTab.USER)
	private val viewModeState = MutableStateFlow(prefs.appsViewMode.get().ifBlank { "list" })

	val showSystemAppsFlow: StateFlow<Boolean> = prefs.showSystemAppsFlow

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

	private suspend fun refreshAndGetLocked(load: Boolean): Result<List<AppInstalled>> {
		if (load) state.value = buildLoadingState()
		badger.changeAppsBadge("")
		val result = repository.getApps()
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

	fun ignore(packageName: String) = viewModelScope.launchWithMutex(mutex, Dispatchers.Default) {
		val ignored = prefs.ignoredApps.get().toMutableList()
		if (ignored.contains(packageName)) {
			ignored.remove(packageName)
		} else {
			ignored.add(packageName)
		}
		prefs.ignoredApps.put(ignored)
		refresh(false)
	}

	private fun buildLoadingState() = AppsUiState.Loading

}
