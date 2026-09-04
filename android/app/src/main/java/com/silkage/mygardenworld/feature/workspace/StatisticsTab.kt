package com.silkage.mygardenworld.feature.workspace

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mygardenworld.v1.DailyBusinessStatisticsView
import com.mygardenworld.v1.RuntimeActionTotal
import com.mygardenworld.v1.RuntimeResourceTotal
import com.mygardenworld.v1.RuntimeStatisticsView
import com.silkage.mygardenworld.core.protocol.WorkspaceUiState
import com.silkage.mygardenworld.core.ui.Badge
import com.silkage.mygardenworld.core.ui.BadgeTone
import com.silkage.mygardenworld.core.ui.EmptyState
import com.silkage.mygardenworld.core.ui.Format
import com.silkage.mygardenworld.core.ui.SectionCard
import com.silkage.mygardenworld.core.ui.StatTile

@Composable
fun StatisticsTab(workspace: WorkspaceUiState) {
    val stats = workspace.state?.takeIf { it.hasStatistics() }?.statistics
    val runtime = stats?.takeIf { it.hasRuntimeStatistics() }?.runtimeStatistics ?: workspace.selectedStatus?.takeIf { it.hasRuntimeStatistics() }?.runtimeStatistics
    val business = stats?.takeIf { it.hasBusinessStatistics() }?.businessStatistics
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { RuntimePanel(runtime) }
        item { BusinessPanel(business?.takeIf { it.observed }?.takeIf { it.hasToday() }?.today, business?.daysList.orEmpty(), business?.observed == true) }
    }
}

private fun sum(items: List<RuntimeActionTotal>) = items.sumOf { it.count }
private fun actionSummary(items: List<RuntimeActionTotal>) = items.filter { it.count > 0 }.take(3).joinToString("、") { "${it.label.ifBlank { it.key }} ${Format.count(it.count)}" }.ifBlank { "暂无完成" }
private fun resourceSummary(items: List<RuntimeResourceTotal>) = items.filter { it.gained > 0 }.take(3).joinToString("、") { "${it.label.ifBlank { it.key }} +${Format.count(it.gained)}" }.ifBlank { "暂无资源进账" }

@Composable
private fun RuntimePanel(r: RuntimeStatisticsView?) {
    SectionCard("本次运行统计", actions = {
        Badge(if (r == null) "暂无" else if (r.running) "运行中" else "已停止", if (r?.running == true) BadgeTone.SECONDARY else BadgeTone.NEUTRAL)
        if (r != null && r.totalOperations > 0) Badge("操作 ${Format.count(r.totalOperations)}")
    }) {
        val window = when {
            r == null -> "暂无运行统计"
            r.running -> Format.timestamp(r.startedAt).let { if (it == "-") "运行中" else "启动 $it" }
            else -> Format.timestamp(r.stoppedAt).let { if (it == "-") "最近已停止" else "停止 $it" }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(Icons.Filled.CalendarMonth, "本次运行", if (r == null) "-" else if (r.running) "运行中" else "已停止", window, Modifier.weight(1f))
            StatTile(Icons.Filled.AutoAwesome, "本次获取", r?.resourceGainsList?.firstOrNull { it.gained > 0 }?.let { "+${Format.count(it.gained)}" } ?: "-", resourceSummary(r?.resourceGainsList.orEmpty()), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(Icons.Filled.ShoppingBag, "本次订单", if (r == null) "-" else Format.count(sum(r.orderCompletionsList)), actionSummary(r?.orderCompletionsList.orEmpty()), Modifier.weight(1f))
            StatTile(Icons.Filled.Checklist, "本次任务", if (r == null) "-" else Format.count(sum(r.taskCompletionsList)), actionSummary(r?.taskCompletionsList.orEmpty()), Modifier.weight(1f))
        }
        listOf("订单完成" to r?.orderCompletionsList.orEmpty(), "任务完成" to r?.taskCompletionsList.orEmpty(), "操作完成" to r?.operationCompletionsList.orEmpty()).forEach { (title, items) ->
            if (items.isNotEmpty()) {
                Text("$title · ${Format.count(sum(items))}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Text(items.joinToString("  ·  ") { "${it.label.ifBlank { it.key }} ${Format.count(it.count)}" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private val DAY_COLUMNS: List<Pair<String, (DailyBusinessStatisticsView) -> Int>> = listOf(
    "金币" to { it.gold }, "经验" to { it.experience }, "元宝" to { it.diamonds }, "收获" to { it.flowerHarvestNum }, "花艺" to { it.flowerArtSold },
    "居民" to { it.residentNormalFinished }, "顾客" to { it.customerFinished }, "宫廷" to { it.palaceFinished }, "绸缎" to { it.residentSatinFinished },
    "建材" to { it.residentDecorateFinished }, "加速券" to { it.speedUpCard }, "花币" to { it.flowerShopCoin }, "绸缎库存" to { it.satin }, "木材" to { it.wood },
)

@Composable
private fun BusinessPanel(today: DailyBusinessStatisticsView?, days: List<DailyBusinessStatisticsView>, observed: Boolean) {
    SectionCard("营业统计", actions = {
        if (!observed) Badge("未同步") else {
            today?.takeIf { it.dayId > 0 }?.let { Badge(Format.dayId(it.dayId), BadgeTone.SECONDARY) }
            today?.takeIf { it.updatedAtMs > 0 }?.let { Badge("更新 ${Format.clock(it.updatedAtMs)}") }
        }
    }) {
        if (!observed || today == null) {
            EmptyState("暂无营业统计", "登录后同步今日收益、收获和订单完成数")
        } else {
            val orderTotal = today.residentNormalFinished + today.customerFinished + today.palaceFinished + today.residentSatinFinished + today.residentDecorateFinished
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(Icons.Filled.MonetizationOn, "金币", Format.count(today.gold), "今日获得", Modifier.weight(1f))
                StatTile(Icons.Filled.TrendingUp, "经验", Format.count(today.experience), "今日获得", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(Icons.Filled.Diamond, "元宝", Format.count(today.diamonds), "今日获得", Modifier.weight(1f))
                StatTile(Icons.Filled.LocalFlorist, "收获鲜花", Format.count(today.flowerHarvestNum), "今日收获", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(Icons.Filled.AutoAwesome, "花艺售出", Format.count(today.flowerArtSold), null, Modifier.weight(1f))
                StatTile(Icons.Filled.Checklist, "完成订单", Format.count(orderTotal), "居民/顾客/宫廷/绸缎/建材", Modifier.weight(1f))
            }
            if (days.isNotEmpty()) {
                Text("历史明细", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    Row {
                        Text("日期", Modifier.width(88.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        DAY_COLUMNS.forEach { (label, _) -> Text(label, Modifier.width(72.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End) }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    days.forEach { day ->
                        Row(Modifier.padding(vertical = 4.dp)) {
                            Text(Format.dayId(day.dayId), Modifier.width(88.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                            DAY_COLUMNS.forEach { (_, get) -> Text(Format.count(get(day)), Modifier.width(72.dp), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End) }
                        }
                    }
                }
            }
        }
    }
}
