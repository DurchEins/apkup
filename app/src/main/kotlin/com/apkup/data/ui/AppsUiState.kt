package com.apkup.data.ui


sealed class AppsUiState {

	data object Loading : AppsUiState()

	data object Error : AppsUiState()

	data class Success(val apps: List<AppInstalled>) : AppsUiState()

	inline fun onLoading(block: (Loading) -> Unit): AppsUiState {
		if (this is Loading) block(this)
		return this
	}

	inline fun onError(block: (Error) -> Unit): AppsUiState {
		if (this is Error) block(this)
		return this
	}

	inline fun onSuccess(block: (Success) -> Unit): AppsUiState {
		if (this is Success) block(this)
		return this
	}

	fun apps(): List<AppInstalled> = if (this is Success) apps else emptyList()
}
