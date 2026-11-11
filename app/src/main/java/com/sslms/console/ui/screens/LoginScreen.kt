package com.sslms.console.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sslms.console.data.ApiException
import com.sslms.console.data.Session
import com.sslms.console.ui.components.ErrorNote
import com.sslms.console.ui.components.Field
import com.sslms.console.ui.components.LocalNotify
import com.sslms.console.ui.components.PrimaryButton
import com.sslms.console.ui.components.Segmented
import com.sslms.console.ui.theme.C
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(session: Session) {
    val notify = LocalNotify.current
    val scope = rememberCoroutineScope()
    var setupMode by remember { mutableStateOf(false) }
    var demo by remember { mutableStateOf(session.demo || session.baseUrl.isBlank()) }
    var base by remember { mutableStateOf(session.baseUrl) }
    var username by remember { mutableStateOf("admin") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        error = null
        if (setupMode && password != confirm) { error = "The two passwords don't match."; return }
        if (!demo && base.isBlank()) { error = "Enter the server address, or choose Demo data."; return }
        if (!demo && !base.startsWith("http")) { error = "The address must start with http:// or https://"; return }
        session.demo = demo
        if (!demo) session.baseUrl = base
        busy = true
        scope.launch {
            try {
                if (setupMode) notify.show(session.api.setup(username.trim(), password), false)
                val res = session.api.login(username.trim(), password)
                val token = res.token ?: throw ApiException("The server did not return a token.")
                session.signIn(token, res.username ?: username.trim())
            } catch (e: ApiException) {
                error = if (e.status == 401 || e.status == 403) "Username or password is incorrect." else e.message
            } catch (e: Exception) {
                error = e.message ?: "Sign-in failed."
            } finally { busy = false }
        }
    }

    Column(
        Modifier.fillMaxSize().background(C.Night).verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding(),
    ) {
        Box(Modifier.fillMaxWidth().height(250.dp).background(Brush.verticalGradient(listOf(Color(0xFF10152B), C.Night)))) {
            StreetScene(Modifier.fillMaxSize())
            Column(Modifier.statusBarsPadding().padding(horizontal = 24.dp, vertical = 20.dp)) {
                Text("Every street light in the city, on one screen.", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth(0.82f))
            }
        }

        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Segmented(listOf(false to "Sign in", true to "First-time setup"), setupMode, { setupMode = it; error = null })
            Text(if (setupMode) "Create the first admin" else "Sign in to the console", style = MaterialTheme.typography.titleLarge)
            if (setupMode) Text("Only works once, on a fresh server with no users.", style = MaterialTheme.typography.bodySmall)

            Field("Username", username, { username = it })
            Field("Password", password, { password = it }, password = true)
            if (setupMode) Field("Confirm password", confirm, { confirm = it }, password = true)

            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, C.Line, RoundedCornerShape(12.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Connect to", style = MaterialTheme.typography.labelMedium)
                Choice("SSLMS server", null, !demo) { demo = false }
                if (!demo) {
                    Field(
                        "Server address", base, { base = it },
                        placeholder = "http://192.168.1.20:8080/api/sslms",
                        hint = "Include /api/sslms. On the Android emulator, your computer's localhost is http://10.0.2.2:8080/api/sslms",
                        keyboard = KeyboardType.Uri,
                    )
                }
                Choice("Demo data", "No backend needed. Sign in with admin / admin123.", demo) { demo = true }
            }

            ErrorNote(error)
            PrimaryButton(
                if (busy) "Signing in…" else if (setupMode) "Create admin and sign in" else "Sign in",
                ::submit, Modifier.fillMaxWidth(), enabled = !busy && username.isNotBlank() && password.isNotEmpty(),
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun Choice(label: String, sub: String?, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick, colors = RadioButtonDefaults.colors(selectedColor = C.Sodium, unselectedColor = C.Muted))
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Row of lamp posts that switch on one after another: the app's one orchestrated motion moment. */
@Composable
private fun StreetScene(modifier: Modifier) {
    val count = 4
    val anims = remember { List(count) { Animatable(0f) } }
    LaunchedEffect(Unit) {
        anims.forEachIndexed { i, a -> launch { kotlinx.coroutines.delay(250L + i * 220L); a.animateTo(1f, tween(600)) } }
    }
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val road = Path().apply { moveTo(0f, h * 0.86f); lineTo(w, h * 0.74f); lineTo(w, h); lineTo(0f, h); close() }
        drawPath(road, C.Panel)
        drawLine(C.Line, Offset(0f, h * 0.92f), Offset(w, h * 0.80f), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(22f, 18f)))
        for (i in 0 until count) {
            val x = w * (0.12f + i * 0.25f)
            val baseY = h * 0.86f - (x / w) * h * 0.12f
            val topY = baseY - h * 0.5f
            val on = anims[i].value
            val head = Offset(x + 30.dp.toPx(), topY)
            // light pool
            val pool = Path().apply { moveTo(head.x, head.y + 6.dp.toPx()); lineTo(head.x - 34.dp.toPx(), baseY); lineTo(head.x + 34.dp.toPx(), baseY); close() }
            drawPath(pool, Brush.verticalGradient(listOf(C.Sodium.copy(alpha = 0.45f * on), Color.Transparent), startY = head.y, endY = baseY))
            // post
            val post = Path().apply { moveTo(x, baseY); lineTo(x, topY + 8.dp.toPx()); quadraticTo(x, topY, x + 8.dp.toPx(), topY); lineTo(head.x - 8.dp.toPx(), topY) }
            drawPath(post, C.Line2, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
            // lamp head
            drawRoundRect(
                if (on > 0.5f) C.Sodium else Color(0xFF4A5588),
                topLeft = Offset(head.x - 8.dp.toPx(), head.y - 3.dp.toPx()), size = Size(16.dp.toPx(), 6.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
            )
        }
    }
}
