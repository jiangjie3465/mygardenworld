package com.silkage.mygardenworld.core.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mygardenworld.v1.FeatureCapability
import com.mygardenworld.v1.PlanStatus

val QUALITY_OPTIONS = listOf(1, 2, 3, 4, 5)
val QUALITY_LABELS = mapOf(1 to "凡", 2 to "普", 3 to "珍", 4 to "华", 5 to "仙")

/** Web `settingStatusForCapability`: null means the capability executes normally. */
data class SettingStatus(val label: String, val detail: String, val syncOnly: Boolean)

fun settingStatus(capabilities: List<FeatureCapability>, featureId: String): SettingStatus? {
    val capability = capabilities.firstOrNull { it.id == featureId }
        ?: return SettingStatus("未知", "后端未声明该能力，界面不会把它视为可执行。", syncOnly = false)
    val detail = capability.blockedReasonsList.joinToString("；")
    return when (capability.status) {
        PlanStatus.PLAN_STATUS_SYNC_ONLY -> SettingStatus("同步", detail.ifBlank { "当前只做状态或需求展示，不会自动执行。" }, syncOnly = true)
        PlanStatus.PLAN_STATUS_ADAPTER_MISSING -> SettingStatus("阻塞", detail.ifBlank { "执行协议、状态或成本门槛尚不完整，暂不自动执行。" }, syncOnly = false)
        PlanStatus.PLAN_STATUS_BLOCKED -> SettingStatus("阻塞", detail.ifBlank { "该能力当前被后端安全门禁阻塞。" }, syncOnly = false)
        else -> null
    }
}

/** Switch row with an optional capability badge whose detail is shown under the hint. */
@Composable
fun StatusSwitchRow(label: String, checked: Boolean, enabled: Boolean = true, hint: String? = null, status: SettingStatus? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false))
                if (status != null) Badge(status.label, if (status.syncOnly) BadgeTone.NEUTRAL else BadgeTone.DANGER)
            }
            val detail = listOfNotNull(hint?.takeIf { it.isNotBlank() }, status?.detail).joinToString("\n")
            if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/** Number input that only reports values within [min]..[max]. */
@Composable
fun BoundedNumberRow(label: String, value: Long, enabled: Boolean = true, hint: String? = null, min: Long = 0, max: Long = Long.MAX_VALUE, onChange: (Long) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    val parsed = text.toLongOrNull()
    val invalid = parsed == null || parsed < min || parsed > max
    SettingRow(label, if (invalid && enabled) listOfNotNull(hint, rangeHint(min, max)).joinToString("\n") else hint) {
        OutlinedTextField(
            value = text,
            onValueChange = { raw ->
                val filtered = raw.filter { it.isDigit() || (it == '-' && min < 0) }.take(19)
                text = filtered
                filtered.toLongOrNull()?.takeIf { it in min..max }?.let(onChange)
            },
            modifier = Modifier.width(112.dp),
            singleLine = true,
            enabled = enabled,
            isError = invalid && enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun rangeHint(min: Long, max: Long): String = if (max == Long.MAX_VALUE) "请输入不小于 $min 的整数" else "请输入 $min–$max 的整数"

@Composable
fun TextRow(label: String, value: String, enabled: Boolean = true, hint: String? = null, onChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true, enabled = enabled)
        if (!hint.isNullOrBlank()) Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun StatusRow(label: String, value: String, tone: BadgeTone) {
    SettingRow(label) { Badge(value, tone) }
}

/** Single-choice chips; mirrors the Web SegmentedRow. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> SegmentedRow(label: String, value: T, options: List<Pair<T, String>>, enabled: Boolean = true, hint: String? = null, onChange: (T) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        if (!hint.isNullOrBlank()) Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (option, text) -> FilterChip(selected = option == value, onClick = { onChange(option) }, label = { Text(text) }, enabled = enabled) }
        }
    }
}

/**
 * Quality multi-select. With [emptyMeansAll], an empty list is shown as every
 * quality selected and selecting all collapses back to the empty list.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QualityRow(label: String, value: List<Int>, enabled: Boolean = true, emptyMeansAll: Boolean = false, hint: String? = null, onChange: (List<Int>) -> Unit) {
    val selected = if (emptyMeansAll && value.isEmpty()) QUALITY_OPTIONS.toSet() else value.toSet()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        if (!hint.isNullOrBlank()) Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            QUALITY_OPTIONS.forEach { quality ->
                FilterChip(
                    selected = quality in selected,
                    enabled = enabled,
                    onClick = {
                        val current = if (emptyMeansAll && value.isEmpty()) QUALITY_OPTIONS else value
                        val next = toggle(current, quality)
                        onChange(if (emptyMeansAll && next.size == QUALITY_OPTIONS.size) emptyList() else next)
                    },
                    label = { Text(QUALITY_LABELS[quality] ?: quality.toString()) },
                )
            }
        }
    }
}

fun toggle(values: List<Int>, value: Int): List<Int> = if (value in values) values.filter { it != value } else values + value

/** One selectable entry in [PickerRow]. */
data class PickerOption(
    val id: Int,
    val name: String,
    val detail: String = "",
    val quality: Int = 0,
    val stock: Int = 0,
    val matureSeconds: Int = 0,
    val available: Boolean = true,
)

enum class PickerSort(val label: String) { MATURE_ASC("成熟从短到长"), MATURE_DESC("成熟从长到短"), STOCK_ASC("库存从低到高"), STOCK_DESC("库存从高到低") }

/**
 * Multi-select summary row plus a searchable dialog, covering the Web flower,
 * catalog-flower and flower-art pickers.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PickerRow(
    label: String,
    value: List<Int>,
    options: List<PickerOption>,
    enabled: Boolean,
    emptySummary: String,
    countBadge: String,
    nameOf: (Int) -> String,
    hint: String? = null,
    sorts: List<PickerSort> = listOf(PickerSort.STOCK_ASC, PickerSort.STOCK_DESC),
    qualityFilter: Boolean = false,
    synced: Boolean = true,
    onChange: (List<Int>) -> Unit,
) {
    var open by rememberSaveable(label) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Badge(countBadge)
            Badge(if (value.isEmpty()) "未选择" else "${value.size} 种", if (value.isEmpty()) BadgeTone.NEUTRAL else BadgeTone.SECONDARY)
        }
        if (!hint.isNullOrBlank()) Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val preview = value.take(4).joinToString("、") { nameOf(it) } + if (value.size > 4) " 等 ${value.size - 4} 种" else ""
            Text(if (value.isEmpty()) emptySummary else preview, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { open = true }, enabled = enabled) { Text("选择") }
        }
        if (!synced) Text("状态尚未同步，仅显示已选择的项目", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (!open) return

    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(sorts.first()) }
    var qualities by remember { mutableStateOf(emptySet<Int>()) }
    var draft by remember(value) { mutableStateOf(value) }
    val known = options.map { it.id }.toSet()
    val all = options + draft.filter { it !in known }.map { PickerOption(it, nameOf(it), available = false) }
    val text = query.trim().lowercase()
    val visible = all
        .filter { qualities.isEmpty() || it.quality in qualities }
        .filter { text.isEmpty() || it.id.toString().contains(text) || it.name.lowercase().contains(text) || it.detail.lowercase().contains(text) || (QUALITY_LABELS[it.quality] ?: "") == text }
        .sortedWith(
            when (sort) {
                PickerSort.MATURE_ASC -> compareBy<PickerOption> { if (it.matureSeconds > 0) it.matureSeconds else Int.MAX_VALUE }.thenBy { it.id }
                PickerSort.MATURE_DESC -> compareByDescending<PickerOption> { it.matureSeconds }.thenBy { it.id }
                PickerSort.STOCK_ASC -> compareBy<PickerOption> { it.stock }.thenBy { it.id }
                PickerSort.STOCK_DESC -> compareByDescending<PickerOption> { it.stock }.thenBy { it.id }
            },
        )
    AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("搜索名称或 ID") }, singleLine = true)
                if (qualityFilter) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        QUALITY_OPTIONS.forEach { q ->
                            FilterChip(selected = q in qualities, onClick = { qualities = if (q in qualities) qualities - q else qualities + q }, label = { Text(QUALITY_LABELS[q] ?: "") })
                        }
                    }
                }
                if (sorts.size > 1) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        sorts.forEach { s -> FilterChip(selected = sort == s, onClick = { sort = s }, label = { Text(s.label) }) }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("已选 ${draft.size} · 显示 ${visible.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    TextButton(onClick = { draft = (draft + visible.map { it.id }).distinct() }, enabled = visible.isNotEmpty()) { Text("全选") }
                    TextButton(onClick = { draft = emptyList() }, enabled = draft.isNotEmpty()) { Text("清空") }
                }
                if (visible.isEmpty()) EmptyState(if (all.isEmpty()) "暂无可选项目" else "没有匹配的项目")
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    items(visible, key = { it.id }) { option ->
                        val checked = option.id in draft
                        Row(
                            Modifier.fillMaxWidth().clickable { draft = toggle(draft, option.id) }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = { draft = toggle(draft, option.id) })
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(option.name, style = MaterialTheme.typography.bodyMedium, fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                    QUALITY_LABELS[option.quality]?.let { Badge(it) }
                                    if (!option.available) Badge("不可用", BadgeTone.WARNING)
                                }
                                val meta = listOf("#${option.id}", option.detail, "库存 ${option.stock}").filter { it.isNotBlank() }.joinToString(" · ")
                                Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onChange(draft); open = false }) { Text("确定") } },
        dismissButton = { TextButton(onClick = { open = false }) { Text("取消") } },
    )
}

/** Stepper for small integer counts. */
@Composable
fun Stepper(value: Int, enabled: Boolean, min: Int = 0, max: Int? = null, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onChange(value - 1) }, enabled = enabled && value > min) { Text("−") }
        Text(value.toString(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(32.dp), maxLines = 1)
        TextButton(onClick = { onChange(value + 1) }, enabled = enabled && (max == null || value < max)) { Text("+") }
    }
}

/** Ordered list reorderable with up/down buttons; the first entry has the highest priority. */
@Composable
fun <T> ReorderList(items: List<T>, label: (T) -> String, enabled: Boolean, onReorder: (List<T>) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEachIndexed { index, item ->
            Row(
                Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)).padding(start = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${index + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(24.dp))
                Text(label(item), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { onReorder(move(items, index, index - 1)) }, enabled = enabled && index > 0) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "上移 ${label(item)}") }
                IconButton(onClick = { onReorder(move(items, index, index + 1)) }, enabled = enabled && index < items.size - 1) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "下移 ${label(item)}") }
            }
        }
    }
}

private fun <T> move(items: List<T>, from: Int, to: Int): List<T> = items.toMutableList().apply { add(to, removeAt(from)) }
