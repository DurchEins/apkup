package com.apkup.data.ui


sealed class UpdatesUiState {
	open val isChecking: Boolean = false
	data object Loading: UpdatesUiState() {
		override val isChecking: Boolean = true
	}
	data object Error : UpdatesUiState()
    data class Success(
		val updates: List<AppUpdate>,
		val hasSourceErrors: Boolean = false,
		override val isChecking: Boolean = false,
		val checkedApps: Int? = null,
		val totalApps: Int? = null,
		val activeSourceName: String? = null,
		val sourceErrors: Map<String, String> = emptyMap(),
		val lastCheckedAtMillis: Long? = null
	): UpdatesUiState()

	inline fun onLoading(block: (Loading) -> Unit): UpdatesUiState {
		if (this is Loading) block(this)
		return this
	}

	inline fun onError(block: (Error) -> Unit): UpdatesUiState {
		if (this is Error) block(this)
		return this
	}

	inline fun onSuccess(block: (Success) -> Unit): UpdatesUiState {
		if (this is Success) block(this)
		return this
	}

	fun mutableUpdates(): MutableList<AppUpdate> {
		if (this is Success) {
			return updates.toMutableList()
		}
		return mutableListOf()
	}

	fun updates(): List<AppUpdate> {
		if (this is Success) {
			return updates
		}
		return emptyList()
	}

}

internal fun updateCheckLoadingState(existingState: UpdatesUiState? = null): UpdatesUiState {
	if (existingState is UpdatesUiState.Success && existingState.updates.isNotEmpty()) {
		return existingState.copy(
			isChecking = true,
			checkedApps = 0,
			activeSourceName = null
		)
	}
	return UpdatesUiState.Loading
}

internal fun mergeRetainedUpdatesDuringCheck(
	retainedUpdates: List<AppUpdate>,
	incomingUpdates: List<AppUpdate>,
	isChecking: Boolean
): List<AppUpdate> {
	if (!isChecking || retainedUpdates.isEmpty()) return incomingUpdates
	if (incomingUpdates.isEmpty()) return retainedUpdates
	val incomingByPackage = incomingUpdates.associateBy { it.packageName }
	val merged = retainedUpdates.map { existing ->
		incomingByPackage[existing.packageName] ?: existing
	}.toMutableList()
	val existingPackages = retainedUpdates.asSequence().map { it.packageName }.toSet()
	incomingUpdates.forEach { incoming ->
		if (incoming.packageName !in existingPackages) {
			merged.add(incoming)
		}
	}
	return merged.sortedBy { it.name.lowercase() }
}

internal fun UpdateCheckResult<AppUpdate>.toUpdatesUiState(): UpdatesUiState =
	when {
		isChecking -> UpdatesUiState.Success(
			updates = updates,
			hasSourceErrors = hasFailures,
			isChecking = true,
			checkedApps = checkedApps,
			totalApps = totalApps,
			activeSourceName = activeSourceName,
			sourceErrors = sourceErrors
		)
		isCompleteFailure -> UpdatesUiState.Error
		else -> UpdatesUiState.Success(
			updates = updates,
			hasSourceErrors = hasFailures,
			isChecking = false,
			checkedApps = checkedApps,
			totalApps = totalApps,
			activeSourceName = null,
			sourceErrors = sourceErrors
		)
	}

