package com.health.friday.nutrition

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

data class UsdaFoodItem(
    val fdcId: Long,
    val description: String,
    val dataType: String
)

data class IndianFoodEntry(
    val id: Long,
    val name: String,
    val defaultServingGrams: Double,
    val caloriesPerServing: Double,
    val proteinPerServing: Double,
    val carbsPerServing: Double,
    val fatPerServing: Double,
    val keywords: List<String>,
    val mealCategories: List<String>
)

private val indianFoodDatabase = listOf(
    // Breakfast Options
    IndianFoodEntry(1018, "Poha", 150.0, 220.0, 4.0, 38.0, 6.0, listOf("poha", "flattened rice"), listOf("Breakfast", "Snack")),
    IndianFoodEntry(1019, "Upma", 150.0, 210.0, 4.5, 34.0, 6.5, listOf("upma", "semolina"), listOf("Breakfast", "Snack")),
    IndianFoodEntry(1015, "Idli", 50.0, 65.0, 2.0, 13.5, 0.4, listOf("idli", "idly", "rice cake"), listOf("Breakfast")),
    IndianFoodEntry(1013, "Masala Dosa", 150.0, 300.0, 6.0, 48.0, 9.5, listOf("dosa", "masala dosa"), listOf("Breakfast", "Dinner")),
    IndianFoodEntry(1014, "Plain Dosa", 100.0, 180.0, 4.0, 32.0, 4.2, listOf("dosa", "plain dosa"), listOf("Breakfast", "Dinner")),
    IndianFoodEntry(1026, "Egg Bhurji / Scrambled Eggs", 120.0, 210.0, 13.0, 3.5, 16.0, listOf("egg bhurji", "bhurji", "scrambled egg", "egg"), listOf("Breakfast", "Dinner")),
    IndianFoodEntry(1030, "Masala Oats", 200.0, 180.0, 6.0, 30.0, 3.8, listOf("oats", "masala oats", "oatmeal"), listOf("Breakfast")),
    IndianFoodEntry(1017, "Medu Vada", 60.0, 190.0, 4.5, 18.0, 11.0, listOf("vada", "medu vada", "fritter"), listOf("Breakfast", "Snack")),

    // Lunch & Dinner Options
    IndianFoodEntry(1001, "Roti / Chapati / Phulka", 40.0, 104.0, 3.1, 18.2, 2.2, listOf("roti", "chapati", "phulka", "flatbread", "bread"), listOf("Lunch", "Dinner")),
    IndianFoodEntry(1002, "Butter Naan", 80.0, 260.0, 6.5, 42.0, 7.5, listOf("naan", "butter naan", "bread"), listOf("Lunch", "Dinner")),
    IndianFoodEntry(1004, "Dal Tadka / Dal Fry", 180.0, 180.0, 8.5, 24.0, 5.2, listOf("dal", "daal", "dal tadka", "dal fry", "yellow dal", "lentils"), listOf("Lunch", "Dinner")),
    IndianFoodEntry(1005, "Dal Makhani", 180.0, 260.0, 9.2, 26.5, 13.0, listOf("dal", "dal makhani", "black dal", "makhani"), listOf("Lunch", "Dinner")),
    IndianFoodEntry(1006, "Chole / Chana Masala", 180.0, 240.0, 10.5, 32.0, 7.8, listOf("chole", "chana", "chana masala", "chickpeas"), listOf("Lunch", "Dinner")),
    IndianFoodEntry(1007, "Rajma Masala", 180.0, 220.0, 9.8, 30.5, 6.5, listOf("rajma", "kidney beans", "rajma chawal"), listOf("Lunch", "Dinner")),
    IndianFoodEntry(1008, "Paneer Butter Masala", 180.0, 320.0, 12.0, 12.5, 25.0, listOf("paneer", "paneer butter masala", "paneer makhani", "cottage cheese"), listOf("Lunch", "Dinner")),
    IndianFoodEntry(1009, "Palak Paneer", 180.0, 250.0, 13.5, 9.0, 18.2, listOf("paneer", "palak paneer", "spinach paneer"), listOf("Lunch", "Dinner")),
    IndianFoodEntry(1010, "Chicken Biryani", 300.0, 450.0, 28.0, 52.0, 15.0, listOf("biryani", "chicken biryani", "rice"), listOf("Lunch", "Dinner")),
    IndianFoodEntry(1011, "Veg Biryani / Pulao", 250.0, 320.0, 6.5, 54.0, 8.5, listOf("biryani", "veg biryani", "pulao", "pulav", "rice"), listOf("Lunch", "Dinner")),
    IndianFoodEntry(1012, "Butter Chicken", 200.0, 380.0, 26.0, 10.0, 26.0, listOf("chicken", "butter chicken", "murgh makhani"), listOf("Lunch", "Dinner")),
    IndianFoodEntry(1016, "Sambar", 180.0, 110.0, 4.2, 16.0, 3.0, listOf("sambar", "sambhar", "lentil soup"), listOf("Breakfast", "Lunch", "Dinner")),
    IndianFoodEntry(1022, "Khichdi", 200.0, 210.0, 6.5, 38.0, 3.5, listOf("khichdi", "khichuri"), listOf("Lunch", "Dinner")),
    IndianFoodEntry(1023, "Curd / Dahi (Plain Yogurt)", 150.0, 90.0, 5.0, 7.0, 4.5, listOf("curd", "dahi", "yogurt"), listOf("Breakfast", "Lunch", "Dinner")),
    IndianFoodEntry(1027, "Aloo Gobi", 180.0, 150.0, 3.5, 20.0, 6.5, listOf("aloo gobi", "gobhi", "sabji"), listOf("Lunch", "Dinner")),
    IndianFoodEntry(1028, "Bhindi Masala", 150.0, 120.0, 2.8, 12.0, 7.0, listOf("bhindi", "okra", "sabji"), listOf("Lunch", "Dinner")),

    // Snack Options
    IndianFoodEntry(1020, "Samosa", 80.0, 250.0, 4.0, 28.0, 13.5, listOf("samosa", "snack"), listOf("Snack")),
    IndianFoodEntry(1021, "Pav Bhaji", 250.0, 420.0, 9.0, 58.0, 17.5, listOf("pav bhaji", "bhaji"), listOf("Snack", "Dinner")),
    IndianFoodEntry(1029, "Pani Puri / Golgappa", 120.0, 180.0, 3.0, 32.0, 5.0, listOf("pani puri", "golgappa", "chaat"), listOf("Snack")),
    IndianFoodEntry(1031, "Packaged Potato Chips", 50.0, 270.0, 3.0, 26.0, 17.0, listOf("chips", "potato chips", "lays", "packaged snack"), listOf("Snack")),
    IndianFoodEntry(1032, "Biscuits / Cookies", 30.0, 140.0, 2.0, 21.0, 5.5, listOf("biscuits", "cookies", "biscuit", "parle-g"), listOf("Snack")),
    IndianFoodEntry(1033, "Maggi / Instant Noodles", 200.0, 310.0, 6.5, 44.0, 12.0, listOf("maggi", "noodles", "instant noodles"), listOf("Snack")),
    IndianFoodEntry(1034, "Namkeen / Bhujia", 40.0, 220.0, 4.0, 18.0, 14.0, listOf("namkeen", "bhujia", "sev", "snack"), listOf("Snack")),
    IndianFoodEntry(1024, "Sweet Lassi", 250.0, 220.0, 6.0, 32.0, 7.5, listOf("lassi", "sweet lassi"), listOf("Snack", "Lunch")),
    IndianFoodEntry(1025, "Gulab Jamun", 45.0, 150.0, 2.2, 24.0, 5.5, listOf("gulab jamun", "dessert", "sweet"), listOf("Snack", "Dinner"))
)

class LocalNutritionProvider(
    context: Context
) : NutritionProvider {

    private val usdaDatabase = UsdaFoodDatabase(context)

    suspend fun searchFoods(query: String, mealType: String = "", limit: Int = 30): List<UsdaFoodItem> = withContext(Dispatchers.IO) {
        val cleanQuery = query.lowercase().trim()
        val results = mutableListOf<UsdaFoodItem>()

        // 1. Filter Indian Food Database prioritizing selected mealType
        val matchingIndian = if (cleanQuery.isBlank()) {
            if (mealType.isNotBlank()) {
                val typeMatches = indianFoodDatabase.filter { it.mealCategories.any { cat -> cat.equals(mealType, ignoreCase = true) } }
                if (typeMatches.isNotEmpty()) typeMatches else indianFoodDatabase.take(12)
            } else {
                indianFoodDatabase.take(12)
            }
        } else {
            indianFoodDatabase.filter { entry ->
                entry.name.lowercase().contains(cleanQuery) ||
                        entry.keywords.any { it.contains(cleanQuery) }
            }.sortedByDescending { entry ->
                if (mealType.isNotBlank() && entry.mealCategories.any { cat -> cat.equals(mealType, ignoreCase = true) }) 1 else 0
            }
        }

        matchingIndian.forEach { indian ->
            results += UsdaFoodItem(
                fdcId = indian.id,
                description = indian.name,
                dataType = "indian_food_db"
            )
        }

        // 2. Search USDA SQLite Database
        val db = try {
            usdaDatabase.database()
        } catch (e: Exception) {
            return@withContext results
        }

        if (cleanQuery.isBlank()) {
            val popularQuery = when {
                mealType.equals("Breakfast", ignoreCase = true) -> """
                    SELECT fdc_id, description, data_type FROM food 
                    WHERE LOWER(description) LIKE '%egg, whole, cooked%' 
                       OR LOWER(description) LIKE '%oats%' 
                       OR LOWER(description) LIKE '%pancake%' 
                       OR LOWER(description) LIKE '%bread, white%'
                    LIMIT $limit
                """.trimIndent()

                mealType.equals("Snack", ignoreCase = true) -> """
                    SELECT fdc_id, description, data_type FROM food 
                    WHERE LOWER(description) LIKE '%chips, potato%' 
                       OR LOWER(description) LIKE '%cookies%' 
                       OR LOWER(description) LIKE '%popcorn%' 
                       OR LOWER(description) LIKE '%nuts, almonds%'
                    LIMIT $limit
                """.trimIndent()

                else -> """
                    SELECT fdc_id, description, data_type FROM food 
                    WHERE LOWER(description) LIKE '%rice, white, cooked%' 
                       OR LOWER(description) LIKE '%chicken breast, cooked%' 
                       OR LOWER(description) LIKE '%milk, whole%' 
                       OR LOWER(description) LIKE '%apple, raw%' 
                       OR LOWER(description) LIKE '%banana, raw%' 
                       OR LOWER(description) LIKE '%paneer%'
                    LIMIT $limit
                """.trimIndent()
            }

            try {
                db.rawQuery(popularQuery, null).use { cursor ->
                    while (cursor.moveToNext()) {
                        results += UsdaFoodItem(
                            fdcId = cursor.getLong(0),
                            description = cursor.getString(1).orEmpty(),
                            dataType = cursor.getString(2).orEmpty()
                        )
                    }
                }
            } catch (_: Exception) {}
            return@withContext results
        }

        val tokens = cleanQuery.split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }.distinct()
        if (tokens.isNotEmpty()) {
            val conditions = tokens.joinToString(" AND ") { "LOWER(description) LIKE ?" }
            val sql = "SELECT fdc_id, description, data_type FROM food WHERE $conditions LIMIT $limit"
            val args = tokens.map { "%$it%" }.toTypedArray()

            try {
                db.rawQuery(sql, args).use { cursor ->
                    while (cursor.moveToNext()) {
                        results += UsdaFoodItem(
                            fdcId = cursor.getLong(0),
                            description = cursor.getString(1).orEmpty(),
                            dataType = cursor.getString(2).orEmpty()
                        )
                    }
                }
            } catch (_: Exception) {}
        }

        results
    }

    private data class FoodCandidate(
        val fdcId: Long,
        val description: String,
        val dataType: String
    )

    private data class PortionCandidate(
        val amount: Double,
        val gramWeight: Double,
        val description: String,
        val modifier: String,
        val unitName: String
    )

    override suspend fun getNutrition(
        food: FoodItem
    ): NutritionResult? = withContext(Dispatchers.IO) {
        if (food.quantity <= 0.0 || food.name.isBlank()) {
            return@withContext null
        }

        // Check Indian Food Database first
        val indianMatch = indianFoodDatabase.firstOrNull {
            it.name.equals(food.name, ignoreCase = true) ||
                    food.name.lowercase().startsWith(it.name.lowercase().take(10))
        }

        if (indianMatch != null) {
            val qty = food.quantity
            val totalGrams = indianMatch.defaultServingGrams * qty
            val calories = indianMatch.caloriesPerServing * qty
            val protein = indianMatch.proteinPerServing * qty
            val carbs = indianMatch.carbsPerServing * qty
            val fat = indianMatch.fatPerServing * qty

            val nutrients = listOf(
                NutrientAmount(1008, "Energy", calories, "kcal"),
                NutrientAmount(1003, "Protein", protein, "g"),
                NutrientAmount(1005, "Carbohydrate, by difference", carbs, "g"),
                NutrientAmount(1004, "Total lipid (fat)", fat, "g")
            )

            return@withContext NutritionResult(
                calories = calories,
                protein = protein,
                carbohydrates = carbs,
                fat = fat,
                nutrients = nutrients,
                totalGrams = totalGrams
            )
        }

        try {
            val db = usdaDatabase.database()
            val candidate = resolveFood(db, food)

            if (candidate == null) {
                Log.w(
                    "FRIDAY_USDA",
                    "No USDA food match for '${food.name}'"
                )
                return@withContext null
            }

            val gramsPerUnit =
                if (
                    food.unit.equals("g", ignoreCase = true) ||
                    food.unit.equals("gram", ignoreCase = true) ||
                    food.unit.equals("grams", ignoreCase = true)
                ) {
                    1.0
                } else {
                    resolveGramsPerUnit(
                        db = db,
                        food = food,
                        fdcId = candidate.fdcId
                    ) ?: run {
                        Log.w(
                            "FRIDAY_USDA",
                            "No valid USDA portion for '${food.name}', " +
                                    "unit='${food.unit}', fdcId=${candidate.fdcId}"
                        )
                        return@withContext null
                    }
                }

            val totalGrams = gramsPerUnit * food.quantity

            if (totalGrams <= 0.0) {
                return@withContext null
            }

            val nutrients = loadNutrients(
                db = db,
                fdcId = candidate.fdcId,
                totalGrams = totalGrams
            )

            val calories = nutrients.firstOrNull {
                (it.name.equals("Energy", ignoreCase = true) ||
                        it.name.startsWith("Energy", ignoreCase = true)) &&
                        it.unit.equals("kcal", ignoreCase = true)
            }?.amount ?: return@withContext null

            val protein = nutrients.firstOrNull {
                it.name.equals("Protein", ignoreCase = true)
            }?.amount ?: 0.0

            val carbohydrates = nutrients.firstOrNull {
                it.name.equals(
                    "Carbohydrate, by difference",
                    ignoreCase = true
                )
            }?.amount ?: 0.0

            val fat = nutrients.firstOrNull {
                it.name.equals(
                    "Total lipid (fat)",
                    ignoreCase = true
                )
            }?.amount ?: 0.0

            Log.i(
                "FRIDAY_USDA",
                "Matched '${food.name}' to '${candidate.description}' " +
                        "(fdcId=${candidate.fdcId}, " +
                        "quantity=${food.quantity}, unit=${food.unit}, " +
                        "gramsPerUnit=$gramsPerUnit, totalGrams=$totalGrams)"
            )

            NutritionResult(
                calories = calories,
                protein = protein,
                carbohydrates = carbohydrates,
                fat = fat,
                nutrients = nutrients,
                totalGrams = totalGrams
            )
        } catch (exception: Exception) {
            Log.e(
                "FRIDAY_USDA",
                "USDA lookup failed for '${food.name}', " +
                        "quantity=${food.quantity}, unit=${food.unit}",
                exception
            )
            return@withContext null
        }
    }

    private fun resolveSearchQuery(foodName: String): String {
        val lower = foodName.lowercase().trim()

        when {
            lower.contains("aloo pyaz paratha") || lower.contains("thepla") -> return "paratha"
            lower.contains("chowmein") -> return "noodles cooked"
            lower.contains("kadhai pakoda") || lower.contains("kadhai pakora") -> return "vegetable fritters"
            lower.contains("bhindi masala") -> return "okra cooked"
            lower.contains("sweet lassi") || lower.contains("lassi") -> return "yogurt drink"
            lower.contains("veg malai kofta") || lower.contains("aloo kofta") -> return "vegetable kofta curry"
            lower.contains("tomato rice") || lower.contains("jeera rice") || lower.contains("lemon rice") || lower.contains("masala rice") || lower.contains("veg pulao") || lower.contains("peas pulao") -> return "seasoned rice cooked"
            lower.contains("rabdi jalebi") || lower.contains("jalebi") -> return "dessert sweet"
            lower.contains("paneer bhurji") -> return "scrambled paneer"
            lower.contains("paneer") -> return "paneer"
            lower.contains("egg bhurji") -> return "scrambled egg"
            lower.contains("boiled egg") -> return "hard boiled egg"
            lower.contains("masala dosa") || lower.contains("dosa") -> return "dosa filling"
            lower.contains("medu vada") -> return "fritter"
            lower.contains("vada pav") || lower.contains("veg sandwich") -> return "sandwich"
            lower.contains("kala chana") || lower.contains("chole") || lower.contains("chana") -> return "chickpeas cooked"
            lower.contains("long beans") -> return "green beans cooked"
            lower.contains("aloo gobi") || lower.contains("gobhi matar") -> return "potatoes cauliflower"
            lower.contains("aloo jeera") || lower.contains("aloo parwal") -> return "potatoes cooked"
            lower.contains("matar aloo") -> return "potatoes peas"
            lower.contains("veg manchurian") || lower.contains("methi matar malai") || lower.contains("veg do pyaza") || lower.contains("veg tawa") -> return "vegetable curry"
            lower.contains("besan gatta") -> return "gram flour curry"
            lower.contains("soyabean") || lower.contains("soya") -> return "soybean curry"
            lower.contains("besan chilla") || lower.contains("chilla") -> return "paratha"
            lower.contains("coffee") -> return "coffee brewed"
            lower.contains("sprouts") -> return "sprouted lentils"
            lower.contains("butter chicken") || lower.contains("matar chicken") || lower.contains("til chicken") || lower.contains("chicken") -> return "chicken curry"
            lower.contains("dal makhani") -> return "black lentils cream"
            lower.contains("vermicelli upma") || lower.contains("poha") || lower.contains("upma") -> return "cooked rice"
            lower.contains("rajma") -> return "kidney beans cooked"
            lower.contains("chaas") || lower.contains("butter milk") -> return "buttermilk"
            lower.contains("veg roll") -> return "wrap vegetable"
            lower.contains("bhature") -> return "fried bread"
            lower.contains("sweet dalia") || lower.contains("masala oats") -> return "oatmeal porridge"
            lower.contains("gulab jamun") || lower.contains("gulab") || lower.contains("jamun") -> return "dessert sweet"
            lower.contains("arhar dal") || lower.contains("toor dal") || lower.contains("chana dal") || lower.contains("moong dal") || lower.contains("mysore dal") || lower.contains("mix dal") || lower.contains("dal") || lower.contains("daal") -> return "lentils cooked"
            lower.contains("breakfast salad") || lower.contains("sprouts salad") || lower.contains("protein salad") || lower.contains("chana moong salad") || lower.contains("boiled sprouts") || lower.contains("morning salad") || lower.contains("sprouted salad") -> return "sprouted lentils"
            lower.contains("salad") -> return "salad greens"
        }

        val words = lower.split(Regex("""\s+"""))
        val mapped = words.map { word ->
            when (word) {
                "sabji", "subji", "sabzi", "subzi" -> "vegetable curry"
                "dal", "daal", "dhal", "sambar", "sambhar" -> "lentils cooked"
                "chole", "chana" -> "chickpeas cooked"
                "rajma" -> "kidney beans cooked"
                "raita" -> "yogurt cucumber"
                "curd", "dahi" -> "yogurt plain"
                "khichdi" -> "rice lentils cooked"
                "poha" -> "cooked rice"
                "puri", "poori" -> "paratha"
                "roti", "rotis", "chapati", "chapatis", "phulka", "phulkas" -> "chapati"
                "bhatura", "bhature" -> "paratha"
                "papad", "papadam" -> "papadum"
                "kadhi" -> "yogurt soup"
                "upma" -> "semolina cooked"
                "salad" -> "salad greens"
                else -> word
            }
        }
        return mapped.joinToString(" ")
    }

    private fun resolveFood(
        db: SQLiteDatabase,
        food: FoodItem
    ): FoodCandidate? {
        val searchQuery = resolveSearchQuery(food.name)
        val tokens = searchQuery
            .lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotBlank() }
            .distinct()

        if (tokens.isEmpty()) return null

        val conditions = tokens.joinToString(" AND ") {
            "LOWER(f.description) LIKE ?"
        }

        val query = """
            SELECT
                f.fdc_id,
                f.description,
                f.data_type
            FROM food f
            WHERE $conditions
            LIMIT 1000
        """.trimIndent()

        val arguments = tokens.map { token ->
            "%$token%"
        }.toTypedArray()

        val candidates = mutableListOf<FoodCandidate>()

        db.rawQuery(query, arguments).use { cursor ->
            while (cursor.moveToNext()) {
                candidates += FoodCandidate(
                    fdcId = cursor.getLong(0),
                    description = cursor.getString(1).orEmpty(),
                    dataType = cursor.getString(2).orEmpty()
                )
            }
        }

        return candidates
            .sortedWith(
                compareByDescending<FoodCandidate> {
                    scoreCandidate(it, food, searchQuery)
                }.thenBy {
                    it.description.length
                }.thenBy {
                    it.description
                }
            )
            .firstOrNull()
    }

    private fun scoreCandidate(
        candidate: FoodCandidate,
        food: FoodItem,
        searchQuery: String
    ): Int {
        val description = candidate.description.lowercase()
        val originalQuery = food.name.lowercase().trim()
        val firstPart = description.substringBefore(",").trim()

        var score = 0

        if (firstPart == searchQuery || firstPart == originalQuery) {
            score += 1000
        } else if (firstPart.startsWith("$searchQuery ") || firstPart.startsWith("$originalQuery ")) {
            score += 450
        }

        if (description.contains(searchQuery) || description.contains(originalQuery)) {
            score += 200
        }

        score += when (candidate.dataType.lowercase()) {
            "foundation_food" -> 40
            "sr_legacy_food" -> 30
            "survey_fndds_food" -> 20
            else -> 0
        }

        val compositeTerms = listOf(
            "sandwich", "soup", "casserole",
            "burrito", "pizza", "cake", "roll", "babyfood"
        )

        if (compositeTerms.any(description::contains) && !originalQuery.contains("salad")) {
            score -= 250
        }

        val queryTokens = searchQuery.split(Regex("""[^a-z0-9]+""")).filter { it.isNotBlank() }
        if (queryTokens.isNotEmpty() && queryTokens.all { description.contains(it) }) {
            score += 150
        }

        if (originalQuery.contains("chapati") || originalQuery.contains("roti")) {
            if (description.contains("chapati") || description.contains("roti")) {
                score += 1000
            }
            if (description.contains("rotisserie")) {
                score -= 2000
            }
        }

        if (originalQuery.contains("dal") || originalQuery.contains("daal") || originalQuery.contains("dhal")) {
            if (description.contains("lentil")) {
                score += 800
            }
            if (description.contains("beef") || description.contains("clod")) {
                score -= 2000
            }
        }

        if (originalQuery.contains("paneer")) {
            if (description.contains("paneer")) {
                score += 1000
            }
        }

        if (originalQuery.contains("dosa")) {
            if (description.contains("dosa")) {
                score += 1000
            }
        }

        if (originalQuery.contains("idli")) {
            if (description.contains("idli")) {
                score += 1000
            }
        }

        if (originalQuery.contains("samosa")) {
            if (description.contains("samosa")) {
                score += 1000
            }
        }

        if (originalQuery.contains("biryani")) {
            if (description.contains("biryani")) {
                score += 1000
            }
        }

        if (originalQuery.contains("egg")) {
            if (food.unit.equals("piece", true)) {
                if ("hard-boiled" in description) score += 180
                else if ("boiled" in description) score += 140
                else if ("cooked" in description || "scrambled" in description || "omelet" in description) score += 100
                else if ("raw" in description) score -= 30
            }
        }

        if (originalQuery.contains("rice") || searchQuery.contains("rice")) {
            if ("rice" in description) {
                score += 300
                if ("cooked" in description || "steamed" in description) {
                    score += 600
                } else if ("raw" in description) {
                    score -= 800
                }
            }
        }

        if (originalQuery.contains("toast")) {
            if ("toasted" in description || "toast" in description) {
                score += 180
            }
        }

        if (originalQuery.contains("salad") || searchQuery.contains("salad")) {
            if ("salad" in description && "dressing" !in description && "chicken" !in description && "tuna" !in description && "turkey" !in description && "oil" !in description) {
                score += 800
            }
            if ("greens" in description || "lettuce" in description || "vegetables" in description) {
                score += 400
            }
            if ("dressing" in description || "chicken" in description || "tuna" in description || "turkey" in description || "oil" in description) {
                score -= 800
            }
        }

        if (listOf("banana", "apple", "orange").any { originalQuery.contains(it) }) {
            if ("raw" in description) score += 80
        }

        return score
    }

    private fun resolveGramsPerUnit(
        db: SQLiteDatabase,
        food: FoodItem,
        fdcId: Long
    ): Double? {
        val portions = mutableListOf<PortionCandidate>()

        db.rawQuery(
            """
            SELECT
                fp.amount,
                fp.gram_weight,
                fp.portion_description,
                fp.modifier,
                COALESCE(mu.name, '')
            FROM food_portion fp
            LEFT JOIN measure_unit mu
                ON mu.id = fp.measure_unit_id
            WHERE fp.fdc_id = ?
            """.trimIndent(),
            arrayOf(fdcId.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.isNull(1)) continue

                val gramWeight = cursor.getDouble(1)
                if (gramWeight <= 0.0) continue

                val amount = if (!cursor.isNull(0)) {
                    val rawAmt = cursor.getDouble(0)
                    if (rawAmt > 0.0) rawAmt else 1.0
                } else {
                    1.0
                }

                portions += PortionCandidate(
                    amount = amount,
                    gramWeight = gramWeight,
                    description = cursor.getString(2).orEmpty(),
                    modifier = cursor.getString(3).orEmpty(),
                    unitName = cursor.getString(4).orEmpty()
                )
            }
        }

        val ranked = portions
            .map { portion ->
                portion to scorePortion(food, portion)
            }
            .filter { it.second > 0 }
            .sortedWith(
                compareByDescending<Pair<PortionCandidate, Int>> {
                    it.second
                }.thenBy {
                    abs(it.first.amount - 1.0)
                }

            )

        val best = ranked.firstOrNull()?.first

        if (best == null) {
            val norm = food.name.lowercase().trim()
            val fallbackGrams = when {
                norm.contains("breakfast salad") || norm.contains("sprouts") || norm.contains("protein salad") -> 90.0
                norm.contains("roti") || norm.contains("chapati") -> 40.0
                norm.contains("naan") -> 80.0
                norm.contains("paratha") -> 70.0
                norm.contains("dosa") -> 100.0
                norm.contains("idli") -> 45.0
                norm.contains("samosa") -> 70.0
                norm.contains("gulab jamun") || norm.contains("gulab") || norm.contains("jamun") -> 45.0
                norm.contains("paneer") -> 180.0
                norm.contains("chole") || norm.contains("rajma") || norm.contains("dal") || norm.contains("sabji") || norm.contains("curry") -> 180.0
                food.unit.equals("bowl", true) || food.unit.equals("katori", true) -> 180.0
                food.unit.equals("plate", true) -> 220.0
                else -> 80.0
            }

            Log.i(
                "FRIDAY_USDA",
                "Using fallback portion for '${food.name}', unit='${food.unit}': $fallbackGrams g"
            )
            return fallbackGrams
        }

        val gramsPerUnit = best.gramWeight / best.amount

        Log.i(
            "FRIDAY_USDA",
            "Selected portion for '${food.name}': " +
                    "${best.description}, ${best.modifier}, " +
                    "${best.unitName}; gramsPerUnit=$gramsPerUnit"
        )

        return gramsPerUnit
    }

    private fun scorePortion(
        food: FoodItem,
        portion: PortionCandidate
    ): Int {
        val description = portion.description.lowercase()
        val modifier = portion.modifier.lowercase()
        val unit = portion.unitName.lowercase()
        val combined = "$description $modifier $unit"
        val oneUnit = abs(portion.amount - 1.0) < 0.001
        val normalizedName = food.name.lowercase().trim().trimEnd('s')

        if (food.unit.equals("piece", ignoreCase = true) || food.unit.equals("slice", ignoreCase = true)) {
            if (listOf("roti", "chapati", "naan", "paratha", "puri", "dosa", "idli", "samosa").any { normalizedName.contains(it) }) {
                if ("cup" in combined || "tbsp" in combined) {
                    return 0
                }
                if ("slice" in combined || "piece" in combined || "item" in combined || oneUnit) {
                    return 1000
                }
                return 800
            }

            if (normalizedName.endsWith("egg") || normalizedName.contains("egg")) {
                val gramsPerUnit = portion.gramWeight / portion.amount
                if ("cup" in combined || "tbsp" in combined || gramsPerUnit !in 25.0..90.0) {
                    return 0
                }
                return when {
                    "large" in modifier && oneUnit -> 1300
                    "extra large" in modifier && oneUnit -> 1250
                    "jumbo" in modifier && oneUnit -> 1200
                    "medium" in modifier && oneUnit -> 1150
                    "small" in modifier && oneUnit -> 1100
                    "egg" in combined && oneUnit -> 1050
                    oneUnit -> 1000
                    else -> 0
                }
            }
        }

        if (food.unit.equals("bowl", ignoreCase = true)) {
            if ("cup" in combined || "bowl" in combined || "katori" in combined || oneUnit) {
                return 1000
            }
            return 500
        }

        if (food.unit.equals("plate", ignoreCase = true)) {
            if ("plate" in combined || "serving" in combined || "cup" in combined || oneUnit) {
                return 1000
            }
            return 500
        }

        return when (food.unit.lowercase()) {
            "piece" -> when {
                "medium" in combined && oneUnit -> 950
                "piece" in combined && oneUnit -> 900
                "slice" in combined && oneUnit -> 880
                "fruit" in unit && oneUnit -> 850
                oneUnit -> 300
                else -> 100
            }

            "slice" -> when {
                "slice" in combined && oneUnit -> 1000
                "slice" in combined -> 800
                else -> 100
            }

            "cup" -> when {
                "cup" in unit && oneUnit -> 1000
                "cup" in combined && oneUnit -> 900
                "cup" in combined -> 750
                else -> 100
            }

            else -> 100
        }
    }


    private fun loadNutrients(
        db: SQLiteDatabase,
        fdcId: Long,
        totalGrams: Double
    ): List<NutrientAmount> {
        val nutrients = mutableListOf<NutrientAmount>()

        db.rawQuery(
            """
            SELECT
                n.id,
                n.name,
                n.unit_name,
                fn.amount
            FROM food_nutrient fn
            JOIN nutrient n ON n.id = fn.nutrient_id
            WHERE fn.fdc_id = ?
              AND fn.amount IS NOT NULL
            """.trimIndent(),
            arrayOf(fdcId.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val amountPer100g = cursor.getDouble(3)

                nutrients += NutrientAmount(
                    id = cursor.getInt(0),
                    name = cursor.getString(1).orEmpty(),
                    unit = cursor.getString(2).orEmpty(),
                    amount = amountPer100g * totalGrams / 100.0
                )
            }
        }

        return nutrients
    }
}
