package com.silkage.mygardenworld.feature.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mygardenworld.v1.ExecutionLane
import com.mygardenworld.v1.PlanStatus
import com.mygardenworld.v1.PlannedOperation
import com.mygardenworld.v1.VideoActionState
import com.silkage.mygardenworld.core.game.Catalog
import com.silkage.mygardenworld.core.protocol.WorkspaceUiState
import com.silkage.mygardenworld.core.ui.Badge
import com.silkage.mygardenworld.core.ui.BadgeTone
import com.silkage.mygardenworld.core.ui.EmptyState
import com.silkage.mygardenworld.core.ui.Format
import com.silkage.mygardenworld.core.ui.SectionCard
import com.silkage.mygardenworld.core.ui.StatTile

private const val SPEED_UP_TICKET_ITEM_ID = 1001
private const val FLORAL_COIN_ITEM_ID = 1002

@Composable
fun BasicTab(workspace: WorkspaceUiState, catalog: Catalog) {
    val state = workspace.state
    val basic = state?.takeIf { it.hasBasic() }?.basic
    val status = workspace.selectedStatus
    val inventory = state?.takeIf { it.hasWarehouse() }?.warehouse?.inventoryMap.orEmpty()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionCard("监控概览", actions = { basic?.capturedAt?.let { Badge("快照 ${Format.timestamp(it)}") } }) {
                val level = basic?.level ?: status?.level ?: 0
                val experience = basic?.experience ?: status?.experience ?: 0
                val nextExp = basic?.nextLevelExperience ?: status?.nextLevelExperience ?: 0
                val maxed = basic?.levelMaxed ?: status?.levelMaxed ?: false
                val toNext = basic?.experienceToNextLevel ?: status?.experienceToNextLevel ?: 0
                val reputationObserved = basic?.reputationObserved ?: status?.reputationObserved ?: false
                val reputation = basic?.reputationScore ?: status?.reputationScore ?: 0
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(Icons.Filled.EmojiEvents, "等级", if (level > 0) "${level}级" else "-", "经验 ${Format.count(experience)}", Modifier.weight(1f))
                    StatTile(Icons.Filled.TrendingUp, "距下一等级", if (maxed) "已满级" else if (nextExp > 0) "${Format.count(toNext)} 经验" else "-", if (!maxed && nextExp > 0) "当前 ${Format.count(experience)} / 需要 ${Format.count(nextExp)}" else null, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(Icons.Filled.Opacity, "水滴", "${Format.count(basic?.waterDrops ?: 0)}/${Format.count(basic?.waterDropsTotal ?: 0)}", null, Modifier.weight(1f))
                    StatTile(Icons.Filled.Star, "礼仪分", if (reputationObserved) Format.count(reputation) else "-", if (reputationObserved) "已同步" else "未同步", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(Icons.Filled.Diamond, "元宝", Format.count(basic?.diamondsFree ?: 0), null, Modifier.weight(1f))
                    StatTile(Icons.Filled.MonetizationOn, "金币", Format.count(basic?.gold ?: 0), null, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(Icons.Filled.MonetizationOn, "花坊币", Format.count(inventory[FLORAL_COIN_ITEM_ID] ?: 0), null, Modifier.weight(1f))
                    StatTile(Icons.Filled.Star, "加速卡", Format.count(inventory[SPEED_UP_TICKET_ITEM_ID] ?: 0), null, Modifier.weight(1f))
                }
                val videos = basic?.videoActionsList.orEmpty()
                if (videos.isNotEmpty()) {
                    Text("手动提醒 · 仅展示服务端状态，请在官方游戏内操作", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    videos.forEach { action ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(action.label, style = MaterialTheme.typography.bodySmall)
                                if (action.detail.isNotBlank()) Text(action.detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            val (label, tone) = when (action.state) {
                                VideoActionState.VIDEO_ACTION_STATE_READY -> "可观看" to BadgeTone.PRIMARY
                                VideoActionState.VIDEO_ACTION_STATE_COOLDOWN -> "冷却" to BadgeTone.SECONDARY
                                VideoActionState.VIDEO_ACTION_STATE_EXHAUSTED -> "已用完" to BadgeTone.NEUTRAL
                                VideoActionState.VIDEO_ACTION_STATE_ACTIVE -> "生效中" to BadgeTone.SUCCESS
                                else -> "未知" to BadgeTone.NEUTRAL
                            }
                            Badge("$label ${action.used}/${action.limit}", tone)
                        }
                    }
                }
            }
        }
        item {
            val pearl = basic?.takeIf { it.hasPearlHire() }?.pearlHire
            SectionCard("珍珠雇佣", defaultOpen = false, actions = {
                if (pearl != null) {
                    Badge("雇佣券 ${Format.count(pearl.ticketCount)}")
                    Badge(if (pearl.dailyTicketLimit > 0) "今日 ${pearl.ticketUsedToday}/${pearl.dailyTicketLimit}" else "今日 ${pearl.ticketUsedToday}（不限）", BadgeTone.SECONDARY)
                }
            }) {
                if (pearl == null || pearl.slotsCount == 0) EmptyState("暂无劳工槽位快照", "登录并完成珍珠状态同步后显示")
                else pearl.slotsList.forEach { slot ->
                    val occupied = slot.laborUid > 0
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text("槽位 ${slot.placeId} · ${slot.laborName.ifBlank { if (occupied) slot.laborUid.toString() else "空闲" }}", style = MaterialTheme.typography.bodySmall)
                            Text(if (slot.laborEndTimeMs > 0) "${if (slot.active) "结束" else "已结束"} ${Format.dayClock(slot.laborEndTimeMs)}" else "待雇佣", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Badge(if (slot.active) "在岗" else if (occupied) "到期" else "空闲", if (slot.active) BadgeTone.PRIMARY else BadgeTone.NEUTRAL)
                    }
                }
            }
        }
        item {
            val queue = basic?.plannedOperationsList.orEmpty().filter { isQueueOperation(it) }
            SectionCard("执行队列", actions = { Badge(queue.size.toString(), BadgeTone.SECONDARY) }) {
                if (queue.isEmpty()) EmptyState("当前无可执行操作")
                else {
                    val farm = queue.filter { it.lane == ExecutionLane.EXECUTION_LANE_FARM }
                    val side = queue.filter { it.lane != ExecutionLane.EXECUTION_LANE_FARM }
                    OperationLane("种植通道", farm, "暂无收获、播种或浇水", catalog)
                    OperationLane("其他通道", side, "暂无任务、订单或活动操作", catalog)
                }
            }
        }
        item {
            val tasks = basic?.pendingTasksList.orEmpty()
            SectionCard("任务", defaultOpen = false, actions = { Badge(tasks.size.toString(), BadgeTone.SECONDARY) }) {
                if (tasks.isEmpty()) EmptyState("暂无任务待监控") else tasks.forEach { PendingTaskRow(it) }
            }
        }
    }
}

@Composable
private fun OperationLane(title: String, operations: List<PlannedOperation>, emptyText: String, catalog: Catalog) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Badge(operations.size.toString(), BadgeTone.SECONDARY)
    }
    if (operations.isEmpty()) Text(emptyText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    operations.forEach { op ->
        val (label, tone) = Format.operationBadge(op)
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Badge(label, tone)
            Column(Modifier.weight(1f)) {
                Text(Format.operationTitle(op) + Format.operationTarget(op, catalog).let { if (it.isBlank()) "" else " $it" }, style = MaterialTheme.typography.bodySmall)
                val note = listOf(Format.operationCost(op, catalog), Format.operationNote(op)).filter { it.isNotBlank() }.joinToString(" · ")
                if (note.isNotBlank()) Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun isQueueOperation(op: PlannedOperation): Boolean {
    val runnable = op.executable && !op.syncOnly && op.status != PlanStatus.PLAN_STATUS_ADAPTER_MISSING && op.status != PlanStatus.PLAN_STATUS_BLOCKED && op.blockedReasonsCount == 0
    return runnable || Format.operationCooling(op)
}
