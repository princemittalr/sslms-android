package com.sslms.console.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

// Shapes follow the SSLMS API Integration Reference. Every field is optional so the
// app keeps working if the backend adds, renames or omits something.

@Serializable
data class LoginResponse(val token: String? = null, val username: String? = null)

@Serializable
data class SystemSettings(
    val id: String? = null,
    val syncCount: Int? = null,
    val syncTimes: String? = null,
) {
    val times: List<String> get() = syncTimes.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.sorted()
}

@Serializable
data class Device(
    val deviceId: String = "",
    val feederId: String? = null,
    val landmark: String? = null,
    val area: String? = null,
    val wardNo: Int? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val onlineStatus: Boolean? = null,
    val status: String? = null,
    val noOfStreetLights: Int? = null,
    val ratedStreetLightWattage: Double? = null,
    val yFactor: Double? = null,
) {
    val online: Boolean get() = onlineStatus == true
    val ratedLoad: Double get() = (noOfStreetLights ?: 0) * (ratedStreetLightWattage ?: 0.0) * (yFactor ?: 1.0)
    val hasLocation: Boolean get() = latitude != null && longitude != null && !(latitude == 0.0 && longitude == 0.0)
    val placeLine: String get() = listOfNotNull(landmark?.takeIf { it.isNotBlank() }, area?.takeIf { it.isNotBlank() }, wardNo?.let { "Ward $it" }).joinToString(", ")
}

@Serializable
data class Telemetry(
    val id: Long? = null,
    val deviceId: String? = null,
    val voltage: Double? = null,
    val current: Double? = null,
    val power: Double? = null,
    val timestamp: String? = null,
)

@Serializable
data class LatestResponse(
    val telemetry: Telemetry? = null,
    val signal: Int? = null,
    val status: JsonElement? = null,
) {
    /** status 1 = relay on, 0 = off. Falls back to measured power when absent. */
    val relayOn: Boolean?
        get() {
            val p = (status as? JsonPrimitive) ?: return telemetry?.power?.let { it > 10 }
            p.intOrNull?.let { return it == 1 }
            p.booleanOrNull?.let { return it }
            return when (p.content.uppercase()) { "ON" -> true; "OFF" -> false; else -> telemetry?.power?.let { it > 10 } }
        }
}

@Serializable
data class SyncResponse(
    val deviceInfo: Device? = null,
    val latestTelemetry: Telemetry? = null,
    val source: String? = null,
)

@Serializable
data class Fault(
    val id: Long = 0,
    val deviceId: String = "",
    val description: String? = null,
    val wattAtFault: Double? = null,
    val resolved: Boolean = false,
    val timestamp: String? = null,
)

/** Spring Page<T> normalised. */
data class PageResult<T>(val rows: List<T>, val total: Long, val pages: Int, val page: Int)

data class ReportQuery(
    val deviceId: String? = null,
    val start: String? = null,
    val end: String? = null,
    val resolved: Boolean? = null,
    val page: Int = 0,
    val size: Int = 20,
)

enum class LampState { ON, OFF, OFFLINE, UNKNOWN }

fun lampState(device: Device?, latest: LatestResponse?): LampState = when {
    device?.online != true -> LampState.OFFLINE
    latest == null -> LampState.UNKNOWN
    latest.relayOn == true -> LampState.ON
    latest.relayOn == false -> LampState.OFF
    else -> LampState.UNKNOWN
}

/** One node on the network with its latest reading. */
data class Node(
    val device: Device,
    val latest: LatestResponse?,
    val faulted: Boolean,
) {
    val state: LampState get() = lampState(device, latest)
    val power: Double? get() = latest?.telemetry?.power
}

class ApiException(message: String, val status: Int = 0) : Exception(message)

internal fun JsonElement.asIntOrNull() = runCatching { jsonPrimitive.intOrNull }.getOrNull()
