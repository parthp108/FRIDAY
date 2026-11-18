package com.health.friday.shared.protocol

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

sealed interface Decoded {
    data class Ok(val envelope: Envelope) : Decoded
    data class VersionMismatch(val theirVersion: Int?, val id: String?) : Decoded
    data class Unsupported(val id: String?, val payloadType: String) : Decoded
    data class Malformed(val reason: String) : Decoded
}

@OptIn(ExperimentalSerializationApi::class)
object ProtocolCodec {

    val json: Json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true   // newer peers may add fields
        encodeDefaults = true      // "v" must always be on the wire
        explicitNulls = false      // unknown stays unknown: nulls are omitted
    }

    /** One message = one line (JSON escapes newlines inside strings). */
    fun encode(envelope: Envelope): String =
        json.encodeToString(Envelope.serializer(), envelope)

    fun decode(line: String): Decoded {
        val element = try {
            json.parseToJsonElement(line)
        } catch (_: Exception) {
            return Decoded.Malformed("not valid JSON")
        }
        val obj = element as? JsonObject ?: return Decoded.Malformed("not a JSON object")

        val id = (obj["id"] as? JsonPrimitive)?.contentOrNull
        val version = (obj["v"] as? JsonPrimitive)?.intOrNull
        if (version != PROTOCOL_VERSION) return Decoded.VersionMismatch(version, id)

        val payloadType = ((obj["payload"] as? JsonObject)?.get("type") as? JsonPrimitive)?.contentOrNull
            ?: return Decoded.Malformed("payload has no type")
        if (payloadType !in Payloads.KNOWN_TYPES) return Decoded.Unsupported(id, payloadType)

        return try {
            Decoded.Ok(json.decodeFromJsonElement(Envelope.serializer(), obj))
        } catch (e: Exception) {
            Decoded.Malformed("invalid $payloadType: ${e.message?.take(120)}")
        }
    }
}
