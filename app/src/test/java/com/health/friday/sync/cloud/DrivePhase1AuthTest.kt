package com.health.friday.sync.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun testDriveHttpExceptionTransientClassification() {
        val rateLimitEx = DriveHttpException(429, "Too Many Requests", "rateLimitExceeded")
        assertTrue(rateLimitEx.isTransient)
        assertEquals("rateLimitExceeded", rateLimitEx.reason)

        val serverError = DriveHttpException(503, "Service Unavailable", "backendError")
        assertTrue(serverError.isTransient)

        val badRequest = DriveHttpException(400, "Bad Request", "invalidParameter")
        assertFalse(badRequest.isTransient)

        val forbidden = DriveHttpException(403, "Forbidden", "quotaExceeded")
        assertFalse(forbidden.isTransient)
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

        val transientFail: DriveSyncExecutionResult = DriveSyncExecutionResult.TransientFailure(IOException("Network error"))
        assertTrue(transientFail is DriveSyncExecutionResult.TransientFailure)

        val permFail: DriveSyncExecutionResult = DriveSyncExecutionResult.PermanentFailure(IllegalStateException("No master key"))
        assertTrue(permFail is DriveSyncExecutionResult.PermanentFailure)
    }

    @Test
    fun testDriveSyncStateEnum() {
        val states = DriveSyncState.values()
        assertTrue(states.contains(DriveSyncState.IDLE))
        assertTrue(states.contains(DriveSyncState.SYNCING))
        assertTrue(states.contains(DriveSyncState.AUTH_REQUIRED))
        assertTrue(states.contains(DriveSyncState.SUCCESS))
        assertTrue(states.contains(DriveSyncState.FAILED))
    }
}
