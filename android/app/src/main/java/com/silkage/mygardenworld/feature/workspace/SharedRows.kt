package com.silkage.mygardenworld.feature.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mygardenworld.v1.PendingTaskView
import com.mygardenworld.v1.PlanStatus
import com.silkage.mygardenworld.core.ui.Badge
import com.silkage.mygardenworld.core.ui.BadgeTone
import com.silkage.mygardenworld.core.ui.Format

fun pendingTaskCategoryLabel(category: String): String = if (category == "activity") "活动" else Format.categoryLabel(category)

@Composable
fun PendingTaskRow(task: PendingTaskView) {
    val cooling = task.cooldownUntilMs > System.currentTimeMillis()
    val shortage = task.requirementsList.any { it.missing > 0 }
    val (label, tone) = when {
        cooling -> "冷却" to BadgeTone.SECONDARY
        task.status == PlanStatus.PLAN_STATUS_BLOCKED || task.requirementsList.any { it.blockedReasonsCount > 0 } -> "阻塞" to BadgeTone.DANGER
        !task.autoCompletionSupported -> "暂不支持" to BadgeTone.NEUTRAL
        shortage -> "缺项" to BadgeTone.WARNING
        task.target > 0 && task.finished >= task.target -> "可领取" to BadgeTone.PRIMARY
        else -> Format.planStatus(task.status) to BadgeTone.NEUTRAL
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Badge(label, tone)
        Column(Modifier.weight(1f)) {
            Text(task.title.ifBlank { task.id }, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val progress = if (task.target > 0) "${task.finished}/${task.target}" else ""
            val missing = task.requirementsList.filter { it.missing > 0 }.joinToString("、") { "${it.itemName.ifBlank { "#${it.itemId}" }} 缺 ${it.missing}" }
            val cooldown = if (cooling) "${task.cooldownReason.ifBlank { "冷却中" }}，${Format.remaining(task.cooldownUntilMs)}后重试" else ""
            val detail = listOf(pendingTaskCategoryLabel(task.category), progress, missing, cooldown).filter { it.isNotBlank() }.joinToString(" · ")
            Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
