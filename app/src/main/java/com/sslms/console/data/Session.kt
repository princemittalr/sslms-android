package com.sslms.console.data

import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Holds the signed-in session and hands out the right API (live server or demo). */
class Session(context: Context) {
    private val prefs = context.getSharedPreferences("sslms_session", Context.MODE_PRIVATE)
    private val demoApi = DemoApi(context.applicationContext)

    private val _user = MutableStateFlow(if (prefs.getString(K_TOKEN, null) != null) prefs.getString(K_USER, null) else null)
    val user: StateFlow<String?> = _user.asStateFlow()

    val token: String? get() = prefs.getString(K_TOKEN, null)
    var demo: Boolean
        get() = prefs.getBoolean(K_DEMO, false)
        set(v) = prefs.edit().putBoolean(K_DEMO, v).apply()
    var baseUrl: String
        get() = prefs.getString(K_BASE, "") ?: ""
        set(v) = prefs.edit().putString(K_BASE, v.trim().trimEnd('/')).apply()

    private var remote: RemoteApi? = null
    private var remoteFor: String? = null

    val api: SslmsApi
        get() {
            if (demo) return demoApi
            if (remote == null || remoteFor != baseUrl) {
                remote = RemoteApi(baseUrl, { token }, { signOut() })
                remoteFor = baseUrl
            }
            return remote!!
        }

    fun signIn(token: String, username: String) {
        prefs.edit().putString(K_TOKEN, token).putString(K_USER, username).apply()
        _user.value = username
    }

    fun signOut() {
        prefs.edit().remove(K_TOKEN).remove(K_USER).apply()
        _user.value = null
    }

    fun resetDemo() = demoApi.reset()

    private companion object {
        const val K_TOKEN = "token"; const val K_USER = "user"; const val K_DEMO = "demo"; const val K_BASE = "base"
    }
}

data class Fleet(val nodes: List<Node>, val faults: List<Fault>)

/** Devices + each online device's latest reading + faults, fetched in parallel. */
suspend fun SslmsApi.loadFleet(): Fleet = coroutineScope {
    val devicesD = async { listDevices() }
    val faultsD = async { runCatching { faults() }.getOrDefault(emptyList()) }
    val devices = devicesD.await()
    val faults = faultsD.await()
    val open = faults.filter { !it.resolved }.map { it.deviceId }.toSet()
    val latest = devices.map { d -> async { if (d.online) runCatching { latest(d.deviceId) }.getOrNull() else null } }.map { it.await() }
    Fleet(devices.mapIndexed { i, d -> Node(d, latest[i], d.deviceId in open) }, faults)
}
