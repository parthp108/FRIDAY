package com.health.friday.ai.parsers

import com.health.friday.ai.AiToolCall

class LocalTaskParser {

    private val todoWord =
        Regex(
            "\\b(todo|todos|task|tasks)\\b"
        )

    private val goalWord =
        Regex(
            "\\b(goal|goals|target|targets)\\b"
        )

    private val goalTimeWord =
        Regex(
            "\\b(week|weekly|month|monthly|year|yearly|" +
                    "days?|weeks?|months?|quarters?|long[- ]term|" +
                    "long term|this month|this week|by next)\\b"
        )

    private val taskAddWord =
        Regex(
            "\\b(add|create|make|set|put|remember|need to|have to|must)\\b"
        )

    private val taskListWord =
        Regex(
            "\\b(list|show|see|view|check|display|what)\\b"
        )

    private val taskDeleteWord =
        Regex(
            "\\b(delete|remove|erase|drop|cancel)\\b"
        )

    private val taskCompleteWord =
        Regex(
            "\\b(complete|completed|finish|finished|done|mark as done)\\b"
        )

    private val questionWord =
        Regex(
            "\\b(what|which|show|list|tell|give|where|who|how)\\b"
        )

    fun parse(
        original: String,
        text: String,
        isQuestion: Boolean
    ): AiToolCall? {

        /*
         * Explicit GOAL request
         */
        if (goalWord.containsMatchIn(text)) {
            return parseGoal(
                original = original,
                text = text,
                isQuestion = isQuestion
            )
        }

        /*
         * Explicit TODO / task request
         */
        if (todoWord.containsMatchIn(text)) {
            return parseTodo(
                original = original,
                text = text,
                isQuestion = isQuestion
            )
        }

        /*
         * Long-term language without the word "goal"
         * should still become a goal.
         */
        if (
            !isQuestion &&
            goalTimeWord.containsMatchIn(text)
        ) {

            val title =
                extractTitle(
                    original = original
                )

            if (title.isNotBlank()) {
                return AiToolCall(
                    name = "goal",
                    arguments = mapOf(
                        "action" to "add",
                        "title" to title
                    )
                )
            }
        }

        /*
         * Natural language for an ordinary task:
         *
         * "I need to buy groceries"
         * "I have to submit the assignment"
         * "remember to call mom"
         */
        if (!isQuestion) {

            val naturalTask =
                Regex(
                    "\\b(i need to|i have to|i must|i should|" +
                            "need to|have to|must|should|remember to)\\b"
                )

            if (naturalTask.containsMatchIn(text)) {

                val title =
                    extractTitle(
                        original = original
                    )

                if (title.isNotBlank()) {
                    return AiToolCall(
                        name = "todo",
                        arguments = mapOf(
                            "action" to "add",
                            "title" to title
                        )
                    )
                }
            }
        }

        return null
    }

    private fun parseTodo(
        original: String,
        text: String,
        isQuestion: Boolean
    ): AiToolCall? {

        val action =
            when {

                taskCompleteWord.containsMatchIn(text) ->
                    "complete"

                taskDeleteWord.containsMatchIn(text) ->
                    "delete"

                taskListWord.containsMatchIn(text) &&
                        isQuestion ->
                    "list"

                Regex(
                    ".*\\b(show|list|what|which|see|view|check)\\b.*" +
                            "\\b(todo|todos|task|tasks)\\b.*"
                ).matches(text) ->
                    "list"

                taskAddWord.containsMatchIn(text) &&
                        !isQuestion ->
                    "add"

                else ->
                    null
            }
                ?: return null

        if (action == "list") {
            return AiToolCall(
                name = "todo",
                arguments = mapOf(
                    "action" to "list"
                )
            )
        }

        val title =
            extractTitle(
                original = original
            )

        if (title.isBlank()) {
            return null
        }

        return AiToolCall(
            name = "todo",
            arguments = mapOf(
                "action" to action,
                "title" to title
            )
        )
    }

    private fun parseGoal(
        original: String,
        text: String,
        isQuestion: Boolean
    ): AiToolCall? {

        val action =
            when {

                taskCompleteWord.containsMatchIn(text) ->
                    "complete"

                taskDeleteWord.containsMatchIn(text) ->
                    "delete"

                taskListWord.containsMatchIn(text) &&
                        isQuestion ->
                    "list"

                Regex(
                    ".*\\b(show|list|what|which|see|view|check)\\b.*" +
                            "\\b(goal|goals|target|targets)\\b.*"
                ).matches(text) ->
                    "list"

                !isQuestion &&
                        (
                                goalWord.containsMatchIn(text) ||
                                        goalTimeWord.containsMatchIn(text)
                                ) ->
                    "add"

                else ->
                    null
            }
                ?: return null

        if (action == "list") {
            return AiToolCall(
                name = "goal",
                arguments = mapOf(
                    "action" to "list"
                )
            )
        }

        val title =
            extractTitle(
                original = original
            )

        if (title.isBlank()) {
            return null
        }

        return AiToolCall(
            name = "goal",
            arguments = mapOf(
                "action" to action,
                "title" to title
            )
        )
    }

    private fun extractTitle(
        original: String
    ): String {

        var cleaned =
            original

        cleaned =
            cleaned.replace(
                Regex(
                    "(?i)^\\s*(please\\s+)?"
                ),
                ""
            )

        cleaned =
            cleaned.replace(
                Regex(
                    "(?i)\\b(add|create|make|set|put|remember)\\b"
                ),
                ""
            )

        cleaned =
            cleaned.replace(
                Regex(
                    "(?i)\\b(todo|todos|task|tasks|goal|goals|target|targets)\\b"
                ),
                ""
            )

        cleaned =
            cleaned.replace(
                Regex(
                    "(?i)^\\s*(i\\s+)?(need|have|must|should)\\s+to\\s+"
                ),
                ""
            )

        cleaned =
            cleaned.replace(
                Regex(
                    "(?i)^\\s*remember\\s+to\\s+"
                ),
                ""
            )

        cleaned =
            cleaned.replace(
                Regex(
                    "(?i)\\b(complete|completed|finish|finished|done|" +
                            "mark as done|delete|remove|erase|drop|cancel)\\b"
                ),
                ""
            )

        cleaned =
            cleaned.replace(
                Regex(
                    "(?i)\\b(this|next)\\s+" +
                            "(week|month|year)\\b"
                ),
                ""
            )

        cleaned =
            cleaned.replace(
                Regex(
                    "(?i)\\b(for|during|within|by)\\s+" +
                            "\\d+\\s+" +
                            "(days?|weeks?|months?|years?)\\b"
                ),
                ""
            )

        cleaned =
            cleaned.replace(
                Regex(
                    "(?i)\\b(weekly|monthly|yearly|long[- ]term|long term)\\b"
                ),
                ""
            )

        return cleaned
            .replace(Regex("\\s+"), " ")
            .trim(
                ' ',
                '.',
                ',',
                '!',
                '?',
                ':',
                ';',
                '-'
            )
    }
}