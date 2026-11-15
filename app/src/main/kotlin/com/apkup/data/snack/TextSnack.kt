package com.apkup.data.snack

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarVisuals


enum class SnackVisualType {
    INFO,
    SUCCESS,
    WARNING,
    ERROR
}

class TextSnack(
    override val message: String,
    override val actionLabel: String? = null,
    override val duration: SnackbarDuration = SnackbarDuration.Short,
    override val withDismissAction: Boolean = true,
    val visualType: SnackVisualType = SnackVisualType.INFO
): SnackbarVisuals
