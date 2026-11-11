package com.sslms.console.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sslms.console.data.Device
import com.sslms.console.ui.LocalSession
import com.sslms.console.ui.components.ErrorNote
import com.sslms.console.ui.components.Field
import com.sslms.console.ui.components.LocalNotify
import com.sslms.console.ui.components.PrimaryButton
import com.sslms.console.ui.theme.C
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

private val STATUSES = listOf("ACTIVE", "INACTIVE", "MAINTENANCE", "OFFLINE")

/** Full-screen form to add a device (device == null) or edit one (sends only changed fields). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeviceFormDialog(device: Device?, onDismiss: () -> Unit, onSaved: (Device) -> Unit) {
    val editing = device != null
    val session = LocalSession.current
    val notify = LocalNotify.current
    val scope = rememberCoroutineScope()

    fun s(v: Any?) = v?.let { if (it is Double && it % 1.0 == 0.0 && it < 1e9) it.toLong().toString() else it.toString() } ?: ""
    var deviceId by remember { mutableStateOf(device?.deviceId ?: "") }
    var feederId by remember { mutableStateOf(device?.feederId ?: "") }
    var area by remember { mutableStateOf(device?.area ?: "") }
    var wardNo by remember { mutableStateOf(s(device?.wardNo)) }
    var landmark by remember { mutableStateOf(device?.landmark ?: "") }
    var lat by remember { mutableStateOf(device?.latitude?.toString() ?: "") }
    var lng by remember { mutableStateOf(device?.longitude?.toString() ?: "") }
    var lights by remember { mutableStateOf(s(device?.noOfStreetLights)) }
    var watt by remember { mutableStateOf(s(device?.ratedStreetLightWattage)) }
    var yFactor by remember { mutableStateOf(device?.yFactor?.toString() ?: "1.05") }
    var status by remember { mutableStateOf(device?.status ?: "ACTIVE") }
    var online by remember { mutableStateOf(device?.onlineStatus ?: true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun save() {
        error = null
        if (deviceId.isBlank()) { error = "Device ID is required."; return }
        val la = lat.toDoubleOrNull(); val lo = lng.toDoubleOrNull()
        if (lat.isNotBlank() && (la == null || la !in -90.0..90.0)) { error = "Latitude must be a number between -90 and 90."; return }
        if (lng.isNotBlank() && (lo == null || lo !in -180.0..180.0)) { error = "Longitude must be a number between -180 and 180."; return }
        val nd = Device(
            deviceId = deviceId.trim(), feederId = feederId.trim().ifBlank { null }, landmark = landmark.trim().ifBlank { null },
            area = area.trim().ifBlank { null }, wardNo = wardNo.toIntOrNull(), latitude = la, longitude = lo,
            onlineStatus = online, status = status, noOfStreetLights = lights.toIntOrNull(),
            ratedStreetLightWattage = watt.toDoubleOrNull(), yFactor = yFactor.toDoubleOrNull(),
        )
        busy = true
        scope.launch {
            try {
                val saved = if (!editing) session.api.createDevice(nd) else {
                    val old = device!!
                    val patch = buildJsonObject {
                        fun put(k: String, a: Any?, b: Any?) {
                            if (a != b) put(k, when (b) { null -> JsonNull; is Number -> JsonPrimitive(b); is Boolean -> JsonPrimitive(b); else -> JsonPrimitive(b.toString()) })
                        }
                        put("feederId", old.feederId, nd.feederId); put("landmark", old.landmark, nd.landmark); put("area", old.area, nd.area)
                        put("wardNo", old.wardNo, nd.wardNo); put("latitude", old.latitude, nd.latitude); put("longitude", old.longitude, nd.longitude)
                        put("onlineStatus", old.onlineStatus, nd.onlineStatus); put("status", old.status, nd.status)
                        put("noOfStreetLights", old.noOfStreetLights, nd.noOfStreetLights)
                        put("ratedStreetLightWattage", old.ratedStreetLightWattage, nd.ratedStreetLightWattage); put("yFactor", old.yFactor, nd.yFactor)
                    }
                    if (patch.isEmpty()) { onDismiss(); return@launch }
                    session.api.updateDevice(old.deviceId, patch) ?: nd
                }
                notify.show(if (editing) "Saved ${nd.deviceId}" else "Added ${nd.deviceId}", false)
                onSaved(if (editing) nd.copy() else saved)
                onDismiss()
            } catch (e: Exception) {
                error = e.message; busy = false
            }
        }
    }

    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(C.Night).statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Cancel", tint = C.Text) }
                Text(if (editing) "Edit ${device!!.deviceId}" else "Add a device", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Section("Identity")
                Field("Device ID", deviceId, { deviceId = it }, enabled = !editing, hint = if (editing) "The ID can't be changed." else "For example NODE013")
                Field("Feeder ID", feederId, { feederId = it }, placeholder = "F1")

                Section("Location")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field("Area", area, { area = it }, Modifier.weight(1.6f), placeholder = "Officer Colony")
                    Field("Ward", wardNo, { wardNo = it }, Modifier.weight(1f), keyboard = KeyboardType.Number)
                }
                Field("Landmark", landmark, { landmark = it }, placeholder = "Near City Centre Mall")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field("Latitude", lat, { lat = it }, Modifier.weight(1f), placeholder = "23.7808", keyboard = KeyboardType.Decimal)
                    Field("Longitude", lng, { lng = it }, Modifier.weight(1f), placeholder = "86.4304", keyboard = KeyboardType.Decimal)
                }

                Section("Load")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field("Street lights", lights, { lights = it }, Modifier.weight(1f), keyboard = KeyboardType.Number)
                    Field("Watts each", watt, { watt = it }, Modifier.weight(1f), keyboard = KeyboardType.Decimal)
                }
                Field("Y factor", yFactor, { yFactor = it }, hint = "Multiplier for driver and line losses", keyboard = KeyboardType.Decimal)

                Section("Status")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    STATUSES.forEach { st ->
                        FilterChip(
                            selected = status == st, onClick = { status = st },
                            label = { Text(st.lowercase().replaceFirstChar { c -> c.uppercase() }) },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                            colors = FilterChipDefaults.filterChipColors(containerColor = C.Night2, labelColor = C.Muted, selectedContainerColor = C.Panel2, selectedLabelColor = C.Text),
                            border = FilterChipDefaults.filterChipBorder(enabled = true, selected = status == st, borderColor = C.Line, selectedBorderColor = C.Sodium.copy(alpha = 0.6f)),
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Node is online", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(online, { online = it }, colors = SwitchDefaults.colors(checkedTrackColor = C.Sodium, checkedThumbColor = C.OnSodium, uncheckedTrackColor = C.Night2, uncheckedBorderColor = C.Line2, uncheckedThumbColor = C.Muted))
                }
                ErrorNote(error)
            }
            PrimaryButton(if (busy) "Saving…" else if (editing) "Save changes" else "Add device", ::save, Modifier.fillMaxWidth().padding(16.dp), enabled = !busy)
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
}
