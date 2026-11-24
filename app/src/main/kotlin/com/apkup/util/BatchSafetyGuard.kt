package com.apkup.util

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.StatFs
import java.io.File

internal const val MIN_BATCH_BATTERY_PERCENT = 15
internal const val MIN_BATCH_FREE_STORAGE_BYTES = 500L * 1024L * 1024L

data class BatchSafetyStatus(
    val isLowBattery: Boolean,
    val batteryPercent: Int?,
    val isCharging: Boolean,
    val isLowStorage: Boolean,
    val freeStorageBytes: Long?
) {
    val hasAnyWarning: Boolean
        get() = (isLowBattery && !isCharging) || isLowStorage
}

internal fun evaluateBatchSafetyStatus(
    batteryPercent: Int?,
    isCharging: Boolean,
    freeStorageBytes: Long?
): BatchSafetyStatus {
    val lowBattery = batteryPercent != null && batteryPercent in 0 until MIN_BATCH_BATTERY_PERCENT
    val lowStorage = freeStorageBytes != null && freeStorageBytes in 0 until MIN_BATCH_FREE_STORAGE_BYTES
    return BatchSafetyStatus(
        isLowBattery = lowBattery,
        batteryPercent = batteryPercent,
        isCharging = isCharging,
        isLowStorage = lowStorage,
        freeStorageBytes = freeStorageBytes
    )
}

internal fun checkBatchSafetyStatus(context: Context, storageDir: File): BatchSafetyStatus {
    val batteryPercent = readBatteryPercent(context)
    val isCharging = readIsDeviceCharging(context)
    val freeBytes = readAvailableStorageBytes(storageDir)
    return evaluateBatchSafetyStatus(
        batteryPercent = batteryPercent,
        isCharging = isCharging,
        freeStorageBytes = freeBytes
    )
}

private fun readBatteryPercent(context: Context): Int? = runCatching {
    val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    val prop = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    if (prop != null && prop in 0..100) return@runCatching prop
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        ?: return@runCatching null
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    if (level >= 0 && scale > 0) (level * 100) / scale else null
}.getOrNull()

private fun readIsDeviceCharging(context: Context): Boolean = runCatching {
    val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    if (bm != null && bm.isCharging) return@runCatching true
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        ?: return@runCatching false
    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
    status == BatteryManager.BATTERY_STATUS_CHARGING ||
        status == BatteryManager.BATTERY_STATUS_FULL ||
        plugged != 0
}.getOrDefault(false)

private fun readAvailableStorageBytes(storageDir: File): Long? = runCatching {
    val target = if (storageDir.exists()) storageDir else storageDir.parentFile ?: storageDir
    StatFs(target.absolutePath).availableBytes
}.getOrNull()
