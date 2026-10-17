package com.apkup.repository

/**
 * Counts an app only after every enabled source has returned a successful batch containing it.
 * Failed and unscheduled batches never advance the checked-app count.
 */
internal class UpdateCheckProgressTracker(
	packageNames: Collection<String>,
	sourceNames: Collection<String>
) {
	private val expectedPackages = packageNames.toSet()
	private val completedBySource = sourceNames.associateWith { mutableSetOf<String>() }
	private val finishedSources = mutableSetOf<String>()
	private var latestActiveSource: String? = sourceNames.firstOrNull()

	@Synchronized
	fun markBatchSucceeded(sourceName: String, packageNames: Collection<String>): Int {
		if (sourceName in completedBySource && sourceName !in finishedSources) {
			latestActiveSource = sourceName
		}
		completedBySource[sourceName]?.addAll(packageNames.filter(expectedPackages::contains))
		return checkedApps()
	}

	@Synchronized
	fun markSourceFinished(sourceName: String): Int {
		if (sourceName in completedBySource) {
			finishedSources += sourceName
			if (latestActiveSource == sourceName) {
				latestActiveSource = completedBySource.keys.firstOrNull { it !in finishedSources }
			}
		}
		return checkedApps()
	}

	@Synchronized
	fun activeSourceName(): String? {
		val current = latestActiveSource?.takeIf { it in completedBySource && it !in finishedSources }
		return current ?: completedBySource.keys.firstOrNull { it !in finishedSources }
	}

	@Synchronized
	fun checkedApps(): Int {
		if (completedBySource.isEmpty()) return 0
		val activeSources = completedBySource.keys.filter { it !in finishedSources }
		val relevantSets = if (activeSources.isNotEmpty()) {
			activeSources.mapNotNull { completedBySource[it] }
		} else {
			completedBySource.values.filter { it.isNotEmpty() }.ifEmpty { completedBySource.values.toList() }
		}
		return expectedPackages.count { packageName ->
			relevantSets.isNotEmpty() && relevantSets.all { packageName in it }
		}
	}

	val totalApps: Int get() = expectedPackages.size
}

internal fun visibleCheckedApps(checkedApps: Int, totalApps: Int, isChecking: Boolean): Int? {
	if (totalApps <= 0) return null
	val checked = checkedApps.coerceIn(0, totalApps)
	return if (isChecking) minOf(checked, totalApps - 1) else checked
}
