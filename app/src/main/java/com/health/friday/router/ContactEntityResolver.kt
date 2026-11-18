package com.health.friday.router

import android.content.Context
import android.provider.ContactsContract
import kotlin.math.max
import kotlin.math.min

data class ResolvedContact(
    val displayName: String,
    val rawNumber: String,
    val whatsappDigitsOnly: String,
    val similarityScore: Double
)

class ContactEntityResolver(private val context: Context) {

    /**
     * Resolves a spoken contact name against Android's ContactsContract using
     * Jaro-Winkler string similarity.
     */
    fun resolveBestContact(spokenName: String, defaultCountryCode: String = "91"): ResolvedContact? {
        val query = spokenName.trim().lowercase()
        if (query.isBlank()) return null

        val candidates = mutableListOf<ResolvedContact>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIdx)?.trim() ?: continue
                val rawNum = cursor.getString(numIdx)?.trim() ?: continue

                val fullScore = jaroWinklerSimilarity(query, name.lowercase())
                val firstTokenScore = jaroWinklerSimilarity(
                    query,
                    name.substringBefore(" ").lowercase()
                )
                val bestScore = max(fullScore, firstTokenScore)

                if (bestScore >= 0.78) {
                    val digits = normalizeToWhatsappDigits(rawNum, defaultCountryCode)
                    candidates.add(
                        ResolvedContact(
                            displayName = name,
                            rawNumber = rawNum,
                            whatsappDigitsOnly = digits,
                            similarityScore = bestScore
                        )
                    )
                }
            }
        }

        return candidates.maxByOrNull { it.similarityScore }
    }

    private fun normalizeToWhatsappDigits(rawPhone: String, defaultCountryCode: String): String {
        val digitsOnly = rawPhone.filter { it.isDigit() }
        return when {
            digitsOnly.length == 10 -> "$defaultCountryCode$digitsOnly"
            digitsOnly.startsWith("0") && digitsOnly.length == 11 -> "$defaultCountryCode${digitsOnly.drop(1)}"
            else -> digitsOnly
        }
    }

    /**
     * Computes Jaro-Winkler similarity in [0.0, 1.0].
     */
    private fun jaroWinklerSimilarity(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        val len1 = s1.length
        val len2 = s2.length
        if (len1 == 0 || len2 == 0) return 0.0

        val matchDistance = (max(len1, len2) / 2) - 1
        val s1Matches = BooleanArray(len1)
        val s2Matches = BooleanArray(len2)

        var matches = 0
        for (i in 0 until len1) {
            val start = max(0, i - matchDistance)
            val end = min(i + matchDistance + 1, len2)
            for (j in start until end) {
                if (s2Matches[j]) continue
                if (s1[i] != s2[j]) continue
                s1Matches[i] = true
                s2Matches[j] = true
                matches++
                break
            }
        }

        if (matches == 0) return 0.0

        var transpositions = 0
        var k = 0
        for (i in 0 until len1) {
            if (!s1Matches[i]) continue
            while (!s2Matches[k]) k++
            if (s1[i] != s2[k]) transpositions++
            k++
        }

        val m = matches.toDouble()
        val jaro = ((m / len1) + (m / len2) + ((m - transpositions / 2.0) / m)) / 3.0

        // Winkler prefix bonus (up to 4 chars)
        var prefix = 0
        val maxPrefix = min(4, min(len1, len2))
        while (prefix < maxPrefix && s1[prefix] == s2[prefix]) {
            prefix++
        }

        return jaro + (prefix * 0.1 * (1.0 - jaro))
    }
}
