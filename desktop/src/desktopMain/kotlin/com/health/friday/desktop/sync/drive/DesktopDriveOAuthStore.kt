package com.health.friday.desktop.sync.drive

import com.health.friday.desktop.sync.cloud.DpapiFileStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal data class DesktopDriveOAuthCredentials(
    val clientId: String,
    val clientSecret: String,
    val refreshToken: String
)

/** Refresh tokens and the installed-app client secret are DPAPI protected. */
internal class DesktopDriveOAuthStore(
    private val secrets: DpapiFileStore = DpapiFileStore(
        DpapiFileStore.appDataFile("google-drive-oauth.dpapi")
    )
) {
    private val json = Json { ignoreUnknownKeys = false }

    fun save(credentials: DesktopDriveOAuthCredentials) {
        require(credentials.clientId.isNotBlank())
        require(credentials.refreshToken.isNotBlank())
        val plain = json.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(),
            buildJsonObject {
                put("clientId", credentials.clientId)
                put("clientSecret", credentials.clientSecret)
                put("refreshToken", credentials.refreshToken)
            }
        ).toByteArray(Charsets.UTF_8)
        try {
            secrets.write(plain)
        } finally {
            plain.fill(0)
        }
    }

    fun load(): DesktopDriveOAuthCredentials? {
        val plain = secrets.read() ?: return null
        try {
            val obj = json.parseToJsonElement(String(plain, Charsets.UTF_8)).jsonObject
            return DesktopDriveOAuthCredentials(
                clientId = obj["clientId"]?.jsonPrimitive?.content
                    ?: error("Stored Google OAuth client ID is missing."),
                clientSecret = obj["clientSecret"]?.jsonPrimitive?.content.orEmpty(),
                refreshToken = obj["refreshToken"]?.jsonPrimitive?.content
                    ?: error("Stored Google OAuth refresh token is missing.")
            ).also {
                require(it.clientId.isNotBlank() && it.refreshToken.isNotBlank()) {
                    "Stored Google OAuth credentials are incomplete."
                }
            }
        } finally {
            plain.fill(0)
        }
    }

    fun clear() = secrets.delete()
}
