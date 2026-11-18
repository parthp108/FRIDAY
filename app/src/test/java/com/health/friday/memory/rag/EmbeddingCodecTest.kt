package com.health.friday.memory.rag

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmbeddingCodecTest {

    @Test
    fun testEncodeAndDecodeRoundtrip() {
        val original = floatArrayOf(0.123f, -0.456f, 0.789f)
        val encoded = EmbeddingCodec.encode(original)

        val decoded = EmbeddingCodec.decode(encoded)

        assertNotNull(decoded)
        assertArrayEquals(original, decoded!!, 0.0001f)
    }

    @Test
    fun testNullAndInvalidHandling() {
        assertNull(EmbeddingCodec.encode(null))
        assertNull(EmbeddingCodec.encode(floatArrayOf()))
        assertNull(EmbeddingCodec.decode(null))
        assertNull(EmbeddingCodec.decode(""))
        assertNull(EmbeddingCodec.decode("invalid json"))
    }

    private fun assertNotNull(obj: Any?) {
        assertEquals(true, obj != null)
    }
}
