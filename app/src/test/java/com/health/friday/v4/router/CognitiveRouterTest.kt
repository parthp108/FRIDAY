package com.health.friday.v4.router

import com.health.friday.v4.TrinityMeshClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class CognitiveRouterTest {

    private lateinit var meshClient: TrinityMeshClient
    private lateinit var router: CognitiveRouter

    @Before
    fun setUp() {
        meshClient = TrinityMeshClient()
        router = CognitiveRouter(meshClient)
    }

    @Test
    fun testGear1_InstantReflexCommand() = runBlocking {
        val response = router.routeQuery("ping", "System Prompt")
        assertEquals(ExecutionGear.GEAR_1_REGEX, response.gearUsed)
        assertEquals("FRIDAY System Operational. All gears ready.", response.reply)
    }

    @Test
    fun testOfflineFallback_NoPcNoCloud() = runBlocking {
        val response = router.routeQuery("what is quantum physics?", "System Prompt")
        // Since PC is offline and no NPU/Cloud injected in dummy test, returns Gear 3 default fallback
        assertEquals(ExecutionGear.GEAR_3_PHONE_NPU, response.gearUsed)
    }
}
