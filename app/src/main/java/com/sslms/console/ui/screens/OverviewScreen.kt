package com.sslms.console.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sslms.console.data.LampState
import com.sslms.console.data.Node
import com.sslms.console.data.loadFleet
import com.sslms.console.ui.LocalSession
import com.sslms.console.ui.components.ConfirmDialog
import com.sslms.console.ui.components.Divider
import com.sslms.console.ui.components.Empty
import com.sslms.console.ui.components.ErrorNote
import com.sslms.console.ui.components.GhostButton
import com.sslms.console.ui.components.Lamp
import com.sslms.console.ui.components.Loading
import com.sslms.console.ui.components.LocalNotify
import com.sslms.console.ui.components.Meter
import com.sslms.console.ui.components.NodeMap
import com.sslms.console.ui.components.Panel
import com.sslms.console.ui.components.PanelHead
import com.sslms.console.ui.components.LitButton
import com.sslms.console.ui.components.rememberLoader
import com.sslms.console.ui.theme.C
import com.sslms.console.ui.theme.Readout
import com.sslms.console.util.ago
import com.sslms.console.util.fmtTime
import com.sslms.console.util.num
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun OverviewScreen(nav: NavController) {
    val session = LocalSession.current
    val notify = LocalNotify.current
    val scope = rememberCoroutineScope()
    val fleet = rememberLoader("fleet", refreshMs = 30_000) { session.api.loadFleet() }
    val log = rememberLoader("log", refreshMs = 30_000) { session.api.telemetryLog() }
    var confirm by remember { mutableStateOf<Boolean?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }

    val nodes = fleet.data?.nodes.orEmpty()
    val openFaults = fleet.data?.faults.orEmpty().filter { !it.resolved }
    val online = nodes.count { it.device.online }
    val lit = nodes.filter { it.state == LampState.ON }
    val lamps = nodes.sumOf { it.device.noOfStreetLights ?: 0 }
    val litLamps = lit.sumOf { it.device.noOfStreetLights ?: 0 }
    val load = nodes.filter { it.state != LampState.OFFLINE }.sumOf { it.power ?: 0.0 }
    val feeders = nodes.mapNotNull { it.device.feederId }.toSet().size

    fun refresh() { fleet.reload(); log.reload() }

    fun control(id: String, on: Boolean) = scope.launch {
        try { notify.show(session.api.control(id, on), false); delay(400); fleet.reload() }
        catch (e: Exception) { notify.show(e.message ?: "Command failed", true) }
    }

    RefreshPage(refreshing = fleet.loading && fleet.data != null, onRefresh = ::refresh) {
        item {
            ScreenHeader("Overview", if (fleet.data != null) "$online of ${nodes.size} nodes online on $feeders feeders" else "Loading the network…")
        }
        item { ErrorNote(fleet.error, fleet::reload) }

        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Panel).border(1.dp, C.Line, RoundedCornerShape(16.dp))) {
                Row(Modifier.height(IntrinsicSize.Min)) {
                    Stat(Modifier.weight(1f), "Nodes online", if (fleet.data != null) "$online" else "—", "/ ${nodes.size}") { Meter(online.toFloat(), nodes.size.toFloat(), C.Teal) }
                    VLine()
                    Stat(Modifier.weight(1f), "Lamps lit", if (fleet.data != null) num(litLamps, 0) else "—", "/ ${num(lamps, 0)}", valueColor = C.Lit) { Meter(litLamps.toFloat(), lamps.toFloat(), C.Lit) }
                }
                Divider()
                Row(Modifier.height(IntrinsicSize.Min)) {
                    Stat(Modifier.weight(1f), "Live load", if (fleet.data != null) num(load / 1000, 2) else "—", "kW") {
                        Text("${lit.size} of ${nodes.size} drawing power", style = MaterialTheme.typography.labelSmall)
                    }
                    VLine()
                    Stat(Modifier.weight(1f).clickable { nav.navigate("faults") }, "Open faults", if (fleet.data != null) "${openFaults.size}" else "—", null,
                        valueColor = if (openFaults.isNotEmpty()) C.Red else C.Text) {
                        Text(if (openFaults.isNotEmpty()) "Latest ${ago(openFaults.first().timestamp)}" else "Nothing waiting", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton("All off", { confirm = false }, Modifier.weight(1f), icon = Icons.Outlined.PowerSettingsNew, enabled = online > 0)
                LitButton("All on", { confirm = true }, Modifier.weight(1f), icon = Icons.Outlined.PowerSettingsNew, enabled = online > 0)
            }
        }

        item {
            when {
                fleet.data == null -> Panel { Loading() }
                nodes.isEmpty() -> Panel { Empty("No devices registered yet", "Add a node with its location and lamp count to start monitoring it.") { GhostButton("Add a device", { nav.navigate("devices") }) } }
                else -> Box {
                    NodeMap(nodes, height = 300.dp, selectedId = selected, onSelect = { selected = it })
                    IconButton(onClick = { nav.navigate("map") }, Modifier.align(Alignment.TopEnd).padding(8.dp).size(40.dp).clip(RoundedCornerShape(10.dp)).background(C.Night.copy(alpha = 0.85f))) {
                        Icon(Icons.Outlined.OpenInFull, "Full-screen map", tint = C.Text, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        nodes.find { it.device.deviceId == selected }?.let { n ->
            item { SelectedNodeCard(n, onOpen = { nav.navigate("device/${n.device.deviceId}") }, onControl = { on -> control(n.device.deviceId, on) }, onClose = { selected = null }) }
        }

        if (nodes.isNotEmpty()) {
            item {
                Panel(padding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 8.dp)) {
                    PanelHead("Nodes") { TextButton(onClick = { nav.navigate("devices") }) { Text("All devices", color = C.Sodium) } }
                    Column {
                        nodes.forEach { n -> NodeRow(n, n.device.deviceId == selected) { selected = n.device.deviceId } }
                    }
                }
            }
        }

        item {
            Panel {
                PanelHead("Latest readings") { TextButton(onClick = { nav.navigate("reports") }) { Text("All telemetry", color = C.Sodium) } }
                ErrorNote(log.error, log::reload)
                val rows = log.data.orEmpty().take(8)
                if (log.data == null) Loading() else if (rows.isEmpty()) Empty("No readings have arrived yet") else rows.forEachIndexed { i, t ->
                    if (i > 0) Divider()
                    Row(Modifier.fillMaxWidth().clickable { t.deviceId?.let { nav.navigate("device/$it") } }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.deviceId ?: "—", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            Text(fmtTime(t.timestamp), style = MaterialTheme.typography.labelSmall)
                        }
                        Text("${num(t.voltage)} V", style = Readout.Small, modifier = Modifier.width(66.dp))
                        Text("${num(t.current, 2)} A", style = Readout.Small, modifier = Modifier.width(58.dp))
                        Text("${num(t.power, 0)} W", style = Readout.Small.copy(color = C.Sodium), modifier = Modifier.width(66.dp))
                    }
                }
            }
        }

        item {
            Panel {
                PanelHead("Open faults") { TextButton(onClick = { nav.navigate("faults") }) { Text("Manage faults", color = C.Sodium) } }
                if (openFaults.isEmpty()) Empty(if (fleet.data == null) "Loading…" else "No open faults", if (fleet.data != null) "Every reported fault has been resolved." else null)
                else openFaults.take(5).forEach { f ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).clickable { nav.navigate("device/${f.deviceId}") }) {
                        Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(C.Red))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.padding(vertical = 2.dp)) {
                            Text(f.description ?: "Fault", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            Text("${f.deviceId}, ${num(f.wattAtFault)} W at fault, ${ago(f.timestamp)}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }

    confirm?.let { on ->
        ConfirmDialog(
            title = if (on) "Turn on every light?" else "Turn off every light?",
            message = "This sends ${if (on) "ON" else "OFF"} to all $online online nodes at once. Offline nodes won't receive it.",
            confirmLabel = if (on) "Turn all on" else "Turn all off",
            danger = !on,
            onConfirm = {
                try { notify.show(session.api.broadcast(on), false); delay(400); refresh() }
                catch (e: Exception) { notify.show(e.message ?: "Broadcast failed", true); throw e }
            },
            onDismiss = { confirm = null },
        )
    }
}

@Composable
private fun VLine() = Box(Modifier.width(1.dp).fillMaxHeight().background(C.Line))

@Composable
private fun Stat(modifier: Modifier, label: String, value: String, of: String?, valueColor: Color = C.Text, foot: @Composable () -> Unit) {
    Column(modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(buildAnnotatedString {
            withStyle(SpanStyle(color = valueColor)) { append(value) }
            if (of != null) withStyle(SpanStyle(color = C.Muted, fontSize = Readout.Unit.fontSize)) { append(" $of") }
        }, style = Readout.Big)
        foot()
    }
}

@Composable
private fun NodeRow(n: Node, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (selected) C.Panel2 else Color.Transparent).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Lamp(n.state)
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(n.device.deviceId, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                if (n.faulted) { Spacer(Modifier.width(6.dp)); Icon(Icons.Outlined.WarningAmber, "Open fault", tint = C.Red, modifier = Modifier.size(15.dp)) }
            }
            Text(n.device.area ?: "—", style = MaterialTheme.typography.labelSmall)
        }
        Text(if (n.state == LampState.OFFLINE) "Offline" else n.power?.let { "${num(it, 0)} W" } ?: "—", style = Readout.Small.copy(color = C.Muted))
        Spacer(Modifier.width(6.dp))
    }
}

@Composable
private fun SelectedNodeCard(n: Node, onOpen: () -> Unit, onControl: (Boolean) -> Unit, onClose: () -> Unit) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Lamp(n.state, 14.dp)
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text("${n.device.deviceId}  ${n.device.feederId ?: ""}", style = MaterialTheme.typography.titleMedium)
                Text(n.device.placeLine, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Close", tint = C.Muted) }
        }
        Text(
            when (n.state) { LampState.OFFLINE -> "Offline"; LampState.ON -> "Lit, drawing ${num(n.power, 0)} W"; LampState.OFF -> "Switched off"; LampState.UNKNOWN -> "No reading yet" } +
                if (n.faulted) ", with an open fault" else "",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (n.state != LampState.OFFLINE) {
                LitButton("Turn on", { onControl(true) }, Modifier.weight(1f))
                GhostButton("Turn off", { onControl(false) }, Modifier.weight(1f))
            }
            GhostButton("Open", onOpen, Modifier.weight(1f), icon = Icons.Outlined.ChevronRight)
        }
    }
}
