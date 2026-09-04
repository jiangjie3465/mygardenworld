package com.silkage.mygardenworld.feature.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mygardenworld.v1.FmlLandView
import com.mygardenworld.v1.FmlRaceTask
import com.mygardenworld.v1.FmlRaceView
import com.mygardenworld.v1.Policy
import com.silkage.mygardenworld.core.game.Catalog
import com.silkage.mygardenworld.core.protocol.WorkspaceUiState
import com.silkage.mygardenworld.core.ui.Badge
import com.silkage.mygardenworld.core.ui.BadgeTone
import com.silkage.mygardenworld.core.ui.CloudColors
import com.silkage.mygardenworld.core.ui.EmptyState
import com.silkage.mygardenworld.core.ui.Format
import com.silkage.mygardenworld.core.ui.SectionCard
import kotlinx.coroutines.delay

@Composable
fun UnionTab(workspace: WorkspaceUiState, policy: Policy?, catalog: Catalog, busyTaskId: Long, raceMessage: String, onTakeTask: (FmlRaceTask) -> Unit) {
    val union = workspace.state?.takeIf { it.hasUnion() }?.union
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            union == null || !union.membershipObserved -> item {
                SectionCard("公会", actions = { Badge("未确认成员资格", BadgeTone.WARNING) }) {
                    EmptyState("公会状态待同步", "确认会员状态前，服务端不会规划或执行任何公会操作。")
                }
            }
            !union.inUnion -> item {
                SectionCard("公会", actions = { Badge("未加入", BadgeTone.NEUTRAL) }) {
                    EmptyState("当前账号未加入公会", "公会土地、建设和竞赛模块均保持停用，加入公会并同步后才会运行。")
                }
            }
            else -> {
                item {
                    SectionCard("公会 #${union.unionId}" + if (union.memberPositionObserved && union.memberPositionLabel.isNotBlank()) " · ${union.memberPositionLabel}" else "") {
                        if (union.hasVideoBuild()) {
                            val v = union.videoBuild
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(v.label, style = MaterialTheme.typography.bodySmall)
                                    if (v.detail.isNotBlank()) Text(v.detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Badge("${v.used}/${v.limit}", BadgeTone.NEUTRAL)
                            }
                        } else {
                            Text("已确认成员资格", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item { FmlLandPanel(union.landsList, union.landsObserved, policy?.automationEnabled ?: false, catalog) }
                item {
                    RacePanel(
                        race = union.takeIf { it.hasRace() }?.race,
                        showTaken = policy?.union?.race?.enabled ?: true,
                        showScore = policy?.union?.race?.showPersonalScoreRank ?: false,
                        online = workspace.online,
                        busyTaskId = busyTaskId,
                        message = raceMessage,
                        onTake = onTakeTask,
                    )
                }
            }
        }
    }
}

@Composable
private fun FmlLandPanel(lands: List<FmlLandView>, observed: Boolean, automationEnabled: Boolean, catalog: Catalog) {
    val planted = lands.count { it.flowerId > 0 }
    val pending = lands.sumOf { it.pendingHarvest }
    SectionCard("公会土地", actions = {
        if (!observed) Badge("等待同步") else Badge("已观测 ${lands.size}", BadgeTone.SECONDARY)
        if (observed && planted > 0) Badge("种植中 $planted")
        if (pending > 0) Badge("可收 $pending", BadgeTone.SECONDARY)
    }) {
        when {
            !observed -> EmptyState("公会土地尚未同步", if (automationEnabled) "账号运行中时会自动拉取公会土地；稍等数秒后刷新即可。" else "请先启动账号自动化，守护进程会自动进入公会并同步土地种植信息。")
            lands.isEmpty() -> EmptyState("暂无公会土地", "当前账号还没有可观测的公会土地槽位。")
            else -> lands.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { land -> FmlLandTile(land, catalog, Modifier.weight(1f)) }
                    if (row.size == 1) Column(Modifier.weight(1f)) {}
                }
            }
        }
    }
}

@Composable
private fun FmlLandTile(land: FmlLandView, catalog: Catalog, modifier: Modifier) {
    val planted = land.flowerId > 0
    val scheme = MaterialTheme.colorScheme
    val (border, bg) = when (land.recommendation) {
        "harvest" -> scheme.primary.copy(alpha = 0.5f) to scheme.primary.copy(alpha = 0.08f)
        "plant" -> CloudColors.Amber.copy(alpha = 0.7f) to CloudColors.Amber.copy(alpha = 0.1f)
        else -> scheme.outlineVariant to scheme.surfaceContainer
    }
    val stock = when {
        planted && land.stockCap > 0 -> "成熟 ${land.pendingHarvest}/${land.stockCap}"
        planted -> "已收 ${land.harvestedCount}"
        else -> ""
    }
    Column(modifier.heightIn(min = 72.dp).background(bg, RoundedCornerShape(6.dp)).border(1.dp, border, RoundedCornerShape(6.dp)).padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("#${land.landId}", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
            Badge(Format.recommendation(land.recommendation), if (land.recommendation == "harvest") BadgeTone.SECONDARY else BadgeTone.NEUTRAL)
        }
        Text(if (planted) catalog.itemName(land.flowerId) + (if (land.flowerLvl > 0) " lv${land.flowerLvl}" else "") else "空地", style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(listOf(if (land.level > 0) "土地 ${land.level}级" else "", stock, if (land.pendingHarvest > 0) "待收 ${land.pendingHarvest}" else "").filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "-" }, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(Format.fmlLandTiming(land), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun RacePanel(race: FmlRaceView?, showTaken: Boolean, showScore: Boolean, online: Boolean, busyTaskId: Long, message: String, onTake: (FmlRaceTask) -> Unit) {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); nowMs = System.currentTimeMillis() } }
    var onlyReady by rememberSaveable { mutableStateOf(false) }
    var sortByScore by rememberSaveable { mutableStateOf(true) }
    val tasks = race?.tasksList.orEmpty()
    val taken = race?.takeIf { it.hasTaken() }?.taken
    val observed = race?.observed ?: false
    val batchActive = race?.batchActive ?: false
    val canTake = taken?.hasTask != true
    val readyCount = if (canTake) tasks.count { Format.raceTaskReady(it, nowMs) } else 0
    SectionCard("公会竞赛", actions = {
        when {
            !observed -> Badge("等待同步")
            !batchActive -> Badge("非竞赛期间")
            else -> Badge("竞赛进行中", BadgeTone.SECONDARY)
        }
        if (race?.taskQuotaObserved == true) Badge("已做 ${race.finishedTaskNum}" + if (race.totalTaskNum > 0) "/${race.totalTaskNum}" else "")
        if (showScore && race?.scoreObserved == true) Badge("得分 ${race.score}")
        if (showScore && race?.rankObserved == true && race.rank > 0) Badge("第 ${race.rank} 名")
    }) {
        when {
            !observed -> EmptyState("竞赛状态尚未同步", "连接游戏并进入公会界面后，竞赛任务列表会自动同步。")
            !batchActive -> EmptyState("当前不在竞赛批次中", if (race != null && race.batchStartMs > 0 && race.batchEndMs > 0) "竞赛按批次开放。当前批次：${Format.dayClock(race.batchStartMs)} ~ ${Format.dayClock(race.batchEndMs)}" else "竞赛按批次开放，非竞赛期间任务池不可用。")
            else -> {
                if (showTaken) {
                    if (taken != null && taken.hasTask) {
                        val progress = if (taken.targetCnt > 0) (taken.finishCnt * 100 / taken.targetCnt).coerceIn(0, 100) else 0
                        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(8.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("当前已接：" + taken.taskLabel.ifBlank { "任务 #${taken.taskId}" } + if (taken.targetLabel.isNotBlank()) " · ${taken.targetLabel}" else "", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                Badge("$progress%", if (progress >= 100) BadgeTone.SECONDARY else BadgeTone.NEUTRAL)
                            }
                            LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                            val remain = taken.expireTimeMs - nowMs
                            Text("进度 ${taken.finishCnt} / ${taken.targetCnt} · 分数 ${taken.score}" + when {
                                taken.expireTimeMs <= 0 -> " · 过期时间待同步"
                                progress >= 100 -> " · 已完成，待提交"
                                remain <= 0 -> " · 已过期"
                                else -> " · 剩余 ${Format.remaining(taken.expireTimeMs)}"
                            }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Text("当前未接取任务", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = !onlyReady, onClick = { onlyReady = false }, label = { Text("全部 ${tasks.size}") })
                    FilterChip(selected = onlyReady, onClick = { onlyReady = true }, label = { Text("可抢 $readyCount") })
                    FilterChip(selected = sortByScore, onClick = { sortByScore = !sortByScore }, label = { Text(if (sortByScore) "分数 ↓" else "池顺序") })
                }
                if (race != null && race.tasksSyncedAtMs > 0) Text("任务池更新于 ${Format.clock(race.tasksSyncedAtMs)} · 约每 30 秒校准", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                val visible = tasks.withIndex()
                    .filter { !onlyReady || (canTake && Format.raceTaskReady(it.value, nowMs)) }
                    .let { list -> if (sortByScore) list.sortedWith(compareByDescending<IndexedValue<FmlRaceTask>> { it.value.score }.thenBy { it.index }) else list }
                if (tasks.isEmpty()) EmptyState("任务池为空", "竞赛任务已接完或尚未刷新。")
                else if (visible.isEmpty()) EmptyState("当前没有可抢任务", "冷却结束或任务池刷新后会自动出现。")
                visible.forEach { (index, task) ->
                    val takeable = Format.raceTaskReady(task, nowMs)
                    val availability = if (takeable && !canTake) "需先完成当前任务" else Format.raceTaskAvailability(task, nowMs)
                    Column(
                        Modifier.fillMaxWidth()
                            .border(1.dp, if (takeable && canTake) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("${index + 1}. " + task.taskLabel.ifBlank { "任务 #${task.taskId}" } + if (task.targetLabel.isNotBlank()) " · ${task.targetLabel}" else "", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Badge(if (task.isUpgrade) "已升级" else "普通", if (task.isUpgrade) BadgeTone.SECONDARY else BadgeTone.NEUTRAL)
                        }
                        Text(listOfNotNull("${task.score} 分", Format.raceTaskProgress(task), if (task.upgradeUid > 0) "升级人 #${task.upgradeUid}" else null).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(availability, style = MaterialTheme.typography.labelSmall, color = if (takeable && canTake) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            if (takeable && canTake) Button(onClick = { onTake(task) }, enabled = online && busyTaskId == 0L, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)) { Text(if (busyTaskId == task.msId) "接取中" else "手动抢") }
                        }
                    }
                }
            }
        }
    }
}
