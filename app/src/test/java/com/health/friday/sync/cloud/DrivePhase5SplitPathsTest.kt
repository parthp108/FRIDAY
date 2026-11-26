package com.health.friday.sync.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class DrivePhase5SplitPathsTest {

    @Test
    fun testSuppressedExceptionsCombination() {
        val primary = DriveAuthException("Primary auth error")
        val secondary = IOException("Secondary network error")

        primary.addSuppressed(secondary)

        assertEquals("Primary auth error", primary.message)
        assertEquals(1, primary.suppressed.size)
        assertEquals("Secondary network error", primary.suppressed[0].message)
    }

    @Test
    fun testDriveSyncResultDataStructure() {
        val result = DriveSyncResult(
            backfilledRecords = 10,
            uploadedBatches = 2,
            uploadedOperations = 15,
            downloadedBatches = 3,
            appliedOperations = 20
        )

        assertEquals(10, result.backfilledRecords)
        assertEquals(2, result.uploadedBatches)
        assertEquals(15, result.uploadedOperations)
        assertEquals(3, result.downloadedBatches)
        assertEquals(20, result.appliedOperations)
    }
}
