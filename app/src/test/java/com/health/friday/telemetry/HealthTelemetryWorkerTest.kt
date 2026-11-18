package com.health.friday.telemetry

import org.junit.Assert.assertEquals
import org.junit.Test

class HealthTelemetryWorkerTest {

    @Test
    fun testUniqueWorkName() {
        assertEquals("friday_telemetry_sync", HealthTelemetryWorker.UNIQUE_PERIODIC_WORK_NAME)
    }
}
