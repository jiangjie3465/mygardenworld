package com.silkage.mygardenworld.feature.workspace

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mygardenworld.v1.Event
import com.mygardenworld.v1.WorkspaceLogCategory
import com.silkage.mygardenworld.core.protocol.WorkspaceUiState
import com.silkage.mygardenworld.core.ui.CloudColors
import com.silkage.mygardenworld.core.ui.EmptyState
import com.silkage.mygardenworld.core.ui.Format

private val CATEGORY_TABS = listOf(
    WorkspaceLogCategory.WORKSPACE_LOG_CATEGORY_ACCOUNT,
    WorkspaceLogCategory.WORKSPACE_LOG_CATEGORY_BASIC,
    WorkspaceLogCategory.WORKSPACE_LOG_CATEGORY_GARDEN,
    WorkspaceLogCategory.WORKSPACE_LOG_CATEGORY_ORDERS,
    WorkspaceLogCategory.WORKSPACE_LOG_CATEGORY_UNION,
    WorkspaceLogCategory.WORKSPACE_LOG_CATEGORY_ACTIVITIES,
    WorkspaceLogCategory.WORKSPACE_LOG_CATEGORY_WAREHOUSE,
    WorkspaceLogCategory.WORKSPACE_LOG_CATEGORY_SYSTEM,
)

enum class LogView(val label: String) { KEY("关键操作"), WARNING("警告与错误"), STATE("状态变化"), ALL("全部明细") }

private val STATE_KINDS = setOf("land_changed", "resource_changed", "inventory_changed", "race_task_sync")

fun eventMatchesView(event: Event, view: LogView): Boolean = when (view) {
    LogView.ALL -> true
    LogView.WARNING -> event.level == "warn" || event.level == "error"
    LogView.STATE -> event.kind in STATE_KINDS
    LogView.KEY -> event.kind !in STATE_KINDS && event.kind != "operation_planned"
}

@Composable
fun LogsTab(workspace: WorkspaceUiState, onLoadMore: () -> Unit) {
    var category by rememberSaveable { mutableStateOf(-1) }
    var view by rememberSaveable { mutableStateOf(LogView.KEY) }
    val events = workspace.logs.events
    val visible = remember(events, category, view) {
        events.filter { (category < 0 || it.categoryValue == category) && eventMatchesView(it, view) }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = category < 0, onClick = { category = -1 }, label = { Text("全部 ${events.size}") })
            CATEGORY_TABS.forEach { tab ->
                val count = events.count { it.category == tab }
                FilterChip(selected = category == tab.number, onClick = { category = tab.number }, label = { Text("${Format.logCategory(tab)} $count") })
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LogView.entries.forEach { entry -> FilterChip(selected = view == entry, onClick = { view = entry }, label = { Text(entry.label) }) }
        }
        if (workspace.logs.gapDetected) Text("日志存在缺口，已尽力追赶。", style = MaterialTheme.typography.labelSmall, color = CloudColors.Amber, modifier = Modifier.padding(horizontal = 16.dp))
        if (visible.isEmpty()) {
            EmptyState("当前筛选下暂无日志", "分类始终保留；有事件后会自动出现在这里。", Modifier.padding(16.dp))
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                itemsIndexed(visible, key = { index, event -> if (event.id > 0) event.id else -(index + 1L) }) { _, event ->
                    LogRow(event)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                if (workspace.logs.hasMoreBefore) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
                            OutlinedButton(onClick = onLoadMore, enabled = !workspace.logs.loadingOlder && workspace.online) { Text(if (workspace.logs.loadingOlder) "加载中" else "加载更早日志") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogRow(event: Event) {
    val levelColor = when (event.level) {
        "error" -> MaterialTheme.colorScheme.error
        "warn" -> CloudColors.Amber
        else -> MaterialTheme.colorScheme.primary
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(Format.timestamp(event.ts), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Format.logCategory(event.category), style = MaterialTheme.typography.labelSmall, color = levelColor, fontWeight = FontWeight.Medium)
            Text(Format.eventTitle(event), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
        val message = Format.eventMessage(event)
        if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}
