package com.health.friday.telemetry.ble

import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.sqrt

data class HeartRateGattPacket(
    val timestampMs: Long,
    val heartRateBpm: Int,
    val rrIntervalsMs: List<Double>
)

data class BiometricStressSnapshot(
    val timestampMs: Long,
    val currentHrBpm: Int,
    val rmssdMs: Double,
    val hrvZScore: Double,
    val hrZScore: Double,
    val isStationary: Boolean,
    val isAcuteStressState: Boolean
)

object BiometricPacketParser {

    /**
     * Parses raw bytes from BLE GATT Characteristic 0x2A37 (Heart Rate Measurement)
     * per Bluetooth SIG Specification.
     */
    fun parseHeartRateMeasurement(rawBytes: ByteArray, nowMs: Long = System.currentTimeMillis()): HeartRateGattPacket? {
        if (rawBytes.size < 2) return null

        val flags = rawBytes[0].toInt() and 0xFF
        val isHrUint16 = (flags and 0x01) != 0
        val hasEnergyExpended = (flags and 0x08) != 0
        val hasRrIntervals = (flags and 0x10) != 0

        var offset = 1
        val hrBpm: Int
        if (isHrUint16) {
            if (rawBytes.size < offset + 2) return null
            hrBpm = readUint16LittleEndian(rawBytes, offset)
            offset += 2
        } else {
            hrBpm = rawBytes[offset].toInt() and 0xFF
            offset += 1
        }

        // Skip Energy Expended field (2 bytes) if present
        if (hasEnergyExpended) {
            offset += 2
        }

        // Extract all UINT16 RR-Intervals (in 1/1024s resolution)
        val rrListMs = mutableListOf<Double>()
        if (hasRrIntervals) {
            while (offset + 1 < rawBytes.size) {
                val rawRr = readUint16LittleEndian(rawBytes, offset)
                offset += 2
                val rrMs = (rawRr / 1024.0) * 1000.0
                rrListMs.add(rrMs)
            }
        }

        return HeartRateGattPacket(
            timestampMs = nowMs,
            heartRateBpm = hrBpm,
            rrIntervalsMs = rrListMs
        )
    }

    private fun readUint16LittleEndian(bytes: ByteArray, index: Int): Int {
        val low = bytes[index].toInt() and 0xFF
        val high = bytes[index + 1].toInt() and 0xFF
        return (high shl 8) or low
    }
}

class SlidingWindowHrvAnalyzer(
    private val windowDurationMs: Long = 60_000L,
    var baselineRmssdMean: Double = 42.0,
    var baselineRmssdStd: Double = 11.0,
    var baselineHrMean: Double = 68.0,
    var baselineHrStd: Double = 7.5
) {
    private data class TimedNnInterval(val timestampMs: Long, val nnMs: Double)

    private val nnWindow = ArrayDeque<TimedNnInterval>()
    private val recentFiveBuffer = ArrayDeque<Double>(5)

    /**
     * Filters ectopic/PPG motion artifacts and computes sliding-window RMSSD + Stress Z-Scores.
     */
    @Synchronized
    fun ingestPacket(
        packet: HeartRateGattPacket,
        isUserStationary: Boolean
    ): BiometricStressSnapshot? {
        val cutoff = packet.timestampMs - windowDurationMs

        for (rr in packet.rrIntervalsMs) {
            // 1. Physiological bounds gate (30 BPM to 200 BPM equivalent)
            if (rr < 300.0 || rr > 2000.0) continue

            // 2. Quotient artifact filter against 5-beat local median
            if (recentFiveBuffer.size >= 3) {
                val sorted = recentFiveBuffer.sorted()
                val median = sorted[sorted.size / 2]
                if (abs(rr - median) / median > 0.20) {
                    continue // Reject PPG wrist-motion spike
                }
            }

            if (recentFiveBuffer.size == 5) recentFiveBuffer.removeFirst()
            recentFiveBuffer.addLast(rr)
            nnWindow.addLast(TimedNnInterval(packet.timestampMs, rr))
        }

        // Evict intervals older than 60 seconds
        while (nnWindow.isNotEmpty() && nnWindow.peekFirst()!!.timestampMs < cutoff) {
            nnWindow.removeFirst()
        }

        // Require at least 15 clean NN intervals to compute a statistically valid RMSSD
        if (nnWindow.size < 15) return null

        val intervals = nnWindow.map { it.nnMs }
        var sumSqDiff = 0.0
        for (i in 0 until intervals.size - 1) {
            val diff = intervals[i + 1] - intervals[i]
            sumSqDiff += diff * diff
        }

        val rmssd = sqrt(sumSqDiff / (intervals.size - 1))
        val hrvZ = (baselineRmssdMean - rmssd) / baselineRmssdStd.coerceAtLeast(1.0)
        val hrZ = (packet.heartRateBpm - baselineHrMean) / baselineHrStd.coerceAtLeast(1.0)

        val acuteStress = isUserStationary && (hrvZ >= 1.85) && (hrZ >= 1.50)

        return BiometricStressSnapshot(
            timestampMs = packet.timestampMs,
            currentHrBpm = packet.heartRateBpm,
            rmssdMs = rmssd,
            hrvZScore = hrvZ,
            hrZScore = hrZ,
            isStationary = isUserStationary,
            isAcuteStressState = acuteStress
        )
    }
}
