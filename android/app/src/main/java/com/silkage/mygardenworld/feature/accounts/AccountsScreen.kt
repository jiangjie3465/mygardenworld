package com.silkage.mygardenworld.feature.accounts

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mygardenworld.v1.Account
import com.mygardenworld.v1.AccountStatus
import com.silkage.mygardenworld.core.protocol.WorkspaceConnectionState
import com.silkage.mygardenworld.core.ui.Badge
import com.silkage.mygardenworld.core.ui.BadgeTone
import com.silkage.mygardenworld.core.ui.ErrorBanner
import com.silkage.mygardenworld.core.ui.Format
import com.silkage.mygardenworld.feature.workspace.MaintenanceBanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    viewModel: AccountsViewModel,
    serverLabel: String,
    onOpenAccount: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRedeem: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val workspace by viewModel.workspace.collectAsStateWithLifecycle()
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<Long>()) }

    // Returning from a workspace (e.g. after deleting an account) re-lists.
    LaunchedEffect(Unit) { viewModel.refresh() }

    LaunchedEffect(state.createdAccountId) {
        if (state.createdAccountId != 0L) {
            showAdd = false
            viewModel.clearQr()
            val id = state.createdAccountId
            viewModel.consumeCreated()
            onOpenAccount(id)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("账号", fontWeight = FontWeight.SemiBold)
                        Text(serverLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                actions = {
                    ConnectionDot(workspace.connection)
                    IconButton(onClick = viewModel::refresh, enabled = !state.loading) { Icon(Icons.Filled.Refresh, contentDescription = "刷新") }
                    IconButton(onClick = { showAdd = true }, enabled = !quotaReached(state)) { Icon(Icons.Filled.Add, contentDescription = "新增账号") }
                    IconButton(onClick = onOpenRedeem) { Icon(Icons.Filled.ConfirmationNumber, contentDescription = "兑换码中心") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, contentDescription = "设置") }
                    IconButton(onClick = { menu = true }, enabled = state.accounts.isNotEmpty()) { Icon(Icons.Filled.MoreVert, contentDescription = "批量操作") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        val idle = workspace.online && state.bulkAction.isBlank() && state.busyAccountId == 0L
                        DropdownMenuItem(text = { Text(if (state.bulkAction == "start") "正在全部启动…" else "全部启动") }, enabled = idle, onClick = { menu = false; viewModel.bulk(start = true) })
                        DropdownMenuItem(text = { Text(if (state.bulkAction == "pause") "正在全部暂停…" else "全部暂停") }, enabled = idle, onClick = { menu = false; viewModel.bulk(start = false) })
                        DropdownMenuItem(text = { Text("选择账号批量管理") }, enabled = state.bulkAction.isBlank(), onClick = { menu = false; selecting = true; selected = emptySet() })
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (workspace.maintenance?.enabled == true) item { MaintenanceBanner(workspace.maintenance) }
            if (selecting) {
                item {
                    val selectable = state.accounts.filter { !Format.accountDeleting(it, workspace.statuses[it.id]) }.map { it.id }.toSet()
                    val idle = workspace.online && state.bulkAction.isBlank() && state.busyAccountId == 0L
                    Row(
                        Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)).padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = selectable.isNotEmpty() && selected.containsAll(selectable), onCheckedChange = { all -> selected = if (all) selectable else emptySet() })
                        Text("已选 ${selected.size}/${state.accounts.size}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewModel.bulk(start = true, only = selected) }, enabled = idle && selected.isNotEmpty()) { Text(if (state.bulkAction == "start") "启动中…" else "启动") }
                        TextButton(onClick = { viewModel.bulk(start = false, only = selected) }, enabled = idle && selected.isNotEmpty()) { Text(if (state.bulkAction == "pause") "暂停中…" else "暂停") }
                        TextButton(onClick = { selecting = false; selected = emptySet() }, enabled = state.bulkAction.isBlank()) { Text("退出") }
                    }
                }
            }
            if (state.error.isNotBlank()) item { ErrorBanner(state.error) }
            state.user?.let { user ->
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(user.username, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Badge("${user.currentAccounts}/${user.maxAccounts}", if (quotaReached(state)) BadgeTone.DANGER else BadgeTone.SECONDARY)
                    }
                }
            }
            if (state.accounts.isEmpty() && !state.loading) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("☁", style = MaterialTheme.typography.displaySmall)
                        Text("还没有账号", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("添加后开始监控。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { showAdd = true }, enabled = !quotaReached(state)) { Text("添加账号") }
                    }
                }
            }
            items(state.accounts, key = { it.id }) { account ->
                if (selecting) {
                    val deleting = Format.accountDeleting(account, workspace.statuses[account.id])
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = account.id in selected, enabled = !deleting, onCheckedChange = { on -> selected = if (on) selected + account.id else selected - account.id })
                        Text(Format.accountNickname(account), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        val (label, tone) = Format.healthBadge(account, workspace.statuses[account.id])
                        Badge(label, tone)
                    }
                    return@items
                }
                AccountRow(
                    account = account,
                    status = workspace.statuses[account.id],
                    busy = state.busyAccountId == account.id,
                    locked = state.bulkAction.isNotBlank() || (state.busyAccountId != 0L && state.busyAccountId != account.id),
                    online = workspace.online,
                    onOpen = { onOpenAccount(account.id) },
                    onToggle = { connected -> viewModel.toggleAutomation(account, connected) },
                    onStop = { viewModel.stop(account) },
                )
            }
            if (state.loading && state.accounts.isEmpty()) item { Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) } }
        }
    }

    if (showAdd) {
        AddAccountDialog(
            state = state,
            onDismiss = { showAdd = false; viewModel.clearQr(); viewModel.dismissError() },
            onCreateIos = { u, p, initial -> viewModel.createIos(u, p, initial) { ok -> if (ok) showAdd = false } },
            onStartAlipay = viewModel::startAlipay,
            onClearQr = viewModel::clearQr,
        )
    }
}

private fun quotaReached(state: AccountsUiState): Boolean = state.user?.let { it.maxAccounts > 0 && it.currentAccounts >= it.maxAccounts } ?: false

@Composable
fun ConnectionDot(state: WorkspaceConnectionState) {
    val (label, tone) = when (state) {
        WorkspaceConnectionState.OPEN -> "已连接" to BadgeTone.SUCCESS
        WorkspaceConnectionState.CONNECTING -> "连接中" to BadgeTone.WARNING
        WorkspaceConnectionState.CLOSED -> "离线" to BadgeTone.DANGER
    }
    Badge(label, tone, Modifier.padding(end = 4.dp))
}

@Composable
private fun AccountRow(
    account: Account,
    status: AccountStatus?,
    busy: Boolean,
    locked: Boolean,
    online: Boolean,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onStop: () -> Unit,
) {
    val connected = Format.accountConnected(account, status)
    val (healthLabel, healthTone) = Format.healthBadge(account, status)
    val deleting = Format.accountDeleting(account, status)
    val abnormal = !deleting && Format.accountAbnormal(status)
    val enabled = online && !locked
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(Format.accountNickname(account), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Badge(healthLabel, healthTone)
                }
                Text("${Format.accountArea(account, status)} · ${Format.channel(account.channel)}" + (status?.let { " · ${it.level}级" } ?: ""), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val issue = if (abnormal) Format.statusIssues(status).firstOrNull() else null
                when {
                    deleting -> Text(status?.takeIf { it.hasDeletionProgress() }?.deletionProgress?.let { "后台清理：${Format.deletionPhase(it.phase)}" } ?: "删除请求已保存，后台清理中", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    issue != null -> Text(issue, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    else -> status?.diagnostics?.currentOperation?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            if (busy) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else if (!deleting) {
                if (abnormal) {
                    OutlinedButton(onClick = onStop, enabled = enabled, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("停止") }
                    Spacer(Modifier.width(6.dp))
                }
                if (connected) {
                    OutlinedButton(onClick = { onToggle(true) }, enabled = enabled, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("暂停") }
                } else {
                    Button(onClick = { onToggle(false) }, enabled = enabled, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("启动") }
                }
            }
        }
    }
}
