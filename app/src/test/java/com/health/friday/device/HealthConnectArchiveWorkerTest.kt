package com.health.friday.device

import org.junit.Assert.assertEquals
import org.junit.Test

class HealthConnectArchiveWorkerTest {

    @Test
    fun testPeriodicWorkNameIsUnique() {
        assertEquals("health_connect_archive_periodic", HealthConnectArchiveWorker.UNIQUE_PERIODIC_WORK_NAME)
    }
}
