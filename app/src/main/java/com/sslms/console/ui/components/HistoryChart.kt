package com.sslms.console.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sslms.console.data.Telemetry
import com.sslms.console.ui.theme.BarlowCondensed
import com.sslms.console.ui.theme.C
import com.sslms.console.util.fmtClock
import com.sslms.console.util.fmtDay
import com.sslms.console.util.fmtTime
import com.sslms.console.util.num
import com.sslms.console.util.parseTime
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.pow

private data class Pt(val t: Long, val r: Telemetry)

/** Power (amber area, left axis) and voltage (blue line, right axis) over time. */
@Composable
fun HistoryChart(data: List<Telemetry>, height: Dp = 240.dp) {
    val pts = remember(data) {
        data.mapNotNull { r -> parseTime(r.timestamp)?.let { Pt(it.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), r) } }.sortedBy { it.t }
    }
    if (pts.size < 2) { Empty("Not enough readings to draw a chart"); return }

    val measurer = rememberTextMeasurer()
    val axisStyle = TextStyle(fontFamily = BarlowCondensed, fontSize = 12.sp, color = C.Muted)
    val voltStyle = axisStyle.copy(color = C.Blue)
    var touchX by remember(data) { mutableStateOf<Float?>(null) }

    val t0 = pts.first().t; val t1 = pts.last().t
    val span = (t1 - t0).coerceAtLeast(1)
    val maxP = niceMax(pts.maxOf { it.r.power ?: 0.0 })
    val vMin = 200.0; val vMax = 260.0
    val multiDay = span > 2 * 86_400_000L

    val dens = androidx.compose.ui.platform.LocalDensity.current.density
    val left = 44f * dens; val right = 34f * dens; val top = 8f * dens; val bottom = 24f * dens
    var widthPx by remember { mutableStateOf(0f) }
    val hoverIndex: Int? = touchX?.let { tx ->
        val w = widthPx - left - right
        if (w <= 0f) null else {
            val target = t0 + ((tx - left) / w * span).toLong()
            pts.indices.minByOrNull { kotlin.math.abs(pts[it].t - target) }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            Canvas(
                Modifier.fillMaxWidth().height(height)
                    .onSizeChanged { widthPx = it.width.toFloat() }
                    .pointerInput(data) { detectTapGestures { touchX = it.x } }
                    .pointerInput(data) { detectDragGestures(onDragEnd = { }, onDrag = { c, _ -> touchX = c.position.x }) },
            ) {
                val l = left; val r = right
                val w = size.width - l - r; val h = size.height - top - bottom
                fun x(t: Long) = l + (t - t0).toFloat() / span * w
                fun yP(p: Double) = top + h - (p / maxP).toFloat() * h
                fun yV(v: Double) = top + h - ((v - vMin) / (vMax - vMin)).toFloat().coerceIn(0f, 1f) * h

                // grid + left labels
                for (i in 0..4) {
                    val y = top + h * i / 4
                    drawLine(C.Line, Offset(l, y), Offset(l + w, y), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 10f)))
                    val pv = maxP * (4 - i) / 4
                    val tl = measurer.measure(num(pv, 0), axisStyle)
                    drawText(tl, topLeft = Offset(l - tl.size.width - 6f, y - tl.size.height / 2))
                    val vv = vMin + (vMax - vMin) * (4 - i) / 4
                    val tv = measurer.measure(num(vv, 0), voltStyle)
                    drawText(tv, topLeft = Offset(l + w + 6f, y - tv.size.height / 2))
                }
                // x labels
                val ticks = 5
                for (i in 0..ticks) {
                    val t = t0 + span * i / ticks
                    val lt = java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(t), ZoneId.systemDefault())
                    val tl = measurer.measure(if (multiDay) fmtDay(lt) else fmtClock(lt), axisStyle)
                    val cx = (x(t) - tl.size.width / 2).coerceIn(l - 4f, l + w - tl.size.width + 4f)
                    drawText(tl, topLeft = Offset(cx, top + h + 6f))
                }
                // power step area
                val area = Path(); val line = Path()
                pts.forEachIndexed { i, p ->
                    val px = x(p.t); val py = yP(p.r.power ?: 0.0)
                    if (i == 0) { area.moveTo(px, top + h); area.lineTo(px, py); line.moveTo(px, py) }
                    else { area.lineTo(px, yP(pts[i - 1].r.power ?: 0.0)); area.lineTo(px, py); line.lineTo(px, yP(pts[i - 1].r.power ?: 0.0)); line.lineTo(px, py) }
                }
                area.lineTo(x(pts.last().t), top + h); area.close()
                drawPath(area, Brush.verticalGradient(listOf(C.Sodium.copy(alpha = 0.32f), Color.Transparent), startY = top, endY = top + h))
                drawPath(line, C.Sodium, style = Stroke(2.dp.toPx()))
                // voltage line
                val vl = Path()
                pts.forEachIndexed { i, p -> val px = x(p.t); val py = yV(p.r.voltage ?: vMin); if (i == 0) vl.moveTo(px, py) else vl.lineTo(px, py) }
                drawPath(vl, C.Blue, style = Stroke(1.5.dp.toPx()))

                // crosshair
                hoverIndex?.let { idx ->
                    val px = x(pts[idx].t)
                    drawLine(C.Muted, Offset(px, top), Offset(px, top + h), 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                    drawCircle(C.Sodium, 4.dp.toPx(), Offset(px, yP(pts[idx].r.power ?: 0.0)))
                    drawCircle(C.Blue, 3.dp.toPx(), Offset(px, yV(pts[idx].r.voltage ?: vMin)))
                }
            }
        }
        val hover = hoverIndex?.let { pts.getOrNull(it)?.r }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (hover != null) {
                Text(fmtTime(hover.timestamp), style = MaterialTheme.typography.labelMedium)
                Key(C.Sodium, "${num(hover.power, 0)} W"); Key(C.Blue, "${num(hover.voltage)} V"); Key(C.Teal, "${num(hover.current, 2)} A")
            } else {
                Key(C.Sodium, "Power, W (left)"); Key(C.Blue, "Voltage, V (right)")
                Spacer(Modifier.weight(1f))
                Text("Touch the chart to read values", style = MaterialTheme.typography.labelSmall.copy(color = C.Faint))
            }
        }
    }
}

@Composable
private fun Key(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

private fun niceMax(v: Double): Double {
    if (v <= 0) return 10.0
    val mag = 10.0.pow(kotlin.math.floor(log10(v)))
    val n = v / mag
    val nice = when { n <= 1 -> 1.0; n <= 2 -> 2.0; n <= 2.5 -> 2.5; n <= 5 -> 5.0; else -> 10.0 }
    return ceil(nice * mag)
}
