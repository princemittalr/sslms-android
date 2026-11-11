package com.sslms.console.data

import kotlinx.serialization.json.JsonObject

/** One function per endpoint in the SSLMS API Integration Reference. */
interface SslmsApi {
    // Auth
    suspend fun setup(username: String, password: String): String
    suspend fun login(username: String, password: String): LoginResponse

    // Settings
    suspend fun getSettings(): SystemSettings
    suspend fun updateSettings(syncTimes: String): SystemSettings

    // Devices
    suspend fun listDevices(): List<Device>
    suspend fun getDevice(id: String): Device
    suspend fun createDevice(device: Device): Device
    suspend fun updateDevice(id: String, patch: JsonObject): Device?
    suspend fun deleteDevice(id: String): String

    // Control
    suspend fun control(id: String, on: Boolean): String
    suspend fun broadcast(on: Boolean): String

    // Telemetry
    suspend fun latest(id: String): LatestResponse
    suspend fun syncNow(id: String): SyncResponse
    suspend fun history(id: String, start: String?, end: String?): List<Telemetry>
    suspend fun telemetryLog(): List<Telemetry>

    // Reports
    suspend fun telemetryReport(q: ReportQuery): PageResult<Telemetry>
    suspend fun faultReport(q: ReportQuery): PageResult<Fault>

    // Faults
    suspend fun faults(): List<Fault>
    suspend fun deviceFaults(id: String): List<Fault>
    suspend fun resolveFault(id: Long): Fault?
    suspend fun deleteFault(id: Long): String
}
