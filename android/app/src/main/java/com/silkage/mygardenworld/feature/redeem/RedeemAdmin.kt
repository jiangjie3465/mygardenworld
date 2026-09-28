package com.silkage.mygardenworld.feature.redeem

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mygardenworld.v1.Channel
import com.mygardenworld.v1.RedeemCode
import com.mygardenworld.v1.RedeemSource
import com.mygardenworld.v1.RedeemSourceType
import com.mygardenworld.v1.UpsertRedeemSourceRequest
import com.silkage.mygardenworld.core.ui.Badge
import com.silkage.mygardenworld.core.ui.BadgeTone
import com.silkage.mygardenworld.core.ui.ErrorBanner
import com.silkage.mygardenworld.core.ui.Format
import com.silkage.mygardenworld.core.ui.SectionCard
import com.silkage.mygardenworld.core.ui.SwitchRow

private const val DEFAULT_PARSER = "{\n  \"type\": \"json_array\",\n  \"code_field\": \"code\",\n  \"permanent\": true\n}"

private fun emptySource(): UpsertRedeemSourceRequest = UpsertRedeemSourceRequest.newBuilder()
    .setType(RedeemSourceType.REDEEM_SOURCE_TYPE_MYGARDENWORLD)
    .setChannel(Channel.CHANNEL_IOS)
    .setParserConfigJson(DEFAULT_PARSER)
    .setEnabled(true)
    .setPushEnabled(true)
    .setPollIntervalSeconds(300)
    .build()

private fun RedeemSource.toForm(): UpsertRedeemSourceRequest = UpsertRedeemSourceRequest.newBuilder()
    .setId(id).setName(name).setType(type).setBaseUrl(baseUrl)
    .setChannel(if (channel == Channel.CHANNEL_UNSPECIFIED) Channel.CHANNEL_IOS else channel)
    .setParserConfigJson(parserConfigJson).setEnabled(enabled).setPushEnabled(pushEnabled)
    .setPollIntervalSeconds(pollIntervalSeconds)
    .build()

/** Admin-only data-source manager (Web SourceManager). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RedeemSourceManager(state: RedeemUiState, online: Boolean, onSave: (UpsertRedeemSourceRequest, () -> Unit) -> Unit, onSync: (Long) -> Unit, onDelete: (RedeemSource) -> Unit) {
    var form by remember { mutableStateOf<UpsertRedeemSourceRequest?>(null) }
    var confirmDelete by remember { mutableStateOf<RedeemSource?>(null) }
    SectionCard("数据源管理", actions = { OutlinedButton(onClick = { form = emptySource() }, enabled = online, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)) { Text("添加") } }) {
        Text("MyGardenWorld 节点的每条兑换码自带渠道；自定义网页来源只读取，并统一归入所选渠道。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.sourceError.isNotBlank()) ErrorBanner(state.sourceError)
        if (state.sources.isEmpty()) Text("尚未配置数据源，本节点仍可公开录入和验证。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.sources.forEach { source ->
            val node = source.type == RedeemSourceType.REDEEM_SOURCE_TYPE_MYGARDENWORLD
            Column(
                Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)).clickable(enabled = online) { form = source.toForm() }.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(source.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(source.baseUrl, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Badge(if (node) "节点" else "网页", if (source.enabled) BadgeTone.SECONDARY else BadgeTone.NEUTRAL)
                    Badge(if (node) "按兑换码" else Format.channel(source.channel))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Badge("收录 ${source.observedCount}")
                    Badge("可采信 ${source.trustedCount}", BadgeTone.SECONDARY)
                    Badge("无效 ${source.invalidCount}", if (source.invalidCount > 0) BadgeTone.DANGER else BadgeTone.NEUTRAL)
                }
                Text("成功 ${source.successCount} · 已兑换 ${source.alreadyRedeemedCount} · 已过期 ${source.expiredCount} · 待验证 ${source.pendingCount}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (source.lastError.isNotBlank()) Text(source.lastError, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onSync(source.id) }, enabled = online && state.sourceBusy != "sync:${source.id}") { Text(if (state.sourceBusy == "sync:${source.id}") "同步中…" else "立即同步") }
                    TextButton(onClick = { confirmDelete = source }, enabled = online) { Text("删除", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
    form?.let { current ->
        SourceFormDialog(current, saving = state.sourceBusy == "save", onDismiss = { form = null }) { next -> onSave(next) { form = null } }
    }
    confirmDelete?.let { source ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("删除数据源") },
            text = { Text("删除数据源“${source.name}”？") },
            confirmButton = { TextButton(onClick = { onDelete(source); confirmDelete = null }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("取消") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourceFormDialog(initial: UpsertRedeemSourceRequest, saving: Boolean, onDismiss: () -> Unit, onSave: (UpsertRedeemSourceRequest) -> Unit) {
    var form by remember { mutableStateOf(initial) }
    var interval by remember { mutableStateOf(initial.pollIntervalSeconds.toString()) }
    val node = form.type == RedeemSourceType.REDEEM_SOURCE_TYPE_MYGARDENWORLD
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(if (initial.id == 0L) "添加数据源" else "编辑数据源") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(form.name, { form = form.toBuilder().setName(it).build() }, Modifier.fillMaxWidth(), label = { Text("名称") }, singleLine = true)
                OutlinedTextField(form.baseUrl, { form = form.toBuilder().setBaseUrl(it).build() }, Modifier.fillMaxWidth(), label = { Text("地址") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    placeholder = { Text(if (node) "https://gardend.example.com" else "https://example.com/codes.json") })
                if (node) Text("填写对方部署的站点根地址，无需附加 Connect 接口路径。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("类型", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = node, onClick = { form = form.toBuilder().setType(RedeemSourceType.REDEEM_SOURCE_TYPE_MYGARDENWORLD).setPushEnabled(true).build() }, label = { Text("MyGardenWorld 节点") })
                    FilterChip(selected = !node, onClick = { form = form.toBuilder().setType(RedeemSourceType.REDEEM_SOURCE_TYPE_CUSTOM_HTTP).setPushEnabled(false).build() }, label = { Text("自定义网页来源") })
                }
                OutlinedTextField(interval, { interval = it.filter(Char::isDigit).take(6) }, Modifier.fillMaxWidth(), label = { Text("拉取间隔（秒）") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                if (!node) {
                    Text("兑换渠道", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(Channel.CHANNEL_IOS to "iOS", Channel.CHANNEL_ALIPAY to "Alipay").forEach { (channel, label) ->
                            FilterChip(selected = form.channel == channel, onClick = { form = form.toBuilder().setChannel(channel).build() }, label = { Text(label) })
                        }
                    }
                    Text("该来源解析出的所有兑换码都归入此渠道。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(form.parserConfigJson, { form = form.toBuilder().setParserConfigJson(it).build() }, Modifier.fillMaxWidth(), label = { Text("解析配置") }, minLines = 5, textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                    Text("必须明确一种期限规则：permanent、expires_field 或 default_ttl_seconds。来源未提供真实过期时间时建议使用 permanent: true，由游戏结果判定失效。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SwitchRow("启用拉取", form.enabled) { form = form.toBuilder().setEnabled(it).build() }
                if (node) SwitchRow("验证后回传", form.pushEnabled) { form = form.toBuilder().setPushEnabled(it).build() }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(form.toBuilder().setPollIntervalSeconds(interval.toIntOrNull() ?: 0).build()) },
                enabled = !saving && form.name.isNotBlank() && form.baseUrl.isNotBlank(),
            ) { Text(if (saving) "保存中…" else "保存数据源") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") } },
    )
}

private val CUSTOM_UNITS = listOf("分钟" to 60L, "小时" to 3600L, "天" to 86400L)

/**
 * Admin expiry correction (Web RedeemExpiryDialog). [onSave] receives the
 * chosen lifetime in seconds, or null for permanent.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RedeemExpiryDialog(entry: RedeemCode, saving: Boolean, error: String, onDismiss: () -> Unit, onSave: (Long?) -> Unit, onRestoreSource: () -> Unit) {
    val remainingMinutes = if (entry.hasExpiresAt()) ((entry.expiresAt.seconds * 1000 - System.currentTimeMillis()) / 60_000 + 1).coerceAtLeast(1) else 15
    var preset by remember { mutableStateOf(-1) }
    var amount by remember { mutableStateOf(remainingMinutes.toString()) }
    var unit by remember { mutableStateOf(0) }
    val permanentIndex = EXPIRY_PRESETS.indexOfFirst { it.second == 0L }
    val current = when {
        entry.permanent -> "当前为未知期限"
        entry.hasExpiresAt() -> "当前至 ${Format.dayClock(entry.expiresAt.seconds * 1000)}"
        else -> "当前期限待确认"
    } + if (entry.expiryOverridden) " · 已人工校正" else " · 来自数据源"
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("修正兑换码有效期") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("管理员修正仅改变当前节点采用的期限；兑换码文本与渠道仍保持原始身份。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(entry.code, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                Text(current, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("新的有效时间", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EXPIRY_PRESETS.forEachIndexed { index, (label, _) -> FilterChip(selected = preset == index, onClick = { preset = index }, label = { Text(label) }) }
                    FilterChip(selected = preset == -1, onClick = { preset = -1 }, label = { Text("自定义") })
                }
                if (preset == -1) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(amount, { amount = it.filter(Char::isDigit).take(6) }, Modifier.width(96.dp), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        CUSTOM_UNITS.forEachIndexed { index, (label, _) -> FilterChip(selected = unit == index, onClick = { unit = index }, label = { Text(label) }) }
                    }
                }
                Text("保存后，后续数据源同步不会覆盖这次人工修正。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (error.isNotBlank()) ErrorBanner(error)
                if (entry.expiryOverridden) TextButton(onClick = onRestoreSource, enabled = !saving) { Text("恢复数据源期限") }
            }
        },
        confirmButton = {
            val seconds: Long? = when {
                preset == permanentIndex -> null
                preset >= 0 -> EXPIRY_PRESETS[preset].second
                else -> (amount.toLongOrNull() ?: 0) * CUSTOM_UNITS[unit].second
            }
            TextButton(onClick = { onSave(seconds) }, enabled = !saving && (seconds == null || seconds > 0)) { Text(if (saving) "保存中…" else "保存修正") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") } },
    )
}
