package ca.adamhammer.babelfit.samples.customersupport.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val json = Json { ignoreUnknownKeys = true }

fun parseArgs(arguments: String): Map<String, String> {
    return try {
        val obj = json.parseToJsonElement(arguments).jsonObject
        obj.mapValues { it.value.jsonPrimitive.content }
    } catch (_: Exception) {
        emptyMap()
    }
}
