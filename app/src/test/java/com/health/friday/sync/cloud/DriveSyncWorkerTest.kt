package com.health.friday.sync.cloud

import org.junit.Assert.assertEquals
import org.junit.Test

class DriveSyncWorkerTest {

    @Test
    fun testPeriodicWorkNameIsUnique() {
        assertEquals("friday_drive_sync_periodic", DriveSyncWorker.UNIQUE_PERIODIC_WORK_NAME)
    }
}
