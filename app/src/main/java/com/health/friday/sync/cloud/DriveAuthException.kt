package com.health.friday.sync.cloud

import android.app.PendingIntent

class DriveAuthException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

class DriveAuthorizationRequiredException(
    val pendingIntent: PendingIntent? = null,
    message: String = "Google Drive authorization is required."
) : Exception(message)
