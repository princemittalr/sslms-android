package com.sslms.console.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sslms.console.data.LampState
import com.sslms.console.data.Node
import com.sslms.console.ui.theme.C
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import kotlin.math.cos
import kotlin.math.sin

/**
 * Map of nodes, each drawn as a lamp dot: glowing amber when lit, hollow when off,
 * grey when offline, red ring for an open fault. Tapping a dot calls onSelect.
 */
@Composable
fun NodeMap(
    nodes: List<Node>,
    modifier: Modifier = Modifier,
    height: Dp = 300.dp,
    selectedId: String? = null,
    onSelect: ((String) -> Unit)? = null,
    showLegend: Boolean = true,
    interactive: Boolean = true,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val map = remember { createMap(context, interactive) }
    val placed = nodes.filter { it.device.hasLocation }
    val signature = placed.joinToString("|") { "${it.device.deviceId}:${it.device.latitude},${it.device.longitude}" }

    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) { Lifecycle.Event.ON_RESUME -> map.onResume(); Lifecycle.Event.ON_PAUSE -> map.onPause(); else -> {} }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs); map.onDetach() }
    }

    // Fit to nodes when the set of locations changes.
    LaunchedEffect(signature) {
        val pts = placed.map { GeoPoint(it.device.latitude!!, it.device.longitude!!) }
        map.post {
            when {
                pts.isEmpty() -> map.controller.setCenter(GeoPoint(23.7957, 86.4304))
                pts.size == 1 -> { map.controller.setZoom(15.5); map.controller.setCenter(pts[0]) }
                else -> runCatching {
                    map.zoomToBoundingBox(BoundingBox.fromGeoPointsSafe(pts).increaseByScale(1.3f), false, (32 * density).toInt(), 16.0, null)
                }
            }
        }
    }
    LaunchedEffect(selectedId) {
        placed.find { it.device.deviceId == selectedId }?.let { n ->
            map.controller.animateTo(GeoPoint(n.device.latitude!!, n.device.longitude!!), maxOf(map.zoomLevelDouble, 14.5), 500L)
        }
    }

    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(16.dp))) {
        AndroidView(factory = { map }, modifier = Modifier.fillMaxWidth().height(height), update = { mv ->
            mv.overlays.removeAll { it is Marker }
            placed.forEach { n ->
                mv.overlays += Marker(mv).apply {
                    position = GeoPoint(n.device.latitude!!, n.device.longitude!!)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = BitmapDrawable(context.resources, dotBitmap(n.state, n.faulted, n.device.deviceId == selectedId, density))
                    title = n.device.deviceId
                    setInfoWindow(null)
                    setOnMarkerClickListener { _, _ -> onSelect?.invoke(n.device.deviceId); true }
                }
            }
            mv.invalidate()
        })
        if (showLegend) {
            Row(
                Modifier.align(Alignment.BottomStart).padding(10.dp).clip(RoundedCornerShape(10.dp))
                    .background(C.Night.copy(alpha = 0.88f)).border(1.dp, C.Line, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                LegendItem(LampState.ON, "Lit"); LegendItem(LampState.OFF, "Off"); LegendItem(LampState.OFFLINE, "Offline")
            }
        }
        Text("© OpenStreetMap contributors", Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 3.dp),
            style = MaterialTheme.typography.labelSmall.copy(color = C.Faint, fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.85f))
    }
}

@Composable
private fun LegendItem(state: LampState, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Lamp(state, 8.dp)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@SuppressLint("ClickableViewAccessibility")
private fun createMap(context: Context, interactive: Boolean) = MapView(context).apply {
    setTileSource(TileSourceFactory.MAPNIK)
    setMultiTouchControls(interactive)
    isClickable = interactive
    zoomController.setVisibility(if (interactive) CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT else CustomZoomButtonsController.Visibility.NEVER)
    minZoomLevel = 4.0
    maxZoomLevel = 19.0
    isTilesScaledToDpi = true
    setBackgroundColor(C.Night.toArgb())
    // The night filter also applies to loading placeholders, so use OSM's light land colour,
    // which the filter turns into the night background instead of a grey flash.
    overlayManager.tilesOverlay.loadingBackgroundColor = 0xFFF2EFE9.toInt()
    overlayManager.tilesOverlay.loadingLineColor = 0xFFE6E2DA.toInt()
    overlayManager.tilesOverlay.setColorFilter(nightFilter())
    controller.setZoom(13.0)
    controller.setCenter(GeoPoint(23.7957, 86.4304))
    // Keep vertical page scrolling from stealing map drags.
    setOnTouchListener { v, e ->
        if (e.action == MotionEvent.ACTION_DOWN || e.action == MotionEvent.ACTION_MOVE) v.parent?.requestDisallowInterceptTouchEvent(interactive)
        false
    }
}

/** CSS-equivalent of invert(1) hue-rotate(200deg) brightness(.78) saturate(.55). */
private fun nightFilter(): ColorMatrixColorFilter {
    val m = ColorMatrix(floatArrayOf(-1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f))
    val rad = Math.toRadians(200.0)
    val c = cos(rad).toFloat(); val s = sin(rad).toFloat()
    m.postConcat(ColorMatrix(floatArrayOf(
        0.213f + c * 0.787f - s * 0.213f, 0.715f - c * 0.715f - s * 0.715f, 0.072f - c * 0.072f + s * 0.928f, 0f, 0f,
        0.213f - c * 0.213f + s * 0.143f, 0.715f + c * 0.285f + s * 0.140f, 0.072f - c * 0.072f - s * 0.283f, 0f, 0f,
        0.213f - c * 0.213f - s * 0.787f, 0.715f - c * 0.715f + s * 0.715f, 0.072f + c * 0.928f + s * 0.072f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )))
    val b = 0.78f
    m.postConcat(ColorMatrix(floatArrayOf(b, 0f, 0f, 0f, 0f, 0f, b, 0f, 0f, 0f, 0f, 0f, b, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
    val sat = 0.55f
    m.postConcat(ColorMatrix(floatArrayOf(
        0.213f + 0.787f * sat, 0.715f - 0.715f * sat, 0.072f - 0.072f * sat, 0f, 0f,
        0.213f - 0.213f * sat, 0.715f + 0.285f * sat, 0.072f - 0.072f * sat, 0f, 0f,
        0.213f - 0.213f * sat, 0.715f - 0.715f * sat, 0.072f + 0.928f * sat, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )))
    return ColorMatrixColorFilter(m)
}

private fun dotBitmap(state: LampState, faulted: Boolean, selected: Boolean, density: Float): Bitmap {
    val scale = if (selected) 1.35f else 1f
    val size = (44 * density).toInt()
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val cv = Canvas(bmp)
    val cx = size / 2f
    val r = 7 * density * scale
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    when (state) {
        LampState.ON -> {
            p.shader = RadialGradient(cx, cx, r * 3f, intArrayOf(C.Sodium.copy(alpha = 0.6f).toArgb(), 0x00FFB547), null, Shader.TileMode.CLAMP)
            cv.drawCircle(cx, cx, r * 3f, p)
            p.shader = null; p.color = C.Sodium.toArgb()
            cv.drawCircle(cx, cx, r, p)
        }
        LampState.OFF -> {
            p.color = 0xFF2A3260.toInt(); cv.drawCircle(cx, cx, r, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = 2 * density; p.color = C.Muted.toArgb(); cv.drawCircle(cx, cx, r, p)
        }
        LampState.OFFLINE, LampState.UNKNOWN -> { p.color = C.Line2.toArgb(); cv.drawCircle(cx, cx, r, p) }
    }
    if (faulted) {
        p.shader = null; p.style = Paint.Style.STROKE; p.strokeWidth = 2 * density; p.color = C.Red.toArgb()
        cv.drawCircle(cx, cx, r + 4 * density, p)
    }
    return bmp
}
