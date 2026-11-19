package com.health.friday.sync.cloud

import com.health.friday.shared.protocol.HlcTimestamp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudSyncVersioningTest {
    private fun hlc(wallMs: Long, counter: Int = 0, node: String = "device") =
        HlcTimestamp(wallMs, counter, node).encode()

    @Test
    fun olderUpsertIsRejectedAfterNewerDeleteTombstone() {
        val deleteHlc = hlc(2_000L)
        val staleUpsertHlc = hlc(1_000L)

        assertFalse(
            CloudSyncVersioning.shouldApply(
                incomingHlc = staleUpsertHlc,
                persistedHlcs = listOf(deleteHlc)
            )
        )
    }

    @Test
    fun newerUpsertCanRestoreAnEntityAfterDelete() {
        val deleteHlc = hlc(2_000L)
        val restoreHlc = hlc(2_000L, counter = 1)

        assertTrue(
            CloudSyncVersioning.shouldApply(
                incomingHlc = restoreHlc,
                persistedHlcs = listOf(deleteHlc)
            )
        )
    }

    @Test
    fun equalHlcIsRejectedAsDuplicateVersion() {
        val version = hlc(2_000L, counter = 7)

        assertFalse(
            CloudSyncVersioning.shouldApply(
                incomingHlc = version,
                persistedHlcs = listOf(version)
            )
        )
    }

    @Test(expected = IllegalStateException::class)
    fun malformedPersistedHlcFailsClosed() {
        CloudSyncVersioning.shouldApply(
            incomingHlc = hlc(3_000L),
            persistedHlcs = listOf("not-an-hlc")
        )
    }
}
