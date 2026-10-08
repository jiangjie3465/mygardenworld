package com.silkage.mygardenworld.feature.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mygardenworld.v1.AccountDeletionProgress
import com.mygardenworld.v1.MaintenanceView
import com.mygardenworld.v1.Policy
import com.silkage.mygardenworld.core.ui.CloudColors
import com.silkage.mygardenworld.core.ui.Format

/**
 * Recovery guidance for the 5000 request protection. Start/pause does not end
 * the cooldown, but an explicit start authorizes one fresh login for the
 * current incident; the policy switch makes later incidents recover unattended.
 */
fun restrictionHint(issues: List<String>, policy: Policy?): String? {
    if (issues.none { it.contains("5000") }) return null
    if (policy == null) return "账号处于 5000 请求保护，冷却结束后核验恢复；手动启动可允许本次使用一次重新认证。"
    if (policy.basic.serverErrorFreshLoginEnabled) {
        if (!policy.automationEnabled) return "账号处于 5000 请求保护，已允许冷却结束后重新认证；启用自动化后会在冷却结束时自动核验恢复。"
        return "账号处于 5000 请求保护，已允许冷却结束后重新认证，请等待冷却结束后的自动核验。"
    }
    return "账号处于 5000 请求保护。若缓存会话不可用，手动启动账号（或启用自动化）后，冷却结束时会使用本次唯一一次重新认证恢复；" +
        "开启「5000 异常后允许重新登录」并保存可让以后自动恢复。重新认证可能挤下手机端。"
}

@Composable
fun AccountIssuesBanner(issues: List<String>, hint: String?, onOpenSettings: (() -> Unit)?, modifier: Modifier = Modifier) {
    if (issues.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(true) }
    Column(
        modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClickLabel = if (expanded) "收起" else "展开") { expanded = !expanded }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("异常信息" + if (issues.size > 1) "（${issues.size}）" else "", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
            Text(if (expanded) "收起" else "展开", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
        if (!expanded) {
            Text(issues.last(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 1, overflow = TextOverflow.Ellipsis)
            return@Column
        }
        issues.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 4.dp))
            if (onOpenSettings != null) TextButton(onClick = onOpenSettings) { Text("前往设置") }
        }
    }
}

@Composable
fun MaintenanceBanner(maintenance: MaintenanceView?, modifier: Modifier = Modifier) {
    if (maintenance?.enabled != true) return
    Text(
        if (maintenance.draining) "正在进入系统维护，停止游戏连接与在途请求。" else "系统维护中，游戏连接与操作已暂停。",
        modifier.fillMaxWidth()
            .background(CloudColors.Amber.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodySmall,
    )
}

/** Absolute timestamps only; no countdown or percentage (Web AccountDeletionProgressDetails). */
@Composable
fun DeletionProgressCard(failed: Boolean, progress: AccountDeletionProgress?, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(if (failed) "账号清理待重试" else "账号删除中", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text("删除请求已保存，后台清理完成后会自动移出列表。清理期间不能操作或重新添加该账号。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (progress == null) {
            Text("正在读取清理进度…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return
        }
        fun time(ms: Long) = if (ms > 0) Format.dayClock(ms) else "尚未记录"
        Text("当前阶段：${Format.deletionPhase(progress.phase)}", style = MaterialTheme.typography.bodySmall)
        Text("已记录清理：${Format.count(progress.removedRows)} 条", style = MaterialTheme.typography.bodySmall)
        Text("最后有进展：${time(progress.lastProgressMs)} · 最近一次尝试：${time(progress.attemptMs)}", style = MaterialTheme.typography.bodySmall)
        if (progress.errorKind.isNotBlank()) {
            Text("${Format.deletionError(progress.errorKind)} · 连续失败 ${progress.failures} 次" + if (progress.retryAtMs > 0) "\n下次尝试不早于 ${time(progress.retryAtMs)}" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        if (progress.stalled) Text("已超过 15 分钟没有新的清理进展。请检查磁盘空间、数据库占用及服务进程日志；无需反复点击删除。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}
