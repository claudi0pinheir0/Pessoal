package br.com.neo.acoes.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

internal val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

internal fun parseJson(text: String): JsonElement = lenientJson.parseToJsonElement(text)

internal operator fun JsonElement?.get(key: String): JsonElement? = (this as? JsonObject)?.get(key)
internal operator fun JsonElement?.get(index: Int): JsonElement? = (this as? JsonArray)?.getOrNull(index)

internal val JsonElement?.array: List<JsonElement?> get() = (this as? JsonArray) ?: emptyList()
internal val JsonElement?.objectMap: Map<String, JsonElement> get() = (this as? JsonObject) ?: emptyMap()

/** Lê um número, aceitando tanto `1.5` quanto o formato do Yahoo `{"raw": 1.5, "fmt": "1,50"}`. */
internal val JsonElement?.double: Double?
    get() = when (this) {
        null, JsonNull -> null
        is JsonPrimitive -> if (isString) content.toDoubleOrNull() else doubleOrNull
        is JsonObject -> this["raw"].double
        else -> null
    }?.takeIf { it.isFinite() }

internal val JsonElement?.long: Long?
    get() = when (this) {
        null, JsonNull -> null
        is JsonPrimitive -> longOrNull ?: doubleOrNull?.toLong()
        is JsonObject -> this["raw"].long
        else -> null
    }

internal val JsonElement?.string: String?
    get() = when (this) {
        is JsonPrimitive -> if (this is JsonNull) null else content.takeIf { it.isNotBlank() }
        is JsonObject -> this["fmt"].string
        else -> null
    }
