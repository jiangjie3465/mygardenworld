package com.silkage.mygardenworld.feature.redeem

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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.google.protobuf.Timestamp
import com.mygardenworld.v1.Channel
import com.mygardenworld.v1.RedeemCode
import com.mygardenworld.v1.RedeemSubmitDisposition
import com.mygardenworld.v1.RedeemValidation
import com.silkage.mygardenworld.AppContainer
import com.silkage.mygardenworld.core.network.ConnectException
import com.silkage.mygardenworld.core.ui.Badge
import com.silkage.mygardenworld.core.ui.BadgeTone
import com.silkage.mygardenworld.core.ui.EmptyState
import com.silkage.mygardenworld.core.ui.ErrorBanner
import com.silkage.mygardenworld.core.ui.Format
import com.silkage.mygardenworld.core.ui.SectionCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RedeemUiState(
    val entries: List<RedeemCode> = emptyList(),
    val loading: Boolean = false,
    val submitting: Boolean = false,
    val error: String = "",
    val message: String = "",
)

/** Expiry presets mirroring the Web page. Seconds of 0 means permanent. */
val EXPIRY_PRESETS = listOf("5分钟" to 5 * 60L, "10分钟" to 10 * 60L, "30分钟" to 30 * 60L, "1小时" to 3600L, "6小时" to 6 * 3600L, "1天" to 86400L, "不限" to 0L)

class RedeemViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(RedeemUiState())
    val state = _state

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = "") }
            try {
                val entries = container.redeem.listAll()
                _state.update { it.copy(entries = entries, loading = false) }
            } catch (e: ConnectException) {
                _state.update { it.copy(loading = false, error = e.userMessage) }
            }
        }
    }

    fun submit(code: String, channels: List<Channel>, expirySeconds: Long) {
        val normalized = code.trim()
        if (normalized.isEmpty()) { _state.update { it.copy(error = "请输入兑换码") }; return }
        if (channels.isEmpty()) { _state.update { it.copy(error = "请至少选择一个渠道") }; return }
        if (!container.workspace.state.value.online) { _state.update { it.copy(error = "当前离线，无法录入兑换码") }; return }
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = "", message = "") }
            try {
                val expiresAt = if (expirySeconds > 0) Timestamp.newBuilder().setSeconds(System.currentTimeMillis() / 1000 + expirySeconds).build() else null
                val results = container.redeem.submit(normalized, channels, expiresAt)
                val rejected = results.filter { it.disposition == RedeemSubmitDisposition.REDEEM_SUBMIT_DISPOSITION_REJECTED }
                if (rejected.isNotEmpty()) {
                    _state.update { it.copy(submitting = false, error = rejected.joinToString("；") { r -> r.message }.ifBlank { "兑换码未被接受" }) }
                    return@launch
                }
                val accepted = results.count { it.disposition == RedeemSubmitDisposition.REDEEM_SUBMIT_DISPOSITION_ACCEPTED }
                val duplicate = results.size - accepted
                _state.update {
                    it.copy(
                        submitting = false,
                        message = if (accepted > 0) "已录入 $accepted 个渠道范围，正在使用现有账号验证" + (if (duplicate > 0) "；$duplicate 个已存在" else "") else "兑换码已存在，验证状态已刷新",
                    )
                }
                refresh()
            } catch (e: ConnectException) {
                _state.update { it.copy(submitting = false, error = e.userMessage) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RedeemScreen(viewModel: RedeemViewModel, online: Boolean, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var code by rememberSaveable { mutableStateOf("") }
    var ios by rememberSaveable { mutableStateOf(true) }
    var alipay by rememberSaveable { mutableStateOf(false) }
    var expiryIndex by rememberSaveable { mutableStateOf(2) }
    var showHistorical by rememberSaveable { mutableStateOf(false) }
    val historical = state.entries.filter { Format.redeemExpired(it) || it.validation == RedeemValidation.REDEEM_VALIDATION_INVALID }
    val active = state.entries.filter { !(Format.redeemExpired(it) || it.validation == RedeemValidation.REDEEM_VALIDATION_INVALID) }
    val visible = if (showHistorical) historical else active

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("兑换码中心") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") } },
                actions = { IconButton(onClick = viewModel::refresh, enabled = !state.loading) { Icon(Icons.Filled.Refresh, contentDescription = "刷新") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                SectionCard("录入兑换码") {
                    if (state.error.isNotBlank()) ErrorBanner(state.error)
                    if (state.message.isNotBlank()) Text(state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    OutlinedTextField(code, { code = it }, Modifier.fillMaxWidth(), label = { Text("兑换码") }, singleLine = true, enabled = !state.submitting)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = ios, onClick = { ios = !ios }, label = { Text("iOS") }, enabled = !state.submitting)
                        FilterChip(selected = alipay, onClick = { alipay = !alipay }, label = { Text("Alipay") }, enabled = !state.submitting)
                    }
                    Text("有效期", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        EXPIRY_PRESETS.take(4).forEachIndexed { i, (label, _) -> FilterChip(selected = expiryIndex == i, onClick = { expiryIndex = i }, label = { Text(label) }) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        EXPIRY_PRESETS.drop(4).forEachIndexed { i, (label, _) -> FilterChip(selected = expiryIndex == i + 4, onClick = { expiryIndex = i + 4 }, label = { Text(label) }) }
                    }
                    Text(if (EXPIRY_PRESETS[expiryIndex].second == 0L) "不会按时间自动过期，仍会接受游戏返回的失效结果" else "按所选时长计算过期时间", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        onClick = { viewModel.submit(code, listOfNotNull(Channel.CHANNEL_IOS.takeIf { ios }, Channel.CHANNEL_ALIPAY.takeIf { alipay }), EXPIRY_PRESETS[expiryIndex].second); code = "" },
                        enabled = online && !state.submitting && code.isNotBlank() && (ios || alipay),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (state.submitting) "提交中…" else "录入") }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = !showHistorical, onClick = { showHistorical = false }, label = { Text("可用 ${active.size}") })
                    FilterChip(selected = showHistorical, onClick = { showHistorical = true }, label = { Text("历史 ${historical.size}") })
                }
            }
            if (visible.isEmpty()) item { EmptyState(if (state.loading) "正在加载兑换码" else if (showHistorical) "暂无历史兑换码" else "暂无可用兑换码", "录入后会由现有账号自动验证") }
            items(visible, key = { it.fingerprint }) { entry ->
                val (label, tone) = Format.redeemValidation(entry)
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(entry.code, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Badge(Format.channel(entry.channel))
                        Badge(label, tone)
                    }
                    Text(Format.redeemExpiry(entry) + if (entry.lastMessage.isNotBlank()) " · ${entry.lastMessage}" else "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}
