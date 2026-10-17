package com.karim.foodrun.server

import com.karim.foodrun.orders.orderJson
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64
import kotlinx.serialization.json.*

/** SQL over HTTP; tokens, SQL arguments and provider error bodies are never logged. */
internal class TursoSql(url: String, private val token: String, allowLocalhost: Boolean = false) {
    private val endpoint = endpoint(url, allowLocalhost)
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER).build()

    init { require(token.isNotBlank() && token.none { it == '\r' || it == '\n' }) { "Turso database token is required." } }

    data class Statement(val sql: String, val args: List<Any> = emptyList())

    fun execute(sql: String, vararg args: Any): JsonObject = request(buildJsonObject {
        put("type", "execute"); put("stmt", statement(Statement(sql, args.toList())))
    })["result"]!!.jsonObject

    /** A failed step skips every later mutation and COMMIT; rollback runs on failure. */
    fun transaction(statements: List<Statement>): List<JsonObject> {
        val sql = listOf(Statement("BEGIN IMMEDIATE")) + statements + Statement("COMMIT")
        val commit = sql.lastIndex
        val steps = sql.mapIndexed { index, value -> buildJsonObject {
            if (index > 0) put("condition", ok(index - 1))
            put("stmt", statement(value))
        } } + buildJsonObject {
            put("condition", buildJsonObject { put("type", "not"); put("cond", ok(commit)) })
            put("stmt", statement(Statement("ROLLBACK")))
        }
        val result = request(buildJsonObject {
            put("type", "batch"); put("batch", buildJsonObject { put("steps", JsonArray(steps)) })
        })["result"]!!.jsonObject
        val values = result["step_results"]!!.jsonArray
        val errors = result["step_errors"]!!.jsonArray
        if (values.size != steps.size || errors.size != steps.size || (0..commit).any { values[it] == JsonNull || errors[it] != JsonNull })
            throw StorageUnavailable("Turso could not commit the complete change.")
        return values.subList(1, commit).map { it.jsonObject }
    }

    private fun request(operation: JsonObject): JsonObject {
        val body = buildJsonObject {
            put("requests", JsonArray(listOf(operation, buildJsonObject { put("type", "close") })))
        }.toString()
        if (body.toByteArray().size > MAX_BYTES) throw StorageUnavailable("This change exceeds the atomic storage limit.")
        val request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(20))
            .header("Authorization", "Bearer $token").header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build()
        val response = try { http.send(request, HttpResponse.BodyHandlers.ofInputStream()) }
            catch (_: Exception) { throw StorageUnavailable("Turso storage could not be reached.") }
        response.body().use { stream ->
            if (response.statusCode() != 200) throw StorageUnavailable("Turso storage request failed (${response.statusCode()}).")
            val bytes = stream.readNBytes(MAX_BYTES + 1)
            if (bytes.size > MAX_BYTES) throw StorageUnavailable("Turso storage response exceeds its size limit.")
            val results = orderJson.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject["results"]!!.jsonArray
            if (results.size != 2 || results.any { it.jsonObject["type"]?.jsonPrimitive?.content != "ok" })
                throw StorageUnavailable("Turso storage rejected the request.")
            val value = results.first().jsonObject["response"]!!.jsonObject
            if (value["type"] != operation["type"]) throw StorageUnavailable("Invalid Turso storage response.")
            return value
        }
    }

    companion object {
        private const val MAX_BYTES = 8 * 1024 * 1024
        fun endpoint(url: String, allowLocalhost: Boolean = false): URI {
            val uri = try { URI(url.trim().replaceFirst(Regex("^(libsql|turso)://"), "https://")) }
                catch (_: Exception) { throw IllegalArgumentException("Invalid Turso database URL.") }
            require(uri.userInfo == null && uri.query == null && uri.fragment == null && uri.path.orEmpty() in listOf("", "/") &&
                ((uri.scheme == "https" && uri.host?.endsWith(".turso.io") == true && uri.port == -1) ||
                    (allowLocalhost && uri.scheme == "http" && uri.host in listOf("127.0.0.1", "localhost") && uri.port > 0))) {
                "Use the Turso database URL, rather than the account dashboard URL."
            }
            return uri.resolve("/v2/pipeline")
        }
        private fun ok(index: Int) = buildJsonObject { put("type", "ok"); put("step", index) }
        private fun statement(value: Statement) = buildJsonObject {
            put("sql", value.sql)
            put("args", JsonArray(value.args.map { argument -> buildJsonObject {
                when (argument) {
                    is ByteArray -> { put("type", "blob"); put("base64", Base64.getEncoder().encodeToString(argument)) }
                    is Number -> { put("type", "integer"); put("value", argument.toString()) }
                    is String -> { put("type", "text"); put("value", argument) }
                    else -> error("Unsupported SQL argument")
                }
            } }))
        }
    }
}

internal fun JsonObject.sqlRows(): List<List<JsonObject>> = get("rows")!!.jsonArray.map { row -> row.jsonArray.map { it.jsonObject } }
internal fun JsonObject.sqlText(): String = get("value")!!.jsonPrimitive.content
