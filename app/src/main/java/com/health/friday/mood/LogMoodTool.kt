package com.health.friday.mood

import com.health.friday.ai.AiTool
import com.health.friday.ai.AiToolParameter
import com.health.friday.data.local.DailySignalsDao
import com.health.friday.data.local.DailySignalsEntity
import com.health.friday.domain.SignalsBuilder
import com.health.friday.util.DateParser
import com.health.friday.util.DayRange
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class LogMoodTool(
    private val signalsDao: DailySignalsDao,
    private val signalsBuilder: SignalsBuilder? = null
) : AiTool {

    override val name = "log_mood"

    override val description =
        "Logs the user's mood score for today or a past day on a 1 (terrible) to 5 (great) scale. " +
                "Use when the user shares how they feel, rates their day, or explicitly logs their mood."

    override val parameters = listOf(
        AiToolParameter(
            name = "score",
            description = "Mood score from 1 to 5, or qualitative word (terrible/awful=1, bad/low/sad=2, okay/meh/fine=3, good/well/happy=4, great/amazing/fantastic=5).",
            required = true
        ),
        AiToolParameter(
            name = "date",
            description = "Optional day override: 'today' (default), 'yesterday', '3 days ago', or YYYY-MM-DD.",
            required = false
        )
    )

    override suspend fun execute(arguments: Map<String, String>): String {
        val scoreRaw = arguments["score"].orEmpty().trim().lowercase(Locale.ENGLISH)
        val validScore = parseMoodScore(scoreRaw)
            ?: return "Nothing logged. Mood score must be a number from 1 to 5 or a qualitative descriptor (e.g., terrible, bad, okay, good, great)."

        val dateArg = arguments["date"].orEmpty().trim()
        val dayStart = DateParser.parseDayStart(dateArg)
            ?: return "Nothing logged. Could not parse date '$dateArg'. Use YYYY-MM-DD, 'today', 'yesterday', or '3 days ago'."

        val zone = ZoneId.systemDefault()
        val dateStr = Instant.ofEpochMilli(dayStart)
            .atZone(zone)
            .toLocalDate()
            .toString()

        val isToday = dayStart == DayRange.today().first

        // 1. Try targeted update to preserve existing metrics in the daily row
        val rowsAffected = signalsDao.updateMood(dateStr, validScore)

        if (rowsAffected == 0) {
            // 2. If no row exists yet, build full day signals first then insert mood
            signalsBuilder?.buildSignalsForDate(dayStart)
            val existing = signalsDao.getSignalForDate(dateStr)
            if (existing != null) {
                signalsDao.insertOrUpdate(existing.copy(moodScore = validScore))
            } else {
                signalsDao.insertOrUpdate(
                    DailySignalsEntity(
                        date = dateStr,
                        moodScore = validScore
                    )
                )
            }
        }

        val label = moodLabel(validScore)
        val dayLabel = if (isToday) "today" else "for $dateStr"

        return "Mood logged $dayLabel: $validScore/5 ($label). " +
                "You can ask 'how does my mood correlate with my sleep or steps?' to discover trends."
    }

    private fun parseMoodScore(raw: String): Int? {
        val numeric = raw.toIntOrNull()
        if (numeric != null && numeric in 1..5) return numeric

        return when {
            raw.contains("terrible") || raw.contains("awful") || raw.contains("miserable") || raw.contains("horrible") -> 1
            raw.contains("bad") || raw.contains("low") || raw.contains("sad") || raw.contains("depressed") || raw.contains("upset") -> 2
            raw.contains("okay") || raw.contains("meh") || raw.contains("fine") || raw.contains("average") || raw.contains("so-so") -> 3
            raw.contains("good") || raw.contains("well") || raw.contains("happy") || raw.contains("decent") -> 4
            raw.contains("great") || raw.contains("amazing") || raw.contains("fantastic") || raw.contains("awesome") || raw.contains("excellent") -> 5
            else -> null
        }
    }

    private fun moodLabel(score: Int): String = when (score) {
        1 -> "terrible"
        2 -> "bad"
        3 -> "okay"
        4 -> "good"
        5 -> "great"
        else -> "unknown"
    }
}
