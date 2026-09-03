package com.silkage.mygardenworld.feature.workspace

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.silkage.mygardenworld.core.ui.Badge
import com.silkage.mygardenworld.core.ui.ErrorBanner
import com.silkage.mygardenworld.core.ui.Format
import com.silkage.mygardenworld.feature.accounts.ConnectionDot

enum class WorkspaceTab(val label: String) { BASIC("基础"), GARDEN("花园"), ORDERS("订单"), LOGS("日志"), SETTINGS("设置") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(viewModel: WorkspaceViewModel, onBack: () -> Unit) {
    val screen by viewModel.state.collectAsStateWithLifecycle()
    val workspace by viewModel.workspace.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(WorkspaceTab.BASIC) }
    val status = workspace.statuses[viewModel.accountId]
    val account = screen.account

    LaunchedEffect(screen.deleted) { if (screen.deleted) onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(account?.let(Format::accountNickname) ?: status?.accountName ?: "账号", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val sub = buildString {
                            if (account != null) append(Format.accountArea(account, status)).append(" · ").append(Format.channel(account.channel))
                            status?.let { if (it.level > 0) append(" · ${it.level}级") }
                        }
                        Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回账号列表") } },
                actions = {
                    val (label, tone) = Format.healthBadge(account, status)
                    Badge(label, tone, Modifier.padding(end = 6.dp))
                    ConnectionDot(workspace.connection)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            NavigationBar {
                WorkspaceTab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        label = { Text(entry.label) },
                        icon = {
                            Icon(
                                when (entry) {
                                    WorkspaceTab.BASIC -> Icons.Filled.Dashboard
                                    WorkspaceTab.GARDEN -> Icons.Filled.LocalFlorist
                                    WorkspaceTab.ORDERS -> Icons.Filled.Receipt
                                    WorkspaceTab.LOGS -> Icons.AutoMirrored.Filled.List
                                    WorkspaceTab.SETTINGS -> Icons.Filled.Tune
                                },
                                contentDescription = null,
                            )
                        },
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val banner = screen.error.ifBlank { workspace.lastError?.message.orEmpty() }
            if (banner.isNotBlank()) ErrorBanner(banner, Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            if (workspace.state == null && tab != WorkspaceTab.SETTINGS && tab != WorkspaceTab.LOGS) {
                com.silkage.mygardenworld.core.ui.LoadingBox(if (workspace.online) "等待账号快照…" else "等待连接服务端…")
            }
            when (tab) {
                WorkspaceTab.BASIC -> BasicTab(workspace, viewModel.catalog)
                WorkspaceTab.GARDEN -> GardenTab(workspace, screen.policy, viewModel.catalog)
                WorkspaceTab.ORDERS -> OrdersTab(workspace, viewModel.catalog)
                WorkspaceTab.LOGS -> LogsTab(workspace, onLoadMore = viewModel::loadOlderLogs)
                WorkspaceTab.SETTINGS -> SettingsTab(viewModel, screen, workspace)
            }
        }
    }
}
