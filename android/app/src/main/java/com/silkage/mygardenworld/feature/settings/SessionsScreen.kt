package com.silkage.mygardenworld.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mygardenworld.v1.MobileSession
import com.silkage.mygardenworld.AppContainer
import com.silkage.mygardenworld.core.network.ConnectException
import com.silkage.mygardenworld.core.ui.Badge
import com.silkage.mygardenworld.core.ui.BadgeTone
import com.silkage.mygardenworld.core.ui.EmptyState
import com.silkage.mygardenworld.core.ui.ErrorBanner
import com.silkage.mygardenworld.core.ui.Format
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SessionsUiState(val sessions: List<MobileSession> = emptyList(), val loading: Boolean = false, val busyId: Long = 0, val error: String = "")

class SessionsViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(SessionsUiState())
    val state = _state

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = "") }
            try {
                _state.update { it.copy(sessions = container.sessions.list(), loading = false) }
            } catch (e: ConnectException) {
                _state.update { it.copy(loading = false, error = e.userMessage) }
            }
        }
    }

    /** Revoking the current device's session also signs this app out. */
    fun revoke(session: MobileSession) {
        viewModelScope.launch {
            _state.update { it.copy(busyId = session.id, error = "") }
            try {
                container.sessions.revoke(session.id)
                if (session.current) container.auth.logout() else refresh()
            } catch (e: ConnectException) {
                _state.update { it.copy(error = e.userMessage) }
            } finally {
                _state.update { it.copy(busyId = 0) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(viewModel: SessionsViewModel, online: Boolean, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设备会话") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") } },
                actions = { IconButton(onClick = viewModel::refresh, enabled = !state.loading) { Icon(Icons.Filled.Refresh, contentDescription = "刷新") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (state.error.isNotBlank()) item { ErrorBanner(state.error) }
            item { Text("列出当前用户在移动端登录的设备。撤销后该设备需要重新登录。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp)) }
            if (state.sessions.isEmpty() && !state.loading) item { EmptyState("暂无设备会话") }
            items(state.sessions, key = { it.id }) { s ->
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(s.deviceName.ifBlank { "未命名设备" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        if (s.current) Badge("本机", BadgeTone.PRIMARY)
                        OutlinedButton(onClick = { viewModel.revoke(s) }, enabled = online && state.busyId == 0L) { Text(if (state.busyId == s.id) "撤销中…" else if (s.current) "退出本机" else "撤销") }
                    }
                    Text("设备 ${s.deviceId.take(8)}… · 登录 ${Format.timestamp(s.createdAt)} · 最近使用 ${Format.timestamp(s.lastUsedAt)} · 到期 ${Format.timestamp(s.expiresAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}
