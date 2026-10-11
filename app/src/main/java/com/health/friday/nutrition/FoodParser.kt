package com.health.friday.nutrition

class FoodParser {

    /*
     * Kept temporarily for compatibility with existing callers.
     * Foods are no longer restricted to this list.
     */
    @Deprecated("Food recognition is database-driven now.")
    val supportedFoodNames: List<String> = emptyList()

    private val numberWords = mapOf(
        "one" to 1.0,
        "two" to 2.0,
        "three" to 3.0,
        "four" to 4.0,
        "five" to 5.0,
        "six" to 6.0,
        "seven" to 7.0,
        "eight" to 8.0,
        "nine" to 9.0,
        "ten" to 10.0
    )

    private val leadingContext = Regex(
        """^(?:(?:i|we)\s+)?(?:ate|eat|had|have|consumed|logged|log|drank|drink)\s+""",
        RegexOption.IGNORE_CASE
    )

    private val quantityAtStart = Regex(
        """^(?:(\d+(?:\.\d+)?)|(one|two|three|four|five|six|seven|eight|nine|ten|a|an))\s+(.+)$""",
        RegexOption.IGNORE_CASE
    )

    private val unitAtStart = Regex(
        """^(kg|kilograms?|g|grams?|oz|ounces?|lbs?|pounds?|ml|millilit(?:er|re)s?|lit(?:er|re)s?|cups?|slices?|pieces?|eggs?|bowls?|plates?|servings?|glasses?|tablespoons?|tbsp|teaspoons?|tsp)\b\s*(?:of\s+)?(.+)$""",
        RegexOption.IGNORE_CASE
    )

    private val splitMultipleFoods = Regex(
        """,\s*|\s+and\s+(?=(?:\d+(?:\.\d+)?|one|two|three|four|five|six|seven|eight|nine|ten|a|an)\s+)""",
        RegexOption.IGNORE_CASE
    )

    fun parseResult(text: String): FoodParseResult {
        val normalized = normalize(text)

        if (normalized.isBlank()) {
            return FoodParseResult(
                foods = emptyList(),
                unknownText = ""
            )
        }

        val clauses = normalized
            .split(splitMultipleFoods)
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val foods = mutableListOf<FoodItem>()
        val unparsedClauses = mutableListOf<String>()

        for (clause in clauses) {
            val cleanedClause = cleanClause(clause)

            if (cleanedClause.isBlank()) {
                continue
            }

            val foodItem = parseClause(cleanedClause)

            if (foodItem != null) {
                foods.add(foodItem)
            } else {
                unparsedClauses.add(cleanedClause)
            }
        }

        return FoodParseResult(
            foods = foods,
            unknownText = unparsedClauses.joinToString(", ")
        )
    }

    fun parse(text: String): List<FoodItem> {
        return parseResult(text).foods
    }

    private fun parseClause(clause: String): FoodItem? {
        var quantity = 100.0
        var unit = "g"
        var foodName = clause

        val quantityMatch = quantityAtStart.matchEntire(clause)

        if (quantityMatch != null) {
            val numericQuantity = quantityMatch.groupValues[1]
            val wordQuantity = quantityMatch.groupValues[2]

            quantity = numericQuantity.toDoubleOrNull()
                ?: numberWords[wordQuantity.lowercase()]
                        ?: if (
                    wordQuantity.equals("a", ignoreCase = true) ||
                    wordQuantity.equals("an", ignoreCase = true)
                ) {
                    1.0
                } else {
                    return null
                }

            foodName = quantityMatch.groupValues[3].trim()

            val unitMatch = unitAtStart.matchEntire(foodName)

            if (unitMatch != null) {
                val writtenUnit = unitMatch.groupValues[1]
                    .lowercase()
                foodName = unitMatch.groupValues[2].trim()

                when (writtenUnit) {
                    "kg", "kilogram", "kilograms" -> {
                        quantity *= 1000.0
                        unit = "g"
                    }

                    "g", "gram", "grams" -> {
                        unit = "g"
                    }

                    "oz", "ounce", "ounces" -> {
                        quantity *= 28.3495
                        unit = "g"
                    }

                    "lb", "lbs", "pound", "pounds" -> {
                        quantity *= 453.592
                        unit = "g"
                    }

                    "ml", "milliliter", "milliliters",
                    "millilitre", "millilitres" -> {
                        unit = "ml"
                    }

                    "l", "liter", "liters", "litre", "litres" -> {
                        quantity *= 1000.0
                        unit = "ml"
                    }

                    "cup", "cups" -> unit = "cup"
                    "slice", "slices" -> unit = "slice"
                    "piece", "pieces", "egg", "eggs" -> unit = "piece"
                    "bowl", "bowls" -> unit = "bowl"
                    "plate", "plates" -> unit = "plate"
                    "serving", "servings" -> unit = "serving"
                    "glass", "glasses" -> unit = "glass"
                    "tablespoon", "tablespoons", "tbsp" -> unit = "tbsp"
                    "teaspoon", "teaspoons", "tsp" -> unit = "tsp"
                }
            } else {
                /*
                 * A quantity without a measurement unit means
                 * a countable item: 2 eggs, 3 grapes, etc.
                 */
                unit = "piece"
            }
        }

        foodName = cleanFoodName(foodName)

        if (foodName.isBlank() || quantity <= 0.0) {
            return null
        }

        return FoodItem(
            name = foodName,
            quantity = quantity,
            unit = unit
        )
    }

    private fun cleanClause(clause: String): String {
        var result = clause.trim()

        // Remove conversational prefixes such as "I ate".
        repeat(3) {
            result = leadingContext.replaceFirst(result, "").trim()
        }

        result = result.replace(
            Regex("""^(?:some|lots of|a little|a few)\s+"""),
            ""
        )

        return result.trim()
    }

    private fun cleanFoodName(value: String): String {
        var result = value
            .lowercase()
            .replace(Regex("""\b(?:please|today|just|for breakfast|for lunch|for dinner)\b"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trim('.', '!', '?', ':', ';')

        // Remove an optional "of" left at the beginning.
        result = result.replace(Regex("""^of\s+"""), "").trim()

        /*
         * Basic plural normalization helps match descriptions such
         * as "grape" when the user says "grapes".
         * It deliberately avoids changing common uncountable words.
         */
        val uncountable = setOf(
            "rice",
            "milk",
            "toast",
            "fish",
            "hummus",
            "couscous",
            "asparagus"
        )

        if (
            result.endsWith("s") &&
            !result.endsWith("ss") &&
            result !in uncountable
        ) {
            result = when {
                result.endsWith("ies") && result.length > 3 ->
                    result.dropLast(3) + "y"

                result.endsWith("oes") && result.length > 3 ->
                    result.dropLast(2)

                else -> result.dropLast(1)
            }
        }

        return result
    }

    private fun normalize(text: String): String {
        var result = text
            .lowercase()
            .replace(Regex("""\b(\d+(?:\.\d+)?)\s*-\s*(\d+(?:\.\d+)?)\b"""), "$1 $2")
            .replace(Regex("""[;]+"""), ",")
            .replace(Regex("""\s+"""), " ")
            .trim()

        for ((word, value) in numberWords) {
            result = result.replace(
                Regex("""\b$word\b"""),
                value.toInt().toString()
            )
        }

        return result
    }
}