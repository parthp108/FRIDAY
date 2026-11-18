package com.health.friday.sovereign

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SovereignMeshTest {

    @Test
    fun privateRangesAccepted() {
        listOf("192.168.1.5", "10.0.0.1", "172.16.0.1", "172.31.255.255", "127.0.0.1")
            .forEach { assertTrue(it, MeshAddressPolicy.isPrivateLanIpv4(it)) }
    }

    @Test
    fun everythingElseRejected() {
        listOf(
            "172.32.0.1", "172.15.0.1", "8.8.8.8", "192.168.1", "192.168.1.256",
            "fe80::1", "myhost.local", "192.168.1.5%wlan0", "", "300.1.1.1", " 192.168.1.5"
        ).forEach { assertFalse(it, MeshAddressPolicy.isPrivateLanIpv4(it)) }
    }

    private fun decide(
        rtt: Long = 5, chars: Int = 40, image: Boolean = false, out: Int = 128,
        sensitive: Boolean = false, thermal: Int = 0, battery: Int = 100
    ) = SplitComputePolicy.decide(rtt, chars, image, out, sensitive, thermal, battery)

    @Test
    fun offloadsWhenReachableAndFaster() {
        val d = decide()
        assertTrue(d.offload)
        assertEquals(7.2, d.localSec, 0.05)
    }

    @Test
    fun slowOrUnreachableStaysLocal() {
        assertFalse(decide(rtt = 40).offload)
        assertFalse(decide(rtt = -1).offload)
    }

    @Test
    fun sensitiveScreenNeverOffloads() {
        assertFalse(decide(sensitive = true).offload)
    }

    @Test
    fun thermalThrottlingRaisesLocalEstimate() {
        assertEquals(decide(thermal = 0).localSec * 3.0, decide(thermal = 3).localSec, 0.01)
    }

    private class FakeClock { var now = 1_000_000L }

    @Test
    fun strongSignalEntersZone() {
        val c = FakeClock(); val e = RoomZoneEstimator { c.now }
        repeat(5) { c.now += 1000; e.update(-50) }
        assertTrue(e.isInside)
    }

    @Test
    fun leavingExitsZone() {
        val c = FakeClock(); val e = RoomZoneEstimator { c.now }
        repeat(5) { c.now += 500; e.update(-50) }
        repeat(80) { c.now += 500; e.update(-90) }
        assertFalse(e.isInside)
    }

    @Test
    fun hysteresisHoldsBetweenThresholds() {
        val c = FakeClock()
        val inside = RoomZoneEstimator { c.now }
        repeat(30) { c.now += 200; inside.update(-50) }
        repeat(200) { c.now += 200; inside.update(-73) }
        assertTrue(inside.isInside)

        val outside = RoomZoneEstimator { c.now }
        repeat(50) { c.now += 200; outside.update(-73) }
        assertFalse(outside.isInside)
    }

    @Test
    fun silenceExpiresTheZone() {
        val c = FakeClock(); val e = RoomZoneEstimator { c.now }
        repeat(5) { c.now += 500; e.update(-50) }
        assertTrue(e.isInside)
        c.now += 11_000
        assertFalse(e.isInside)
    }

    @Test
    fun longGapResetsTheFilter() {
        val c = FakeClock(); val e = RoomZoneEstimator { c.now }
        repeat(5) { c.now += 500; e.update(-50) }
        c.now += 61_000
        e.update(-90)
        assertFalse(e.isInside)
    }

    @Test
    fun routingPriority() {
        val room = ActiveAudioTarget.ROOM_SATELLITE_SPEAKER
        val buds = ActiveAudioTarget.USER_EARBUDS
        val phone = ActiveAudioTarget.PHONE_SPEAKER
        assertEquals(room, KalmanRoomAudioRouter.chooseTarget(true, true, true))
        assertEquals(buds, KalmanRoomAudioRouter.chooseTarget(false, true, true))
        assertEquals(buds, KalmanRoomAudioRouter.chooseTarget(true, false, true))
        assertEquals(phone, KalmanRoomAudioRouter.chooseTarget(false, false, false))
    }
}
