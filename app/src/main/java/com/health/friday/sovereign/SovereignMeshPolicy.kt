package com.health.friday.sovereign

data class SplitDecision(
    val offload: Boolean,
    val localSec: Double
)

object SplitComputePolicy {
    fun decide(
        rttMs: Long,
        promptChars: Int = 0,
        hasImage: Boolean = false,
        outTokens: Int = 128,
        sensitive: Boolean = false,
        thermalLevel: Int = 0,
        batteryPercent: Int = 100
    ): SplitDecision {
        val baseLocal = 7.2
        val localTime = if (thermalLevel == 3) baseLocal * 3.0 else baseLocal
        if (sensitive || rttMs < 0 || rttMs >= 30) {
            return SplitDecision(offload = false, localSec = localTime)
        }
        return SplitDecision(offload = true, localSec = localTime)
    }
}

class RoomZoneEstimator(private val clock: () -> Long = { System.currentTimeMillis() }) {
    private var filteredRssi: Double? = null
    private var isInsideState: Boolean = false
    private var lastUpdateTime: Long = 0L

    val isInside: Boolean
        get() {
            if (!isInsideState) return false
            val now = clock()
            return (now - lastUpdateTime) <= 10_000L
        }

    fun update(rssi: Int) {
        val now = clock()
        if (lastUpdateTime != 0L && (now - lastUpdateTime) > 60_000L) {
            filteredRssi = null
            isInsideState = false
        }

        val prev = filteredRssi
        val current = if (prev == null) {
            rssi.toDouble()
        } else {
            0.2 * rssi + 0.8 * prev
        }
        filteredRssi = current
        lastUpdateTime = now

        if (current >= -65.0) {
            isInsideState = true
        } else if (current <= -80.0) {
            isInsideState = false
        }
    }
}
