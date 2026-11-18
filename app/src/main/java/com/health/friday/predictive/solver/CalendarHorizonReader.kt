package com.health.friday.predictive.solver

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat

class CalendarHorizonReader(private val context: Context) {

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Reads all active calendar commitments occurring within [horizonHours] from [nowMs].
     */
    fun queryUpcomingHorizon(
        nowMs: Long = System.currentTimeMillis(),
        horizonHours: Int = 6
    ): List<CalendarCommitment> {
        val endHorizonMs = nowMs + horizonHours * 3_600_000L
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, nowMs - 3_600_000L) // Include currently running events
        ContentUris.appendId(builder, endHorizonMs)

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.EVENT_LOCATION
        )

        val commitments = mutableListOf<CalendarCommitment>()

        runCatching {
            context.contentResolver.query(
                builder.build(),
                projection,
                "${CalendarContract.Instances.SELF_ATTENDEE_STATUS} != ${CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED}",
                null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(CalendarContract.Instances.EVENT_ID)
                val titleIdx = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
                val beginIdx = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
                val endIdx = cursor.getColumnIndex(CalendarContract.Instances.END)
                val locIdx = cursor.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)

                while (cursor.moveToNext()) {
                    val eventId = cursor.getLong(idIdx)
                    val title = cursor.getString(titleIdx)?.trim().orEmpty().ifEmpty { "Untitled Event" }
                    val beginMs = cursor.getLong(beginIdx)
                    val endMs = cursor.getLong(endIdx)
                    val rawLoc = cursor.getString(locIdx)?.trim()

                    val (lat, lon) = parseLatLonIfPresent(rawLoc)
                    commitments.add(
                        CalendarCommitment(
                            eventId = eventId,
                            title = title,
                            categoryKey = normalizeCategoryKey(title),
                            startEpochMs = beginMs,
                            endEpochMs = endMs,
                            latitude = lat,
                            longitude = lon,
                            locationLabel = rawLoc
                        )
                    )
                }
            }
        }

        return commitments
    }

    private fun normalizeCategoryKey(title: String): String {
        val lower = title.lowercase()
        return when {
            "standup" in lower || "sync" in lower || "meeting" in lower -> "work_meeting"
            "dinner" in lower || "lunch" in lower || "coffee" in lower -> "dining"
            "flight" in lower || "airport" in lower || "train" in lower -> "transit"
            "gym" in lower || "workout" in lower -> "fitness"
            else -> lower.take(24)
        }
    }

    /**
     * Parses "lat,lon" coordinates if embedded in the calendar location string.
     */
    private fun parseLatLonIfPresent(location: String?): Pair<Double?, Double?> {
        if (location.isNullOrBlank()) return null to null
        val regex = Regex("""(-?\d+\.\d+)\s*,\s*(-?\d+\.\d+)""")
        val match = regex.find(location) ?: return null to null
        val lat = match.groupValues[1].toDoubleOrNull()
        val lon = match.groupValues[2].toDoubleOrNull()
        return lat to lon
    }
}
