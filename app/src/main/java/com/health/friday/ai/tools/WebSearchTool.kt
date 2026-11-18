package com.health.friday.ai.tools

import com.health.friday.ai.AiTool
import com.health.friday.ai.AiToolParameter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class WebSearchTool : AiTool {

    override val name: String = "web_search"

    override val description: String =
        "Search the live internet for current weather/temperature, movie/game release dates, live news, people, or scientific/world knowledge."

    override val parameters: List<AiToolParameter> = listOf(
        AiToolParameter(
            name = "query",
            description = "The search query (e.g., 'current temperature in USA', 'Avengers Doomsday release date', 'SIMPLER algorithm fluid dynamics')",
            required = true
        )
    )

    override suspend fun execute(arguments: Map<String, String>): String = withContext(Dispatchers.IO) {
        val rawQuery = arguments["query"]?.trim().orEmpty()
        if (rawQuery.isBlank()) return@withContext "Error: Missing search query."

        val results = mutableListOf<String>()

        // 1. Check if it's a weather/temperature query -> Hit live Open-Meteo API
        if (rawQuery.contains("temp", ignoreCase = true) ||
            rawQuery.contains("weather", ignoreCase = true)
        ) {
            fetchLiveWeather(rawQuery)?.let { results.add(it) }
        }

        // 2. Fetch live News / Web snippets (great for release dates, current events, live facts)
        fetchLiveWebNews(rawQuery)?.let { results.add(it) }

        // 3. Fetch Wikipedia/Encyclopedic summary (great for science, MCU lore, history)
        fetchWikipediaSummary(rawQuery)?.let { results.add(it) }

        if (results.isEmpty()) {
            "No live internet results found for '$rawQuery'. Answer using your pre-trained world knowledge."
        } else {
            results.joinToString("\n\n")
        }
    }

    private fun fetchLiveWeather(query: String): String? {
        return try {
            // Strip weather words to isolate the location name
            val locationQuery = query.lowercase()
                .replace(Regex("(what|is|the|current|weather|temperature|temp|in|at|right|now|today|\\?)"), " ")
                .trim()
                .ifBlank { "New York" }
                .let { if (it == "america" || it == "usa" || it == "us") "Washington" else it }

            val encodedLoc = URLEncoder.encode(locationQuery, "UTF-8")
            val geoJson = httpGet("https://geocoding-api.open-meteo.com/v1/search?name=$encodedLoc&count=1&language=en&format=json")
            val geoResults = JSONObject(geoJson).optJSONArray("results") ?: return null
            val place = geoResults.optJSONObject(0) ?: return null

            val lat = place.getDouble("latitude")
            val lon = place.getDouble("longitude")
            val name = place.optString("name")
            val country = place.optString("country")

            val weatherJson = httpGet(
                "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m,apparent_temperature,wind_speed_10m"
            )
            val current = JSONObject(weatherJson).getJSONObject("current")
            val tempC = current.getDouble("temperature_2m")
            val feelsC = current.getDouble("apparent_temperature")
            val humidity = current.getInt("relative_humidity_2m")
            val wind = current.getDouble("wind_speed_10m")

            "[LIVE WEATHER DATA for $name, $country]: Temperature is ${tempC}°C (Feels like ${feelsC}°C), Humidity: $humidity%, Wind: $wind km/h."
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchLiveWebNews(query: String): String? {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val xml = httpGet("https://news.google.com/rss/search?q=$encoded&hl=en-US&gl=US&ceid=US:en")
            val items = Regex("<item>.*?</item>", RegexOption.DOT_MATCHES_ALL)
                .findAll(xml)
                .take(4)
                .mapNotNull { match ->
                    val block = match.value
                    val title = Regex("<title>(.*?)</title>").find(block)?.groupValues?.get(1)
                        ?.replace("&amp;", "&")?.replace("&quot;", "\"")
                    val pubDate = Regex("<pubDate>(.*?)</pubDate>").find(block)?.groupValues?.get(1)
                    if (title != null) "- $title ($pubDate)" else null
                }.toList()

            if (items.isNotEmpty()) {
                "[LIVE WEB RESULTS]:\n" + items.joinToString("\n")
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchWikipediaSummary(query: String): String? {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val searchJson = httpGet("https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encoded&utf8=&format=json&srlimit=2")
            val searchArray = JSONObject(searchJson)
                .optJSONObject("query")
                ?.optJSONArray("search") ?: JSONArray()

            if (searchArray.length() == 0) return null

            val snippets = mutableListOf<String>()
            for (i in 0 until searchArray.length()) {
                val item = searchArray.getJSONObject(i)
                val title = item.getString("title")
                val cleanSnippet = item.getString("snippet")
                    .replace(Regex("<.*?>"), "")
                    .replace("&quot;", "\"")
                    .replace("&amp;", "&")
                snippets.add("- $title: $cleanSnippet")
            }
            "[ENCYCLOPEDIA KNOWLEDGE]:\n" + snippets.joinToString("\n")
        } catch (_: Exception) {
            null
        }
    }

    private fun httpGet(urlStr: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 5000
            setRequestProperty("User-Agent", "FRIDAY-Android-OS/3.0")
        }
        return conn.inputStream.bufferedReader().use { it.readText() }
    }
}
