package com.health.friday.predictive.solver

import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

data class OverrunDistribution(
    val sampleCount: Int = 0,
    val meanOverrunMinutes: Double = 5.0,
    val varianceMinutes: Double = 64.0 // Initial sigma = 8 min
) {
    val stdDevMinutes: Double get() = sqrt(varianceMinutes.coerceAtLeast(1.0))
}

data class CalendarCommitment(
    val eventId: Long,
    val title: String,
    val categoryKey: String,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationLabel: String? = null
)

data class ScheduleClashPrediction(
    val currentOrLeadingEvent: CalendarCommitment,
    val conflictingNextEvent: CalendarCommitment,
    val expectedSlackMinutes: Double,
    val estimatedTravelMinutes: Double,
    val clashProbability: Double,
    val recommendedNewStartEpochMs: Long
)

data class CommitmentPair(
    val lead: CalendarCommitment,
    val next: CalendarCommitment,
    val gapMinutes: Double
)

class SpatioTemporalSolver {

    companion object {
        private const val EARTH_RADIUS_KM = 6371.0
        private const val ROAD_TORTUOSITY_FACTOR = 1.38
        private const val EMA_ALPHA = 0.25
    }

    // Stores historical overrun priors keyed by normalized event category/title
    private val overrunPriors = ConcurrentHashMap<String, OverrunDistribution>()

    /**
     * Updates the Exponential Moving Average (EMA) mean and variance when a meeting
     * or task finishes earlier or later than scheduled.
     */
    fun recordObservedOverrun(categoryKey: String, actualOverrunMinutes: Double) {
        val clampedActual = actualOverrunMinutes.coerceIn(-60.0, 120.0)
        val key = categoryKey.trim().lowercase()
        val prev = overrunPriors[key] ?: OverrunDistribution()

        val newMean = if (prev.sampleCount == 0) {
            clampedActual
        } else {
            (1.0 - EMA_ALPHA) * prev.meanOverrunMinutes + EMA_ALPHA * clampedActual
        }

        val diff = clampedActual - prev.meanOverrunMinutes
        val newVariance = if (prev.sampleCount == 0) {
            prev.varianceMinutes
        } else {
            (1.0 - EMA_ALPHA) * prev.varianceMinutes + EMA_ALPHA * (1.0 - EMA_ALPHA) * (diff * diff)
        }

        overrunPriors[key] = OverrunDistribution(
            sampleCount = prev.sampleCount + 1,
            meanOverrunMinutes = newMean,
            varianceMinutes = newVariance.coerceIn(4.0, 3600.0)
        )
    }

    fun getOverrunPrior(categoryKey: String): OverrunDistribution {
        return overrunPriors[categoryKey.trim().lowercase()] ?: OverrunDistribution()
    }

    /**
     * Finds pairs of events that are scheduled to overlap.
     */
    fun findScheduledOverlaps(events: List<CalendarCommitment>): List<Pair<CalendarCommitment, CalendarCommitment>> {
        val sorted = events.sortedBy { it.startEpochMs }
        val overlaps = mutableListOf<Pair<CalendarCommitment, CalendarCommitment>>()
        for (i in 0 until sorted.size) {
            val a = sorted[i]
            for (j in i + 1 until sorted.size) {
                val b = sorted[j]
                if (b.startEpochMs < a.endEpochMs) {
                    overlaps.add(a to b)
                } else {
                    break
                }
            }
        }
        return overlaps
    }

    /**
     * Returns adjacent non-overlapping pairs of upcoming events.
     */
    fun adjacentPairs(
        nowEpochMs: Long,
        events: List<CalendarCommitment>
    ): List<CommitmentPair> {
        val sorted = events.sortedBy { it.startEpochMs }
        val pairs = mutableListOf<CommitmentPair>()
        for (i in 0 until sorted.size - 1) {
            val lead = sorted[i]
            val next = sorted[i + 1]
            if (next.startEpochMs >= lead.endEpochMs) {
                val effectiveLeadEnd = max(nowEpochMs, lead.endEpochMs)
                val gapMin = (next.startEpochMs - effectiveLeadEnd) / 60_000.0
                pairs.add(CommitmentPair(lead, next, gapMin))
            }
        }
        return pairs
    }

    /**
     * Evaluates a sorted list of upcoming commitments over the next 6 hours and returns
     * all pairs where P(clash) >= [probabilityThreshold].
     */
    fun detectHorizonClashes(
        nowEpochMs: Long,
        currentLat: Double?,
        currentLon: Double?,
        upcomingEvents: List<CalendarCommitment>,
        prepBufferMinutes: Double = 10.0,
        probabilityThreshold: Double = 0.70
    ): List<ScheduleClashPrediction> {
        if (upcomingEvents.size < 2) return emptyList()
        val sorted = upcomingEvents.sortedBy { it.startEpochMs }
        val clashes = mutableListOf<ScheduleClashPrediction>()

        for (i in 0 until sorted.size - 1) {
            val lead = sorted[i]
            val next = sorted[i + 1]

            if (next.startEpochMs < lead.endEpochMs) continue
            if (lead.endEpochMs <= nowEpochMs) continue

            val prior = getOverrunPrior(lead.categoryKey)
            val hasOverrunEvidence = prior.sampleCount > 0
            val meanOverrun = if (hasOverrunEvidence) prior.meanOverrunMinutes else 0.0

            val effectiveLeadEndMs = max(nowEpochMs, lead.endEpochMs)

            // Determine origin coordinates for travel to next event
            val originLat = lead.latitude ?: currentLat
            val originLon = lead.longitude ?: currentLon

            val (travelMeanMin, travelStdMin) = estimateTravelTimeDistribution(
                originLat = originLat,
                originLon = originLon,
                destLat = next.latitude,
                destLon = next.longitude,
                departureEpochMs = effectiveLeadEndMs
            )

            if (!hasOverrunEvidence && travelMeanMin <= 0.0) continue

            val rawGapMinutes = (next.startEpochMs - effectiveLeadEndMs) / 60_000.0
            val expectedSlackMin = rawGapMinutes - (meanOverrun + travelMeanMin + prepBufferMinutes)
            val stdDev = if (hasOverrunEvidence) prior.stdDevMinutes else 0.0
            val combinedSigma = hypot(stdDev, travelStdMin).coerceAtLeast(1.0)

            val zScore = -expectedSlackMin / combinedSigma
            val pClash = standardNormalCdf(zScore)

            if (pClash >= probabilityThreshold) {
                // Recommend pushing the conflicting event to 15-minute rounded boundary after 95% confidence arrival
                val safeArrivalMs = effectiveLeadEndMs +
                        ((meanOverrun + travelMeanMin + prepBufferMinutes + 1.65 * combinedSigma) * 60_000.0).toLong()
                val fifteenMinMs = 15 * 60_000L
                val roundedStartMs = ((safeArrivalMs + fifteenMinMs - 1) / fifteenMinMs) * fifteenMinMs

                clashes.add(
                    ScheduleClashPrediction(
                        currentOrLeadingEvent = lead,
                        conflictingNextEvent = next,
                        expectedSlackMinutes = expectedSlackMin,
                        estimatedTravelMinutes = travelMeanMin,
                        clashProbability = pClash,
                        recommendedNewStartEpochMs = roundedStartMs
                    )
                )
            }
        }

        return clashes
    }

    /**
     * Computes Haversine great-circle distance and converts to urban travel time distribution
     * based on time-of-day traffic speed profiles.
     */
    private fun estimateTravelTimeDistribution(
        originLat: Double?,
        originLon: Double?,
        destLat: Double?,
        destLon: Double?,
        departureEpochMs: Long
    ): Pair<Double, Double> {
        if (originLat == null || originLon == null || destLat == null || destLon == null) {
            return 0.0 to 2.0
        }

        val dLat = Math.toRadians(destLat - originLat)
        val dLon = Math.toRadians(destLon - originLon)
        val lat1 = Math.toRadians(originLat)
        val lat2 = Math.toRadians(destLat)

        val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1.0 - a))
        val haversineKm = EARTH_RADIUS_KM * c

        if (haversineKm < 0.15) return 0.0 to 1.0 // Same building / walking radius

        val roadDistanceKm = haversineKm * ROAD_TORTUOSITY_FACTOR
        val cal = Calendar.getInstance().apply { timeInMillis = departureEpochMs }
        val hour = cal.get(Calendar.HOUR_OF_DAY)

        // Urban speed profile (km/h): peak rush hour vs normal vs late night
        val avgSpeedKmh = when (hour) {
            in 8..10, in 17..20 -> 18.0 // Rush hour traffic
            in 11..16, in 21..22 -> 28.0 // Standard urban flow
            else -> 40.0 // Clear roads
        }

        val meanMinutes = (roadDistanceKm / avgSpeedKmh) * 60.0
        val stdDevMinutes = (meanMinutes * 0.22).coerceAtLeast(3.0)
        return meanMinutes to stdDevMinutes
    }

    /**
     * Standard Normal CDF Φ(z) using Abramowitz & Stegun formula 7.1.26 (max error < 1.5e-7).
     */
    private fun standardNormalCdf(z: Double): Double {
        val x = abs(z) / sqrt(2.0)
        val t = 1.0 / (1.0 + 0.3275911 * x)
        val erfApprox = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t -
                0.284496736) * t + 0.254829592) * t * exp(-x * x)
        val erfSigned = if (z >= 0.0) erfApprox else -erfApprox
        return 0.5 * (1.0 + erfSigned)
    }
}
