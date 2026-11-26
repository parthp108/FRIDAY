package com.health.friday.shared.sync.cloud

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Coalesces bursts of sync requests without dropping requests that arrive
 * while a sync is already running.
 *
 * Requests received during an active sync remain buffered and cause another
 * sync after the current one finishes.
 */
class DebouncedSyncTrigger(
    scope: CoroutineScope,
    private val debounceMs: Long = DEFAULT_DEBOUNCE_MS,
    private val onFailure: (Throwable) -> Unit = {},
    private val action: suspend () -> Unit
) {
    private val requests = Channel<Unit>(Channel.CONFLATED)

    private val job: Job = scope.launch {
        for (ignored in requests) {
            // Coalesce a burst of requests.
            delay(debounceMs)

            // Drain requests accumulated during the debounce interval.
            while (requests.tryReceive().isSuccess) {
                // The conflated channel represents one pending sync.
            }

            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                runCatching {
                    onFailure(failure)
                }
            }
        }
    }

    init {
        require(debounceMs >= 0)
    }

    /**
     * Non-blocking. Multiple requests are coalesced into one pending sync.
     */
    fun request() {
        check(requests.trySend(Unit).isSuccess) {
            "Unable to enqueue FRIDAY sync request."
        }
    }

    fun cancel() {
        requests.close()
        job.cancel()
    }

    companion object {
        const val DEFAULT_DEBOUNCE_MS = 500L
    }
}
