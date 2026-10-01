package com.health.friday.nutrition

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

class OpenFoodFactsProvider : NutritionProvider {

    override fun getNutrition(
        food: FoodItem
    ): NutritionResult? {

        return try {
            val results = searchFood(food.name)

            if (results.isEmpty()) {
                return null
            }

            val result = results.first()

            val multiplier =
                food.quantity

            NutritionResult(
                calories =
                    result.caloriesPer100g * multiplier,
                protein =
                    result.proteinPer100g * multiplier,
                carbohydrates =
                    result.carbohydratesPer100g * multiplier,
                fat =
                    result.fatPer100g * multiplier
            )

        } catch (
            exception: Exception
        ) {
            null
        }
    }

    fun searchFood(
        query: String
    ): List<FoodSearchResult> {

        return try {

            val encodedQuery =
                URLEncoder.encode(
                    query,
                    "UTF-8"
                )

            val url = URL(
                "https://world.openfoodfacts.org/cgi/search.pl" +
                        "?search_terms=$encodedQuery" +
                        "&search_simple=1" +
                        "&action=process" +
                        "&json=1" +
                        "&page_size=10"
            )

            val connection =
                url.openConnection()
                        as HttpURLConnection

            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000

            connection.setRequestProperty(
                "User-Agent",
                "FRIDAY-Health/1.0"
            )

            if (
                connection.responseCode !=
                HttpURLConnection.HTTP_OK
            ) {
                connection.disconnect()
                return emptyList()
            }

            val response =
                connection.inputStream
                    .bufferedReader()
                    .use {
                        it.readText()
                    }

            connection.disconnect()

            val root =
                JSONObject(response)

            val products =
                root.optJSONArray(
                    "products"
                )
                    ?: return emptyList()

            val results =
                mutableListOf<FoodSearchResult>()

            for (
            index in 0 until products.length()
            ) {

                val product =
                    products.optJSONObject(index)
                        ?: continue

                val name =
                    product.optString(
                        "product_name",
                        ""
                    )

                if (name.isBlank()) {
                    continue
                }

                val brand =
                    product.optString(
                        "brands",
                        ""
                    )

                val nutriments =
                    product.optJSONObject(
                        "nutriments"
                    )
                        ?: continue

                val calories =
                    nutriments.optDouble(
                        "energy-kcal_100g",
                        Double.NaN
                    )

                val protein =
                    nutriments.optDouble(
                        "proteins_100g",
                        Double.NaN
                    )

                val carbohydrates =
                    nutriments.optDouble(
                        "carbohydrates_100g",
                        Double.NaN
                    )

                val fat =
                    nutriments.optDouble(
                        "fat_100g",
                        Double.NaN
                    )

                if (
                    calories.isNaN() ||
                    protein.isNaN() ||
                    carbohydrates.isNaN() ||
                    fat.isNaN()
                ) {
                    continue
                }

                results.add(
                    FoodSearchResult(
                        name = name,
                        brand = brand,
                        caloriesPer100g =
                            calories,
                        proteinPer100g =
                            protein,
                        carbohydratesPer100g =
                            carbohydrates,
                        fatPer100g =
                            fat
                    )
                )
            }

            results

        } catch (
            exception: Exception
        ) {
            emptyList()
        }
    }
}