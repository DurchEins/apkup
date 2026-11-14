package com.apkup.data.ui

import android.content.pm.PackageInstaller

enum class InstallFailureReason {
	SIGNATURE_MISMATCH,
	ABORTED,
	STORAGE_FULL,
	GENERIC
}

data class AppInstallStatus(
	val success: Boolean,
	val id: Int,
	val snack: Boolean = true,
	val failureReason: InstallFailureReason? = null
)

internal fun resolveInstallFailureReason(
	status: Int?,
	message: String?
): InstallFailureReason {
	val msg = message.orEmpty()
	return when {
		msg.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE", ignoreCase = true) ||
			msg.contains("INSTALL_FAILED_SHARED_USER_INCOMPATIBLE", ignoreCase = true) ||
			msg.contains("signatures do not match", ignoreCase = true) ||
			status == PackageInstaller.STATUS_FAILURE_CONFLICT -> InstallFailureReason.SIGNATURE_MISMATCH
		status == PackageInstaller.STATUS_FAILURE_ABORTED -> InstallFailureReason.ABORTED
		status == PackageInstaller.STATUS_FAILURE_STORAGE -> InstallFailureReason.STORAGE_FULL
		else -> InstallFailureReason.GENERIC
	}
}
