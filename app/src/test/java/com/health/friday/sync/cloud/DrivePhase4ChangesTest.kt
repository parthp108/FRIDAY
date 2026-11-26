package com.health.friday.sync.cloud

import com.health.friday.shared.sync.cloud.SyncCursor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DrivePhase4ChangesTest {

    @Test
    fun testSyncCursorPageToken() {
        val cursor = SyncCursor(drivePageToken = "token_123", lastSuccessfulSyncAtMs = 1000L)
        assertEquals("token_123", cursor.drivePageToken)
        assertEquals(1000L, cursor.lastSuccessfulSyncAtMs)

        val updated = cursor.copy(drivePageToken = "token_456")
        assertEquals("token_456", updated.drivePageToken)
    }

    @Test
    fun testDriveHttpException410Detection() {
        val ex = DriveHttpException(410, "Page token expired", "pageTokenExpired")
        assertEquals(410, ex.statusCode)
        assertEquals("pageTokenExpired", ex.reason)
    }
}
