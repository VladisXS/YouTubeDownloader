package com.vladisx.youtubedownloader.potoken

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.util.Base64

/**
 * Conversions between the wire formats YouTube's BotGuard endpoints speak and
 * the JavaScript snippets that run inside the hidden WebView.
 */
object JavascriptUtil {

    /**
     * Parses the raw challenge from `/api/jnn/v1/Create` into a JSON object that
     * can be embedded directly into a JavaScript snippet.
     */
    fun parseChallengeData(rawChallengeData: String): String {
        val scrambled = Json.parseToJsonElement(rawChallengeData).jsonArray

        // Newer responses wrap the real payload in a base64 blob shifted by 97.
        val challengeData = if (scrambled.size > 1 && scrambled[1].jsonPrimitive.isString) {
            val descrambled = descramble(scrambled[1].jsonPrimitive.content)
            Json.parseToJsonElement(descrambled).jsonArray
        } else {
            scrambled[1].jsonArray
        }

        val messageId = challengeData[0].jsonPrimitive.content
        val interpreterHash = challengeData[3].jsonPrimitive.content
        val program = challengeData[4].jsonPrimitive.content
        val globalName = challengeData[5].jsonPrimitive.content
        val clientExperimentsStateBlob = challengeData[7].jsonPrimitive.content

        val interpreterScript = challengeData[1]
            .takeIf { it !is JsonNull }
            ?.jsonArray
            ?.find { it.jsonPrimitive.isString }
        val trustedResourceUrl = challengeData[2]
            .takeIf { it !is JsonNull }
            ?.jsonArray
            ?.find { it.jsonPrimitive.isString }

        return Json.encodeToString(
            JsonObject.serializer(),
            JsonObject(
                mapOf(
                    "messageId" to JsonPrimitive(messageId),
                    "interpreterJavascript" to JsonObject(
                        mapOf(
                            "privateDoNotAccessOrElseSafeScriptWrappedValue" to
                                (interpreterScript ?: JsonNull),
                            "privateDoNotAccessOrElseTrustedResourceUrlWrappedValue" to
                                (trustedResourceUrl ?: JsonNull),
                        )
                    ),
                    "interpreterHash" to JsonPrimitive(interpreterHash),
                    "program" to JsonPrimitive(program),
                    "globalName" to JsonPrimitive(globalName),
                    "clientExperimentsStateBlob" to JsonPrimitive(clientExperimentsStateBlob),
                )
            )
        )
    }

    /**
     * Parses `/api/jnn/v1/GenerateIT` into a JavaScript `Uint8Array` literal plus
     * the token lifetime in seconds.
     */
    fun parseIntegrityTokenData(raw: String): Pair<String, Long> {
        val data = Json.parseToJsonElement(raw).jsonArray
        val token = base64ToU8(data[0].jsonPrimitive.content)
        val ttl = data[1].jsonPrimitive.long
        return token to ttl
    }

    /** Turns the content binding / video id into a JS `Uint8Array` literal. */
    fun stringToU8(value: String): String = newUint8Array(value.toByteArray(Charsets.UTF_8))

    /**
     * The WebView returns the token as comma separated byte values; convert that
     * back into the URL-safe base64 form yt-dlp expects.
     */
    fun u8ToBase64(poToken: String): String {
        val bytes = poToken.split(",")
            .map { it.trim().toInt().toByte() }
            .toByteArray()
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun descramble(scrambled: String): String =
        base64ToBytes(scrambled)
            .map { (it + 97).toByte() }
            .toByteArray()
            .toString(Charsets.UTF_8)

    private fun base64ToU8(base64: String): String = newUint8Array(base64ToBytes(base64))

    private fun newUint8Array(contents: ByteArray): String =
        "new Uint8Array([" + contents.joinToString(",") { it.toUByte().toString() } + "])"

    private fun base64ToBytes(base64: String): ByteArray {
        val normalized = base64
            .replace('-', '+')
            .replace('_', '/')
            .replace('.', '=')
        return Base64.getDecoder().decode(normalized)
    }
}
