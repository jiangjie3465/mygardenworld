package com.silkage.mygardenworld.feature.notifications

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mygardenworld.v1.NotificationProvider
import com.mygardenworld.v1.UserNotificationsView
import com.silkage.mygardenworld.AppContainer
import com.silkage.mygardenworld.core.network.ConnectException
import com.silkage.mygardenworld.core.ui.ErrorBanner
import com.silkage.mygardenworld.core.ui.Format
import com.silkage.mygardenworld.core.ui.SectionCard
import com.silkage.mygardenworld.core.ui.SwitchRow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ProviderOption(val provider: NotificationProvider, val label: String, val placeholder: String)

val NOTIFICATION_PROVIDERS = listOf(
    ProviderOption(NotificationProvider.NOTIFICATION_PROVIDER_CUSTOM, "自定义 Webhook", "https://example.com/webhook"),
    ProviderOption(NotificationProvider.NOTIFICATION_PROVIDER_WECOM, "企业微信群机器人", "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=…"),
    ProviderOption(NotificationProvider.NOTIFICATION_PROVIDER_DINGTALK, "钉钉群机器人", "https://oapi.dingtalk.com/robot/send?access_token=…"),
    ProviderOption(NotificationProvider.NOTIFICATION_PROVIDER_FEISHU, "飞书群机器人", "https://open.feishu.cn/open-apis/bot/v2/hook/…"),
)

fun supportsSigning(provider: NotificationProvider): Boolean =
    provider == NotificationProvider.NOTIFICATION_PROVIDER_DINGTALK || provider == NotificationProvider.NOTIFICATION_PROVIDER_FEISHU

private val STATUS_LABELS = mapOf("pending" to "等待发送", "sending" to "发送中", "sent" to "接收端已确认", "failed" to "发送失败", "cancelled" to "已取消")

data class NotificationDraft(
    val initialized: Boolean = false,
    val enabled: Boolean = false,
    val provider: NotificationProvider = NotificationProvider.NOTIFICATION_PROVIDER_CUSTOM,
    val endpoint: String = "",
    val clearEndpoint: Boolean = false,
    val signingSecret: String = "",
    val clearSigningSecret: Boolean = false,
    val cooldown: String = "30",
)

/** Values for SaveNotificationSettings; mirrors web notificationSettingsUpdate. Null keeps the saved value. */
data class NotificationUpdate(val enabled: Boolean, val provider: NotificationProvider, val cooldownMinutes: Int, val endpoint: String?, val signingSecret: String?)

fun notificationUpdate(draft: NotificationDraft, savedProvider: NotificationProvider): NotificationUpdate {
    val minutes = draft.cooldown.trim().toIntOrNull()
    require(minutes != null && minutes in 1..1440) { "冷却时间须为 1–1440 分钟的整数" }
    require(NOTIFICATION_PROVIDERS.any { it.provider == draft.provider }) { "请选择支持的通知渠道" }
    require(draft.provider == savedProvider || draft.endpoint.isNotBlank() || draft.clearEndpoint) { "切换渠道时请填写新的接收地址" }
    return NotificationUpdate(
        enabled = if (draft.clearEndpoint) false else draft.enabled,
        provider = draft.provider,
        cooldownMinutes = minutes,
        endpoint = if (draft.clearEndpoint) "" else draft.endpoint.trim().ifBlank { null },
        signingSecret = if (draft.clearEndpoint || draft.clearSigningSecret || !supportsSigning(draft.provider)) "" else draft.signingSecret.trim().ifBlank { null },
    )
}

data class NotificationsUiState(
    val draft: NotificationDraft = NotificationDraft(),
    val pages: List<Long> = listOf(0L),
    val busy: Boolean = false,
    val notice: String = "",
    val error: String = "",
)

class NotificationsViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(NotificationsUiState())
    val state = _state
    val view = container.workspace.state
    private var poll: Job? = null

    init {
        viewModelScope.launch {
            container.workspace.state.collect { ws ->
                val settings = ws.notifications?.takeIf { it.hasSettings() }?.settings ?: return@collect
                if (_state.value.draft.initialized) return@collect
                _state.update {
                    it.copy(draft = it.draft.copy(initialized = true, enabled = settings.enabled, provider = settings.provider, cooldown = settings.cooldownMinutes.toString()))
                }
            }
        }
    }

    /** Polls the current page every 5 s while the screen is visible, like the Web dialog. */
    fun startPolling() {
        poll?.cancel()
        poll = viewModelScope.launch {
            while (isActive) {
                if (container.workspace.state.value.online) container.socket.loadNotifications(_state.value.pages.last())
                delay(5_000)
            }
        }
    }

    fun stopPolling() {
        poll?.cancel()
        poll = null
    }

    fun edit(transform: (NotificationDraft) -> NotificationDraft) = _state.update { it.copy(draft = transform(it.draft), notice = "", error = "") }

    fun showPage(pages: List<Long>) {
        _state.update { it.copy(pages = pages) }
        container.socket.loadNotifications(pages.last())
    }

    fun save(savedProvider: NotificationProvider) {
        val update = try {
            notificationUpdate(_state.value.draft, savedProvider)
        } catch (e: IllegalArgumentException) {
            _state.update { it.copy(error = e.message.orEmpty()) }
            return
        }
        command {
            container.notifications.save(update.enabled, update.provider, update.cooldownMinutes, update.endpoint, update.signingSecret)
            _state.update {
                it.copy(
                    draft = it.draft.copy(endpoint = "", clearEndpoint = false, signingSecret = "", clearSigningSecret = false, enabled = update.enabled),
                    notice = "已保存。启用或更换渠道、地址、密钥后仅处理新事件；旧的待发送记录会取消。请发送测试确认配置。",
                )
            }
        }
    }

    fun test() = command {
        container.notifications.test()
        _state.update { it.copy(notice = "测试已加入队列，请查看下方投递结果。每分钟可测试一次。") }
    }

    private fun command(block: suspend () -> Unit) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, notice = "", error = "") }
            try {
                block()
                showPage(listOf(0L))
            } catch (e: ConnectException) {
                _state.update { it.copy(error = e.userMessage) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(viewModel: NotificationsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val workspace by viewModel.view.collectAsStateWithLifecycle()
    DisposableEffect(Unit) {
        viewModel.startPolling()
        onDispose { viewModel.stopPolling() }
    }
    val view: UserNotificationsView? = workspace.notifications
    val saved = view?.takeIf { it.hasSettings() }?.settings
    val draft = state.draft
    val online = workspace.online
    val sameProvider = saved != null && draft.provider == saved.provider
    val dirty = saved != null && (draft.enabled != saved.enabled || draft.provider != saved.provider || draft.cooldown != saved.cooldownMinutes.toString() ||
        draft.endpoint.isNotBlank() || draft.clearEndpoint || draft.signingSecret.isNotBlank() || draft.clearSigningSecret)
    val keepingSecret = sameProvider && draft.endpoint.isBlank() && !draft.clearEndpoint && !draft.clearSigningSecret && saved?.hasSigningSecret == true
    val pageReady = view != null && view.beforeId == state.pages.last()
    val formEnabled = saved != null && !state.busy

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("个人通知") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("仅通知你名下的游戏账号。管理员也不会收到其他用户的事件。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SectionCard("接收设置") {
                SwitchRow("启用个人通知", draft.enabled, formEnabled, "一个接收渠道，覆盖你的全部游戏账号") { v -> viewModel.edit { it.copy(enabled = v, clearEndpoint = if (v) false else it.clearEndpoint) } }
                ProviderPicker(draft.provider, formEnabled) { p -> viewModel.edit { it.copy(provider = p, endpoint = "", signingSecret = "", clearEndpoint = false, clearSigningSecret = false) } }
                if (saved != null && !sameProvider) Text("切换渠道需重新填写地址和密钥，不会沿用其他渠道的凭据。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    draft.endpoint,
                    { v -> viewModel.edit { it.copy(endpoint = v, clearEndpoint = false) } },
                    Modifier.fillMaxWidth(),
                    label = { Text("接收地址") },
                    placeholder = { Text(if (sameProvider && saved?.hasEndpoint == true && !draft.clearEndpoint) "已保存加密地址，留空保持不变" else NOTIFICATION_PROVIDERS.first { it.provider == draft.provider }.placeholder) },
                    singleLine = true,
                    enabled = formEnabled,
                    visualTransformation = PasswordVisualTransformation(),
                )
                Text(
                    (if (draft.provider == NotificationProvider.NOTIFICATION_PROVIDER_CUSTOM) "接收端需支持下方说明中的通用 JSON 格式。" else "从所选平台的群机器人设置中复制原始 Webhook 地址，系统会自动转换消息格式；同一用户的机器人消息至少间隔 4 秒发送。") + " 地址加密保存，不会回显；仅支持公网 HTTPS。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (saved?.hasEndpoint == true) {
                    TextButton(onClick = { viewModel.edit { it.copy(clearEndpoint = true, endpoint = "", enabled = false) } }, enabled = !state.busy) { Text(if (draft.clearEndpoint) "保存后清除地址并关闭通知" else "清除已保存地址") }
                }
                if (supportsSigning(draft.provider)) {
                    OutlinedTextField(
                        draft.signingSecret,
                        { v -> viewModel.edit { it.copy(signingSecret = v.take(1024), clearSigningSecret = false) } },
                        Modifier.fillMaxWidth(),
                        label = { Text("加签密钥（按机器人安全设置填写）") },
                        placeholder = { Text(if (keepingSecret) "已加密保存，留空保持不变" else "机器人启用签名校验时必填") },
                        singleLine = true,
                        enabled = formEnabled && !draft.clearEndpoint,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    Text("更换地址时请重新填写对应密钥，旧密钥会清除。若使用关键词校验，请在机器人中添加关键词“小云朵”；若配置 IP 白名单，请放行部署服务器的出口 IP。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (sameProvider && saved?.hasSigningSecret == true) {
                        TextButton(onClick = { viewModel.edit { it.copy(clearSigningSecret = true, signingSecret = "") } }, enabled = !state.busy && !draft.clearEndpoint) { Text(if (draft.clearSigningSecret) "保存后清除加签密钥" else "清除已保存密钥") }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("同一账号同类异常冷却（分钟）", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(top = 12.dp))
                    OutlinedTextField(draft.cooldown, { v -> viewModel.edit { it.copy(cooldown = v.filter(Char::isDigit).take(4)) } }, Modifier.width(96.dp), singleLine = true, enabled = formEnabled, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                Text("通知范围：请求保护、会话失效、礼仪分保护和珍珠雇佣锁定。首个异常立即通知，重复异常按冷却汇总；请求恢复、会话重建单独通知。普通操作失败和正常等待不推送。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.error.isNotBlank()) ErrorBanner(state.error)
                if (state.notice.isNotBlank()) Text(state.notice, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = viewModel::test, enabled = online && !state.busy && saved?.enabled == true && !dirty, modifier = Modifier.weight(1f)) { Text("发送测试") }
                    Button(onClick = { saved?.let { viewModel.save(it.provider) } }, enabled = online && saved != null && !state.busy && dirty, modifier = Modifier.weight(1f)) { Text(if (state.busy) "处理中…" else "保存设置") }
                }
            }
            if (draft.provider == NotificationProvider.NOTIFICATION_PROVIDER_CUSTOM) WebhookHelp(view?.customPayloadExample.orEmpty())
            SectionCard("投递记录", actions = { Text("保留 7 天 · 每页 5 条", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }) {
                when {
                    !pageReady -> Text(if (online) "加载中…" else "正在连接…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    view!!.deliveriesList.isEmpty() -> Text("暂无通知，可保存设置后发送测试", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> view.deliveriesList.forEach { item ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(Modifier.fillMaxWidth()) {
                                Text(item.title, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                Text(STATUS_LABELS[item.status] ?: item.status, style = MaterialTheme.typography.labelSmall, color = if (item.status == "failed") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("${Format.dayClock(item.createdMs)} · 尝试 ${item.attempts} 次", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (item.lastError.isNotBlank()) Text(item.lastError, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.showPage(state.pages.dropLast(1)) }, enabled = state.pages.size > 1 && online && pageReady) { Text("上一页") }
                    Text("第 ${state.pages.size} 页", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f).padding(top = 12.dp))
                    OutlinedButton(onClick = { viewModel.showPage(state.pages + view!!.nextBeforeId) }, enabled = view?.hasMore == true && online && pageReady) { Text("下一页") }
                }
            }
        }
    }
}

@Composable
private fun ProviderPicker(value: NotificationProvider, enabled: Boolean, onChange: (NotificationProvider) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("推送渠道", style = MaterialTheme.typography.bodyMedium)
        Box {
            OutlinedButton(onClick = { open = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(NOTIFICATION_PROVIDERS.firstOrNull { it.provider == value }?.label ?: "请选择渠道") }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                NOTIFICATION_PROVIDERS.forEach { option -> DropdownMenuItem(text = { Text(option.label) }, onClick = { open = false; onChange(option.provider) }) }
            }
        }
    }
}

@Composable
private fun WebhookHelp(example: String) {
    val context = LocalContext.current
    var status by rememberSaveable { mutableStateOf("") }
    SectionCard("自定义 Webhook 对接说明 · JSON 示例", defaultOpen = false) {
        Text("通过 POST 向公网 HTTPS 地址发送 JSON，Content-Type 为 application/json。以下为虚构的异常通知，不包含你的真实账号信息。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth()) {
            Text("请求体示例", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f).padding(top = 12.dp))
            TextButton(onClick = {
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                clipboard?.setPrimaryClip(ClipData.newPlainText("Webhook JSON 示例", example))
                status = if (clipboard != null) "已复制 JSON 示例" else "无法自动复制，请选中下方示例手动复制"
            }, enabled = example.isNotBlank()) { Text("复制 JSON") }
        }
        Text(
            example.ifBlank { "加载中…" },
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(8.dp)).horizontalScroll(rememberScrollState()).padding(10.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
        )
        if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        listOf(
            "id" to "通知唯一字符串；重试不变，与请求头 X-Notification-ID 相同，用于去重。",
            "kind" to "account_request 请求保护、session 会话、reputation 礼仪分、pearl_hire 珍珠雇佣、test 测试。",
            "level / message" to "级别 info、warn 或 error；message 是经过筛选的可读说明。",
            "account_id / account_name" to "游戏账号的数字 ID 和显示名称；测试通知不含账号字段，名称为空时省略。",
            "ts" to "事件时间，RFC 3339 字符串，携带时区，可能含小数秒。",
            "recovered" to "布尔值，true 表示恢复通知；异常和测试为 false。",
            "occurrences / duration_seconds" to "本次异常累计出现次数、从首次出现到当前事件的秒数，均为整数；首次一般为 1 / 0，测试固定为 1 / 0。",
        ).forEach { (field, detail) ->
            Text(field, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace))
            Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("接收端应在 10 秒内返回任意 HTTP 2xx，响应体不参与判断。网络失败、408、429、5xx 会退避重试，最多尝试 5 次；其他非 2xx 直接失败，不跟随重定向。通知超过 24 小时不再投递。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("重试可能重复投递，请先按 id 幂等入队再确认。每条新通知的 id 不同（包括同一异常的后续汇总和恢复）；不要把 id 当作异常编号。可按 account_id + kind 关联异常和恢复。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("当前自定义渠道不提供签名或自定义请求头，可使用接收 URL 中的随机令牌校验请求；请勿公开该地址。只发送必要状态，不含游戏凭据、原始响应或完整日志。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
