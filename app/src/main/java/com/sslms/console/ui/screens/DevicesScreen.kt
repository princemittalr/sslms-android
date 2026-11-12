package com.sslms.console.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sslms.console.data.Device
import com.sslms.console.data.LampState
import com.sslms.console.data.Node
import com.sslms.console.data.loadFleet
import com.sslms.console.ui.LocalSession
import com.sslms.console.ui.components.Badge
import com.sslms.console.ui.components.ConfirmDialog
import com.sslms.console.ui.components.Empty
import com.sslms.console.ui.components.ErrorNote
import com.sslms.console.ui.components.GhostButton
import com.sslms.console.ui.components.Lamp
import com.sslms.console.ui.components.Loading
import com.sslms.console.ui.components.LocalNotify
import com.sslms.console.ui.components.Panel
import com.sslms.console.ui.components.rememberLoader
import com.sslms.console.ui.components.statusTone
import com.sslms.console.ui.components.Tone
import com.sslms.console.ui.theme.C
import com.sslms.console.ui.theme.Readout
import com.sslms.console.util.num
import com.sslms.console.util.prettyStatus
import com.sslms.console.util.shareCsv
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class StateFilter(val label: String) { ANY("Any state"), ONLINE("Online"), OFFLINE("Offline"), LIT("Lit now") }

@Composable
fun DevicesScreen(nav: NavController) {
    val session = LocalSession.current
    val notify = LocalNotify.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fleet = rememberLoader("fleet-devices") { session.api.loadFleet() }

    var query by remember { mutableStateOf("") }
    var feeder by remember { mutableStateOf<String?>(null) }
    var stateFilter by remember { mutableStateOf(StateFilter.ANY) }
    var editing by remember { mutableStateOf<Device?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Device?>(null) }
    var busyId by remember { mutableStateOf<String?>(null) }

    val nodes = fleet.data?.nodes.orEmpty()
    val feeders = nodes.mapNotNull { it.device.feederId }.distinct().sorted()
    val rows = nodes.filter { n ->
        val d = n.device
        (feeder == null || d.feederId == feeder) &&
            when (stateFilter) { StateFilter.ANY -> true; StateFilter.ONLINE -> d.online; StateFilter.OFFLINE -> !d.online; StateFilter.LIT -> n.state == LampState.ON } &&
            (query.isBlank() || listOf(d.deviceId, d.area, d.landmark, d.feederId, d.wardNo?.toString()).any { it?.contains(query.trim(), ignoreCase = true) == true })
    }

    fun control(id: String, on: Boolean) = scope.launch {
        busyId = id
        try { notify.show(session.api.control(id, on), false); delay(400); fleet.reload() }
        catch (e: Exception) { notify.show(e.message ?: "Command failed", true) }
        finally { busyId = null }
    }

    Box(Modifier.fillMaxSize()) {
        RefreshPage(refreshing = fleet.loading && fleet.data != null, onRefresh = fleet::reload) {
            item {
                ScreenHeader("Devices", if (fleet.data != null) "${nodes.size} nodes, ${num(nodes.sumOf { it.device.noOfStreetLights ?: 0 }, 0)} lamps in total" else "Loading devices…") {
                    IconButton(enabled = rows.isNotEmpty(), onClick = {
                        shareCsv(context, "sslms-devices.csv",
                            listOf("Device ID", "Feeder", "Area", "Landmark", "Ward", "Latitude", "Longitude", "Status", "Online", "Lights", "Rated W", "Y factor"),
                            rows.map { it.device }.map { listOf(it.deviceId, it.feederId, it.area, it.landmark, it.wardNo, it.latitude, it.longitude, it.status, it.onlineStatus, it.noOfStreetLights, it.ratedStreetLightWattage, it.yFactor) })
                    }) { Icon(Icons.Outlined.FileDownload, "Export CSV", tint = C.Text) }
                }
            }
            item {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search ID, area, landmark or ward", color = C.Faint) },
                    leadingIcon = { Icon(Icons.Outlined.Search, null, tint = C.Muted) },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.Sodium, unfocusedBorderColor = C.Line2, cursorColor = C.Sodium, focusedContainerColor = C.Night2, unfocusedContainerColor = C.Night2),
                )
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StateFilter.entries.forEach { f -> Chip(f.label, stateFilter == f) { stateFilter = f } }
                    Box(Modifier.width(1.dp).height(32.dp).background(C.Line).align(Alignment.CenterVertically))
                    Chip("All feeders", feeder == null) { feeder = null }
                    feeders.forEach { f -> Chip("Feeder $f", feeder == f) { feeder = if (feeder == f) null else f } }
                }
            }
            item { ErrorNote(fleet.error, fleet::reload) }

            when {
                fleet.data == null && fleet.loading -> item { Loading() }
                rows.isEmpty() && nodes.isNotEmpty() -> item {
                    Panel { Empty("No devices match these filters") { TextButton(onClick = { query = ""; feeder = null; stateFilter = StateFilter.ANY }) { Text("Clear filters", color = C.Sodium) } } }
                }
                nodes.isEmpty() && fleet.data != null -> item { Panel { Empty("No devices registered yet", "Tap Add device to register a node with its location and lamp count.") } }
                else -> items(rows, key = { it.device.deviceId }) { n ->
                    DeviceCard(
                        n, busy = busyId == n.device.deviceId,
                        onOpen = { nav.navigate("device/${n.device.deviceId}") },
                        onControl = { control(n.device.deviceId, it) },
                        onEdit = { editing = n.device },
                        onDelete = { deleting = n.device },
                    )
                }
            }
            item { Spacer(Modifier.height(64.dp)) }
        }

        ExtendedFloatingActionButton(
            onClick = { creating = true },
            icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Add device", fontWeight = FontWeight.SemiBold, color = C.Night) },
            containerColor = C.Text, contentColor = C.Night,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (creating || editing != null) {
        DeviceFormDialog(editing, onDismiss = { creating = false; editing = null }, onSaved = { fleet.reload() })
    }
    deleting?.let { d ->
        ConfirmDialog(
            title = "Delete ${d.deviceId}?",
            message = "The node at ${d.landmark ?: d.area ?: "this location"} will be removed from monitoring. Its history may be lost on the server.",
            confirmLabel = "Delete device",
            onConfirm = {
                try { notify.show(session.api.deleteDevice(d.deviceId).let { "Deleted ${d.deviceId}" }, false); fleet.reload() }
                catch (e: Exception) { notify.show(e.message ?: "Delete failed", true); throw e }
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick, label = { Text(label) },
        shape = RoundedCornerShape(10.dp),
        colors = FilterChipDefaults.filterChipColors(containerColor = C.Night2, labelColor = C.Muted, selectedContainerColor = C.Panel2, selectedLabelColor = C.Text),
        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = selected, borderColor = C.Line, selectedBorderColor = C.Sodium.copy(alpha = 0.6f)),
    )
}

@Composable
private fun DeviceCard(n: Node, busy: Boolean, onOpen: () -> Unit, onControl: (Boolean) -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val d = n.device
    var menu by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Panel).border(1.dp, C.Line, RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen).padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Lamp(n.state, 12.dp)
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(d.deviceId, style = MaterialTheme.typography.titleMedium)
                    if (n.faulted) Icon(Icons.Outlined.WarningAmber, "Open fault", tint = C.Red, modifier = Modifier.size(16.dp))
                    Badge(prettyStatus(d.status), statusTone(d.status))
                    if (!d.online && d.status?.uppercase() != "OFFLINE") Badge("Offline", Tone.DANGER)
                }
                Text(listOfNotNull(d.area, d.landmark, d.feederId?.let { "Feeder $it" }).joinToString(", "), style = MaterialTheme.typography.bodySmall)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More for ${d.deviceId}", tint = C.Muted) }
                DropdownMenu(menu, { menu = false }, containerColor = C.Panel2) {
                    DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text("Delete", color = C.Red) }, leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = C.Red) }, onClick = { menu = false; onDelete() })
                }
            }
        }
        Row(Modifier.padding(end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Metric("Lamps", num(d.noOfStreetLights, 0), Modifier.weight(1f))
            Metric("Rated", "${num(d.ratedLoad, 0)} W", Modifier.weight(1.2f))
            Metric("Live", if (n.state == LampState.OFFLINE) "—" else "${num(n.power, 0)} W", Modifier.weight(1.2f), if (n.state == LampState.ON) C.Lit else C.Text)
            Relay(n.state, enabled = d.online && !busy, onControl = onControl)
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier, color: Color = C.Text) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(value, style = Readout.Small.copy(color = color))
    }
}

@Composable
fun Relay(state: LampState, enabled: Boolean, onControl: (Boolean) -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(8.dp)).border(BorderStroke(1.dp, C.Line2), RoundedCornerShape(8.dp))) {
        RelayBtn("On", state == LampState.ON, C.Lit, C.OnLit, enabled) { onControl(true) }
        Box(Modifier.width(1.dp).height(34.dp).background(C.Line2))
        RelayBtn("Off", state == LampState.OFF, C.Panel2, C.Text, enabled) { onControl(false) }
    }
}

@Composable
private fun RelayBtn(label: String, active: Boolean, bg: Color, fg: Color, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(34.dp).width(48.dp).background(if (active) bg else Color.Transparent).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium.copy(color = (if (active) fg else C.Muted).copy(alpha = if (enabled) 1f else 0.4f), fontWeight = FontWeight.SemiBold))
    }
}
