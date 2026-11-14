package com.apkup.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Unexported BroadcastReceiver for instant download cancellation from system notifications
 * without triggering ForegroundServiceStartNotAllowedException on Android 14+.
 */
class DownloadCancelReceiver : BroadcastReceiver(), KoinComponent {

    companion object {
        const val ACTION_CANCEL_DOWNLOAD = "com.apkup.worker.ACTION_CANCEL_DOWNLOAD"
        const val ACTION_CANCEL_ALL = "com.apkup.worker.ACTION_CANCEL_ALL"
        const val EXTRA_UPDATE_ID = "extra_update_id"
    }

    private val coordinator: DownloadCoordinator by inject()

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_CANCEL_DOWNLOAD -> {
                val id = intent.getIntExtra(EXTRA_UPDATE_ID, -1)
                if (id != -1) {
                    coordinator.cancelTask(id)
                }
            }
            ACTION_CANCEL_ALL -> {
                coordinator.cancelAll()
            }
        }
    }
}
