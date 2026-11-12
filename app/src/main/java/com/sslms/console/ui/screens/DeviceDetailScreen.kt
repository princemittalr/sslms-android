package com.sslms.console.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sslms.console.data.LampState
import com.sslms.console.data.Node
import com.sslms.console.data.SyncResponse
import com.sslms.console.data.Telemetry
import com.sslms.console.data.lampState
import com.sslms.console.ui.LocalSession
import com.sslms.console.ui.components.Badge
import com.sslms.console.ui.components.ConfirmDialog
import com.sslms.console.ui.components.Divider
import com.sslms.console.ui.components.Empty
import com.sslms.console.ui.components.ErrorNote
import com.sslms.console.ui.components.Fact
import com.sslms.console.ui.components.FactText
import com.sslms.console.ui.components.GhostButton
import com.sslms.console.ui.components.HistoryChart
import com.sslms.console.ui.components.Lamp
import com.sslms.console.ui.components.Loading
import com.sslms.console.ui.components.LocalNotify
import com.sslms.console.ui.components.NodeMap
import com.sslms.console.ui.components.Panel
import com.sslms.console.ui.components.PanelHead
import com.sslms.console.ui.components.Segmented
import com.sslms.console.ui.components.SignalBars
import com.sslms.console.ui.components.LitButton
import com.sslms.console.ui.components.Tone
import com.sslms.console.ui.components.WarnNote
import com.sslms.console.ui.components.rememberLoader
import com.sslms.console.ui.components.statusTone
import com.sslms.console.ui.theme.C
import com.sslms.console.ui.theme.Readout
import com.sslms.console.util.ago
import com.sslms.console.util.fmtTime
import com.sslms.console.util.num
import com.sslms.console.util.prettyStatus
import com.sslms.console.util.toIso
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime

private enum class Range(val label: String, val hours: Long) { H6("6 h", 6), H24("24 h", 24), D7("7 days", 168), D30("30 days", 720) }

@Composable
fun DeviceDetailScreen(nav: NavController, deviceId: String) {
    val session = LocalSession.current
    val notify = LocalNotify.current
    val scope = rememberCoroutineScope()

    val device = rememberLoader("device-$deviceId") { session.api.getDevice(deviceId) }
    val latest = rememberLoader("latest-$deviceId", refreshMs = 30_000) { runCatching { session.api.latest(deviceId) }.getOrNull() }
    val faults = rememberLoader("faults-$deviceId") { runCatching { session.api.deviceFaults(deviceId) }.getOrDefault(emptyList()) }
    var range by remember { mutableStateOf(Range.H24) }
    val history = rememberLoader("history-$deviceId-${range.name}") {
        val end = LocalDateTime.now()
        session.api.history(deviceId, toIso(end.minusHours(range.hours)), toIso(end))
    }

    var sync by remember { mutableStateOf<Pair<SyncResponse, String>?>(null) }
    var syncing by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf<Boolean?>(null) }
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    val d = device.data
    val l = latest.data
    val state = lampState(d, l)
    val reading: Telemetry? = sync?.let { (s, at) -> (l?.telemetry ?: Telemetry()).copy(voltage = s.latestTelemetry?.voltage, current = s.latestTelemetry?.current, power = s.latestTelemetry?.power, timestamp = at) } ?: l?.telemetry
    val rated = d?.ratedLoad ?: 0.0
    val openFaults = faults.data.orEmpty().filter { !it.resolved }

    fun refresh() { device.reload(); latest.reload(); faults.reload(); history.reload() }

    fun doSync() = scope.launch {
        syncing = true
        try { val r = session.api.syncNow(deviceId); sync = r to toIso(LocalDateTime.now()); notify.show("Fresh reading received from $deviceId", false); latest.reload() }
        catch (e: Exception) { notify.show(e.message ?: "Sync failed", true) }
        finally { syncing = false }
    }
    fun control(on: Boolean) = scope.launch {
        sending = on
        try { notify.show(session.api.control(deviceId, on), false); sync = null; delay(400); latest.reload() }
        catch (e: Exception) { notify.show(e.message ?: "Command failed", true) }
        finally { sending = null }
    }

    RefreshPage(refreshing = device.loading && d != null, onRefresh = ::refresh) {
        item {
            ScreenHeader(deviceId, d?.placeLine?.ifBlank { null }, onBack = { nav.popBackStack() }) {
                if (d != null) {
                    IconButton(onClick = { editing = true }) { Icon(Icons.Outlined.Edit, "Edit", tint = C.Text) }
                    IconButton(onClick = { deleting = true }) { Icon(Icons.Outlined.Delete, "Delete", tint = C.Red) }
                }
            }
        }
        item { ErrorNote(device.error, device::reload) }
        if (d == null) { if (device.error == null) item { Loading() }; return@RefreshPage }

        item {
            Panel {
                PanelHead("Live reading") {
                    GhostButton(if (syncing) "Syncing…" else "Sync now", ::doSync, enabled = !syncing && d.online, icon = Icons.Outlined.Sync, small = true)
                }
                if (!d.online) WarnNote("This node is offline. Readings below are the last ones received.")
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(12.dp)).border(1.dp, C.Line, RoundedCornerShape(12.dp)).background(C.Night2)) {
                    Gauge("Power", num(reading?.power, 0), "W", Modifier.weight(1.15f), C.Sodium)
                    Box(Modifier.width(1.dp).fillMaxHeight().background(C.Line))
                    Gauge("Voltage", num(reading?.voltage), "V", Modifier.weight(1f))
                    Box(Modifier.width(1.dp).fillMaxHeight().background(C.Line))
                    Gauge("Current", num(reading?.current, 2), "A", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Fact("Relay") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Lamp(state, 9.dp)
                                Text(when (state) { LampState.ON -> "On"; LampState.OFF -> "Off"; LampState.OFFLINE -> "Unreachable"; LampState.UNKNOWN -> "Unknown" }, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                            }
                        }
                        FactText("Load vs rated", if (state == LampState.ON && rated > 0 && reading?.power != null) "${num(reading.power / rated * 100, 0)}% of ${num(rated, 0)} W" else "${num(rated, 0)} W rated")
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Fact("Signal") {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SignalBars(l?.signal); Text(l?.signal?.let { "$it%" } ?: "—", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                            }
                        }
                        FactText("Reading time", reading?.timestamp?.let { "${fmtTime(it)}\n${ago(it)}" } ?: "—")
                    }
                }
                sync?.first?.source?.let { Text("Source: ${if (it == "HARDWARE_REALTIME") "live from the hardware" else it}", style = MaterialTheme.typography.bodySmall) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LitButton(if (sending == true) "Sending…" else "Turn on", { control(true) }, Modifier.weight(1f), enabled = d.online && sending == null, icon = Icons.Outlined.PowerSettingsNew)
                    GhostButton(if (sending == false) "Sending…" else "Turn off", { control(false) }, Modifier.weight(1f), enabled = d.online && sending == null, icon = Icons.Outlined.PowerSettingsNew)
                }
            }
        }

        item {
            Panel {
                PanelHead("Telemetry history")
                Segmented(Range.entries.map { it to it.label }, range, { range = it }, Modifier.fillMaxWidth())
                ErrorNote(history.error, history::reload)
                val h = history.data
                when {
                    h == null -> Loading()
                    h.size < 2 -> Empty("No readings in this period", "Try a longer range, or tap Sync now to pull a fresh reading.")
                    else -> HistoryChart(h)
                }
            }
        }

        item {
            Panel {
                PanelHead("Installation") { Badge(prettyStatus(d.status), statusTone(d.status)); if (!d.online && d.status?.uppercase() != "OFFLINE") { Spacer(Modifier.width(4.dp)); Badge("Offline", Tone.DANGER) } }
                Row { FactText("Feeder", d.feederId ?: "—", Modifier.weight(1f)); FactText("Street lights", num(d.noOfStreetLights, 0), Modifier.weight(1f)); FactText("Watts each", "${num(d.ratedStreetLightWattage, 0)} W", Modifier.weight(1f)) }
                Row { FactText("Y factor", num(d.yFactor, 2), Modifier.weight(1f)); FactText("Coordinates", if (d.hasLocation) "${num(d.latitude, 4)}, ${num(d.longitude, 4)}" else "Not set", Modifier.weight(2f)) }
                if (d.hasLocation) NodeMap(listOf(Node(d, l, openFaults.isNotEmpty())), height = 200.dp, showLegend = false)
            }
        }

        item {
            Panel {
                PanelHead("Faults on this node") { Text("${openFaults.size} open, ${faults.data.orEmpty().size - openFaults.size} resolved", style = MaterialTheme.typography.labelSmall) }
                val list = faults.data
                if (list == null) Loading() else if (list.isEmpty()) Empty("No faults reported", "This node hasn't reported any faults.")
                else list.forEachIndexed { i, f ->
                    if (i > 0) Divider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(f.description ?: "Fault", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            Text("${num(f.wattAtFault)} W at fault, ${fmtTime(f.timestamp)}", style = MaterialTheme.typography.labelSmall)
                        }
                        if (f.resolved) Badge("Resolved", Tone.OK)
                        else GhostButton("Resolve", {
                            scope.launch {
                                try { session.api.resolveFault(f.id); notify.show("Fault #${f.id} resolved", false); faults.update { l2 -> l2.map { if (it.id == f.id) it.copy(resolved = true) else it } } }
                                catch (e: Exception) { notify.show(e.message ?: "Couldn't resolve", true) }
                            }
                        }, icon = Icons.Outlined.Check, small = true)
                    }
                }
            }
        }
    }

    if (editing && d != null) DeviceFormDialog(d, onDismiss = { editing = false }, onSaved = { device.data = it })
    if (deleting && d != null) {
        ConfirmDialog(
            title = "Delete ${d.deviceId}?",
            message = "The node will be removed from monitoring. Its history may be lost on the server.",
            confirmLabel = "Delete device",
            onConfirm = {
                try { session.api.deleteDevice(d.deviceId); notify.show("Deleted ${d.deviceId}", false); nav.popBackStack() }
                catch (e: Exception) { notify.show(e.message ?: "Delete failed", true); throw e }
            },
            onDismiss = { deleting = false },
        )
    }
}

@Composable
private fun Gauge(label: String, value: String, unit: String, modifier: Modifier, color: Color = C.Text) {
    Column(modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(buildAnnotatedString {
            withStyle(SpanStyle(color = color)) { append(value) }
            withStyle(SpanStyle(color = C.Muted, fontSize = Readout.Unit.fontSize)) { append(" $unit") }
        }, style = Readout.Mid, maxLines = 1)
    }
}
