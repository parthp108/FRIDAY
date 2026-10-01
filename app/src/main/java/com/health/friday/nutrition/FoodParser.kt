package com.health.friday.nutrition

class FoodParser {

    fun parseResult(
        text: String
    ): FoodParseResult {

        val normalized =
            text
                .lowercase()
                .replace(",", " ")
                .replace(" and ", " ")
                .replace(Regex("\\s+"), " ")
                .trim()

        val results =
            mutableListOf<FoodItem>()

        val matchedRanges =
            mutableListOf<IntRange>()

        val patterns =
            listOf(

                Regex(
                    """(\d+(?:\.\d+)?)\s+(?:large\s+)?eggs?"""
                ) to FoodItemType(
                    "egg",
                    "piece"
                ),

                Regex(
                    """(\d+(?:\.\d+)?)\s+(?:slices?|slice)\s+(?:of\s+)?toast"""
                ) to FoodItemType(
                    "toast",
                    "slice"
                ),

                Regex(
                    """(\d+(?:\.\d+)?)\s+bananas?"""
                ) to FoodItemType(
                    "banana",
                    "piece"
                ),

                Regex(
                    """(\d+(?:\.\d+)?)\s+apples?"""
                ) to FoodItemType(
                    "apple",
                    "piece"
                ),

                Regex(
                    """(\d+(?:\.\d+)?)\s+oranges?"""
                ) to FoodItemType(
                    "orange",
                    "piece"
                ),

                Regex(
                    """(\d+(?:\.\d+)?)\s+(?:cups?|cup)\s+(?:of\s+)?rice"""
                ) to FoodItemType(
                    "rice",
                    "cup"
                ),

                Regex(
                    """(\d+(?:\.\d+)?)\s+(?:cups?|cup)\s+(?:of\s+)?milk"""
                ) to FoodItemType(
                    "milk",
                    "cup"
                )
            )

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