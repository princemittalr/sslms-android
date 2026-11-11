package com.sslms.console.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sslms.console.data.Session
import com.sslms.console.ui.components.LocalNotify
import com.sslms.console.ui.components.Notifier
import com.sslms.console.ui.screens.DeviceDetailScreen
import com.sslms.console.ui.screens.DevicesScreen
import com.sslms.console.ui.screens.FaultsScreen
import com.sslms.console.ui.screens.LoginScreen
import com.sslms.console.ui.screens.MapScreen
import com.sslms.console.ui.screens.OverviewScreen
import com.sslms.console.ui.screens.ReportsScreen
import com.sslms.console.ui.screens.SettingsScreen
import com.sslms.console.ui.theme.C
import kotlinx.coroutines.delay

val LocalSession = staticCompositionLocalOf<Session> { error("No session") }

private data class Toast(val id: Long, val message: String, val error: Boolean)

private data class Tab(val route: String, val label: String, val icon: ImageVector)
private val TABS = listOf(
    Tab("overview", "Overview", Icons.Outlined.Home),
    Tab("devices", "Devices", Icons.Outlined.Lightbulb),
    Tab("faults", "Faults", Icons.Outlined.WarningAmber),
    Tab("reports", "Reports", Icons.Outlined.Assessment),
    Tab("settings", "Settings", Icons.Outlined.Settings),
)

@Composable
fun SslmsRoot(session: Session) {
    val user by session.user.collectAsStateWithLifecycle()
    var toast by remember { mutableStateOf<Toast?>(null) }
    val notifier = remember { Notifier { m, e -> toast = Toast(System.nanoTime(), m, e) } }

    LaunchedEffect(toast?.id) {
        val t = toast ?: return@LaunchedEffect
        delay(if (t.error) 5500 else 3000)
        if (toast?.id == t.id) toast = null
    }

    CompositionLocalProvider(LocalSession provides session, LocalNotify provides notifier) {
        Surface(Modifier.fillMaxSize(), color = C.Night, contentColor = C.Text) {
            Box(Modifier.fillMaxSize()) {
                if (user == null) LoginScreen(session) else MainNav(session)
                ToastHost(toast, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

@Composable
private fun MainNav(session: Session) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = TABS.any { it.route == route }

    Scaffold(
        containerColor = C.Night,
        bottomBar = { if (showBar) BottomBar(nav, route) },
    ) { pad ->
        NavHost(nav, startDestination = "overview", modifier = Modifier.padding(pad)) {
            composable("overview") { OverviewScreen(nav) }
            composable("devices") { DevicesScreen(nav) }
            composable("faults") { FaultsScreen(nav) }
            composable("reports") { ReportsScreen(nav) }
            composable("settings") { SettingsScreen() }
            composable("map") { MapScreen(nav) }
            composable("device/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                DeviceDetailScreen(nav, it.arguments?.getString("id").orEmpty())
            }
        }
    }
}

@Composable
private fun BottomBar(nav: NavHostController, route: String?) {
    NavigationBar(containerColor = C.Night2, tonalElevation = 0.dp, modifier = Modifier.border(1.dp, C.Line, RoundedCornerShape(0.dp))) {
        TABS.forEach { tab ->
            NavigationBarItem(
                selected = route == tab.route,
                onClick = {
                    if (route != tab.route) nav.navigate(tab.route) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(tab.icon, null) },
                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall.copy(color = androidx.compose.ui.graphics.Color.Unspecified)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = C.Sodium, selectedTextColor = C.Text, indicatorColor = C.Panel2,
                    unselectedIconColor = C.Muted, unselectedTextColor = C.Muted,
                ),
            )
        }
    }
}

@Composable
private fun ToastHost(toast: Toast?, modifier: Modifier) {
    AnimatedVisibility(toast != null, modifier.navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 92.dp),
        enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 }) {
        val t = toast ?: return@AnimatedVisibility
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.Panel2)
                .border(1.dp, if (t.error) C.Red.copy(alpha = 0.5f) else C.Line2, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (t.error) Icons.Outlined.Close else Icons.Outlined.Check, null, tint = if (t.error) C.Red else C.Teal, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(t.message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
