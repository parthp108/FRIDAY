package com.health.friday.sync.cloud

import android.app.PendingIntent
import java.io.IOException

class DriveAuthException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

class DriveAuthorizationRequiredException(
    val pendingIntent: PendingIntent? = null,
    message: String = "Google Drive authorization is required."
) : Exception(message)

/**
 * An HTTP response from Google Drive that was not successful.
 *
 * Network-level IOException remains separate so the sync engine can retry
 * network failures without retrying permanent HTTP errors indefinitely.
 */
class DriveHttpException(
    val statusCode: Int,
    message: String,
    val reason: String? = null
) : IOException(message) {

    val isTransient: Boolean
        get() = statusCode == 408 ||
            statusCode == 429 ||
            statusCode in 500..599
}
