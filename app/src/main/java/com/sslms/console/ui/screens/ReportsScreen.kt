package com.sslms.console.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sslms.console.data.ReportQuery
import com.sslms.console.ui.LocalSession
import com.sslms.console.ui.components.Badge
import com.sslms.console.ui.components.Divider
import com.sslms.console.ui.components.Empty
import com.sslms.console.ui.components.ErrorNote
import com.sslms.console.ui.components.GhostButton
import com.sslms.console.ui.components.Loading
import com.sslms.console.ui.components.LocalNotify
import com.sslms.console.ui.components.Panel
import com.sslms.console.ui.components.PrimaryButton
import com.sslms.console.ui.components.Segmented
import com.sslms.console.ui.components.Tone
import com.sslms.console.ui.components.rememberLoader
import com.sslms.console.ui.theme.C
import com.sslms.console.ui.theme.Readout
import com.sslms.console.util.fmtTime
import com.sslms.console.util.num
import com.sslms.console.util.shareCsv
import com.sslms.console.util.toIso
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val PAGE = 20

private data class Filters(val deviceId: String? = null, val start: LocalDateTime, val end: LocalDateTime, val resolved: Boolean? = null)

@Composable
fun ReportsScreen(nav: NavController) {
    val session = LocalSession.current
    val notify = LocalNotify.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var faultsMode by remember { mutableStateOf(false) }
    var form by remember { mutableStateOf(Filters(start = LocalDateTime.now().minusDays(1), end = LocalDateTime.now())) }
    var applied by remember { mutableStateOf(form) }
    var page by remember { mutableStateOf(0) }
    var exporting by remember { mutableStateOf(false) }

    val devices = rememberLoader("report-devices") { runCatching { session.api.listDevices() }.getOrDefault(emptyList()) }
    fun query(p: Int, size: Int = PAGE) = ReportQuery(applied.deviceId, toIso(applied.start), toIso(applied.end), if (faultsMode) applied.resolved else null, p, size)
    val telemetry = rememberLoader("rep-t-$applied-$page-$faultsMode") { if (!faultsMode) session.api.telemetryReport(query(page)) else null }
    val faults = rememberLoader("rep-f-$applied-$page-$faultsMode") { if (faultsMode) session.api.faultReport(query(page)) else null }
    val loader = if (faultsMode) faults else telemetry
    val total = (if (faultsMode) faults.data?.total else telemetry.data?.total) ?: 0
    val pages = (if (faultsMode) faults.data?.pages else telemetry.data?.pages) ?: 0

    fun export() = scope.launch {
        exporting = true
        try {
            if (faultsMode) {
                val first = session.api.faultReport(query(0, 500)); var rows = first.rows
                for (p in 1 until minOf(first.pages, 20)) rows = rows + session.api.faultReport(query(p, 500)).rows
                shareCsv(context, "sslms-fault-report.csv", listOf("ID", "Device", "Description", "Watts at fault", "Resolved", "Timestamp"),
                    rows.map { listOf(it.id, it.deviceId, it.description, it.wattAtFault, it.resolved, it.timestamp) })
            } else {
                val first = session.api.telemetryReport(query(0, 500)); var rows = first.rows
                for (p in 1 until minOf(first.pages, 20)) rows = rows + session.api.telemetryReport(query(p, 500)).rows
                shareCsv(context, "sslms-telemetry-report.csv", listOf("Device", "Voltage (V)", "Current (A)", "Power (W)", "Timestamp"),
                    rows.map { listOf(it.deviceId, it.voltage, it.current, it.power, it.timestamp) })
            }
        } catch (e: Exception) { notify.show(e.message ?: "Export failed", true) } finally { exporting = false }
    }

    RefreshPage(refreshing = loader.loading && loader.data != null, onRefresh = loader::reload) {
        item {
            ScreenHeader("Reports", "Filter by device and period, then export") {
                IconButton(onClick = { export() }, enabled = !exporting && total > 0) { Icon(Icons.Outlined.FileDownload, "Export CSV", tint = C.Text) }
            }
        }
        item { Segmented(listOf(false to "Telemetry", true to "Faults"), faultsMode, { faultsMode = it; page = 0 }, Modifier.fillMaxWidth()) }
        item {
            Panel {
                Picker("Device", form.deviceId ?: "All devices", listOf<Pair<String?, String>>(null to "All devices") + devices.data.orEmpty().map { it.deviceId to listOfNotNull(it.deviceId, it.area).joinToString(", ") }) { form = form.copy(deviceId = it) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DateTimeBox("From", form.start, Modifier.weight(1f)) { form = form.copy(start = it) }
                    DateTimeBox("To", form.end, Modifier.weight(1f)) { form = form.copy(end = it) }
                }
                if (faultsMode) Picker("State", when (form.resolved) { null -> "Any"; true -> "Resolved"; false -> "Open" }, listOf<Pair<Boolean?, String>>(null to "Any", false to "Open", true to "Resolved")) { form = form.copy(resolved = it) }
                PrimaryButton("Run report", { page = 0; applied = form }, Modifier.fillMaxWidth())
            }
        }
        item { ErrorNote(loader.error, loader::reload) }
        item {
            Panel {
                if (loader.data == null) Loading()
                else if (!faultsMode) {
                    val rows = telemetry.data?.rows.orEmpty()
                    if (rows.isEmpty()) Empty("Nothing in this period", "Widen the date range or pick a different device.") else {
                        Text("On this page: average ${num(rows.mapNotNull { it.voltage }.average())} V, peak ${num(rows.maxOf { it.power ?: 0.0 }, 0)} W", style = MaterialTheme.typography.bodySmall)
                        rows.forEachIndexed { i, t ->
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
                } else {
                    val rows = faults.data?.rows.orEmpty()
                    if (rows.isEmpty()) Empty("Nothing in this period", "Widen the date range or pick a different device.")
                    else rows.forEachIndexed { i, f ->
                        if (i > 0) Divider()
                        Row(Modifier.fillMaxWidth().clickable { nav.navigate("device/${f.deviceId}") }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(f.description ?: "Fault", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                Text("${f.deviceId}, ${num(f.wattAtFault)} W, ${fmtTime(f.timestamp)}", style = MaterialTheme.typography.labelSmall)
                            }
                            if (f.resolved) Badge("Resolved", Tone.OK) else Badge("Open", Tone.DANGER)
                        }
                    }
                }
                if (total > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${num(total, 0)} records" + if (pages > 1) ", page ${page + 1} of $pages" else "", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                        if (pages > 1) {
                            GhostButton("Previous", { page-- }, enabled = page > 0, small = true)
                            Box(Modifier.width(6.dp))
                            GhostButton("Next", { page++ }, enabled = page < pages - 1, small = true)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> Picker(label: String, value: String, options: List<Pair<T, String>>, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FieldBox(label, value, Modifier.fillMaxWidth()) { open = true }
        DropdownMenu(open, { open = false }, containerColor = C.Panel2) {
            options.forEach { (v, l) -> DropdownMenuItem(text = { Text(l) }, onClick = { open = false; onPick(v) }) }
        }
    }
}

private val DT = DateTimeFormatter.ofPattern("dd MMM, HH:mm", Locale.ENGLISH)

@Composable
private fun DateTimeBox(label: String, value: LocalDateTime, modifier: Modifier, onPick: (LocalDateTime) -> Unit) {
    val context = LocalContext.current
    FieldBox(label, value.format(DT), modifier) {
        DatePickerDialog(context, { _, y, m, d ->
            TimePickerDialog(context, { _, h, min -> onPick(LocalDateTime.of(y, m + 1, d, h, min)) }, value.hour, value.minute, true).show()
        }, value.year, value.monthValue - 1, value.dayOfMonth).show()
    }
}

@Composable
private fun FieldBox(label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(C.Night2).border(1.dp, C.Line2, RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
            Icon(Icons.Outlined.ArrowDropDown, null, tint = C.Muted)
        }
    }
}
