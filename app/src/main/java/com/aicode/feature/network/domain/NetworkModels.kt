package com.aicode.feature.network.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private val compactJson = Json { ignoreUnknownKeys = true }
private val prettyJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
}

data class NetworkHeader(
    val name: String,
    val value: String
)

enum class HttpMethod {
    GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS
}

enum class NetworkPane {
    REQUEST, RESPONSE
}

enum class ResponseTab {
    HEADERS, BODY, METRICS
}

enum class ResponseViewMode {
    TABLE, JSON
}

data class NetworkMetrics(
    val requestId: String = "",
    val method: String = "",
    val url: String = "",
    val statusCode: Int = 0,
    val statusText: String = "",
    val protocol: String = "",
    val remoteAddress: String = "",
    val durationMs: Long = 0,
    val dnsMs: Long = -1,
    val connectMs: Long = -1,
    val tlsMs: Long = -1,
    val requestBytes: Long = 0,
    val responseBytes: Long = 0,
    val contentType: String = ""
)

data class NetworkExchange(
    val headers: List<NetworkHeader> = emptyList(),
    val body: String = "",
    val metrics: NetworkMetrics = NetworkMetrics()
)

sealed interface NetworkSendState {
    data object Idle : NetworkSendState
    data object Sending : NetworkSendState
    data class Success(val exchange: NetworkExchange) : NetworkSendState
    data class Failure(val message: String, val exchange: NetworkExchange? = null) : NetworkSendState
}

fun defaultViewMode(tab: ResponseTab): ResponseViewMode? = when (tab) {
    ResponseTab.HEADERS -> ResponseViewMode.TABLE
    ResponseTab.BODY -> ResponseViewMode.JSON
    ResponseTab.METRICS -> null
}

fun normalizeRequestUrl(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return trimmed
    val lower = trimmed.lowercase()
    return when {
        lower.startsWith("http://") -> "http://" + trimmed.substring("http://".length)
        lower.startsWith("https://") -> "https://" + trimmed.substring("https://".length)
        else -> "https://$trimmed"
    }
}

fun prettyPrintJson(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return raw
    return try {
        val element = compactJson.parseToJsonElement(trimmed)
        prettyJson.encodeToString(JsonElement.serializer(), element)
    } catch (_: Exception) {
        raw
    }
}

fun headersToJson(headers: List<NetworkHeader>): String {
    val map = linkedMapOf<String, JsonElement>()
    headers.forEach { header ->
        val value = JsonPrimitive(header.value)
        when (val existing = map[header.name]) {
            null -> map[header.name] = value
            is JsonArray -> map[header.name] = JsonArray(existing + value)
            else -> map[header.name] = JsonArray(listOf(existing, value))
        }
    }
    return prettyJson.encodeToString(JsonObject.serializer(), JsonObject(map))
}

fun bodyToTableRows(body: String): List<NetworkHeader> {
    val trimmed = body.trim()
    if (trimmed.isEmpty()) return emptyList()
    return try {
        when (val element = compactJson.parseToJsonElement(trimmed)) {
            is JsonObject -> element.entries.map { (key, value) ->
                NetworkHeader(key, jsonElementText(value))
            }
            is JsonArray -> element.mapIndexed { index, value ->
                NetworkHeader("[$index]", jsonElementText(value))
            }
            else -> listOf(NetworkHeader("body", body))
        }
    } catch (_: Exception) {
        listOf(NetworkHeader("body", body))
    }
}

private fun jsonElementText(value: JsonElement): String = when (value) {
    is JsonPrimitive -> value.content
    else -> prettyJson.encodeToString(JsonElement.serializer(), value)
}
