package com.sslms.console.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sslms.console.ui.theme.C

@Composable
fun ScreenHeader(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = C.Text) }
        Column(Modifier.weight(1f).padding(start = if (onBack != null) 0.dp else 4.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        actions()
    }
}

/** Scrollable page with pull-to-refresh and standard padding. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshPage(refreshing: Boolean, onRefresh: () -> Unit, content: LazyListScope.() -> Unit) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = refreshing, onRefresh = onRefresh, state = state,
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        indicator = {
            PullToRefreshDefaults.Indicator(state = state, isRefreshing = refreshing, modifier = Modifier.align(Alignment.TopCenter),
                containerColor = C.Panel2, color = C.Sodium)
        },
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}
