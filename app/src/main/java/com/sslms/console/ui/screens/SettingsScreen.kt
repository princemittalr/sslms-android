package com.sslms.console.ui.screens

import android.app.TimePickerDialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sslms.console.ui.LocalSession
import com.sslms.console.ui.components.ConfirmDialog
import com.sslms.console.ui.components.ErrorNote
import com.sslms.console.ui.components.FactText
import com.sslms.console.ui.components.GhostButton
import com.sslms.console.ui.components.Loading
import com.sslms.console.ui.components.LocalNotify
import com.sslms.console.ui.components.Panel
import com.sslms.console.ui.components.PanelHead
import com.sslms.console.ui.components.PrimaryButton
import com.sslms.console.ui.components.rememberLoader
import com.sslms.console.ui.theme.BarlowCondensed
import com.sslms.console.ui.theme.C
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen() {
    val session = LocalSession.current
    val notify = LocalNotify.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = rememberLoader("settings") { session.api.getSettings() }
    var times by remember { mutableStateOf<List<String>>(emptyList()) }
    var saving by remember { mutableStateOf(false) }
    var resetting by remember { mutableStateOf(false) }
    var signingOut by remember { mutableStateOf(false) }

    LaunchedEffect(settings.data) { settings.data?.let { times = it.times } }
    val dirty = settings.data != null && times != settings.data!!.times

    RefreshPage(refreshing = settings.loading && settings.data != null, onRefresh = settings::reload) {
        item { ScreenHeader("Settings", "These apply to every node on the network") }
        item {
            Panel {
                PanelHead("Hardware sync schedule")
                Text("At each time below, every node reports its reading to the server. More syncs give fresher data but use more GSM airtime.", style = MaterialTheme.typography.bodySmall)
                ErrorNote(settings.error, settings::reload)
                if (settings.data == null && settings.loading) Loading() else {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { SyncDial(times) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        times.forEach { t ->
                            Row(Modifier.clip(CircleShape).background(C.Night2).border(1.dp, C.Line2, CircleShape).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Schedule, null, tint = C.Teal, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(t, style = TextStyle(fontFamily = BarlowCondensed, fontSize = 17.sp, color = C.Text))
                                IconButton(onClick = { times = times - t }, Modifier.size(36.dp)) { Icon(Icons.Outlined.Close, "Remove $t", tint = C.Muted, modifier = Modifier.size(16.dp)) }
                            }
                        }
                    }
                    if (times.isEmpty()) Text("No syncs scheduled. Nodes will only report when you tap Sync now.", style = MaterialTheme.typography.bodySmall)
                    GhostButton("Add time", {
                        TimePickerDialog(context, { _, h, m ->
                            val t = "%02d:%02d".format(h, m)
                            if (t in times) notify.show("$t is already scheduled.", true) else times = (times + t).sorted()
                        }, 18, 0, true).show()
                    }, icon = Icons.Outlined.Add)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${times.size} sync${if (times.size == 1) "" else "s"} a day" + if (dirty) ", unsaved" else "", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                        if (dirty) GhostButton("Discard", { times = settings.data!!.times }, small = true)
                        PrimaryButton(if (saving) "Saving…" else "Save", {
                            saving = true
                            scope.launch {
                                try { settings.data = session.api.updateSettings(times.joinToString(",")); notify.show("Sync schedule saved", false) }
                                catch (e: Exception) { notify.show(e.message ?: "Couldn't save", true) } finally { saving = false }
                            }
                        }, enabled = dirty && !saving)
                    }
                }
            }
        }
        item {
            Panel {
                PanelHead("Connection")
                FactText("Mode", if (session.demo) "Demo data on this phone" else "Live SSLMS server")
                if (!session.demo) FactText("Server address", session.baseUrl)
                FactText("Signed in as", session.user.value ?: "—")
                Text("To switch between demo data and a live server, sign out and choose on the sign-in screen.", style = MaterialTheme.typography.bodySmall)
                if (session.demo) GhostButton("Reset demo data", { resetting = true })
                GhostButton("Sign out", { signingOut = true }, icon = Icons.AutoMirrored.Outlined.Logout, color = C.Red)
            }
        }
    }

    if (resetting) ConfirmDialog("Reset demo data?", "Devices, faults and settings you changed in demo mode go back to the starting set.", "Reset demo data",
        onConfirm = { session.resetDemo(); settings.reload(); notify.show("Demo data reset", false) }, onDismiss = { resetting = false })
    if (signingOut) ConfirmDialog("Sign out?", "You'll need your username and password to sign in again.", "Sign out",
        onConfirm = { session.signOut() }, onDismiss = { signingOut = false })
}

/** 24-hour dial; shaded arc is the 18:00–06:00 lighting window, teal ticks are sync times. */
@Composable
private fun SyncDial(times: List<String>) {
    val measurer = rememberTextMeasurer()
    val numStyle = TextStyle(fontFamily = BarlowCondensed, fontSize = 12.sp, color = C.Faint)
    val bigStyle = TextStyle(fontFamily = BarlowCondensed, fontSize = 34.sp, color = C.Text)
    val smallStyle = TextStyle(fontSize = 12.sp, color = C.Muted)
    Canvas(Modifier.size(180.dp)) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension / 2 - 14.dp.toPx()
        fun pt(h: Float, rr: Float): Offset { val a = h / 24f * 2 * Math.PI - Math.PI / 2; return Offset(c.x + rr * cos(a).toFloat(), c.y + rr * sin(a).toFloat()) }
        drawCircle(C.Line, r, c, style = Stroke(10.dp.toPx()))
        // 18:00 -> 06:00 through midnight: start angle at 18h (-180+... ) sweep 180 degrees
        drawArc(C.Sodium.copy(alpha = 0.28f), startAngle = 180f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(c.x - r, c.y - r), size = androidx.compose.ui.geometry.Size(r * 2, r * 2), style = Stroke(10.dp.toPx()))
        listOf(0, 6, 12, 18).forEach { h ->
            val l = measurer.measure("%02d".format(h), numStyle); val p = pt(h.toFloat(), r - 22.dp.toPx())
            drawText(l, topLeft = Offset(p.x - l.size.width / 2, p.y - l.size.height / 2))
        }
        times.forEach { t ->
            val (hh, mm) = t.split(":").map { it.toIntOrNull() ?: 0 }
            val h = hh + mm / 60f
            drawLine(C.Teal, pt(h, r - 9.dp.toPx()), pt(h, r + 9.dp.toPx()), 3.dp.toPx(), cap = StrokeCap.Round)
        }
        val big = measurer.measure("${times.size}", bigStyle)
        drawText(big, topLeft = Offset(c.x - big.size.width / 2, c.y - big.size.height / 2 - 8.dp.toPx()))
        val sm = measurer.measure("syncs a day", smallStyle)
        drawText(sm, topLeft = Offset(c.x - sm.size.width / 2, c.y + 14.dp.toPx()))
    }
}
