package com.apkup.repository

import com.apkup.data.ui.SourceUpdateResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal data class BatchRequestResult<T>(
	val responses: List<T>,
	val successfulRequests: Int,
	val failedRequests: Int
)

internal suspend fun <Input, Output> requestSequentialBatches(
	items: List<Input>,
	batchSize: Int,
	request: suspend (List<Input>) -> List<Output>,
	onFailure: (Exception) -> Unit = {}
): BatchRequestResult<Output> {
	require(batchSize > 0) { "Batch size must be greater than zero" }
	val responses = mutableListOf<Output>()
	var successfulRequests = 0
	var failedRequests = 0

	for (batch in items.chunked(batchSize)) {
		try {
			responses += request(batch)
			successfulRequests++
		} catch (cancellation: CancellationException) {
			throw cancellation
		} catch (exception: Exception) {
			failedRequests++
			onFailure(exception)
		}
	}

	return BatchRequestResult(responses, successfulRequests, failedRequests)
}

internal suspend fun <Input, Output> requestConcurrentBatches(
	items: List<Input>,
	batchSize: Int,
	concurrency: Int,
	request: suspend (List<Input>) -> List<Output>,
	stopSchedulingAfterFailure: (Exception) -> Boolean = { false },
	onFailure: (Exception) -> Unit = {},
	onBatchSuccess: suspend (List<Input>) -> Unit = {},
	workerStaggerMs: Long = 0L,
	dispatcher: CoroutineDispatcher = Dispatchers.IO
): BatchRequestResult<Output> = coroutineScope {
	require(batchSize > 0) { "Batch size must be greater than zero" }
	require(concurrency > 0) { "Concurrency must be greater than zero" }
	val batches = items.chunked(batchSize)
	if (batches.isEmpty()) return@coroutineScope BatchRequestResult(emptyList(), 0, 0)

	val nextBatch = AtomicInteger(0)
	val stopScheduling = AtomicBoolean(false)
	val workerCount = minOf(concurrency, batches.size)
	val workerResults = (0 until workerCount).map { workerIndex ->
		async(dispatcher) {
			if (workerStaggerMs > 0L && workerIndex > 0) {
				delay(workerIndex * workerStaggerMs)
			}
			val responses = mutableListOf<Output>()
			var successfulRequests = 0
			var failedRequests = 0
			while (!stopScheduling.get()) {
				val index = nextBatch.getAndIncrement()
				if (index >= batches.size) break
				val response = try {
					try {
						request(batches[index])
					} catch (timeout: java.net.SocketTimeoutException) {
						delay(1500L)
						request(batches[index])
					}
				} catch (cancellation: CancellationException) {
					throw cancellation
				} catch (exception: Exception) {
					failedRequests++
					onFailure(exception)
					if (stopSchedulingAfterFailure(exception)) stopScheduling.set(true)
					continue
				}
				responses += response
				successfulRequests++
				onBatchSuccess(batches[index])
			}
			BatchRequestResult(responses, successfulRequests, failedRequests)
		}
	}.awaitAll()
	BatchRequestResult(
		responses = workerResults.flatMap { it.responses },
		successfulRequests = workerResults.sumOf { it.successfulRequests },
		failedRequests = workerResults.sumOf { it.failedRequests }
	)
}

internal suspend fun <T> captureUpdateSourceCheck(
	check: suspend () -> SourceUpdateResult<T>,
	onFailure: (Exception) -> Unit = {}
): SourceUpdateResult<T> = try {
	check()
} catch (cancellation: CancellationException) {
	throw cancellation
} catch (exception: Exception) {
	onFailure(exception)
	SourceUpdateResult(
		emptyList(),
		successfulRequests = 0,
		failedRequests = 1,
		failureSummary = exception.javaClass.simpleName
	)
}
