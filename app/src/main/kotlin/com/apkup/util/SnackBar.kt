package com.apkup.util

import androidx.compose.material3.SnackbarVisuals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

class SnackBar {

    private val snackBars = MutableSharedFlow<SnackbarVisuals>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    fun flow(): SharedFlow<SnackbarVisuals> = snackBars

    fun emit(message: SnackbarVisuals): Boolean = snackBars.tryEmit(message)

    @Suppress("UNUSED_PARAMETER")
    fun snackBar(
        scope: CoroutineScope? = null,
        message: SnackbarVisuals
    ): Boolean = snackBars.tryEmit(message)
}
