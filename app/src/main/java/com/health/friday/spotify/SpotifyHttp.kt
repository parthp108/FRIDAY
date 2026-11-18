package com.health.friday.spotify

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class SpotifyHttpResult(val code: Int, val body: String) {
    val isSuccess: Boolean get() = code in 200..299
}

/** Tiny HTTP helper shared by login now and search/playback in the next step. Call off the main thread. */
internal object SpotifyHttp {

    fun request(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null
    ): SpotifyHttpResult {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return SpotifyHttpResult(code, text)
        } finally {
            connection.disconnect()
        }
    }

    fun postForm(url: String, params: Map<String, String>): SpotifyHttpResult {
        val body = params.entries.joinToString("&") { (key, value) ->
            URLEncoder.encode(key, "UTF-8") + "=" + URLEncoder.encode(value, "UTF-8")
        }
        return request(
            "POST",
            url,
            mapOf("Content-Type" to "application/x-www-form-urlencoded"),
            body
        )
    }
}
