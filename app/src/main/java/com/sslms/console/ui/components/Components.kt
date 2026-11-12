package com.sslms.console.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.sslms.console.data.LampState
import com.sslms.console.ui.theme.C
import kotlinx.coroutines.launch

/** The signature element: a lamp dot that glows green when the lights are on. */
@Composable
fun Lamp(state: LampState, size: Dp = 12.dp) {
    val label = when (state) { LampState.ON -> "Lights on"; LampState.OFF -> "Lights off"; LampState.OFFLINE -> "Offline"; LampState.UNKNOWN -> "No reading" }
    Canvas(Modifier.size(size * 2).semantics { contentDescription = label }) {
        val r = size.toPx() / 2
        when (state) {
            LampState.ON -> {
                drawCircle(Brush.radialGradient(listOf(C.Lit.copy(alpha = 0.55f), Color.Transparent), radius = r * 2), radius = r * 2)
                drawCircle(C.Lit, radius = r)
            }
            LampState.OFF -> drawCircle(C.Muted, radius = r - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
            LampState.OFFLINE -> drawCircle(C.Line2, radius = r)
            LampState.UNKNOWN -> drawCircle(C.Faint, radius = r - 1.dp.toPx(), style = Stroke(2.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4f, 4f))))
        }
    }
}

enum class Tone { OK, DANGER, WARN, MUTED }

fun statusTone(s: String?): Tone = when (s?.uppercase()) {
    "ACTIVE" -> Tone.OK
    "OFFLINE", "INACTIVE" -> Tone.DANGER
    "MAINTENANCE" -> Tone.WARN
    else -> Tone.MUTED
}

@Composable
fun Badge(text: String, tone: Tone) {
    val (bg, fg) = when (tone) {
        Tone.OK -> C.Teal.copy(alpha = 0.14f) to C.Teal
        Tone.DANGER -> C.Red.copy(alpha = 0.14f) to C.Red
        Tone.WARN -> C.Warn.copy(alpha = 0.14f) to C.Warn
        Tone.MUTED -> C.Panel2 to C.Muted
    }
    Text(text, Modifier.clip(CircleShape).background(bg).padding(horizontal = 9.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall.copy(color = fg, fontWeight = FontWeight.SemiBold))
}

@Composable
fun Panel(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(16.dp), content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Panel).border(1.dp, C.Line, RoundedCornerShape(16.dp)).padding(padding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
fun PanelHead(title: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun ErrorNote(message: String?, onRetry: (() -> Unit)? = null) {
    if (message == null) return
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.Red.copy(alpha = 0.1f))
            .border(1.dp, C.Red.copy(alpha = 0.35f), RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFFFB3B3)))
        if (onRetry != null) TextButton(onClick = onRetry) { Text("Try again", color = C.Text) }
    }
}

@Composable
fun WarnNote(message: String) {
    Text(message, Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.Warn.copy(alpha = 0.08f))
        .border(1.dp, C.Warn.copy(alpha = 0.3f), RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        style = MaterialTheme.typography.bodyMedium.copy(color = C.Warn))
}

@Composable
fun Empty(title: String, body: String? = null, action: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
        if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium.copy(color = C.Muted), textAlign = TextAlign.Center)
        action?.invoke()
    }
}

@Composable
fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = C.Sodium, trackColor = C.Line2, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    Button(onClick, modifier.height(46.dp), enabled = enabled, shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = C.Text, contentColor = C.Night, disabledContainerColor = C.Text.copy(alpha = 0.35f), disabledContentColor = C.Night)) {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge.copy(color = Color.Unspecified))
    }
}

@Composable
fun LitButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    Button(onClick, modifier.height(46.dp), enabled = enabled, shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = C.Lit, contentColor = C.OnLit, disabledContainerColor = C.Lit.copy(alpha = 0.35f), disabledContentColor = C.OnLit)) {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge.copy(color = Color.Unspecified))
    }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null, color: Color = C.Text, small: Boolean = false) {
    OutlinedButton(onClick, modifier.height(if (small) 36.dp else 46.dp), enabled = enabled, shape = RoundedCornerShape(if (small) 8.dp else 12.dp),
        border = BorderStroke(1.dp, C.Line2), contentPadding = PaddingValues(horizontal = if (small) 12.dp else 16.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color, disabledContentColor = color.copy(alpha = 0.4f))) {
        if (icon != null) { Icon(icon, null, Modifier.size(if (small) 16.dp else 18.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, style = (if (small) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge).copy(color = Color.Unspecified, fontWeight = FontWeight.SemiBold))
    }
}

@Composable
fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    keyboard: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = value, onValueChange = onChange, label = { Text(label) }, enabled = enabled, singleLine = true,
            placeholder = placeholder?.let { { Text(it, color = C.Faint) } },
            keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
            visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = C.Sodium, unfocusedBorderColor = C.Line2, focusedLabelColor = C.Sodium, unfocusedLabelColor = C.Muted,
                cursorColor = C.Sodium, focusedContainerColor = C.Night2, unfocusedContainerColor = C.Night2, disabledContainerColor = C.Night2,
                disabledBorderColor = C.Line, disabledTextColor = C.Muted, disabledLabelColor = C.Faint,
            ),
        )
        if (hint != null) Text(hint, style = MaterialTheme.typography.labelSmall.copy(color = C.Faint), modifier = Modifier.padding(start = 4.dp))
    }
}

/** Segmented control (tabs-like). */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.clip(RoundedCornerShape(12.dp)).background(C.Night2).border(1.dp, C.Line, RoundedCornerShape(12.dp)).padding(3.dp)) {
        options.forEach { (v, label) ->
            val on = v == selected
            TextButton(
                onClick = { onSelect(v) },
                modifier = Modifier.weight(1f, fill = false).height(36.dp),
                shape = RoundedCornerShape(9.dp),
                contentPadding = PaddingValues(horizontal = 14.dp),
                colors = ButtonDefaults.textButtonColors(containerColor = if (on) C.Panel2 else Color.Transparent, contentColor = if (on) C.Text else C.Muted),
            ) { Text(label, style = MaterialTheme.typography.labelMedium.copy(color = Color.Unspecified, fontWeight = FontWeight.SemiBold)) }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    danger: Boolean = true,
    onConfirm: suspend () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = C.Panel,
        titleContentColor = C.Text,
        textContentColor = C.Muted,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium.copy(color = C.Muted)) },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel", color = C.Text) } },
        confirmButton = {
            Button(
                onClick = { busy = true; scope.launch { try { onConfirm(); onDismiss() } catch (_: Exception) { busy = false } } },
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = if (danger) C.Red else C.Lit, contentColor = if (danger) Color(0xFF2B0606) else C.OnLit),
            ) { Text(if (busy) "Working…" else confirmLabel, fontWeight = FontWeight.SemiBold) }
        },
    )
}

@Composable
fun Fact(label: String, modifier: Modifier = Modifier, value: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        value()
    }
}

@Composable
fun FactText(label: String, value: String, modifier: Modifier = Modifier) =
    Fact(label, modifier) { Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)) }

@Composable
fun SignalBars(value: Int?) {
    val lvl = when { value == null -> 0; value >= 75 -> 4; value >= 50 -> 3; value >= 25 -> 2; value > 0 -> 1; else -> 0 }
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom, modifier = Modifier.height(16.dp)) {
        (1..4).forEach { i -> Box(Modifier.width(4.dp).height((4 + i * 3).dp).clip(RoundedCornerShape(1.dp)).background(if (i <= lvl) C.Teal else C.Line2)) }
    }
}

@Composable
fun Meter(value: Float, max: Float, color: Color) {
    val pct = if (max > 0) (value / max).coerceIn(0f, 1f) else 0f
    Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(C.Line)) {
        Box(Modifier.fillMaxWidth(pct).height(4.dp).clip(CircleShape).background(color))
    }
}

@Composable
fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line))
