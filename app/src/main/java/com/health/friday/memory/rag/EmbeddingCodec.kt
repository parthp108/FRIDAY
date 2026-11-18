package com.health.friday.memory.rag

import org.json.JSONArray

object EmbeddingCodec {

    fun encode(embedding: FloatArray?): String? {
        if (embedding == null || embedding.isEmpty()) return null
        val array = JSONArray()
        for (f in embedding) {
            array.put(f.toDouble())
        }
        return array.toString()
    }

    fun decode(json: String?): FloatArray? {
        if (json.isNullOrBlank()) return null
        return try {
            val array = JSONArray(json)
            FloatArray(array.length()) { array.getDouble(it).toFloat() }
        } catch (_: Exception) {
            null
        }
    }
}
