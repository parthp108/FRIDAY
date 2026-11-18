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
        "ten" to 10.0,
        "half" to 0.5,
        "quarter" to 0.25
    )

    private val leadingContext = Regex(
        """^(?:(?:i|we)\s+)?(?:ate|eat|had|have|consumed|logged|log|drank|drink)\s+""",
        RegexOption.IGNORE_CASE
    )

    private val quantityAtStart = Regex(
        """^(?:(\d+(?:\.\d+)?)|(one|two|three|four|five|six|seven|eight|nine|ten|half|quarter|a|an))\s+(.+)$""",
        RegexOption.IGNORE_CASE
    )

    private val unitAtStart = Regex(
        """^(kg|kilograms?|g|grams?|oz|ounces?|lbs?|pounds?|ml|millilit(?:er|re)s?|lit(?:er|re)s?|cups?|slices?|pieces?|bowls?|katori|katoris|plates?|servings?|glasses?|tablespoons?|tbsp|teaspoons?|tsp)\b\s*(?:of\s+)?(.+)$""",
        RegexOption.IGNORE_CASE
    )

    private val splitMultipleFoods = Regex(
        """,\s*|;\s*|\s+and\s+(?=(?:\d+(?:\.\d+)?|one|two|three|four|five|six|seven|eight|nine|ten|half|quarter|a|an)\s+|[a-z])""",
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

        val rawClauses = normalized
            .split(splitMultipleFoods)
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val clauses = rawClauses.flatMap { splitClauseDishes(it) }

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

            foodName = quantityMatch.groupValues[3]
                .trim()
                .replaceFirst(Regex("""^(?:a|an)\s+""", RegexOption.IGNORE_CASE), "")
                .trim()

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
                    "piece", "pieces" -> unit = "piece"
                    "bowl", "bowls", "katori", "katoris" -> unit = "bowl"
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
        } else {
            /*
             * A food clause without an explicit quantity (e.g. "cup of coffee" or "toast" in "2 eggs and toast")
             */
            val unitMatch = unitAtStart.matchEntire(foodName)
            if (unitMatch != null) {
                val writtenUnit = unitMatch.groupValues[1].lowercase()
                foodName = unitMatch.groupValues[2].trim()
                quantity = 1.0
                when (writtenUnit) {
                    "cup", "cups" -> unit = "cup"
                    "slice", "slices" -> unit = "slice"
                    "piece", "pieces" -> unit = "piece"
                    "bowl", "bowls", "katori", "katoris" -> unit = "bowl"
                    "plate", "plates" -> unit = "plate"
                    "serving", "servings" -> unit = "serving"
                    "glass", "glasses" -> unit = "glass"
                    "tablespoon", "tablespoons", "tbsp" -> unit = "tbsp"
                    "teaspoon", "teaspoons", "tsp" -> unit = "tsp"
                    else -> unit = "piece"
                }
            } else {
                quantity = 1.0
                unit = "piece"
            }
        }

        foodName = cleanFoodName(foodName)

        if (foodName.isBlank() || quantity <= 0.0 || foodName.all { it.isDigit() } || foodName in setOf("then", "and", "with", "like")) {
            return null
        }

        return FoodItem(
            name = foodName,
            quantity = quantity,
            unit = unit
        )
    }

    private fun cleanClause(clause: String): String {
        var result = clause.trim(' ', ',')

        // Remove time expressions e.g. "today morning", "yesterday evening"
        result = result.replace(
            Regex("""\b(?:today|yesterday|this)\s+(?:morning|afternoon|evening|night)\b""", RegexOption.IGNORE_CASE),
            ""
        ).trim()

        // Remove meal header prefixes e.g. "Wednesday Dinner:", "Thursday Lunch:", "Breakfast:"
        result = result.replace(
            Regex("""^(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)?\s*(?:breakfast|lunch|dinner|supper|snack)?\s*:\s*""", RegexOption.IGNORE_CASE),
            ""
        ).trim()

        // Remove conversational prefixes such as "I ate".
        repeat(3) {
            result = leadingContext.replaceFirst(result, "").trim()
        }

        result = result.replace(
            Regex("""^(?:some|lots of|a little|a few)\s+"""),
            ""
        )

        return result.trim(' ', ',')
    }

    private fun cleanFoodName(value: String): String {
        var result = value
            .lowercase()
            .replace(Regex("""\b(?:please|today|just|for breakfast|for lunch|for dinner)\b"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trim('.', '!', '?', ':', ';', ',')

        // Remove leading connectors like "then", "and", "with", or "of" left at the beginning.
        result = result
            .replace(Regex("""^(?:then|and|with|of)\s+"""), "")
            .trim(' ', ',')

        /*
         * Basic plural normalization helps match descriptions such
         * as "grape" when the user says "grapes".
         * It deliberately avoids changing common uncountable words.
         */
        val uncountable = setOf(
            "rice", "milk", "toast", "fish", "hummus", "couscous", "asparagus",
            "water", "coffee", "tea", "spinach", "oatmeal", "dal", "daal", "dhal",
            "sabji", "subji", "sabzi", "subzi", "sambar", "sambhar", "raita", "khichdi",
            "paneer", "poha", "curry", "roti", "chapati", "naan", "paratha", "puri",
            "poori", "idli", "dosa", "upma", "chole", "rajma", "kadhi", "bhature",
            "papad", "papadam", "biryani", "pulao", "halwa", "kheer", "gulab jamun",
            "salad", "chowmein", "pakoda", "pakora", "lassi", "jalebi", "vada", "chilla",
            "dalia", "oats", "sev", "thepla", "chaas", "dahi", "curd",
            "breakfast salad", "sprouts salad", "protein salad", "chana moong salad", "boiled sprouts", "morning salad", "sprouted salad"
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

    private fun splitClauseDishes(clause: String): List<String> {
        val knownBoundaryDishes = listOf(
            "paneer butter masala", "paneer bhurji", "scrambled paneer", "palak paneer", "matar paneer", "kadai paneer", "kadhai paneer", "shahi paneer", "paneer roti", "paneer sabji", "paneer subji", "paneer sabzi", "paneer curry",
            "chicken butter masala", "butter chicken", "matar chicken", "til chicken", "egg curry", "egg bhurji", "boiled egg",
            "dal makhani", "dal tadka", "dal fry", "arhar dal", "toor dal tadka", "toor dal", "chana dal", "lehsuni chana dal", "moong dal", "moong mysore dal", "mysore dal", "black mysore dal", "mix dal", "dal", "daal", "dhal", "sambar", "sambhar",
            "veg malai kofta", "aloo kofta masala", "methi matar malai", "veg do pyaza", "veg manchurian", "veg tawa", "besan gatta sabji", "soyabean gravy", "chilli soya", "kadhai pakoda", "kadhai pakora", "bhindi masala", "aloo gobi", "gobhi matar", "aloo jeera", "matar aloo", "aloo parwal", "aloo chana sabji", "sprouts masala", "kala chana", "chole gravy", "chole sabji", "chole subji", "chana masala", "chole", "rajma masala", "rajma", "mix veg", "sabji", "subji", "sabzi",
            "tomato rice", "lemon rice", "jeera rice", "masala rice", "veg pulao", "peas pulao", "plain rice", "steamed rice", "steam rice", "fried rice", "rice", "chawal", "biryani",
            "aloo pyaz paratha", "besan chilla", "vada pav", "vermicelli upma", "upma", "masala dosa", "mysore masala dosa", "medu vada", "idli", "dosa", "dosas", "poha", "thepla", "bhature", "jawa naan", "naan", "paratha", "puri", "poori", "chapati", "chapatis", "roti", "rotis", "phulka", "phulkas", "chowmein", "veg roll", "veg sandwich", "sweet dalia", "masala oats", "sev", "papad", "papadam",
            "gulab jamun", "rabdi jalebi", "jalebi", "rasgulla", "kheer", "halwa", "ice cream", "sweet lassi", "lassi", "nimbu paani", "masala lemonade", "chaas", "butter milk", "curd", "raita", "salad",
            "breakfast salad", "sprouts salad", "protein salad", "chana moong salad", "boiled sprouts", "morning salad", "sprouted salad"
        ).sortedByDescending { it.length }

        val tokens = clause.split(Regex("""\s+"""))
        val subClauses = mutableListOf<String>()
        val curr = mutableListOf<String>()

        for (word in tokens) {
            if (curr.isNotEmpty()) {
                val joined = curr.joinToString(" ")

                val qMatch = quantityAtStart.matchEntire(joined)
                val dishNameInCurr = qMatch?.groupValues?.getOrNull(3)?.trim() ?: joined

                val isNewDish = listOf("rice", "dal", "daal", "dhal", "sabji", "subji", "sabzi", "salad", "raita", "gulab", "paneer", "chole", "rajma", "chicken", "biryani", "roti", "chapati", "paratha", "jalebi")
                    .any { word.equals(it, ignoreCase = true) || it.startsWith(word, ignoreCase = true) }
                val isCurrDish = knownBoundaryDishes.any { dishNameInCurr.equals(it, ignoreCase = true) }

                if (isCurrDish && isNewDish && knownBoundaryDishes.none { "$dishNameInCurr $word".equals(it, ignoreCase = true) }) {
                    subClauses.add(joined)
                    curr.clear()
                    curr.add(word)
                    continue
                }
            }
            curr.add(word)
        }

        if (curr.isNotEmpty()) {
            subClauses.add(curr.joinToString(" "))
        }

        return subClauses
    }

    private fun normalize(text: String): String {
        var result = text.lowercase()

        // Strip parenthetical descriptions e.g. (cucumber 3-4 pieces...)
        result = result.replace(Regex("""\(.*?\)"""), " ")

        // Strip filler phrases like "like regular mess dish", "mess dish", "piece of", "pieces of"
        result = result.replace(Regex("""\b(?:like\s+regular\s+)?mess\s+dish\b""", RegexOption.IGNORE_CASE), " ")
        result = result.replace(Regex("""\b(?:piece|pieces)\s+of\b""", RegexOption.IGNORE_CASE), " ")

        // Correct common Indian food typos
        val typoMap = mapOf(
            "panner" to "paneer",
            "paner" to "paneer",
            "choley" to "chole",
            "channna" to "chana",
            "rotti" to "roti",
            "chappati" to "chapati",
            "cucumer" to "cucumber",
            "tamatoes" to "tomatoes"
        )
        for ((typo, fix) in typoMap) {
            result = result.replace(Regex("""\b${Regex.escape(typo)}\b""", RegexOption.IGNORE_CASE), fix)
        }

        // Split before digits in the middle of a clause e.g. "paneer sabji 3 chapati" -> "paneer sabji , 3 chapati"
        val verbPrefixes = setOf("ate", "had", "eat", "have", "drank", "logged", "consumed")
        result = Regex("""\b([a-z]+)\s+(\d+(?:\.\d+)?)\s+""", RegexOption.IGNORE_CASE).replace(result) { m ->
            val word = m.groupValues[1].lowercase()
            val num = m.groupValues[2]
            if (word !in verbPrefixes) {
                "$word , $num "
            } else {
                m.value
            }
        }

        result = result
            .replace(Regex("""\b(\d+(?:\.\d+)?)\s*-\s*(\d+(?:\.\d+)?)\b"""), "$1 $2")
            .replace(Regex("""[;]+"""), ",")
            .replace(Regex("""\s+"""), " ")
            .trim()

        for ((word, value) in numberWords) {
            val strVal = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
            result = result.replace(
                Regex("""\b$word\b"""),
                strVal
            )
        }

        // Replace connectors "and", "then", "with" with commas
        result = result.replace(Regex("""\b(?:and|then|with)\b""", RegexOption.IGNORE_CASE), " , ")
        result = result.replace(Regex(""",\s*,+"""), ",")
        result = result.replace(Regex("""\s+"""), " ").trim(' ', ',')

        return result
    }
}