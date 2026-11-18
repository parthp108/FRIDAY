package com.health.friday.shared.models

import kotlinx.serialization.Serializable

@Serializable
enum class JournalSentiment { POSITIVE, NEUTRAL, NEGATIVE, STRESSED, ENERGETIC }

@Serializable
data class JournalEntryDto(
    val id: String,
    val content: String,
    val sentiment: JournalSentiment = JournalSentiment.NEUTRAL,
    val tags: List<String> = emptyList(),
    val timestamp: Long = 0L
)
