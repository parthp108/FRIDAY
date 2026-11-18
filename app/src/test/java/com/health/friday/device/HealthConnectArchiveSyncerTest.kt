package com.health.friday.device

import com.health.friday.data.local.HealthConnectSyncStateEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class HealthConnectArchiveSyncerTest {

    @Test
    fun testResetAfterChangesTokenExpiryClearsAllBaselineFields() {
        val state = HealthConnectSyncStateEntity(
            recordType = "steps",
            changesToken = "old_token",
            baselinePageToken = "page_123",
            baselineFromEpochMs = 1000L,
            baselineToEpochMs = 5000L,
            baselineStartedAtMs = 2000L,
            baselineCompletedAtMs = 4000L,
            baselineComplete = true,
            lastSyncAtMs = 3000L
        )

        val reset = state.resetAfterChangesTokenExpiry()

        assertEquals("steps", reset.recordType)
        assertNull(reset.changesToken)
        assertNull(reset.baselinePageToken)
        assertNull(reset.baselineFromEpochMs)
        assertNull(reset.baselineToEpochMs)
        assertNull(reset.baselineStartedAtMs)
        assertNull(reset.baselineCompletedAtMs)
        assertFalse(reset.baselineComplete)
    }
}
