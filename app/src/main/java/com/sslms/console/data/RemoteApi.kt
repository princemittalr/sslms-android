package com.sslms.console.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

val SslmsJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
    encodeDefaults = true
}

/**
 * Talks to the real Spring Boot backend.
 * baseUrl example: http://192.168.1.20:8080/api/sslms
 */
class RemoteApi(
    private val baseUrl: String,
    private val token: () -> String?,
    private val onUnauthorized: () -> Unit,
) : SslmsApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private fun url(path: String, query: Map<String, Any?> = emptyMap()): HttpUrl {
        val base = baseUrl.trimEnd('/').toHttpUrlOrNull()
            ?: throw ApiException("The server address \"$baseUrl\" isn't a valid URL. Use a form like http://192.168.1.20:8080/api/sslms")
        val b = base.newBuilder()
        path.trim('/').split('/').filter { it.isNotEmpty() }.forEach { b.addPathSegment(it) }
        query.forEach { (k, v) -> if (v != null && v.toString().isNotEmpty()) b.addQueryParameter(k, v.toString()) }
        return b.build()
    }

    /** Returns the parsed JSON, or a JsonPrimitive string for text responses. */
    private suspend fun call(
        method: String,
        path: String,
        query: Map<String, Any?> = emptyMap(),
        body: JsonElement? = null,
        auth: Boolean = true,
    ): JsonElement = withContext(Dispatchers.IO) {
        val reqBody: RequestBody? = when {
            body != null -> SslmsJson.encodeToString(JsonElement.serializer(), body).toRequestBody(jsonType)
            method in setOf("POST", "PUT", "PATCH") -> "".toRequestBody(jsonType)
            else -> null
        }
        val req = Request.Builder()
            .url(url(path, query))
            .method(method, reqBody)
            .header("Accept", "application/json, text/plain, */*")
            .apply { if (auth) token()?.let { header("Authorization", "Bearer $it") } }
            .build()

        val res = try {
            client.newCall(req).execute()
        } catch (e: IOException) {
            throw ApiException("Can't reach the server at $baseUrl. Check that the backend is running, the address is right, and the phone is on the same network.")
        }
        res.use { r ->
            val raw = r.body.string()
            val parsed: JsonElement = if (raw.isBlank()) JsonPrimitive("") else {
                val t = raw.trim()
                if (t.startsWith("{") || t.startsWith("[")) runCatching { SslmsJson.parseToJsonElement(t) }.getOrElse { JsonPrimitive(raw) }
                else JsonPrimitive(raw)
            }
            if (!r.isSuccessful) {
                if ((r.code == 401 || r.code == 403) && auth) onUnauthorized()
                val msg = when (parsed) {
                    is JsonObject -> (parsed["message"] ?: parsed["error"])?.jsonPrimitive?.contentOrNull
                    is JsonPrimitive -> parsed.contentOrNull?.takeIf { it.isNotBlank() }
                    else -> null
                } ?: "Server returned ${r.code}"
                throw ApiException(msg, r.code)
            }
            parsed
        }
    }

    private fun <T> JsonElement.decode(s: KSerializer<T>): T = SslmsJson.decodeFromJsonElement(s, this)
    private fun <T> JsonElement.list(s: KSerializer<T>): List<T> = when (this) {
        is JsonArray -> decode(ListSerializer(s))
        is JsonObject -> (this["content"] as? JsonArray)?.decode(ListSerializer(s)) ?: emptyList()
        else -> emptyList()
    }
    private fun JsonElement.text(fallback: String) = (this as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: fallback
    private fun <T> JsonElement.page(s: KSerializer<T>): PageResult<T> {
        if (this is JsonArray) { val rows = list(s); return PageResult(rows, rows.size.toLong(), 1, 0) }
        val o = this as? JsonObject ?: return PageResult(emptyList(), 0, 0, 0)
        val rows = list(s)
        return PageResult(
            rows = rows,
            total = o["totalElements"]?.jsonPrimitive?.longOrNull ?: rows.size.toLong(),
            pages = o["totalPages"]?.jsonPrimitive?.intOrNull ?: 1,
            page = o["number"]?.jsonPrimitive?.intOrNull ?: 0,
        )
    }
    private fun creds(u: String, p: String) = buildJsonObject { put("username", u); put("password", p) }
    private fun reportQuery(q: ReportQuery) = mapOf(
        "deviceId" to q.deviceId, "start" to q.start, "end" to q.end,
        "resolved" to q.resolved, "page" to q.page, "size" to q.size,
    )

    override suspend fun setup(username: String, password: String) =
        call("POST", "/auth/setup", body = creds(username, password), auth = false).text("Admin user created successfully")

    override suspend fun login(username: String, password: String): LoginResponse {
        val r = call("POST", "/auth/login", body = creds(username, password), auth = false)
        return if (r is JsonPrimitive) LoginResponse(token = r.contentOrNull, username = username) else r.decode(LoginResponse.serializer())
    }

    override suspend fun getSettings() = call("GET", "/settings").decode(SystemSettings.serializer())
    override suspend fun updateSettings(syncTimes: String) =
        call("PUT", "/settings", body = buildJsonObject { put("syncTimes", syncTimes) }).decode(SystemSettings.serializer())

    override suspend fun listDevices() = call("GET", "/devices").list(Device.serializer())
    override suspend fun getDevice(id: String) = call("GET", "/devices/$id").decode(Device.serializer())
    override suspend fun createDevice(device: Device): Device {
        val r = call("POST", "/devices", body = SslmsJson.encodeToJsonElement(Device.serializer(), device))
        return (r as? JsonObject)?.decode(Device.serializer()) ?: device
    }
    override suspend fun updateDevice(id: String, patch: JsonObject) =
        (call("PATCH", "/devices/$id", body = patch) as? JsonObject)?.decode(Device.serializer())
    override suspend fun deleteDevice(id: String) = call("DELETE", "/devices/$id").text("Deleted $id")

    override suspend fun control(id: String, on: Boolean): String {
        val cmd = if (on) "ON" else "OFF"
        return call("POST", "/devices/$id/control", mapOf("command" to cmd)).text("Command $cmd sent to $id")
    }
    override suspend fun broadcast(on: Boolean): String {
        val cmd = if (on) "ON" else "OFF"
        return call("POST", "/devices/broadcast", mapOf("command" to cmd)).text("Broadcast $cmd sent to all nodes")
    }

    override suspend fun latest(id: String) = call("GET", "/devices/$id/latest").decode(LatestResponse.serializer())
    override suspend fun syncNow(id: String) = call("POST", "/devices/$id/syncnow").decode(SyncResponse.serializer())
    override suspend fun history(id: String, start: String?, end: String?) =
        call("GET", "/devices/$id/history", mapOf("start" to start, "end" to end)).list(Telemetry.serializer())
    override suspend fun telemetryLog() = call("GET", "/devices/telemetry/log").list(Telemetry.serializer())

    override suspend fun telemetryReport(q: ReportQuery) = call("GET", "/devices/reports/telemetry", reportQuery(q)).page(Telemetry.serializer())
    override suspend fun faultReport(q: ReportQuery) = call("GET", "/devices/reports/faults", reportQuery(q)).page(Fault.serializer())

    override suspend fun faults() = call("GET", "/devices/faults").list(Fault.serializer())
    override suspend fun deviceFaults(id: String) = call("GET", "/devices/faults/$id").list(Fault.serializer())
    override suspend fun resolveFault(id: Long) = (call("PATCH", "/devices/faults/$id/resolve") as? JsonObject)?.decode(Fault.serializer())
    override suspend fun deleteFault(id: Long) = call("DELETE", "/devices/faults/$id").text("Fault deleted successfully")
}
