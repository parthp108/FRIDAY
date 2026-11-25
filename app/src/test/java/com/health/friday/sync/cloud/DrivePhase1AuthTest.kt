package com.health.friday.sync.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class DrivePhase1AuthTest {

    @Test
    fun testDriveAuthExceptionHierarchy() {
        val authException = DriveAuthException("HTTP 401 Unauthorized")
        assertTrue(authException is Exception)
        assertEquals("HTTP 401 Unauthorized", authException.message)

        val reqException = DriveAuthorizationRequiredException(null, "Interactive auth needed")
        assertTrue(reqException is Exception)
        assertEquals("Interactive auth needed", reqException.message)
    }

    @Test
    fun testDriveAuthorizationResultHierarchy() {
        val authorized: DriveAuthorizationResult = DriveAuthorizationResult.Authorized("valid_token")
        assertTrue(authorized is DriveAuthorizationResult.Authorized)
        assertEquals("valid_token", (authorized as DriveAuthorizationResult.Authorized).accessToken)

        val unavailable: DriveAuthorizationResult = DriveAuthorizationResult.Unavailable(IOException("Failed"))
        assertTrue(unavailable is DriveAuthorizationResult.Unavailable)
        assertEquals("Failed", (unavailable as DriveAuthorizationResult.Unavailable).cause.message)
    }

    @Test
    fun testDriveSyncExecutionResultHierarchy() {
        val dummyResult = DriveSyncResult(1, 2, 3, 4, 5)
        val success: DriveSyncExecutionResult = DriveSyncExecutionResult.Success(dummyResult)
        assertTrue(success is DriveSyncExecutionResult.Success)
        assertEquals(dummyResult, (success as DriveSyncExecutionResult.Success).result)

        val authReq: DriveSyncExecutionResult = DriveSyncExecutionResult.AuthRequired(null)
        assertTrue(authReq is DriveSyncExecutionResult.AuthRequired)

        val failed: DriveSyncExecutionResult = DriveSyncExecutionResult.Failed(IOException("Network error"))
        assertTrue(failed is DriveSyncExecutionResult.Failed)
    }
}
