package com.health.friday.device

import com.health.friday.shared.sync.cloud.CloudSyncEntityTypes
import com.health.friday.shared.sync.cloud.SyncOperationType
import org.junit.Assert.assertEquals
import org.junit.Test

class HealthConnectArchiveAdaptersTest {

    @Test
    fun testCloudSyncEntityTypesConstants() {
        assertEquals("health_record", CloudSyncEntityTypes.HEALTH_RECORD)
        assertEquals("meal", CloudSyncEntityTypes.MEAL)
        assertEquals("water_entry", CloudSyncEntityTypes.WATER_ENTRY)
        assertEquals("todo", CloudSyncEntityTypes.TODO)
        assertEquals("goal", CloudSyncEntityTypes.GOAL)
        assertEquals("journal_entry", CloudSyncEntityTypes.JOURNAL_ENTRY)
        assertEquals("daily_signals", CloudSyncEntityTypes.DAILY_SIGNALS)
    }

    @Test
    fun testSyncOperationTypes() {
        assertEquals(SyncOperationType.UPSERT, SyncOperationType.valueOf("UPSERT"))
        assertEquals(SyncOperationType.DELETE, SyncOperationType.valueOf("DELETE"))
    }
}
