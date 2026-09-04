package com.silkage.mygardenworld.feature.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mygardenworld.v1.GetSystemStatsResponse
import com.mygardenworld.v1.User
import com.mygardenworld.v1.UserRole
import com.mygardenworld.v1.UserStatus
import com.silkage.mygardenworld.AppContainer
import com.silkage.mygardenworld.core.network.ConnectException
import com.silkage.mygardenworld.core.ui.Badge
import com.silkage.mygardenworld.core.ui.BadgeTone
import com.silkage.mygardenworld.core.ui.ErrorBanner
import com.silkage.mygardenworld.core.ui.SectionCard
import com.silkage.mygardenworld.core.ui.StatTile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminUiState(
    val stats: GetSystemStatsResponse? = null,
    val users: List<User> = emptyList(),
    val loading: Boolean = false,
    val busyUserId: Long = 0,
    val creating: Boolean = false,
    val error: String = "",
    val message: String = "",
)

class AdminViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(AdminUiState())
    val state = _state

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = "") }
            try {
                val stats = container.admin.stats()
                val users = container.admin.users()
                _state.update { it.copy(stats = stats, users = users, loading = false) }
            } catch (e: ConnectException) {
                _state.update { it.copy(loading = false, error = e.userMessage) }
            }
        }
    }

    fun create(username: String, email: String, password: String, maxAccounts: Int?) {
        viewModelScope.launch {
            _state.update { it.copy(creating = true, error = "", message = "") }
            try {
                val user = container.admin.create(username, email, password, maxAccounts)
                _state.update { it.copy(creating = false, message = "已创建用户 ${user.username}") }
                refresh()
            } catch (e: ConnectException) {
                _state.update { it.copy(creating = false, error = e.userMessage) }
            }
        }
    }

    fun setQuota(user: User, maxAccounts: Int) = mutate(user.id) { container.admin.setMaxAccounts(user.id, maxAccounts) }

    fun toggleStatus(user: User) = mutate(user.id) {
        container.admin.setStatus(user.id, if (user.status == UserStatus.USER_STATUS_DISABLED) UserStatus.USER_STATUS_ACTIVE else UserStatus.USER_STATUS_DISABLED)
    }

    private fun mutate(userId: Long, block: suspend () -> Unit) {
        if (_state.value.busyUserId != 0L) return
        viewModelScope.launch {
            _state.update { it.copy(busyUserId = userId, error = "", message = "") }
            try {
                block()
                refresh()
            } catch (e: ConnectException) {
                _state.update { it.copy(error = e.userMessage) }
            } finally {
                _state.update { it.copy(busyUserId = 0) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(viewModel: AdminViewModel, online: Boolean, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var username by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var quota by rememberSaveable { mutableStateOf("") }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("用户管理") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") } },
                actions = { IconButton(onClick = viewModel::refresh, enabled = !state.loading) { Icon(Icons.Filled.Refresh, contentDescription = "刷新") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.error.isNotBlank()) item { ErrorBanner(state.error) }
            if (state.message.isNotBlank()) item { Text(state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            item {
                val s = state.stats
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile(Icons.Filled.Group, "用户总数", s?.totalUsers?.toString() ?: "-", null, Modifier.weight(1f))
                        StatTile(Icons.Filled.LocalFlorist, "游戏账号", s?.totalGameAccounts?.toString() ?: "-", null, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile(Icons.Filled.Memory, "活跃 Runner", s?.activeRunners?.toString() ?: "-", null, Modifier.weight(1f))
                        StatTile(Icons.Filled.Wifi, "已连接", s?.connectedRunners?.toString() ?: "-", null, Modifier.weight(1f))
                    }
                }
            }
            item {
                SectionCard("创建用户", defaultOpen = false) {
                    OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), label = { Text("用户名") }, singleLine = true)
                    OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("邮箱") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                    OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("初始密码") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                    OutlinedTextField(quota, { quota = it.filter { c -> c.isDigit() } }, Modifier.fillMaxWidth(), label = { Text("账号配额（留空使用默认）") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    Button(
                        onClick = { viewModel.create(username, email, password, quota.toIntOrNull()); password = "" },
                        enabled = online && !state.creating && username.isNotBlank() && email.isNotBlank() && password.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (state.creating) "创建中…" else "创建") }
                }
            }
            items(state.users, key = { it.id }) { user -> UserRow(user, online, state.busyUserId == user.id, onQuota = { viewModel.setQuota(user, it) }, onToggle = { viewModel.toggleStatus(user) }) }
        }
    }
}

@Composable
private fun UserRow(user: User, online: Boolean, busy: Boolean, onQuota: (Int) -> Unit, onToggle: () -> Unit) {
    var quota by remember(user.maxAccounts) { mutableStateOf(user.maxAccounts.toString()) }
    val disabled = user.status == UserStatus.USER_STATUS_DISABLED
    val quotaValue = quota.toIntOrNull()
    val quotaInvalid = quotaValue != null && quotaValue < user.currentAccounts
    SectionCard(user.username, defaultOpen = false, actions = {
        Badge(if (user.role == UserRole.USER_ROLE_ADMIN) "管理员" else "用户", if (user.role == UserRole.USER_ROLE_ADMIN) BadgeTone.SECONDARY else BadgeTone.NEUTRAL)
        Badge(if (disabled) "已禁用" else "正常", if (disabled) BadgeTone.DANGER else BadgeTone.SUCCESS)
        Badge("${user.currentAccounts}/${user.maxAccounts}")
    }) {
        Text(user.email, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(quota, { quota = it.filter { c -> c.isDigit() }.take(5) }, Modifier.width(96.dp), label = { Text("配额") }, singleLine = true, enabled = !busy, isError = quotaInvalid, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedButton(onClick = { quotaValue?.let(onQuota) }, enabled = online && !busy && quotaValue != null && !quotaInvalid && quotaValue != user.maxAccounts) { Text("保存配额") }
            OutlinedButton(onClick = onToggle, enabled = online && !busy && user.role != UserRole.USER_ROLE_ADMIN) { Text(if (disabled) "启用" else "禁用", fontWeight = FontWeight.Medium) }
        }
        if (quotaInvalid) Text("配额不能小于当前账号数", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
    }
}
