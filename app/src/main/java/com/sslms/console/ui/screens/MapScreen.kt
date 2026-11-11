package com.sslms.console.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sslms.console.data.LampState
import com.sslms.console.data.loadFleet
import com.sslms.console.ui.LocalSession
import com.sslms.console.ui.components.GhostButton
import com.sslms.console.ui.components.Loading
import com.sslms.console.ui.components.NodeMap
import com.sslms.console.ui.components.Panel
import com.sslms.console.ui.components.rememberLoader
import com.sslms.console.ui.theme.C
import com.sslms.console.util.num

@Composable
fun MapScreen(nav: NavController) {
    val session = LocalSession.current
    val fleet = rememberLoader("fleet-map", refreshMs = 30_000) { session.api.loadFleet() }
    var selected by remember { mutableStateOf<String?>(null) }
    val height = LocalConfiguration.current.screenHeightDp.dp

    Box(Modifier.fillMaxSize().background(C.Night)) {
        val nodes = fleet.data?.nodes
        if (nodes == null) Loading(Modifier.statusBarsPadding())
        else NodeMap(nodes, height = height + 80.dp, selectedId = selected, onSelect = { selected = it })

        IconButton(onClick = { nav.popBackStack() }, Modifier.statusBarsPadding().padding(12.dp).size(44.dp).clip(RoundedCornerShape(12.dp)).background(C.Night.copy(alpha = 0.9f))) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = C.Text)
        }

        nodes?.find { it.device.deviceId == selected }?.let { n ->
            Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp).fillMaxWidth()) {
                Panel {
                    Text("${n.device.deviceId}  ${n.device.feederId ?: ""}", style = MaterialTheme.typography.titleMedium)
                    Text(n.device.placeLine, style = MaterialTheme.typography.bodySmall)
                    Text(when (n.state) { LampState.OFFLINE -> "Offline"; LampState.ON -> "Lit, drawing ${num(n.power, 0)} W"; LampState.OFF -> "Switched off"; LampState.UNKNOWN -> "No reading yet" })
                    GhostButton("Open device", { nav.navigate("device/${n.device.deviceId}") }, Modifier.fillMaxWidth())
                }
            }
        }
    }
}
