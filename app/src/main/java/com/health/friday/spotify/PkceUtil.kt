package com.health.friday.spotify

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** The small cryptographic helpers PKCE login needs. No Android classes, so it is unit-testable. */
object PkceUtil {

    private const val VERIFIER_CHARS =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
    private val random = SecureRandom()

    /** A random secret kept on the phone; Spotify checks it at the end of the login. 43-128 chars. */
    fun newVerifier(length: Int = 64): String {
        require(length in 43..128) { "PKCE verifier length must be 43..128" }
        return buildString(length) {
            repeat(length) { append(VERIFIER_CHARS[random.nextInt(VERIFIER_CHARS.length)]) }
        }
    }

    /** Random value that proves the login response belongs to the login we started. */
    fun newState(): String {
        val bytes = ByteArray(16).also { random.nextBytes(it) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /** SHA-256 of the verifier, URL-safe base64, no padding (RFC 7636). */
    fun challengeFor(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }
}
