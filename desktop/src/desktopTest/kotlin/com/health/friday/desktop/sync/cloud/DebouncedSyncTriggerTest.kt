package com.health.friday.desktop.sync.cloud

import com.health.friday.shared.sync.cloud.DebouncedSyncTrigger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals

class DebouncedSyncTriggerTest {

    @Test
    fun rapidRequestsAreCoalesced() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val executions = AtomicInteger(0)
        val firstExecution = CompletableDeferred<Unit>()

        val trigger = DebouncedSyncTrigger(
            scope = scope,
            debounceMs = 60,
            action = {
                executions.incrementAndGet()
                firstExecution.complete(Unit)
            }
        )

        try {
            repeat(10) {
                trigger.request()
            }

            withTimeout(2_000) {
                firstExecution.await()
            }

            delay(150)

            assertEquals(1, executions.get())
        } finally {
            trigger.cancel()
        }
    }

    @Test
    fun requestsDuringActiveSyncAreNotLost() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val executions = AtomicInteger(0)

        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val secondCompleted = CompletableDeferred<Unit>()

        val trigger = DebouncedSyncTrigger(
            scope = scope,
            debounceMs = 60,
            action = {
                when (executions.incrementAndGet()) {
                    1 -> {
                        firstStarted.complete(Unit)
                        releaseFirst.await()
                    }

                    2 -> secondCompleted.complete(Unit)
                }
            }
        )

        try {
            trigger.request()

            withTimeout(2_000) {
                firstStarted.await()
            }

            // Mutations happen while the first sync is still running.
            repeat(10) {
                trigger.request()
            }

            releaseFirst.complete(Unit)

            withTimeout(2_000) {
                secondCompleted.await()
            }

            delay(150)

            // One active sync and one follow-up sync.
            assertEquals(2, executions.get())
        } finally {
            trigger.cancel()
        }
    }
}
