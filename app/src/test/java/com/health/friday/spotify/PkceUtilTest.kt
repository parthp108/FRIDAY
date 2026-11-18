package com.health.friday.spotify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PkceUtilTest {

    @Test
    fun challenge_matchesRfc7636Example() {
        val challenge = PkceUtil.challengeFor("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", challenge)
    }

    @Test
    fun verifier_hasValidLengthAndCharacters() {
        repeat(20) {
            val verifier = PkceUtil.newVerifier()
            assertTrue(Regex("^[A-Za-z0-9._~-]{43,128}$").matches(verifier))
        }
    }

    @Test
    fun verifiersAndStates_areDifferentEachTime() {
        assertNotEquals(PkceUtil.newVerifier(), PkceUtil.newVerifier())
        assertNotEquals(PkceUtil.newState(), PkceUtil.newState())
    }

    @Test
    fun challenge_hasNoPaddingOrUnsafeCharacters() {
        val challenge = PkceUtil.challengeFor(PkceUtil.newVerifier())
        assertFalse(challenge.contains("="))
        assertFalse(challenge.contains("+"))
        assertFalse(challenge.contains("/"))
    }
}
