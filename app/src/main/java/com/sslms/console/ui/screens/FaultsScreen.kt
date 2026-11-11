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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
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
import com.sslms.console.data.Fault
import com.sslms.console.ui.LocalSession
import com.sslms.console.ui.components.Badge
import com.sslms.console.ui.components.ConfirmDialog
import com.sslms.console.ui.components.Empty
import com.sslms.console.ui.components.ErrorNote
import com.sslms.console.ui.components.GhostButton
import com.sslms.console.ui.components.Loading
import com.sslms.console.ui.components.LocalNotify
import com.sslms.console.ui.components.Panel
import com.sslms.console.ui.components.Segmented
import com.sslms.console.ui.components.Tone
import com.sslms.console.ui.components.rememberLoader
import com.sslms.console.ui.theme.C
import com.sslms.console.util.ago
import com.sslms.console.util.fmtTime
import com.sslms.console.util.num
import com.sslms.console.util.shareCsv
import kotlinx.coroutines.launch

private enum class FaultView { OPEN, RESOLVED, ALL }

@Composable
fun FaultsScreen(nav: NavController) {
    val session = LocalSession.current
    val notify = LocalNotify.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val faults = rememberLoader("faults", refreshMs = 60_000) { session.api.faults() }
    var view by remember { mutableStateOf(FaultView.OPEN) }
    var deleting by remember { mutableStateOf<Fault?>(null) }
    var busy by remember { mutableStateOf<Long?>(null) }

    val all = faults.data.orEmpty()
    val open = all.count { !it.resolved }
    val resolved = all.size - open
    val rows = all.filter { when (view) { FaultView.OPEN -> !it.resolved; FaultView.RESOLVED -> it.resolved; FaultView.ALL -> true } }

    fun resolve(f: Fault) = scope.launch {
        busy = f.id
        try {
            val r = session.api.resolveFault(f.id)
            faults.update { list -> list.map { if (it.id == f.id) (r ?: it).copy(resolved = true) else it } }
            notify.show("Fault #${f.id} on ${f.deviceId} resolved", false)
        } catch (e: Exception) { notify.show(e.message ?: "Couldn't resolve", true) } finally { busy = null }
    }

    RefreshPage(refreshing = faults.loading && faults.data != null, onRefresh = faults::reload) {
        item {
            ScreenHeader("Faults", if (faults.data != null) "$open open and $resolved resolved" else "Loading faults…") {
                IconButton(enabled = rows.isNotEmpty(), onClick = {
                    shareCsv(context, "sslms-faults.csv", listOf("ID", "Device", "Description", "Watts at fault", "Resolved", "Timestamp"),
                        rows.map { listOf(it.id, it.deviceId, it.description, it.wattAtFault, it.resolved, it.timestamp) })
                }) { Icon(Icons.Outlined.FileDownload, "Export CSV", tint = C.Text) }
            }
        }
        item { Segmented(listOf(FaultView.OPEN to "Open  $open", FaultView.RESOLVED to "Resolved  $resolved", FaultView.ALL to "All  ${all.size}"), view, { view = it }, Modifier.fillMaxWidth()) }
        item { ErrorNote(faults.error, faults::reload) }
        when {
            faults.data == null -> item { Loading() }
            rows.isEmpty() -> item { Panel { Empty(if (view == FaultView.OPEN) "No open faults" else "No faults here", if (view == FaultView.OPEN) "Every reported fault has been resolved." else null) } }
            else -> items(rows, key = { it.id }) { f ->
                FaultCard(f, busy == f.id, onOpenDevice = { nav.navigate("device/${f.deviceId}") }, onResolve = { resolve(f) }, onDelete = { deleting = f })
            }
        }
    }

    deleting?.let { f ->
        ConfirmDialog(
            title = "Delete fault #${f.id}?",
            message = "\"${f.description}\" on ${f.deviceId} will be removed from the fault history permanently. Resolve it instead if you want to keep the record.",
            confirmLabel = "Delete fault",
            onConfirm = {
                try { notify.show(session.api.deleteFault(f.id), false); faults.update { list -> list.filter { it.id != f.id } } }
                catch (e: Exception) { notify.show(e.message ?: "Delete failed", true); throw e }
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun FaultCard(f: Fault, busy: Boolean, onOpenDevice: () -> Unit, onResolve: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(16.dp)).background(C.Panel).border(1.dp, C.Line, RoundedCornerShape(16.dp)),
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(if (f.resolved) C.Line2 else C.Red))
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(f.description ?: "Fault", style = MaterialTheme.typography.titleSmall.copy(color = if (f.resolved) C.Muted else C.Text), modifier = Modifier.weight(1f))
                if (f.resolved) Badge("Resolved", Tone.OK) else Badge("Open", Tone.DANGER)
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Delete fault ${f.id}", tint = C.Muted) }
            }
            Text(
                "${f.deviceId}, ${num(f.wattAtFault)} W at fault",
                style = MaterialTheme.typography.bodyMedium.copy(color = C.Sodium, fontWeight = FontWeight.Medium),
                modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable(onClick = onOpenDevice),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("#${f.id}, reported ${fmtTime(f.timestamp)} (${ago(f.timestamp)})", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                if (!f.resolved) { Spacer(Modifier.width(8.dp)); GhostButton("Resolve", onResolve, enabled = !busy, icon = Icons.Outlined.Check, small = true); Spacer(Modifier.width(8.dp)) }
            }
        }
    }
}
