package com.health.friday.memory.graph

data class MemoryNode(
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val sourceApp: String? = "Voice",
    val rawText: String,
    val embedding: FloatArray,
    val importanceScore: Float = 0.5f,
    var isSuperseded: Boolean = false,
    var supersededById: Long? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as MemoryNode
        return id == other.id && rawText == other.rawText && isSuperseded == other.isSuperseded
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + rawText.hashCode()
        result = 31 * result + isSuperseded.hashCode()
        return result
    }
}

data class EntityNode(
    val id: Long = 0,
    val name: String,
    val type: String, // "PERSON", "PLACE", "PREFERENCE"
    val canonicalAttributesJson: String = "{}"
)
