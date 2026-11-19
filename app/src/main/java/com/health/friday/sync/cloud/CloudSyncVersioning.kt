package com.health.friday.sync.cloud

import com.health.friday.shared.protocol.HlcTimestamp

/**
 * Compares an incoming HLC against all known versions of the same entity.
 *
 * HLC is parsed and compared structurally rather than relying on arbitrary
 * strings. Existing rows can carry an older per-row syncHlc; the dedicated
 * entity-state table carries the durable version, including DELETE tombstones.
 */
internal object CloudSyncVersioning {
    fun shouldApply(
        incomingHlc: String,
        persistedHlcs: List<String?>
    ): Boolean {
        val incoming = HlcTimestamp.parse(incomingHlc)
            ?: throw IllegalArgumentException("Malformed incoming HLC timestamp")

        val known = persistedHlcs.filterNotNull().map { value ->
            HlcTimestamp.parse(value)
                ?: throw IllegalStateException("Malformed persisted HLC timestamp: $value")
        }

        return known.all { incoming > it }
    }
}
