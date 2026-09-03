package com.silkage.mygardenworld.feature.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mygardenworld.v1.PlanStatus
import com.silkage.mygardenworld.core.game.Catalog
import com.silkage.mygardenworld.core.protocol.WorkspaceUiState
import com.silkage.mygardenworld.core.ui.Badge
import com.silkage.mygardenworld.core.ui.BadgeTone
import com.silkage.mygardenworld.core.ui.EmptyState
import com.silkage.mygardenworld.core.ui.Format
import com.silkage.mygardenworld.core.ui.SectionCard

@Composable
fun OrdersTab(workspace: WorkspaceUiState, catalog: Catalog) {
    val orders = workspace.state?.takeIf { it.hasOrders() }?.orders
    val tasks = orders?.pendingTasksList.orEmpty()
    val demands = orders?.demandsList.orEmpty().filter { it.missing > 0 }
    val stats = orders?.takeIf { it.hasOrderStatistics() }?.orderStatistics
    val today = orders?.takeIf { it.hasBusinessStatistics() }?.businessStatistics?.takeIf { it.observed && it.hasToday() }?.today
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionCard("今日进度", actions = { stats?.let { if (it.observed) Badge(Format.dayId(it.dayId)) else Badge("未同步") } }) {
                if (stats == null || !stats.observed) EmptyState("暂无订单统计")
                else {
                    listOf(
                        "普通居民订单" to stats.residentNormalFinished,
                        "绸缎订单" to stats.residentSatinFinished,
                        "建材订单" to stats.residentDecorateFinished,
                        "宫廷订单" to stats.palaceFinished,
                        "顾客订单" to stats.customerFinished,
                        "花艺售卖" to stats.flowerArtSold,
                    ).forEach { (label, value) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(label, style = MaterialTheme.typography.bodySmall)
                            Text(Format.count(value), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (today != null) {
                        Text("今日收益 · 金币 ${Format.count(today.gold)} · 经验 ${Format.count(today.experience)} · 收花 ${Format.count(today.flowerHarvestNum)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            SectionCard("任务/订单监控", actions = { Badge(tasks.size.toString(), BadgeTone.SECONDARY) }) {
                if (tasks.isEmpty()) EmptyState("暂无订单待监控")
                else tasks.sortedWith(compareBy({ it.status != PlanStatus.PLAN_STATUS_READY }, { it.category }, { it.title })).forEach { PendingTaskRow(it) }
            }
        }
        item {
            SectionCard("资源缺口", defaultOpen = demands.isNotEmpty(), actions = { Badge(demands.size.toString(), if (demands.isEmpty()) BadgeTone.NEUTRAL else BadgeTone.WARNING) }) {
                if (demands.isEmpty()) EmptyState("暂无资源缺口")
                else demands.sortedByDescending { it.priority }.forEach { demand ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(demand.itemName.ifBlank { catalog.itemName(demand.itemId) }, style = MaterialTheme.typography.bodySmall)
                            Text(listOf(demand.label, demand.source, demand.blockedReasonsList.joinToString("、")).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Badge("缺 ${demand.missing} · 有 ${demand.owned}", BadgeTone.WARNING)
                    }
                }
            }
        }
        item {
            val arts = orders?.flowerArtAvailabilityList.orEmpty()
            SectionCard("花艺", defaultOpen = false, actions = { Badge("${arts.count { it.craftable }}/${arts.size} 可制作", BadgeTone.SECONDARY) }) {
                if (arts.isEmpty()) EmptyState("暂无花艺快照")
                else arts.sortedWith(compareBy({ !it.craftable }, { -it.saleValue })).forEach { art ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(art.artName.ifBlank { catalog.itemName(art.artId) }, style = MaterialTheme.typography.bodySmall)
                            val missing = art.requirementsList.filter { it.missing > 0 }.joinToString("、") { "${it.itemName.ifBlank { catalog.itemName(it.itemId) }} 缺 ${it.missing}" }
                            Text(listOf("售价 ${art.saleValue}", if (!art.vaseUnlocked) "花瓶未解锁" else "", if (!art.levelOk) "等级不足" else "", missing).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Badge(if (art.craftable) "可制作" else "缺项", if (art.craftable) BadgeTone.PRIMARY else BadgeTone.NEUTRAL)
                    }
                }
            }
        }
    }
}
