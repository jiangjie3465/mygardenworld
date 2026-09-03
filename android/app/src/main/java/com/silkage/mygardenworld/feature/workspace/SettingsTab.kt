package com.silkage.mygardenworld.feature.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.silkage.mygardenworld.core.protocol.WorkspaceUiState
import com.silkage.mygardenworld.core.ui.Badge
import com.silkage.mygardenworld.core.ui.BadgeTone
import com.silkage.mygardenworld.core.ui.Format
import com.silkage.mygardenworld.core.ui.LoadingBox
import com.silkage.mygardenworld.core.ui.NumberRow
import com.silkage.mygardenworld.core.ui.SectionCard
import com.silkage.mygardenworld.core.ui.SwitchRow
import com.silkage.mygardenworld.feature.workspace.PolicyLens.benefit
import com.silkage.mygardenworld.feature.workspace.PolicyLens.basic
import com.silkage.mygardenworld.feature.workspace.PolicyLens.cultivate
import com.silkage.mygardenworld.feature.workspace.PolicyLens.customer
import com.silkage.mygardenworld.feature.workspace.PolicyLens.flowerArt
import com.silkage.mygardenworld.feature.workspace.PolicyLens.palace
import com.silkage.mygardenworld.feature.workspace.PolicyLens.pearl
import com.silkage.mygardenworld.feature.workspace.PolicyLens.planting
import com.silkage.mygardenworld.feature.workspace.PolicyLens.reputation
import com.silkage.mygardenworld.feature.workspace.PolicyLens.resident
import com.silkage.mygardenworld.feature.workspace.PolicyLens.sign
import com.silkage.mygardenworld.feature.workspace.PolicyLens.task
import com.silkage.mygardenworld.feature.workspace.PolicyLens.team

@Composable
fun SettingsTab(viewModel: WorkspaceViewModel, screen: WorkspaceScreenState, workspace: WorkspaceUiState) {
    val policy = screen.policy
    val status = workspace.selectedStatus
    val connected = Format.accountConnected(screen.account, status)
    val editable = policy != null && workspace.online && !screen.savingPolicy
    var confirmDelete by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionCard("账号操作") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (connected) OutlinedButton(onClick = viewModel::disconnect, enabled = workspace.online && screen.busyAction.isBlank(), modifier = Modifier.weight(1f)) { Text(if (screen.busyAction == "logout") "处理中…" else "断开连接") }
                    else Button(onClick = viewModel::connect, enabled = workspace.online && screen.busyAction.isBlank(), modifier = Modifier.weight(1f)) { Text(if (screen.busyAction == "login") "登录中…" else "登录游戏") }
                    OutlinedButton(onClick = { confirmDelete = true }, enabled = workspace.online && screen.busyAction.isBlank(), modifier = Modifier.weight(1f)) { Text("删除账号") }
                }
                val automationOn = status?.automationEnabled ?: policy?.automationEnabled ?: false
                SwitchRow("自动化", automationOn, enabled = workspace.online && screen.busyAction.isBlank(), hint = "关闭后连接保留，仅停止自动操作") { viewModel.setAutomation(it) }
                if (!workspace.online) Text("当前离线，操作已禁用", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
        if (policy == null) {
            item { if (screen.policyLoading) LoadingBox("策略加载中…") else OutlinedButton(onClick = viewModel::loadPolicy) { Text("重新加载策略") } }
            return@LazyColumn
        }
        item {
            SectionCard("运行参数", actions = { if (screen.policyDirty) Badge("未保存", BadgeTone.WARNING) }) {
                NumberRow("决策间隔（秒）", policy.decisionIntervalSeconds.toLong(), editable) { v -> viewModel.editPolicy { it.toBuilder().setDecisionIntervalSeconds(v.coerceAtLeast(1).toDouble()).build() } }
                Text("设置仅在保存后生效", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            val p = policy.plant.planting
            SectionCard("土地与种植") {
                SwitchRow("自动种植", p.autoEnabled, editable) { v -> viewModel.editPolicy { it.planting { autoEnabled = v } } }
                SwitchRow("自动收获", p.autoHarvestEnabled, editable) { v -> viewModel.editPolicy { it.planting { autoHarvestEnabled = v } } }
                NumberRow("延时收获（秒）", p.harvestDelaySeconds.toLong(), editable) { v -> viewModel.editPolicy { it.planting { harvestDelaySeconds = v.toInt() } } }
                SwitchRow("解锁土地", p.autoUnlockLand, editable) { v -> viewModel.editPolicy { it.planting { autoUnlockLand = v } } }
                SwitchRow("使用加速券", p.useSpeedUpTicket, editable) { v -> viewModel.editPolicy { it.planting { useSpeedUpTicket = v } } }
                NumberRow("加速券上限", p.speedUpTicketMax.toLong(), editable) { v -> viewModel.editPolicy { it.planting { speedUpTicketMax = v.toInt() } } }
                NumberRow("保留水滴", p.minWaterDrops.toLong(), editable) { v -> viewModel.editPolicy { it.planting { minWaterDrops = v.toInt() } } }
                NumberRow("最低种植等级", p.autoReplantMinLevel.toLong(), editable, "0 表示不限") { v -> viewModel.editPolicy { it.planting { autoReplantMinLevel = v.toInt() } } }
            }
        }
        item {
            val b = policy.basic
            SectionCard("水滴补给", defaultOpen = false) {
                SwitchRow("水车水滴", b.waterwheelEnabled, editable) { v -> viewModel.editPolicy { it.basic { waterwheelEnabled = v } } }
                SwitchRow("限时水滴", b.freeWaterEnabled, editable) { v -> viewModel.editPolicy { it.basic { freeWaterEnabled = v } } }
                NumberRow("水滴领取阈值", b.waterClaimThreshold.toLong(), editable) { v -> viewModel.editPolicy { it.basic { waterClaimThreshold = v.toInt() } } }
            }
        }
        item {
            val c = policy.plant.cultivate
            SectionCard("培育配置", defaultOpen = false) {
                SwitchRow("自动培育", c.enabled, editable) { v -> viewModel.editPolicy { it.cultivate { enabled = v } } }
                SwitchRow("鲜花升级", c.upgradeEnabled, editable) { v -> viewModel.editPolicy { it.cultivate { upgradeEnabled = v } } }
                NumberRow("目标等级", c.targetLevel.toLong(), editable) { v -> viewModel.editPolicy { it.cultivate { targetLevel = v.toInt() } } }
            }
        }
        item {
            val b = policy.basic
            SectionCard("基础配置", defaultOpen = false) {
                SwitchRow("礼仪分监控", b.reputation.enabled, editable) { v -> viewModel.editPolicy { it.reputation { enabled = v } } }
                NumberRow("礼仪分阈值", b.reputation.threshold.toLong(), editable) { v -> viewModel.editPolicy { it.reputation { threshold = v.toInt() } } }
                SwitchRow("被挤号后自动重登", b.displacedSessionReloginEnabled, editable) { v -> viewModel.editPolicy { it.basic { displacedSessionReloginEnabled = v } } }
                NumberRow("自动重登间隔（秒）", b.reconnectIntervalSeconds.toLong(), editable) { v -> viewModel.editPolicy { it.basic { reconnectIntervalSeconds = v.toDouble() } } }
                SwitchRow("地图随机事件", b.mapEventEnabled, editable) { v -> viewModel.editPolicy { it.basic { mapEventEnabled = v } } }
                SwitchRow("成长之路", b.roadGrowRewardEnabled, editable) { v -> viewModel.editPolicy { it.basic { roadGrowRewardEnabled = v } } }
            }
        }
        item {
            val t = policy.basic.task
            SectionCard("任务与剧情", defaultOpen = false) {
                SwitchRow("主线任务", t.mainEnabled, editable) { v -> viewModel.editPolicy { it.task { mainEnabled = v } } }
                SwitchRow("每日任务", t.dailyEnabled, editable) { v -> viewModel.editPolicy { it.task { dailyEnabled = v } } }
                SwitchRow("每周任务", t.weeklyEnabled, editable) { v -> viewModel.editPolicy { it.task { weeklyEnabled = v } } }
                SwitchRow("主线剧情", t.storyEnabled, editable) { v -> viewModel.editPolicy { it.task { storyEnabled = v } } }
                SwitchRow("成就任务", t.achievementEnabled, editable) { v -> viewModel.editPolicy { it.task { achievementEnabled = v } } }
            }
        }
        item {
            val b = policy.basic
            SectionCard("日常奖励", defaultOpen = false) {
                SwitchRow("邮件", b.mailEnabled, editable) { v -> viewModel.editPolicy { it.basic { mailEnabled = v } } }
                SwitchRow("福利宝箱", b.benefit.boxEnabled, editable) { v -> viewModel.editPolicy { it.benefit { boxEnabled = v } } }
                SwitchRow("分享奖励", b.benefit.shareRewardEnabled, editable) { v -> viewModel.editPolicy { it.benefit { shareRewardEnabled = v } } }
                SwitchRow("防骗宝箱", b.benefit.antiScamBoxEnabled, editable) { v -> viewModel.editPolicy { it.benefit { antiScamBoxEnabled = v } } }
                SwitchRow("防诈骗签到奖励", b.sign.dailyEnabled, editable) { v -> viewModel.editPolicy { it.sign { dailyEnabled = v } } }
                SwitchRow("自动补签", b.sign.patchEnabled, editable) { v -> viewModel.editPolicy { it.sign { patchEnabled = v } } }
            }
        }
        item {
            val p = policy.basic.pearl
            SectionCard("珍珠", defaultOpen = false) {
                SwitchRow("免费珍珠", p.freeEnabled, editable) { v -> viewModel.editPolicy { it.pearl { freeEnabled = v } } }
                SwitchRow("安全雇佣劳工", p.autoHireEnabled, editable) { v -> viewModel.editPolicy { it.pearl { autoHireEnabled = v } } }
                NumberRow("雇佣等级上限（0=不限）", p.maxHireLevel.toLong(), editable) { v -> viewModel.editPolicy { it.pearl { maxHireLevel = v.toInt() } } }
                NumberRow("同时在岗上限（0=关闭）", p.maxHireTicketUsage.toLong(), editable) { v -> viewModel.editPolicy { it.pearl { maxHireTicketUsage = v.toInt() } } }
                NumberRow("每日雇佣券上限（0=不限）", p.dailyHireTicketLimit.toLong(), editable) { v -> viewModel.editPolicy { it.pearl { dailyHireTicketLimit = v.toInt() } } }
                SwitchRow("自动开珍珠", p.drawEnabled, editable) { v -> viewModel.editPolicy { it.pearl { drawEnabled = v } } }
                SwitchRow("开启防身", p.protectEnabled, editable) { v -> viewModel.editPolicy { it.pearl { protectEnabled = v } } }
            }
        }
        item {
            val r = policy.order.resident
            SectionCard("居民订单", defaultOpen = false) {
                SwitchRow("普通居民订单", r.normalEnabled, editable) { v -> viewModel.editPolicy { it.resident { normalEnabled = v } } }
                NumberRow("普通订单上限", r.normalDailyLimit.toLong(), editable) { v -> viewModel.editPolicy { it.resident { normalDailyLimit = v.toInt() } } }
                SwitchRow("绸缎订单", r.satinEnabled, editable) { v -> viewModel.editPolicy { it.resident { satinEnabled = v } } }
                NumberRow("绸缎订单上限", r.satinDailyLimit.toLong(), editable) { v -> viewModel.editPolicy { it.resident { satinDailyLimit = v.toInt() } } }
                SwitchRow("建材订单", r.decorateEnabled, editable) { v -> viewModel.editPolicy { it.resident { decorateEnabled = v } } }
                NumberRow("建材订单上限", r.decorateDailyLimit.toLong(), editable) { v -> viewModel.editPolicy { it.resident { decorateDailyLimit = v.toInt() } } }
                SwitchRow("居民领奖", r.rewardEnabled, editable) { v -> viewModel.editPolicy { it.resident { rewardEnabled = v } } }
            }
        }
        item {
            val c = policy.order.customer
            val pl = policy.order.palace
            val t = policy.order.team
            SectionCard("顾客、宫廷、组团", defaultOpen = false) {
                SwitchRow("顾客订单", c.enabled, editable) { v -> viewModel.editPolicy { it.customer { enabled = v } } }
                NumberRow("每日上限", c.dailyLimit.toLong(), editable) { v -> viewModel.editPolicy { it.customer { dailyLimit = v.toInt() } } }
                NumberRow("最少花艺", c.minFlowerArtCount.toLong(), editable) { v -> viewModel.editPolicy { it.customer { minFlowerArtCount = v.toInt() } } }
                SwitchRow("暂时无货", c.rejectUnavailableEnabled, editable) { v -> viewModel.editPolicy { it.customer { rejectUnavailableEnabled = v } } }
                SwitchRow("宫廷订单", pl.enabled, editable) { v -> viewModel.editPolicy { it.palace { enabled = v } } }
                SwitchRow("组团订单", t.enabled, editable) { v -> viewModel.editPolicy { it.team { enabled = v } } }
                SwitchRow("再来一单", t.oneMoreEnabled, editable) { v -> viewModel.editPolicy { it.team { oneMoreEnabled = v } } }
                SwitchRow("仅已培育", t.submitOnlyCultivated, editable) { v -> viewModel.editPolicy { it.team { submitOnlyCultivated = v } } }
            }
        }
        item {
            val f = policy.order.flowerArt
            SectionCard("花架售卖", defaultOpen = false) {
                SwitchRow("解锁花架", f.autoUnlockStand, editable) { v -> viewModel.editPolicy { it.flowerArt { autoUnlockStand = v } } }
                SwitchRow("自动上架花艺", f.sellEnabled, editable) { v -> viewModel.editPolicy { it.flowerArt { sellEnabled = v } } }
                SwitchRow("自动制作", f.craftEnabled, editable) { v -> viewModel.editPolicy { it.flowerArt { craftEnabled = v } } }
                SwitchRow("提前下架", f.earlyCancelEnabled, editable) { v -> viewModel.editPolicy { it.flowerArt { earlyCancelEnabled = v } } }
                SwitchRow("0-8点关闭自动上架花艺", f.sellNightPauseEnabled, editable) { v -> viewModel.editPolicy { it.flowerArt { sellNightPauseEnabled = v } } }
                SwitchRow("花艺经验", f.createRewardEnabled, editable) { v -> viewModel.editPolicy { it.flowerArt { createRewardEnabled = v } } }
                SwitchRow("图鉴奖励", f.collectRewardEnabled, editable) { v -> viewModel.editPolicy { it.flowerArt { collectRewardEnabled = v } } }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = viewModel::loadPolicy, enabled = !screen.savingPolicy, modifier = Modifier.weight(1f)) { Text("放弃修改") }
                Button(onClick = viewModel::savePolicy, enabled = editable && screen.policyDirty, modifier = Modifier.weight(1f)) { Text(if (screen.savingPolicy) "保存中…" else "保存设置") }
            }
            if (screen.message.isNotBlank()) Text(screen.message, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除账号") },
            text = { Text("将停止运行器并删除账号、会话和策略。此操作不可撤销。") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.delete() }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}
