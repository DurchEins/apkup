package com.apkup.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportPromptPolicyTest {
	private val baseTime = 1_700_000_000_000L

	@Test
	fun `returns false before onboarding is completed`() {
		assertFalse(
			SupportPromptPolicy.shouldShowSupportPrompt(
				hasCompletedOnboarding = false,
				isBlockingPromptVisible = false,
				dismissedPermanently = false,
				snoozedUntilMillis = 0L,
				firstLaunchTimestampMillis = baseTime - SupportPromptPolicy.MIN_USAGE_MILLIS - 1_000L,
				successfulUpdatesCount = 10,
				nowMillis = baseTime
			)
		)
	}

	@Test
	fun `returns false when blocking prompt is visible or permanently dismissed`() {
		assertFalse(
			SupportPromptPolicy.shouldShowSupportPrompt(
				hasCompletedOnboarding = true,
				isBlockingPromptVisible = true,
				dismissedPermanently = false,
				snoozedUntilMillis = 0L,
				firstLaunchTimestampMillis = baseTime - SupportPromptPolicy.MIN_USAGE_MILLIS,
				successfulUpdatesCount = 5,
				nowMillis = baseTime
			)
		)
		assertFalse(
			SupportPromptPolicy.shouldShowSupportPrompt(
				hasCompletedOnboarding = true,
				isBlockingPromptVisible = false,
				dismissedPermanently = true,
				snoozedUntilMillis = 0L,
				firstLaunchTimestampMillis = baseTime - SupportPromptPolicy.MIN_USAGE_MILLIS,
				successfulUpdatesCount = 5,
				nowMillis = baseTime
			)
		)
	}

	@Test
	fun `returns false while snoozed and true once snooze expires`() {
		assertFalse(
			SupportPromptPolicy.shouldShowSupportPrompt(
				hasCompletedOnboarding = true,
				isBlockingPromptVisible = false,
				dismissedPermanently = false,
				snoozedUntilMillis = baseTime + 10_000L,
				firstLaunchTimestampMillis = baseTime - SupportPromptPolicy.MIN_USAGE_MILLIS,
				successfulUpdatesCount = 8,
				nowMillis = baseTime
			)
		)
		assertTrue(
			SupportPromptPolicy.shouldShowSupportPrompt(
				hasCompletedOnboarding = true,
				isBlockingPromptVisible = false,
				dismissedPermanently = false,
				snoozedUntilMillis = baseTime,
				firstLaunchTimestampMillis = baseTime - SupportPromptPolicy.MIN_USAGE_MILLIS,
				successfulUpdatesCount = 0,
				nowMillis = baseTime
			)
		)
	}

	@Test
	fun `triggers at 5 days of usage or 5 successful updates`() {
		assertFalse(
			SupportPromptPolicy.shouldShowSupportPrompt(
				hasCompletedOnboarding = true,
				isBlockingPromptVisible = false,
				dismissedPermanently = false,
				snoozedUntilMillis = 0L,
				firstLaunchTimestampMillis = baseTime - SupportPromptPolicy.MIN_USAGE_MILLIS + 1_000L,
				successfulUpdatesCount = 4,
				nowMillis = baseTime
			)
		)
		assertTrue(
			SupportPromptPolicy.shouldShowSupportPrompt(
				hasCompletedOnboarding = true,
				isBlockingPromptVisible = false,
				dismissedPermanently = false,
				snoozedUntilMillis = 0L,
				firstLaunchTimestampMillis = baseTime - 1_000L,
				successfulUpdatesCount = 5,
				nowMillis = baseTime
			)
		)
		assertTrue(
			SupportPromptPolicy.shouldShowSupportPrompt(
				hasCompletedOnboarding = true,
				isBlockingPromptVisible = false,
				dismissedPermanently = false,
				snoozedUntilMillis = 0L,
				firstLaunchTimestampMillis = baseTime - SupportPromptPolicy.MIN_USAGE_MILLIS,
				successfulUpdatesCount = 0,
				nowMillis = baseTime
			)
		)
	}
}
