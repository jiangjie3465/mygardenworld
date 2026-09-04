package com.silkage.mygardenworld.feature.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mygardenworld.v1.ActivityItem
import com.mygardenworld.v1.CyclicNoteMilestone
import com.mygardenworld.v1.CyclicNoteView
import com.mygardenworld.v1.CyclicStoryView
import com.mygardenworld.v1.PlanStatus
import com.silkage.mygardenworld.core.game.Catalog
import com.silkage.mygardenworld.core.protocol.WorkspaceUiState
import com.silkage.mygardenworld.core.ui.Badge
import com.silkage.mygardenworld.core.ui.BadgeTone
import com.silkage.mygardenworld.core.ui.EmptyState
import com.silkage.mygardenworld.core.ui.Format
import com.silkage.mygardenworld.core.ui.SectionCard
import com.silkage.mygardenworld.core.ui.StatTile

private data class ActivityProfile(val name: String, val currencyItemId: Int, val mechanic: String, val rewardPath: String, val description: String, val automation: String)

private val NOTE_PROFILE = ActivityProfile("花笺集芳", 1107, "品质任务", "进度奖励 · 花笺商店", "完成不同品质的活动任务可获得集芳笺；累计集芳笺解锁进度奖励，也可在花笺商店兑换活动奖励。", "自动同步活动，领取已完成任务与进度奖励；不执行钻石刷新或立即完成。")
private val STORY_PROFILE = ActivityProfile("莳花纪闻", 1108, "鲜花订单", "进度奖励 · 莳花商店", "提交指定鲜花订单可获得花史残页；累计花史残页解锁进度奖励，也可在莳花商店兑换活动奖励。", "自动同步活动，在库存充足时提交订单并领取进度奖励；不付费刷新或跳过冷却。")

private fun visible(found: Boolean, phase: Int) = found && phase in 1..3

@Composable
fun ActivitiesTab(workspace: WorkspaceUiState, catalog: Catalog) {
    val activities = workspace.state?.takeIf { it.hasActivities() }?.activities
    val note = activities?.takeIf { it.hasCyclicNote() }?.cyclicNote
    val story = activities?.takeIf { it.hasCyclicStory() }?.cyclicStory
    val synced = listOf(note?.observed == true, story?.observed == true).count { it }
    val open = listOf(note?.let { visible(it.found, it.phase) } == true, story?.let { visible(it.found, it.phase) } == true).count { it }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("活动监控", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Badge("支持 2")
                Badge("已同步 $synced/2")
                Badge("开放中 $open/2", if (open > 0) BadgeTone.SECONDARY else BadgeTone.NEUTRAL)
            }
        }
        item { CyclicNotePanel(note, catalog) }
        item { CyclicStoryPanel(story, catalog) }
    }
}

@Composable
private fun InactiveOverview(observed: Boolean, profile: ActivityProfile, currencyItemId: Int, catalog: Catalog) {
    val itemId = if (currencyItemId > 0) currencyItemId else profile.currencyItemId
    EmptyState(if (observed) "当前没有开放批次" else "活动状态尚未同步", if (observed) "游戏活动状态已同步，目前没有处于预告、进行中或领奖期的批次。" else "连接游戏并完成活动状态同步后，这里会自动显示当前批次。")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(Icons.Filled.Checklist, "核心玩法", profile.mechanic, "完成活动目标", Modifier.weight(1f))
        StatTile(Icons.Filled.LocalFlorist, "活动货币", catalog.itemName(itemId), "道具 #$itemId", Modifier.weight(1f))
    }
    Text("活动机制：${profile.description}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("自动化范围：${profile.automation}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("奖励去向：${profile.rewardPath}（活动结束后货币清空）", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun statusBadge(status: PlanStatus, received: Boolean, unknown: Boolean = false) {
    when {
        unknown -> Badge("未识别", BadgeTone.DANGER)
        received -> Badge("已领取")
        status == PlanStatus.PLAN_STATUS_READY -> Badge("可领取", BadgeTone.SECONDARY)
        status == PlanStatus.PLAN_STATUS_BLOCKED -> Badge("阻塞", BadgeTone.DANGER)
        status == PlanStatus.PLAN_STATUS_SYNC_ONLY -> Badge("进行中")
        else -> Badge(Format.planStatus(status))
    }
}

@Composable
private fun rewardLine(prefix: String, items: List<ActivityItem>, catalog: Catalog) {
    val text = if (items.isEmpty()) "未配置" else items.joinToString("、") { "${it.itemName.ifBlank { catalog.itemName(it.itemId) }} x${it.count}" }
    Text("$prefix $text", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun MilestoneRow(m: CyclicNoteMilestone, catalog: Catalog) {
    val target = m.target.coerceAtLeast(0)
    val progress = m.progress.coerceIn(0, if (target > 0) target else m.progress)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("积分 ${Format.count(target)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            statusBadge(m.status, m.received)
        }
        if (target > 0) LinearProgressIndicator(progress = { progress.toFloat() / target }, modifier = Modifier.fillMaxWidth())
        rewardLine("进度 ${Format.count(progress)}/${Format.count(target)} · 奖励", m.rewardList, catalog)
    }
}

@Composable
private fun CyclicNotePanel(a: CyclicNoteView?, catalog: Catalog) {
    val isVisible = a != null && visible(a.found, a.phase)
    val active = if (isVisible) a!!.tasksList.filter { it.unlocked } else emptyList()
    val readyTasks = if (isVisible && a!!.valid) active.count { it.status == PlanStatus.PLAN_STATUS_READY && !it.received } else 0
    val readyMilestones = if (isVisible && a!!.valid) a.milestonesList.count { it.ready && !it.received } else 0
    SectionCard(a?.name?.ifBlank { null } ?: NOTE_PROFILE.name, actions = {
        Badge(if (isVisible) Format.activityPhase(a!!.phase) else if (a?.observed == true) "未开放" else "待同步", if (isVisible && a!!.phase == 2) BadgeTone.SECONDARY else BadgeTone.NEUTRAL)
        if (isVisible) Badge("批次 ${a!!.batchId}")
        if (isVisible && !a!!.valid) Badge("配置异常", BadgeTone.DANGER)
        if (readyTasks + readyMilestones > 0) Badge("可领取 ${readyTasks + readyMilestones}", BadgeTone.SECONDARY)
    }) {
        when {
            !isVisible -> InactiveOverview(a?.observed == true, NOTE_PROFILE, a?.currencyItemId ?: 0, catalog)
            !a!!.valid -> EmptyState("花笺集芳配置或状态异常", "已阻塞自动化；等待完整模板与时间状态同步后再显示任务详情。")
            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(Icons.Filled.CalendarMonth, "活动阶段", Format.activityPhase(a.phase), Format.activityPhaseDetail(a.phase, a.phaseEndMs, a.endMs), Modifier.weight(1f))
                    StatTile(Icons.Filled.EmojiEvents, "累计积分", Format.count(a.score), "完成任务 ${Format.count(a.finishCount)} 次", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(Icons.Filled.LocalFlorist, "花笺余额", Format.count(a.currencyBalance), if (a.currencyItemId > 0) catalog.itemName(a.currencyItemId) else "活动货币未识别", Modifier.weight(1f))
                    StatTile(Icons.Filled.Checklist, "任务槽", "${active.size}/${a.tasksCount}", if (readyTasks > 0) "$readyTasks 个奖励可领取" else if (a.taskListObserved) "已同步" else "等待进入活动同步", Modifier.weight(1f))
                }
                if (a.description.isNotBlank()) Text(a.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("任务详情 · ${a.tasksCount} 槽", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                if (a.tasksCount == 0) EmptyState(if (a.taskListObserved) "当前没有任务槽" else "任务列表尚未同步")
                a.tasksList.forEach { t ->
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("槽 ${t.slotId} · " + if (t.unlocked) t.title.ifBlank { "任务 #${t.taskId}" } else "未解锁（仅监控，不自动解锁付费槽位）", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            if (t.unlocked) statusBadge(t.status, t.received, !t.catalogKnown) else Badge("未解锁")
                        }
                        if (t.unlocked && t.target > 0) {
                            val progress = t.progress.coerceIn(0, t.target)
                            LinearProgressIndicator(progress = { progress.toFloat() / t.target }, modifier = Modifier.fillMaxWidth())
                            rewardLine("进度 ${progress}/${t.target} · 奖励", t.rewardList, catalog)
                        } else if (t.unlocked) rewardLine("奖励", t.rewardList, catalog)
                    }
                }
                Text("积分里程碑 · 积分 ${Format.count(a.score)}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                if (a.milestonesCount == 0) EmptyState("暂无里程碑配置")
                a.milestonesList.forEach { MilestoneRow(it, catalog) }
            }
        }
    }
}

@Composable
private fun CyclicStoryPanel(a: CyclicStoryView?, catalog: Catalog) {
    val isVisible = a != null && visible(a.found, a.phase)
    val active = if (isVisible) a!!.ordersList.filter { it.orderId > 0 && !it.onCooldown } else emptyList()
    val readyOrders = if (isVisible && a!!.valid) active.count { it.status == PlanStatus.PLAN_STATUS_READY } else 0
    val readyMilestones = if (isVisible && a!!.valid) a.milestonesList.count { it.ready && !it.received } else 0
    SectionCard(a?.name?.ifBlank { null } ?: STORY_PROFILE.name, actions = {
        Badge(if (isVisible) Format.activityPhase(a!!.phase) else if (a?.observed == true) "未开放" else "待同步", if (isVisible && a!!.phase == 2) BadgeTone.SECONDARY else BadgeTone.NEUTRAL)
        if (isVisible) Badge("批次 ${a!!.batchId}")
        if (isVisible && !a!!.valid) Badge("配置异常", BadgeTone.DANGER)
        if (readyOrders + readyMilestones > 0) Badge("可领取 ${readyOrders + readyMilestones}", BadgeTone.SECONDARY)
    }) {
        when {
            !isVisible -> InactiveOverview(a?.observed == true, STORY_PROFILE, a?.currencyItemId ?: 0, catalog)
            !a!!.valid -> EmptyState("莳花纪闻配置或状态异常", "已阻塞自动化；等待完整模板与时间状态同步后再显示订单详情。")
            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(Icons.Filled.CalendarMonth, "活动阶段", Format.activityPhase(a.phase), Format.activityPhaseDetail(a.phase, a.phaseEndMs, a.endMs), Modifier.weight(1f))
                    StatTile(Icons.Filled.EmojiEvents, "累计积分", Format.count(a.score), "完成订单 ${Format.count(a.finishCount)} 次", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(Icons.Filled.LocalFlorist, "花史残页", Format.count(a.currencyBalance), if (a.currencyItemId > 0) catalog.itemName(a.currencyItemId) else "活动货币未识别", Modifier.weight(1f))
                    StatTile(Icons.Filled.Checklist, "订单槽", "${active.size}/${a.ordersCount}", if (readyOrders > 0) "$readyOrders 个订单可交" else if (a.ordersObserved) "已同步" else "等待进入活动同步", Modifier.weight(1f))
                }
                if (a.description.isNotBlank()) Text(a.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("订单详情 · ${a.ordersCount} 槽", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                if (a.ordersCount == 0) EmptyState(if (a.ordersObserved) "当前没有订单槽" else "订单列表尚未同步")
                a.ordersList.forEach { o ->
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            val idle = o.onCooldown || o.orderId <= 0
                            Text("槽 ${o.orderIdx} · " + if (idle) (if (o.onCooldown) "冷却中（不自动付费刷新）" else "空闲") else if (o.flowerId > 0) "${catalog.itemName(o.flowerId)} x${Format.count(o.cost)}" else "订单 #${o.orderId}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            if (idle) Badge(if (o.onCooldown) "冷却中" else "空闲") else statusBadge(o.status, false, !o.catalogKnown)
                        }
                        if (!(o.onCooldown || o.orderId <= 0)) rewardLine("奖励", o.rewardList, catalog)
                    }
                }
                Text("积分里程碑 · 积分 ${Format.count(a.score)}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                if (a.milestonesCount == 0) EmptyState("当前没有里程碑")
                a.milestonesList.forEach { MilestoneRow(it, catalog) }
            }
        }
    }
}
