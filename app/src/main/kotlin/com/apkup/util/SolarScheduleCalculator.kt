package com.apkup.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/**
 * Computes seasonal local sunrise and sunset times completely offline from the device time zone
 * using the NOAA astronomical solar equation (no location permissions or network calls required).
 */
object SolarScheduleCalculator {

    const val SUBMODE_AUTO_SUN = 0
    const val SUBMODE_LIGHT_AAA = 1
    const val SUBMODE_DARK_AAA = 2
    const val UNSET_MINUTES = -1

    data class SolarWindow(
        val sunriseMinutes: Int,
        val sunsetMinutes: Int,
        val isCustom: Boolean
    )

    private val zoneCoordinates: Map<String, Pair<Double, Double>> = mapOf(
        "Europe/Berlin" to (52.52 to 13.405),
        "Europe/Vienna" to (48.208 to 16.373),
        "Europe/Zurich" to (47.377 to 8.542),
        "Europe/London" to (51.507 to -0.128),
        "Europe/Paris" to (48.857 to 2.352),
        "Europe/Madrid" to (40.417 to -3.704),
        "Europe/Rome" to (41.903 to 12.496),
        "Europe/Amsterdam" to (52.368 to 4.904),
        "Europe/Brussels" to (50.850 to 4.352),
        "Europe/Warsaw" to (52.230 to 21.012),
        "Europe/Prague" to (50.075 to 14.438),
        "Europe/Stockholm" to (59.329 to 18.069),
        "Europe/Lisbon" to (38.722 to -9.139),
        "Europe/Athens" to (37.984 to 23.728),
        "Europe/Kyiv" to (50.450 to 30.523),
        "Europe/Moscow" to (55.756 to 37.617),
        "America/New_York" to (40.713 to -74.006),
        "America/Toronto" to (43.653 to -79.383),
        "America/Chicago" to (41.878 to -87.630),
        "America/Denver" to (39.739 to -104.990),
        "America/Los_Angeles" to (34.052 to -118.244),
        "America/Mexico_City" to (19.433 to -99.133),
        "America/Sao_Paulo" to (-23.550 to -46.633),
        "America/Buenos_Aires" to (-34.604 to -58.382),
        "Asia/Tokyo" to (35.676 to 139.650),
        "Asia/Shanghai" to (31.230 to 121.474),
        "Asia/Hong_Kong" to (22.319 to 114.169),
        "Asia/Taipei" to (25.033 to 121.565),
        "Asia/Seoul" to (37.566 to 126.978),
        "Asia/Kolkata" to (22.573 to 88.364),
        "Asia/Singapore" to (1.352 to 103.820),
        "Asia/Dubai" to (25.205 to 55.271),
        "Australia/Sydney" to (-33.869 to 151.209),
        "Pacific/Auckland" to (-36.848 to 174.763),
        "Africa/Cairo" to (30.044 to 31.236),
        "Africa/Johannesburg" to (-26.204 to 28.047)
    )

    internal fun coordinatesForZone(timeZone: TimeZone): Pair<Double, Double> {
        zoneCoordinates[timeZone.id]?.let { return it }
        val rawOffsetHours = timeZone.rawOffset / 3_600_000.0
        val estimatedLon = (rawOffsetHours * 15.0).coerceIn(-180.0, 180.0)
        val isSouthernHemisphere =
            timeZone.id.startsWith("Australia/") ||
                timeZone.id.startsWith("Antarctica/") ||
                timeZone.id.contains("Sydney") ||
                timeZone.id.contains("Melbourne") ||
                timeZone.id.contains("Auckland") ||
                timeZone.id.contains("Johannesburg") ||
                timeZone.id.contains("Buenos_Aires") ||
                timeZone.id.contains("Sao_Paulo") ||
                timeZone.id.contains("Santiago")
        val estimatedLat = if (isSouthernHemisphere) -33.0 else 48.0
        return estimatedLat to estimatedLon
    }

    fun calculateSolarWindow(
        date: LocalDate = LocalDate.now(),
        timeZone: TimeZone = TimeZone.getDefault(),
        epochMillis: Long = System.currentTimeMillis()
    ): SolarWindow {
        val (latDeg, lonDeg) = coordinatesForZone(timeZone)
        val gamma = (2.0 * PI / 365.0) * (date.dayOfYear - 1)
        val eqTime = 229.18 * (
            0.000075 +
                0.001868 * cos(gamma) -
                0.032077 * sin(gamma) -
                0.014615 * cos(2.0 * gamma) -
                0.040849 * sin(2.0 * gamma)
            )
        val declRad = 0.006918 -
            0.399912 * cos(gamma) +
            0.070257 * sin(gamma) -
            0.006758 * cos(2.0 * gamma) +
            0.000907 * sin(2.0 * gamma) -
            0.002697 * cos(3.0 * gamma) +
            0.00148 * sin(3.0 * gamma)

        val latRad = Math.toRadians(latDeg)
        val cosZenith = cos(Math.toRadians(90.833))
        val cosHa = (cosZenith / (cos(latRad) * cos(declRad))) - (tan(latRad) * tan(declRad))

        val offsetMinutes = timeZone.getOffset(epochMillis) / 60_000.0
        if (cosHa > 1.0) {
            return SolarWindow(sunriseMinutes = 9 * 60, sunsetMinutes = 15 * 60, isCustom = false)
        }
        if (cosHa < -1.0) {
            return SolarWindow(sunriseMinutes = 5 * 60, sunsetMinutes = 21 * 60, isCustom = false)
        }

        val haDeg = Math.toDegrees(acos(cosHa))
        val sunriseUtc = 720.0 - 4.0 * (lonDeg + haDeg) - eqTime
        val sunsetUtc = 720.0 - 4.0 * (lonDeg - haDeg) - eqTime

        val sunriseLocal = normalizeMinutes((sunriseUtc + offsetMinutes).roundToInt())
        val sunsetLocal = normalizeMinutes((sunsetUtc + offsetMinutes).roundToInt())
        return SolarWindow(sunriseMinutes = sunriseLocal, sunsetMinutes = sunsetLocal, isCustom = false)
    }

    fun resolveEffectiveWindow(
        customSunriseMinutes: Int,
        customSunsetMinutes: Int,
        date: LocalDate = LocalDate.now(),
        timeZone: TimeZone = TimeZone.getDefault(),
        epochMillis: Long = System.currentTimeMillis()
    ): SolarWindow {
        if (customSunriseMinutes in 0 until 1440 && customSunsetMinutes in 0 until 1440 && customSunriseMinutes != customSunsetMinutes) {
            return SolarWindow(
                sunriseMinutes = customSunriseMinutes,
                sunsetMinutes = customSunsetMinutes,
                isCustom = true
            )
        }
        return calculateSolarWindow(date = date, timeZone = timeZone, epochMillis = epochMillis)
    }

    fun isDarkAt(
        subMode: Int,
        customSunriseMinutes: Int = UNSET_MINUTES,
        customSunsetMinutes: Int = UNSET_MINUTES,
        timeZone: TimeZone = TimeZone.getDefault(),
        epochMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (subMode == SUBMODE_LIGHT_AAA) return false
        if (subMode == SUBMODE_DARK_AAA) return true

        val zoned = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.of(timeZone.id))
        val window = resolveEffectiveWindow(
            customSunriseMinutes = customSunriseMinutes,
            customSunsetMinutes = customSunsetMinutes,
            date = zoned.toLocalDate(),
            timeZone = timeZone,
            epochMillis = epochMillis
        )
        val currentMinutes = zoned.hour * 60 + zoned.minute
        return if (window.sunriseMinutes < window.sunsetMinutes) {
            currentMinutes < window.sunriseMinutes || currentMinutes >= window.sunsetMinutes
        } else {
            // Wrap-around case if custom times cross midnight
            currentMinutes < window.sunriseMinutes && currentMinutes >= window.sunsetMinutes
        }
    }

    fun millisUntilNextTransition(
        customSunriseMinutes: Int = UNSET_MINUTES,
        customSunsetMinutes: Int = UNSET_MINUTES,
        timeZone: TimeZone = TimeZone.getDefault(),
        epochMillis: Long = System.currentTimeMillis()
    ): Long {
        val zoned = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.of(timeZone.id))
        val window = resolveEffectiveWindow(
            customSunriseMinutes = customSunriseMinutes,
            customSunsetMinutes = customSunsetMinutes,
            date = zoned.toLocalDate(),
            timeZone = timeZone,
            epochMillis = epochMillis
        )
        val currentSec = zoned.hour * 3600L + zoned.minute * 60L + zoned.second
        val sunriseSec = window.sunriseMinutes * 60L
        val sunsetSec = window.sunsetMinutes * 60L

        val candidates = listOf(sunriseSec, sunsetSec, sunriseSec + 86_400L)
        val nextSec = candidates.first { it > currentSec }
        return ((nextSec - currentSec) * 1_000L).coerceAtLeast(1_000L)
    }

    fun formatMinutesHHmm(minutes: Int): String {
        val norm = normalizeMinutes(minutes)
        val h = norm / 60
        val m = norm % 60
        return "%02d:%02d".format(h, m)
    }

    private fun normalizeMinutes(minutes: Int): Int = ((minutes % 1440) + 1440) % 1440
}
