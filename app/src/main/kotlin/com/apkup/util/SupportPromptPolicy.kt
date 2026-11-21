package com.apkup.util

/**
 * Pure policy evaluating when the one-time, non-intrusive support & star reminder may be shown.
 */
object SupportPromptPolicy {
	const val MIN_USAGE_MILLIS: Long = 5L * 24L * 60L * 60L * 1_000L
	const val MIN_SUCCESSFUL_UPDATES: Int = 5
	const val SNOOZE_DURATION_MILLIS: Long = 7L * 24L * 60L * 60L * 1_000L

	/**
	 * Returns `true` only when onboarding is complete, no higher-priority prompt is visible,
	 * the user has neither dismissed nor actively snoozed the prompt, and either 5+ days have
	 * elapsed since first launch OR at least 5 successful app updates have been recorded.
	 */
	fun shouldShowSupportPrompt(
		hasCompletedOnboarding: Boolean,
		isBlockingPromptVisible: Boolean,
		dismissedPermanently: Boolean,
		snoozedUntilMillis: Long,
		firstLaunchTimestampMillis: Long,
		successfulUpdatesCount: Int,
		nowMillis: Long
	): Boolean {
		if (!hasCompletedOnboarding || isBlockingPromptVisible || dismissedPermanently) {
			return false
		}
		if (snoozedUntilMillis > 0L && nowMillis < snoozedUntilMillis) {
			return false
		}
		val hasReachedUsageDays = firstLaunchTimestampMillis > 0L &&
			(nowMillis - firstLaunchTimestampMillis) >= MIN_USAGE_MILLIS
		val hasReachedUpdateCount = successfulUpdatesCount >= MIN_SUCCESSFUL_UPDATES
		return hasReachedUsageDays || hasReachedUpdateCount
	}
}
