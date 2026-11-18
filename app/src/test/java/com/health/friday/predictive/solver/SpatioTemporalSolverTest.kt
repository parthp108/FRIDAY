package com.health.friday.predictive.solver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpatioTemporalSolverTest {

    private val now = 1_700_000_000_000L
    private fun min(m: Long) = m * 60_000L

    private fun event(
        id: Long, cat: String, startMin: Long, endMin: Long,
        lat: Double? = null, lon: Double? = null
    ) = CalendarCommitment(
        eventId = id, title = "E$id", categoryKey = cat,
        startEpochMs = now + min(startMin), endEpochMs = now + min(endMin),
        latitude = lat, longitude = lon
    )

    @Test
    fun emaConvergesToConstantOverrun() {
        val s = SpatioTemporalSolver()
        repeat(5) { s.recordObservedOverrun("board_meeting", 15.0) }
        assertEquals(15.0, s.getOverrunPrior("board_meeting").meanOverrunMinutes, 0.01)
    }

    @Test
    fun absurdOverrunIsClamped() {
        val s = SpatioTemporalSolver()
        s.recordObservedOverrun("x", 600.0)
        assertEquals(120.0, s.getOverrunPrior("x").meanOverrunMinutes, 0.01)
    }

    @Test
    fun backToBackWithNoEvidenceStaysQuiet() {
        val s = SpatioTemporalSolver()
        val events = listOf(event(1, "a", 10, 60), event(2, "b", 60, 90))
        assertTrue(s.detectHorizonClashes(now, null, null, events).isEmpty())
    }

    @Test
    fun learnedOverrunFlagsTightPair() {
        val s = SpatioTemporalSolver()
        repeat(5) { s.recordObservedOverrun("work_meeting", 15.0) }
        val events = listOf(event(1, "work_meeting", 10, 60), event(2, "x", 70, 100))
        val clashes = s.detectHorizonClashes(now, null, null, events)
        assertEquals(1, clashes.size)
        assertEquals(-15.0, clashes[0].expectedSlackMinutes, 0.01)
        assertTrue(clashes[0].clashProbability > 0.99)
        assertTrue(clashes[0].recommendedNewStartEpochMs >= events[1].startEpochMs)
        assertEquals(0L, clashes[0].recommendedNewStartEpochMs % min(15))
    }

    @Test
    fun endedLeadIsNotTreatedAsOverrunning() {
        val s = SpatioTemporalSolver()
        repeat(5) { s.recordObservedOverrun("work_meeting", 15.0) }
        val events = listOf(event(1, "work_meeting", -80, -20), event(2, "x", 10, 40))
        val pairs = s.adjacentPairs(now, events)
        assertEquals(10.0, pairs[0].gapMinutes, 0.01)
        assertTrue(s.detectHorizonClashes(now, null, null, events).isEmpty())
    }

    @Test
    fun scheduledOverlapIsNotAPrediction() {
        val s = SpatioTemporalSolver()
        repeat(5) { s.recordObservedOverrun("work_meeting", 15.0) }
        val events = listOf(event(1, "work_meeting", 10, 60), event(2, "x", 30, 90))
        assertTrue(s.adjacentPairs(now, events).isEmpty())
        assertEquals(1, s.findScheduledOverlaps(events).size)
        assertTrue(s.detectHorizonClashes(now, null, null, events).isEmpty())
    }

    @Test
    fun nestedOverlapsAreAllFound() {
        val s = SpatioTemporalSolver()
        val events = listOf(
            event(1, "a", 5, 120), event(2, "b", 10, 30), event(3, "c", 60, 70)
        )
        assertEquals(2, s.findScheduledOverlaps(events).size)
    }

    @Test
    fun knownTravelCreatesEvidenceWithoutHistory() {
        val s = SpatioTemporalSolver()
        val events = listOf(
            event(1, "a", 10, 60, 26.1445, 91.7362),
            event(2, "b", 63, 90, 26.1900, 91.7362)
        )
        val clashes = s.detectHorizonClashes(now, null, null, events)
        assertEquals(1, clashes.size)
        assertTrue(clashes[0].estimatedTravelMinutes > 5.0)
    }
}
