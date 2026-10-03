package com.health.friday.nutrition

class FoodParser {

    val supportedFoodNames: List<String> =
        listOf(
            "egg",
            "toast",
            "banana",
            "apple",
            "orange",
            "rice",
            "milk"
        )

    private val numberWords =
        listOf(
            "one" to "1",
            "two" to "2",
            "three" to "3",
            "four" to "4",
            "five" to "5",
            "six" to "6",
            "seven" to "7",
            "eight" to "8",
            "nine" to "9",
            "ten" to "10"
        )

    private val articleBeforeFood =
        Regex(
            """\b(?:a|an)\s+(?=(?:large\s+)?(?:eggs?|slices?|bananas?|apples?|oranges?|cups?|glass(?:es)?)\b)"""
        )

    private val patterns =
        listOf(

            Regex(
                """(\d+(?:\.\d+)?)\s+(?:large\s+)?eggs?"""
            ) to FoodItemType("egg", "piece"),

            Regex(
                """(\d+(?:\.\d+)?)\s+slices?\s+(?:of\s+)?toast"""
            ) to FoodItemType("toast", "slice"),

            Regex(
                """(\d+(?:\.\d+)?)\s+toasts?\b"""
            ) to FoodItemType("toast", "slice"),

            Regex(
                """(\d+(?:\.\d+)?)\s+bananas?"""
            ) to FoodItemType("banana", "piece"),

            Regex(
                """(\d+(?:\.\d+)?)\s+apples?"""
            ) to FoodItemType("apple", "piece"),

            Regex(
                """(\d+(?:\.\d+)?)\s+oranges?"""
            ) to FoodItemType("orange", "piece"),

            Regex(
                """(\d+(?:\.\d+)?)\s+cups?\s+(?:of\s+)?rice"""
            ) to FoodItemType("rice", "cup"),

            Regex(
                """(\d+(?:\.\d+)?)\s+(?:cups?|glass(?:es)?)\s+(?:of\s+)?milk"""
            ) to FoodItemType("milk", "cup")
        )

    fun parseResult(
        text: String
    ): FoodParseResult {

        val normalized =
            normalize(text)

        val results =
            mutableListOf<FoodItem>()

        val matchedRanges =
            mutableListOf<IntRange>()

        for ((pattern, foodType) in patterns) {

            for (match in pattern.findAll(normalized)) {

                val quantity =
                    match.groupValues[1]
                        .toDoubleOrNull()
                        ?: continue

                results.add(
                    FoodItem(
                        name = foodType.name,
                        quantity = quantity,
                        unit = foodType.unit
                    )
                )

                matchedRanges.add(
                    match.range
                )
            }
        }

        val unknownText =
            buildUnknownText(
                normalized,
                matchedRanges
            )

        return FoodParseResult(
            foods = results,
            unknownText = unknownText
        )
    }

    fun parse(
        text: String
    ): List<FoodItem> {
        return parseResult(text).foods
    }

    private fun normalize(
        text: String
    ): String {

        var result =
            text
                .lowercase()
                .replace(",", " ")
                .replace(" and ", " ")

        for ((word, digit) in numberWords) {
            result =
                result.replace(
                    Regex("\\b$word\\b"),
                    digit
                )
        }

        result =
            articleBeforeFood.replace(result, "1 ")

        return result
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun buildUnknownText(
        text: String,
        matchedRanges: List<IntRange>
    ): String {

        if (matchedRanges.isEmpty()) {
            return text
        }

        val characters =
            text.toCharArray()

        for (range in matchedRanges) {
            for (index in range) {
                if (index in characters.indices) {
                    characters[index] = ' '
                }
            }
        }

        return String(characters)
            .replace(
                Regex("\\s+"),
                " "
            )
            .trim()
    }

    private data class FoodItemType(
        val name: String,
        val unit: String
    )
}