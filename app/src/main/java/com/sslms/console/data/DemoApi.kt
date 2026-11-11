package com.sslms.console.data

import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.round
import kotlin.random.Random

/**
 * In-app stand-in for the SSLMS backend, used in demo mode.
 * Mirrors the API's response shapes; state is kept in SharedPreferences and
 * telemetry is generated (lights run 18:00–06:00 unless a relay command overrides).
 */
class DemoApi(context: Context) : SslmsApi {

    @Serializable
    private data class User(val username: String, val password: String)

    @Serializable
    private data class State(
        val users: MutableList<User> = mutableListOf(),
        var settings: SystemSettings = SystemSettings("GLOBAL", 3, "06:00,12:00,18:00"),
        val devices: MutableList<Device> = mutableListOf(),
        val relay: MutableMap<String, Boolean> = mutableMapOf(),
        var faults: MutableList<Fault> = mutableListOf(),
        var nextFaultId: Long = 8,
    )

    private val prefs = context.getSharedPreferences("sslms_demo", Context.MODE_PRIVATE)
    private var state: State = load()

    private fun load(): State = prefs.getString("state", null)
        ?.let { runCatching { SslmsJson.decodeFromString(State.serializer(), it) }.getOrNull() }
        ?: seed().also { save(it) }

    private fun save(s: State = state) { prefs.edit().putString("state", SslmsJson.encodeToString(State.serializer(), s)).apply() }

    fun reset() { state = seed(); save() }

    private fun seed(): State {
        val r = Random(42)
        val areas = listOf(
            Triple("Officer Colony", "Near City Centre Mall", 10), Triple("Hirapur", "Hirapur Chowk", 4),
            Triple("Bank More", "Bank More Flyover", 12), Triple("Saraidhela", "Saraidhela Police Station", 22),
            Triple("Jharia Road", "Matkuria Gate", 31), Triple("Bartand", "Bartand Bus Stand", 18),
            Triple("Dhaiya", "ISM Gate No. 2", 25), Triple("Steel Gate", "Steel Gate Market", 20),
            Triple("Police Line", "SSP Office Road", 7), Triple("Katras Road", "Golf Ground", 15),
            Triple("Wasseypur", "Pandarpala Road", 33), Triple("Govindpur Road", "Memco More", 28),
        )
        val lights = listOf(20, 16, 24, 12, 30, 18, 22, 14, 10, 26, 16, 20)
        val watts = listOf(60, 40, 90, 60, 60, 40, 90, 60, 40, 60, 40, 90)
        val devices = areas.mapIndexed { i, (area, landmark, ward) ->
            val offline = i == 6 || i == 10
            Device(
                deviceId = "NODE%03d".format(i + 1), feederId = "F${i % 4 + 1}", landmark = landmark, area = area, wardNo = ward,
                latitude = round4(23.7957 + (r.nextDouble() - 0.5) * 0.07), longitude = round4(86.4304 + (r.nextDouble() - 0.5) * 0.09),
                onlineStatus = !offline, status = if (offline) "OFFLINE" else if (i == 9) "MAINTENANCE" else "ACTIVE",
                noOfStreetLights = lights[i], ratedStreetLightWattage = watts[i].toDouble(), yFactor = 1.05,
            )
        }.toMutableList()
        val now = LocalDateTime.now()
        val faults = listOf(
            listOf(1, "NODE003", "Lamp Failure", 20.5, false, 3), listOf(2, "NODE005", "Over Voltage", 1620.0, false, 9),
            listOf(3, "NODE007", "Communication Loss", 0.0, false, 26), listOf(4, "NODE002", "Partial Lamp Failure", 410.2, true, 50),
            listOf(5, "NODE011", "Communication Loss", 0.0, false, 70), listOf(6, "NODE009", "Low Power Factor", 280.0, true, 120),
            listOf(7, "NODE001", "Lamp Failure", 1100.4, true, 170),
        ).map { f ->
            Fault(id = (f[0] as Int).toLong(), deviceId = f[1] as String, description = f[2] as String, wattAtFault = f[3] as Double,
                resolved = f[4] as Boolean, timestamp = iso(now.minusHours((f[5] as Int).toLong())))
        }.toMutableList()
        return State(devices = devices, relay = devices.associate { it.deviceId to it.online }.toMutableMap(), faults = faults)
    }

    // ---- telemetry generation ----
    private fun round4(v: Double) = round(v * 10000) / 10000
    private fun r1(v: Double) = round(v * 10) / 10
    private fun r2(v: Double) = round(v * 100) / 100
    private fun epoch(t: LocalDateTime) = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun fromEpoch(ms: Long) = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(ms), ZoneId.systemDefault())

    private fun reading(d: Device, ms: Long, liveRelay: Boolean? = null): Telemetry {
        val bucket = ms / 600_000
        val r = Random(d.deviceId.hashCode() xor bucket.toInt())
        val t = fromEpoch(ms)
        val on = liveRelay ?: (t.hour >= 18 || t.hour < 6)
        val voltage = r1(224 + r.nextDouble() * 14)
        val (current, power) = if (!on) r2(0.02 + r.nextDouble() * 0.03) to r1(3 + r.nextDouble() * 4) else {
            val health = if (d.deviceId == "NODE003") 0.8 else 0.94 + r.nextDouble() * 0.06
            val p = d.ratedLoad * health
            r2(p / voltage) to round(p)
        }
        return Telemetry(id = abs(d.deviceId.hashCode() % 900) * 1_000_000L + bucket % 1_000_000, deviceId = d.deviceId,
            voltage = voltage, current = current, power = power, timestamp = iso(t))
    }

    private fun series(d: Device, startMs: Long, endMs0: Long, step: Long): List<Telemetry> {
        val endMs = if (!d.online) minOf(endMs0, System.currentTimeMillis() - 26 * 3_600_000L) else endMs0
        val out = ArrayList<Telemetry>()
        var t = ceil(startMs.toDouble() / step).toLong() * step
        while (t <= endMs) { out += reading(d, t); t += step }
        return out
    }

    private fun parse(s: String?, fallback: Long) = s?.let { runCatching { epoch(LocalDateTime.parse(it)) }.getOrNull() } ?: fallback
    private fun dev(id: String) = state.devices.find { it.deviceId == id } ?: throw ApiException("Device $id not found", 404)
    private fun <T> page(rows: List<T>, q: ReportQuery): PageResult<T> {
        val size = q.size.coerceAtLeast(1)
        val from = (q.page * size).coerceAtMost(rows.size)
        return PageResult(rows.subList(from, minOf(rows.size, from + size)), rows.size.toLong(), ceil(rows.size / size.toDouble()).toInt(), q.page)
    }
    private suspend fun lag() { if (simulateLatency) delay(150L + Random.nextLong(250)) }

    // ---- endpoints ----
    override suspend fun setup(username: String, password: String): String {
        lag()
        if (state.users.isNotEmpty()) throw ApiException("Admin user already exists", 400)
        state.users += User(username, password); save()
        return "Admin user created successfully"
    }

    override suspend fun login(username: String, password: String): LoginResponse {
        lag()
        val ok = state.users.any { it.username == username && it.password == password } ||
            (state.users.isEmpty() && username == "admin" && password == "admin123")
        if (!ok) throw ApiException("Invalid username or password", 401)
        return LoginResponse("demo.$username.${System.currentTimeMillis()}", username)
    }

    override suspend fun getSettings(): SystemSettings { lag(); return state.settings }
    override suspend fun updateSettings(syncTimes: String): SystemSettings {
        lag()
        val times = syncTimes.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        state.settings = state.settings.copy(syncTimes = times.joinToString(","), syncCount = times.size); save()
        return state.settings
    }

    override suspend fun listDevices(): List<Device> { lag(); return state.devices.toList() }
    override suspend fun getDevice(id: String): Device { lag(); return dev(id) }
    override suspend fun createDevice(device: Device): Device {
        lag()
        if (device.deviceId.isBlank()) throw ApiException("deviceId is required", 400)
        if (state.devices.any { it.deviceId == device.deviceId }) throw ApiException("Device ${device.deviceId} already exists", 409)
        state.devices += device; state.relay[device.deviceId] = false; save()
        return device
    }
    override suspend fun updateDevice(id: String, patch: JsonObject): Device {
        lag()
        val d = dev(id)
        fun s(k: String, old: String?) = if (k in patch) (patch[k] as? JsonPrimitive)?.contentOrNull else old
        fun i(k: String, old: Int?) = if (k in patch) (patch[k] as? JsonPrimitive)?.intOrNull else old
        fun dbl(k: String, old: Double?) = if (k in patch) (patch[k] as? JsonPrimitive)?.doubleOrNull else old
        val nd = d.copy(
            feederId = s("feederId", d.feederId), landmark = s("landmark", d.landmark), area = s("area", d.area),
            wardNo = i("wardNo", d.wardNo), latitude = dbl("latitude", d.latitude), longitude = dbl("longitude", d.longitude),
            onlineStatus = if ("onlineStatus" in patch) (patch["onlineStatus"] as? JsonPrimitive)?.booleanOrNull else d.onlineStatus,
            status = s("status", d.status), noOfStreetLights = i("noOfStreetLights", d.noOfStreetLights),
            ratedStreetLightWattage = dbl("ratedStreetLightWattage", d.ratedStreetLightWattage), yFactor = dbl("yFactor", d.yFactor),
        )
        state.devices[state.devices.indexOf(d)] = nd; save()
        return nd
    }
    override suspend fun deleteDevice(id: String): String {
        lag(); dev(id)
        state.devices.removeAll { it.deviceId == id }; state.relay.remove(id); save()
        return "Device deleted successfully"
    }

    override suspend fun control(id: String, on: Boolean): String {
        lag()
        val d = dev(id)
        if (!d.online) throw ApiException("$id is offline; the command could not be delivered", 409)
        state.relay[id] = on; save()
        return "Command ${if (on) "ON" else "OFF"} sent to $id"
    }
    override suspend fun broadcast(on: Boolean): String {
        lag()
        state.devices.filter { it.online }.forEach { state.relay[it.deviceId] = on }; save()
        return "Broadcast ${if (on) "ON" else "OFF"} sent to all nodes"
    }

    override suspend fun latest(id: String): LatestResponse {
        lag()
        val d = dev(id); val now = System.currentTimeMillis()
        if (!d.online) return LatestResponse(reading(d, now - 26 * 3_600_000L, false), 0, JsonPrimitive(0))
        val on = state.relay[id] ?: false
        return LatestResponse(reading(d, now, on), 55 + abs(id.hashCode() % 40), JsonPrimitive(if (on) 1 else 0))
    }
    override suspend fun syncNow(id: String): SyncResponse {
        lag(); if (simulateLatency) delay(500)
        val d = dev(id)
        if (!d.online) throw ApiException("No response from $id hardware", 504)
        val t = reading(d, System.currentTimeMillis(), state.relay[id] ?: false)
        return SyncResponse(Device(deviceId = id, status = d.status), Telemetry(voltage = t.voltage, current = t.current, power = t.power), "HARDWARE_REALTIME")
    }
    override suspend fun history(id: String, start: String?, end: String?): List<Telemetry> {
        lag()
        val d = dev(id); val now = System.currentTimeMillis()
        val e = parse(end, now); val s = parse(start, e - 86_400_000L)
        val span = e - s
        val step = if (span > 14 * 86_400_000L) 6 * 3_600_000L else if (span > 2 * 86_400_000L) 3_600_000L else 900_000L
        return series(d, s, e, step)
    }
    override suspend fun telemetryLog(): List<Telemetry> {
        lag()
        val now = System.currentTimeMillis()
        return state.devices.filter { it.online }.flatMap { d -> (0 until 6).map { k -> reading(d, now - k * 600_000L, if (k == 0) state.relay[d.deviceId] else null) } }
            .sortedByDescending { it.timestamp }.take(50)
    }

    override suspend fun telemetryReport(q: ReportQuery): PageResult<Telemetry> {
        lag()
        val now = System.currentTimeMillis()
        val e = parse(q.end, now); val s = parse(q.start, e - 86_400_000L)
        val targets = if (q.deviceId.isNullOrBlank()) state.devices else state.devices.filter { it.deviceId == q.deviceId }
        return page(targets.flatMap { series(it, s, e, 3_600_000L) }.sortedByDescending { it.timestamp }, q)
    }
    override suspend fun faultReport(q: ReportQuery): PageResult<Fault> {
        lag()
        val e = parse(q.end, Long.MAX_VALUE); val s = parse(q.start, 0)
        val rows = state.faults.filter {
            val t = parse(it.timestamp, 0)
            t in s..e && (q.deviceId.isNullOrBlank() || it.deviceId == q.deviceId) && (q.resolved == null || it.resolved == q.resolved)
        }.sortedByDescending { it.timestamp }
        return page(rows, q)
    }

    override suspend fun faults(): List<Fault> { lag(); return state.faults.sortedByDescending { it.timestamp } }
    override suspend fun deviceFaults(id: String): List<Fault> { lag(); return state.faults.filter { it.deviceId == id }.sortedByDescending { it.timestamp } }
    override suspend fun resolveFault(id: Long): Fault {
        lag()
        val i = state.faults.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: throw ApiException("Fault not found", 404)
        state.faults[i] = state.faults[i].copy(resolved = true); save()
        return state.faults[i]
    }
    override suspend fun deleteFault(id: Long): String {
        lag()
        if (!state.faults.removeAll { it.id == id }) throw ApiException("Fault not found", 404)
        save()
        return "Fault deleted successfully"
    }

    companion object {
        /** Simulated network delay; switched off in screenshot tests. */
        @Volatile var simulateLatency = true
        fun iso(t: LocalDateTime): String = "%04d-%02d-%02dT%02d:%02d:%02d".format(t.year, t.monthValue, t.dayOfMonth, t.hour, t.minute, t.second)
    }
}
