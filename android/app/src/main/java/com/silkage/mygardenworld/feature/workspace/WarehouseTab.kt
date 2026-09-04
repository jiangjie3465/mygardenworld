package com.silkage.mygardenworld.feature.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mygardenworld.v1.InventoryLedgerItem
import com.silkage.mygardenworld.core.game.Catalog
import com.silkage.mygardenworld.core.protocol.WorkspaceUiState
import com.silkage.mygardenworld.core.ui.Badge
import com.silkage.mygardenworld.core.ui.BadgeTone
import com.silkage.mygardenworld.core.ui.EmptyState
import com.silkage.mygardenworld.core.ui.Format

private enum class WarehouseCategory(val label: String) { FLOWER("鲜花"), ART("花艺"), ITEM("道具") }

private fun categoryOf(item: InventoryLedgerItem): WarehouseCategory = when (item.itemId) {
    in 23000..23999 -> WarehouseCategory.FLOWER
    in 300000..399999 -> WarehouseCategory.ART
    else -> WarehouseCategory.ITEM
}

@Composable
fun WarehouseTab(workspace: WorkspaceUiState, catalog: Catalog) {
    val warehouse = workspace.state?.takeIf { it.hasWarehouse() }?.warehouse
    val items = remember(warehouse) {
        warehouse?.inventoryLedger?.itemsList.orEmpty()
            .filter { it.owned > 0 || it.allocated > 0 }
            .sortedWith(compareByDescending<InventoryLedgerItem> { it.owned }.thenByDescending { it.allocated }.thenBy { it.itemId })
    }
    var category by rememberSaveable { mutableStateOf(WarehouseCategory.FLOWER) }
    var query by rememberSaveable { mutableStateOf("") }
    val counts = remember(items) { items.groupingBy { categoryOf(it) }.eachCount() }
    val visible = remember(items, category, query) {
        val q = query.trim().lowercase()
        items.filter { categoryOf(it) == category }.filter { q.isEmpty() || it.itemName.ifBlank { catalog.itemName(it.itemId) }.lowercase().contains(q) || it.itemId.toString().contains(q) }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            WarehouseCategory.entries.forEach { c -> FilterChip(selected = category == c, onClick = { category = c; query = "" }, label = { Text("${c.label} ${counts[c] ?: 0}") }) }
            Badge("种类 ${items.size}", BadgeTone.SECONDARY)
        }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(vertical = 6.dp), singleLine = true, placeholder = { Text("搜索${category.label}或 ID") })
        when {
            items.isEmpty() -> EmptyState("暂无仓库数据", modifier = Modifier.padding(top = 12.dp))
            visible.isEmpty() -> EmptyState(if (query.isBlank()) "暂无${category.label}" else "没有匹配${category.label}", "换个名称或 ID 再试试", Modifier.padding(top = 12.dp))
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                item {
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text("名称", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        listOf("数量", "预留", "可用").forEach { Text(it, Modifier.width(64.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End) }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                items(visible, key = { it.itemId }) { item ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.itemName.ifBlank { catalog.itemName(item.itemId) }, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(item.itemId.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(Format.count(item.owned), Modifier.width(64.dp), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                        Text(Format.count(item.allocated), Modifier.width(64.dp), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End, color = if (item.allocated > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        Text(Format.count(item.available), Modifier.width(64.dp), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End, color = if (item.available < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}
