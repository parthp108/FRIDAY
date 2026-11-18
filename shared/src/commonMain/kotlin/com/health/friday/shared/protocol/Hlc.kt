package com.health.friday.shared.protocol

data class HlcTimestamp(
    val wallMs: Long,
    val counter: Int,
    val nodeId: String
) : Comparable<HlcTimestamp> {

    override fun compareTo(other: HlcTimestamp): Int {
        if (wallMs != other.wallMs) return wallMs.compareTo(other.wallMs)
        if (counter != other.counter) return counter.compareTo(other.counter)
        return nodeId.compareTo(other.nodeId)
    }

    /**
     * Fixed-width, so plain string comparison orders timestamps the same way compareTo does
     * (15-digit wall time, 4-digit hex counter, then the node id).
     */
    fun encode(): String =
        "${wallMs.toString().padStart(WALL_DIGITS, '0')}-" +
            "${counter.toString(16).padStart(COUNTER_DIGITS, '0')}-$nodeId"

    companion object {
        private const val WALL_DIGITS = 15
        private const val COUNTER_DIGITS = 4

        fun parse(text: String): HlcTimestamp? {
            val parts = text.split("-", limit = 3) // node ids may contain '-'
            if (parts.size != 3) return null
            val wall = parts[0].toLongOrNull() ?: return null
            val counter = parts[1].toIntOrNull(16) ?: return null
            if (wall < 0 || counter < 0 || parts[2].isEmpty()) return null
            return HlcTimestamp(wall, counter, parts[2])
        }
    }
}

class ClockDriftException(message: String) : IllegalArgumentException(message)

/**
 * Hybrid logical clock (Kulkarni et al.). now() is strictly increasing even if the wall
 * clock stalls or jumps backwards; receive() merges a remote timestamp so that anything
 * we stamp afterwards sorts after everything we have seen.
 */
class HybridLogicalClock(
    val nodeId: String,
    private val wallClockMs: () -> Long,
    private val maxDriftMs: Long = 60L * 60L * 1000L
) {
    private var lastWall = 0L
    private var lastCounter = 0

    /** Stamp for a local event or an outgoing message. */
    fun now(): HlcTimestamp {
        val pt = wallClockMs()
        if (pt > lastWall) {
            lastWall = pt
            lastCounter = 0
        } else {
            bump()
        }
        return HlcTimestamp(lastWall, lastCounter, nodeId)
    }

    /** Merge a timestamp received from another node; returns our new stamp. */
    fun receive(remote: HlcTimestamp): HlcTimestamp {
        val pt = wallClockMs()
        if (remote.wallMs - pt > maxDriftMs) {
            throw ClockDriftException(
                "Remote clock is ${(remote.wallMs - pt) / 1000}s ahead of ours " +
                    "(limit ${maxDriftMs / 1000}s)"
            )
        }

        val newWall = maxOf(lastWall, remote.wallMs, pt)
        val newCounter = when {
            newWall == lastWall && newWall == remote.wallMs -> maxOf(lastCounter, remote.counter) + 1
            newWall == lastWall -> lastCounter + 1
            newWall == remote.wallMs -> remote.counter + 1
            else -> 0
        }
        lastWall = newWall
        lastCounter = newCounter
        if (lastCounter > MAX_COUNTER) {
            lastWall += 1
            lastCounter = 0
        }
        return HlcTimestamp(lastWall, lastCounter, nodeId)
    }

    private fun bump() {
        if (lastCounter >= MAX_COUNTER) {
            lastWall += 1
            lastCounter = 0
        } else {
            lastCounter++
        }
    }

    private companion object {
        const val MAX_COUNTER = 0xFFFF
    }
}
