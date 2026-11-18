package com.health.friday.sync.cloud

import androidx.room.withTransaction
import com.health.friday.data.local.CloudSyncClockEntity
import com.health.friday.data.local.CloudSyncDao
import com.health.friday.data.local.FridayDatabase
import com.health.friday.shared.protocol.ClockDriftException
import com.health.friday.shared.protocol.HlcTimestamp

/**
 * Room-persisted HLC.
 *
 * Each clock update is serialized in a Room transaction.
 * The node ID is stable for this installation.
 */
class AndroidHybridLogicalClock(
    private val database: FridayDatabase,
    private val dao: CloudSyncDao,
    private val nodeId: String,
    private val wallClockMs: () -> Long = System::currentTimeMillis
) {
    companion object {
        private const val MAX_COUNTER = 0xFFFF
        private const val MAX_DRIFT_MS = 60L * 60L * 1000L
    }

    init {
        require(nodeId.isNotBlank()) {
            "nodeId must not be blank"
        }
    }

    /**
     * Generate a timestamp for a local change.
     */
    suspend fun now(): HlcTimestamp {
        return database.withTransaction {
            nextInTransaction()
        }
    }

    /**
     * Must be called inside an existing Room transaction.
     * The caller's transaction must also contain the entity write
     * and corresponding outbox insertion.
     */
    internal suspend fun nextInTransaction(): HlcTimestamp {
        val state = dao.getClockState()
        val physicalTime = wallClockMs()

        require(physicalTime >= 0L) {
            "System clock returned a negative timestamp"
        }

        val next = when {
            state == null -> {
                HlcTimestamp(
                    wallMs = physicalTime,
                    counter = 0,
                    nodeId = nodeId
                )
            }

            physicalTime > state.lastWallMs -> {
                HlcTimestamp(
                    wallMs = physicalTime,
                    counter = 0,
                    nodeId = nodeId
                )
            }

            state.lastCounter >= MAX_COUNTER -> {
                HlcTimestamp(
                    wallMs = state.lastWallMs + 1,
                    counter = 0,
                    nodeId = nodeId
                )
            }

            else -> {
                HlcTimestamp(
                    wallMs = state.lastWallMs,
                    counter = state.lastCounter + 1,
                    nodeId = nodeId
                )
            }
        }

        save(next)
        return next
    }

    /**
     * Merge a timestamp received from another device.
     * The returned timestamp is a new local timestamp.
     */
    suspend fun receive(remote: HlcTimestamp): HlcTimestamp {
        return database.withTransaction {
            val physicalTime = wallClockMs()

            require(physicalTime >= 0L) {
                "System clock returned a negative timestamp"
            }

            if (
                remote.wallMs > physicalTime &&
                remote.wallMs - physicalTime > MAX_DRIFT_MS
            ) {
                throw ClockDriftException(
                    "Remote clock exceeds the allowed drift"
                )
            }

            val state = dao.getClockState()
            val lastWall = state?.lastWallMs ?: 0L
            val lastCounter = state?.lastCounter ?: 0

            val newWall = maxOf(
                lastWall,
                remote.wallMs,
                physicalTime
            )

            var newCounter = when {
                newWall == lastWall &&
                    newWall == remote.wallMs ->
                    maxOf(lastCounter, remote.counter) + 1

                newWall == lastWall ->
                    lastCounter + 1

                newWall == remote.wallMs ->
                    remote.counter + 1

                else -> 0
            }

            var finalWall = newWall

            if (newCounter > MAX_COUNTER) {
                finalWall += 1
                newCounter = 0
            }

            val merged = HlcTimestamp(
                wallMs = finalWall,
                counter = newCounter,
                nodeId = nodeId
            )

            save(merged)
            merged
        }
    }

    private suspend fun save(timestamp: HlcTimestamp) {
        dao.saveClockState(
            CloudSyncClockEntity(
                id = 1,
                nodeId = nodeId,
                lastWallMs = timestamp.wallMs,
                lastCounter = timestamp.counter
            )
        )
    }
}
