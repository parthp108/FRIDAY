package com.health.friday.shared.tools

import com.health.friday.shared.ai.SharedTool
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLDecoder
import java.net.URLEncoder

data class SearchResultItem(
    val title: String,
    val snippet: String,
    val url: String
)

@Serializable
private data class WikiSearchItem(
    val title: String = "",
    val snippet: String = ""
)

@Serializable
private data class WikiQuery(
    val search: List<WikiSearchItem> = emptyList()
)

@Serializable
private data class WikiResponse(
    val query: WikiQuery = WikiQuery()
)

class WebSearchTool : SharedTool {
    override val name: String = "web_search"
    override val description: String = "Searches the web for live up-to-date information, news, weather, movies, or technical documentation."

    private val client = HttpClient(CIO) {
        engine {
            requestTimeout = 15_000
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun execute(params: Map<String, String>): String {
        val query = params["query"] ?: params["q"] ?: return "Error: No search query provided."
        val results = search(query, maxResults = 5)
        if (results.isEmpty()) {
            return "No web results found for '$query'."
        }

        val sb = StringBuilder()
        sb.append("Web Search Results for '$query':\n\n")
        results.forEachIndexed { index, item ->
            sb.append("${index + 1}. [${item.title}](${item.url})\n")
            sb.append("   Snippet: ${item.snippet}\n\n")
        }
        return sb.toString().trim()
    }

    suspend fun search(query: String, maxResults: Int = 5): List<SearchResultItem> {
        val results = mutableListOf<SearchResultItem>()

        // 1. DuckDuckGo HTML Live Web Search (Primary)
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val htmlResp: HttpResponse = client.post("https://html.duckduckgo.com/html/") {
                header(HttpHeaders.UserAgent, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                contentType(ContentType.Application.FormUrlEncoded)
                setBody("q=$encodedQuery")
            }
            if (htmlResp.status == HttpStatusCode.OK) {
                val htmlResults = parseDuckDuckGoHtml(htmlResp.bodyAsText(), maxResults)
                results.addAll(htmlResults)
            }
        } catch (e: Exception) {
            System.err.println("[WebSearchTool] DDG HTML error: ${e.message}")
        }

        // 2. Wikipedia Search API (Secondary Fallback if DDG HTML returns < maxResults)
        if (results.size < maxResults) {
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val wikiResp: HttpResponse = client.get("https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encodedQuery&format=json")
                if (wikiResp.status == HttpStatusCode.OK) {
                    val parsed = json.decodeFromString(WikiResponse.serializer(), wikiResp.bodyAsText())
                    for (item in parsed.query.search.take(maxResults - results.size)) {
                        val cleanSnippet = stripHtmlTags(item.snippet)
                        if (cleanSnippet.isNotBlank() && results.none { it.title.equals(item.title, ignoreCase = true) }) {
                            val wikiUrl = "https://en.wikipedia.org/wiki/" + URLEncoder.encode(item.title.replace(" ", "_"), "UTF-8")
                            results.add(SearchResultItem(item.title, cleanSnippet, wikiUrl))
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        return results.take(maxResults)
    }

    private fun parseDuckDuckGoHtml(html: String, maxResults: Int): List<SearchResultItem> {
        val list = mutableListOf<SearchResultItem>()
        val linkTitleRegex = Regex("""<a class="result__url"[^>]*href="([^"]+)"[^>]*>([\s\S]*?)</a>|<a class="result__a"[^>]*href="([^"]+)"[^>]*>([\s\S]*?)</a>""", RegexOption.IGNORE_CASE)

        val matches = linkTitleRegex.findAll(html).toList()
        for (match in matches) {
            if (list.size >= maxResults) break

            val rawUrl = match.groupValues[1].ifEmpty { match.groupValues[3] }
            val rawTitleHtml = match.groupValues[2].ifEmpty { match.groupValues[4] }

            if (rawUrl.isBlank()) continue

            var cleanUrl = rawUrl
            val uddgMatch = Regex("""uddg=([^&]+)""").find(rawUrl)
            if (uddgMatch != null) {
                runCatching {
                    cleanUrl = URLDecoder.decode(uddgMatch.groupValues[1], "UTF-8")
                }
            }

            if (!cleanUrl.startsWith("http")) continue

            val cleanTitle = stripHtmlTags(rawTitleHtml).trim()
            if (cleanTitle.isBlank()) continue

            val titleIndex = html.indexOf(rawTitleHtml)
            var snippet = "Live web reference."
            if (titleIndex != -1) {
                val sub = html.substring(titleIndex, (titleIndex + 1200).coerceAtMost(html.length))
                val snippetMatch = Regex("""class="result__snippet"[^>]*>([\s\S]*?)</span>|class="result__snippet"[^>]*>([\s\S]*?)</td>""", RegexOption.IGNORE_CASE).find(sub)
                if (snippetMatch != null) {
                    val rawSnippet = snippetMatch.groupValues[1].ifEmpty { snippetMatch.groupValues[2] }
                    snippet = stripHtmlTags(rawSnippet).replace("\n", " ").trim()
                }
            }

            list.add(SearchResultItem(cleanTitle, snippet, cleanUrl))
        }

        return list
    }

    private fun stripHtmlTags(html: String): String {
        return html
            .replace(Regex("""<[^>]*>"""), "")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .replace(Regex("""\s+"""), " ")
    }
}
