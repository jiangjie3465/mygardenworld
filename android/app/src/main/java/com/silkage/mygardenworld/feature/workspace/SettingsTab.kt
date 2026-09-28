package com.silkage.mygardenworld.feature.workspace

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mygardenworld.v1.FeatureCapability
import com.mygardenworld.v1.FriendTouchFriendView
import com.mygardenworld.v1.Policy
import com.mygardenworld.v1.RedeemConnectMode
import com.mygardenworld.v1.SelectionMode
import com.silkage.mygardenworld.core.game.Catalog
import com.silkage.mygardenworld.core.protocol.WorkspaceUiState
import com.silkage.mygardenworld.core.ui.Badge
import com.silkage.mygardenworld.core.ui.BadgeTone
import com.silkage.mygardenworld.core.ui.BoundedNumberRow
import com.silkage.mygardenworld.core.ui.EmptyState
import com.silkage.mygardenworld.core.ui.Format
import com.silkage.mygardenworld.core.ui.LoadingBox
import com.silkage.mygardenworld.core.ui.PickerOption
import com.silkage.mygardenworld.core.ui.PickerRow
import com.silkage.mygardenworld.core.ui.PickerSort
import com.silkage.mygardenworld.core.ui.QualityRow
import com.silkage.mygardenworld.core.ui.ReorderList
import com.silkage.mygardenworld.core.ui.SectionCard
import com.silkage.mygardenworld.core.ui.SegmentedRow
import com.silkage.mygardenworld.core.ui.SettingStatus
import com.silkage.mygardenworld.core.ui.StatusRow
import com.silkage.mygardenworld.core.ui.StatusSwitchRow
import com.silkage.mygardenworld.core.ui.Stepper
import com.silkage.mygardenworld.core.ui.SwitchRow
import com.silkage.mygardenworld.core.ui.TextRow
import com.silkage.mygardenworld.core.ui.settingStatus
import com.silkage.mygardenworld.feature.workspace.PolicyLens.basic
import com.silkage.mygardenworld.feature.workspace.PolicyLens.benefit
import com.silkage.mygardenworld.feature.workspace.PolicyLens.cultivate
import com.silkage.mygardenworld.feature.workspace.PolicyLens.cultivateShop
import com.silkage.mygardenworld.feature.workspace.PolicyLens.customer
import com.silkage.mygardenworld.feature.workspace.PolicyLens.cyclicNote
import com.silkage.mygardenworld.feature.workspace.PolicyLens.cyclicStory
import com.silkage.mygardenworld.feature.workspace.PolicyLens.flowerArt
import com.silkage.mygardenworld.feature.workspace.PolicyLens.friendSteal
import com.silkage.mygardenworld.feature.workspace.PolicyLens.pearl
import com.silkage.mygardenworld.feature.workspace.PolicyLens.planting
import com.silkage.mygardenworld.feature.workspace.PolicyLens.reputation
import com.silkage.mygardenworld.feature.workspace.PolicyLens.resident
import com.silkage.mygardenworld.feature.workspace.PolicyLens.sign
import com.silkage.mygardenworld.feature.workspace.PolicyLens.task
import com.silkage.mygardenworld.feature.workspace.PolicyLens.union
import com.silkage.mygardenworld.feature.workspace.PolicyLens.unionBuild
import com.silkage.mygardenworld.feature.workspace.PolicyLens.unionFlower
import com.silkage.mygardenworld.feature.workspace.PolicyLens.unionLand
import com.silkage.mygardenworld.feature.workspace.PolicyLens.unionRace
import com.silkage.mygardenworld.feature.workspace.PolicyLens.zoo

/** Demand goals for the planting priority editor, highest default first (Web GOAL_OPTIONS). */
private val DEMAND_GOALS = listOf(
    Triple("order.customer", "顾客订单", 90),
    Triple("order.resident", "居民订单", 80),
    Triple("basic.task.main", "主线任务", 70),
    Triple("basic.task.daily", "日常任务", 60),
    Triple("basic.task.weekly", "周常任务", 55),
    Triple("order.flower_art", "花艺/花架", 40),
    Triple("fallback.auto_replant", "自主补种", 10),
)

private data class RaceTaskType(val id: Int, val label: String, val defaultPriority: Int, val note: String? = null)

private val RACE_TASK_TYPES = listOf(
    RaceTaskType(2004, "VIP商店购买", 0),
    RaceTaskType(3006, "居民订单", 0),
    RaceTaskType(3016, "顾客订单", 0),
    RaceTaskType(3017, "材料商店购买", 0),
    RaceTaskType(3018, "宫廷订单", 0),
    RaceTaskType(3023, "珍珠采集雇佣", 0),
    RaceTaskType(3024, "好友偷花", 0),
    RaceTaskType(3030, "花艺售卖", 0, "不要求「自动上架」；上架满5分钟会全部下架再挂；缺成品先按制作规则做最高价有种子花艺；上架时选库存数量最多的可售花艺"),
    RaceTaskType(3034, "花艺制作", 0, "不要求「自动制作」；只做配方花都有种子且售价最高的花艺"),
    RaceTaskType(3035, "鲜花升级", 0),
    RaceTaskType(3036, "种植收获", 5),
    RaceTaskType(3044, "花种培育", 0, "只接正好 36 分且进度为 0；不要求开启鲜花培育。竞赛不主动培育，只接取并在进度达标后提交。已接的 36 分任务一律不放弃（含手动接取、优先级为 0）"),
    RaceTaskType(3052, "动物互动", 0),
)

private val SELECTION_MODES = listOf(
    SelectionMode.SELECTION_MODE_ALL to "全部",
    SelectionMode.SELECTION_MODE_QUALITY to "品质",
    SelectionMode.SELECTION_MODE_SPECIFIC to "指定",
    SelectionMode.SELECTION_MODE_EXCLUDE to "排除",
)
private val REPLANT_MODES = listOf(
    SelectionMode.SELECTION_MODE_ALL to "全部",
    SelectionMode.SELECTION_MODE_SPECIFIC to "指定",
    SelectionMode.SELECTION_MODE_EXCLUDE to "排除",
)
private val FRIEND_MODES = listOf(
    SelectionMode.SELECTION_MODE_ALL to "全部可摘",
    SelectionMode.SELECTION_MODE_SPECIFIC to "指定次数",
)

private fun SelectionMode.or(default: SelectionMode): SelectionMode =
    if (this == SelectionMode.SELECTION_MODE_UNSPECIFIED || this == SelectionMode.UNRECOGNIZED) default else this

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsTab(viewModel: WorkspaceViewModel, screen: WorkspaceScreenState, workspace: WorkspaceUiState, onReauthenticate: () -> Unit) {
    val policy = screen.policy
    val status = workspace.selectedStatus
    val connected = Format.accountConnected(screen.account, status)
    val editable = policy != null && workspace.online && !screen.savingPolicy
    val actionsEnabled = workspace.online && screen.busyAction.isBlank() && !Format.accountDeleting(screen.account, status)
    var confirmDelete by remember { mutableStateOf(false) }
    var jsonMode by rememberSaveable { mutableStateOf("") }
    val edit: ((Policy) -> Policy) -> Unit = viewModel::editPolicy

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionCard("账号操作", Modifier.padding(top = 12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (connected) OutlinedButton(onClick = viewModel::disconnect, enabled = actionsEnabled, modifier = Modifier.weight(1f)) { Text(if (screen.busyAction == "logout") "处理中…" else "退出登录") }
                    else Button(onClick = viewModel::connect, enabled = actionsEnabled, modifier = Modifier.weight(1f)) { Text(if (screen.busyAction == "login") "登录中…" else "登录") }
                    OutlinedButton(onClick = onReauthenticate, enabled = actionsEnabled, modifier = Modifier.weight(1f)) { Text("重新登录／更新凭据") }
                }
                OutlinedButton(onClick = { confirmDelete = true }, enabled = actionsEnabled, modifier = Modifier.fillMaxWidth()) { Text(if (screen.busyAction == "delete") "正在删除账号…" else "删除账号", color = MaterialTheme.colorScheme.error) }
                val automationOn = status?.automationEnabled ?: policy?.automationEnabled ?: false
                SwitchRow("自动化", automationOn, enabled = actionsEnabled, hint = "关闭后连接保留，仅停止自动操作") { viewModel.setAutomation(it) }
                if (!workspace.online) Text("当前离线，操作已禁用", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
        if (policy == null) {
            item { if (screen.policyLoading) LoadingBox("策略加载中…") else OutlinedButton(onClick = viewModel::loadPolicy) { Text("重新加载策略") } }
            return@LazyColumn
        }
        stickyHeader {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("设置仅在保存后生效", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (screen.message.isNotBlank()) Text(screen.message, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 2)
                }
                if (screen.policyDirty) {
                    Badge("未保存", BadgeTone.WARNING)
                    TextButton(onClick = viewModel::loadPolicy, enabled = !screen.savingPolicy) { Text("放弃") }
                }
                Button(onClick = viewModel::savePolicy, enabled = editable && screen.policyDirty) { Text(if (screen.savingPolicy) "保存中…" else "保存") }
            }
        }
        basicSection(policy, workspace, editable, edit, onJson = { jsonMode = it })
        gardenSection(policy, workspace, viewModel.catalog, editable, edit)
        ordersSection(policy, workspace, editable, edit)
        unionSection(policy, workspace, viewModel.catalog, editable, edit)
        activitiesSection(policy, workspace.capabilities, editable, edit)
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除账号") },
            text = { Text("确认删除账号「${screen.account?.let(Format::accountNickname) ?: ""}」？提交后不可撤销，将停止账号并在后台分批清理本地会话、策略和相关记录。清理期间不能操作或重新添加该账号，不会删除游戏角色。") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.delete() }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
    if (jsonMode.isNotBlank() && policy != null) {
        PolicyJsonDialog(
            export = jsonMode == "export",
            policy = policy,
            codec = viewModel.protoJson,
            enabled = !screen.savingPolicy,
            onImport = { imported -> viewModel.applyImportedPolicy(imported); jsonMode = "" },
            onDismiss = { jsonMode = "" },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Comma-separated integer list, like the Web IntListRow. */
@Composable
private fun IntListRow(label: String, value: List<Int>, enabled: Boolean, hint: String? = null, onChange: (List<Int>) -> Unit) {
    var text by remember(value) { mutableStateOf(value.joinToString(", ")) }
    TextRow(label, text, enabled, hint) { raw ->
        text = raw
        onChange(raw.split(',', '，', ' ', '\n').mapNotNull { it.trim().toIntOrNull() }.distinct())
    }
}

private fun LazyListScope.basicSection(policy: Policy, workspace: WorkspaceUiState, editable: Boolean, edit: ((Policy) -> Policy) -> Unit, onJson: (String) -> Unit) {
    val capabilities = workspace.capabilities
    item { SectionTitle("基础策略") }
    item {
        SectionCard("运行参数") {
            BoundedNumberRow("决策间隔（秒）", policy.decisionIntervalSeconds.toLong().takeIf { it > 0 } ?: 4, editable, min = 1) { v -> edit { it.toBuilder().setDecisionIntervalSeconds(v.toDouble()).build() } }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("账号配置分享", style = MaterialTheme.typography.bodyMedium)
                    Text("复制或粘贴所有模块的配置 JSON", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = { onJson("export") }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("导出") }
                OutlinedButton(onClick = { onJson("import") }, enabled = editable, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("导入") }
            }
        }
    }
    item {
        val b = policy.basic
        SectionCard("基础配置", defaultOpen = false) {
            SwitchRow("礼仪分监控", b.reputation.enabled, editable) { v -> edit { it.reputation { enabled = v } } }
            BoundedNumberRow("礼仪分阈值", b.reputation.threshold.toLong().takeIf { it > 0 } ?: 80, editable) { v -> edit { it.reputation { threshold = v.toInt() } } }
            SwitchRow("被挤号后自动重登", b.displacedSessionReloginEnabled, editable) { v -> edit { it.basic { displacedSessionReloginEnabled = v } } }
            BoundedNumberRow("自动重登间隔（秒）", b.reconnectIntervalSeconds.toLong().takeIf { it > 0 } ?: 300, editable && b.displacedSessionReloginEnabled, min = 1, max = 86400) { v -> edit { it.basic { reconnectIntervalSeconds = v.toDouble() } } }
            Note(
                if (b.displacedSessionReloginEnabled) "已启用：明确检测到异地登录或被挤下线后，将等待上述时间再自动登录。主动退出和普通业务失败不会触发。"
                else "默认关闭。开启后仅在明确检测到异地登录或被挤下线时自动重登；关闭时不会自动登录。",
            )
            SwitchRow(
                "兑换码离线自动上线",
                b.redeemConnectMode != RedeemConnectMode.REDEEM_CONNECT_MODE_ONLINE_ONLY,
                editable,
                "默认开启：有待处理兑换码时可建立游戏会话，可能挤下正在使用的游戏客户端。关闭后仅复用本来就在线的账号，兑换码会保留到下次上线。",
            ) { v -> edit { it.basic { redeemConnectMode = if (v) RedeemConnectMode.REDEEM_CONNECT_MODE_AUTO else RedeemConnectMode.REDEEM_CONNECT_MODE_ONLINE_ONLY } } }
            SwitchRow(
                "5000 异常后允许重新登录",
                b.serverErrorFreshLoginEnabled,
                editable,
                "默认关闭。开启后，5000 首次保护冷却结束且额度可用时，直接重新认证，不先重试旧会话，可能挤下手机端。每次异常最多一次，两次尝试至少间隔 30 分钟；暂停时不尝试，暂停/启动不会重置冷却和额度。与自动挤号设置独立，业务核验通过后才恢复操作。",
            ) { v -> edit { it.basic { serverErrorFreshLoginEnabled = v } } }
        }
    }
    item {
        val t = policy.basic.task
        SectionCard("任务与剧情", defaultOpen = false) {
            SwitchRow("主线任务", t.mainEnabled, editable) { v -> edit { it.task { mainEnabled = v } } }
            SwitchRow("每日任务", t.dailyEnabled, editable) { v -> edit { it.task { dailyEnabled = v } } }
            SwitchRow("每周任务", t.weeklyEnabled, editable) { v -> edit { it.task { weeklyEnabled = v } } }
            SwitchRow("主线剧情", t.storyEnabled, editable) { v -> edit { it.task { storyEnabled = v } } }
            SwitchRow("成就任务", t.achievementEnabled, editable) { v -> edit { it.task { achievementEnabled = v } } }
            SwitchRow("地图随机事件", policy.basic.mapEventEnabled, editable) { v -> edit { it.basic { mapEventEnabled = v } } }
        }
    }
    item {
        val b = policy.basic
        SectionCard("日常奖励", defaultOpen = false) {
            SwitchRow("邮件", b.mailEnabled, editable) { v -> edit { it.basic { mailEnabled = v } } }
            SwitchRow("福利宝箱", b.benefit.boxEnabled, editable) { v -> edit { it.benefit { boxEnabled = v } } }
            SwitchRow("防骗宝箱", b.benefit.antiScamBoxEnabled, editable) { v -> edit { it.benefit { antiScamBoxEnabled = v } } }
            SwitchRow("防诈骗签到奖励", b.sign.dailyEnabled, editable) { v -> edit { it.sign { dailyEnabled = v } } }
            SwitchRow("成长之路", b.roadGrowRewardEnabled, editable) { v -> edit { it.basic { roadGrowRewardEnabled = v } } }
        }
    }
    item {
        val p = policy.basic.pearl
        SectionCard("珍珠", defaultOpen = false) {
            SwitchRow("免费珍珠", p.freeEnabled, editable) { v -> edit { it.pearl { freeEnabled = v } } }
            SwitchRow("安全雇佣劳工", p.autoHireEnabled, editable) { v -> edit { it.pearl { autoHireEnabled = v } } }
            BoundedNumberRow("雇佣等级上限（0=不限）", p.maxHireLevel.toLong(), editable) { v -> edit { it.pearl { maxHireLevel = v.toInt() } } }
            BoundedNumberRow("同时在岗上限（0=关闭）", p.maxHireTicketUsage.toLong(), editable) { v -> edit { it.pearl { maxHireTicketUsage = v.toInt() } } }
            BoundedNumberRow("每日雇佣券上限（0=不限）", p.dailyHireTicketLimit.toLong(), editable) { v -> edit { it.pearl { dailyHireTicketLimit = v.toInt() } } }
            SwitchRow("自动开珍珠", p.drawEnabled, editable) { v -> edit { it.pearl { drawEnabled = v } } }
            SwitchRow("开启防身", p.protectEnabled, editable) { v -> edit { it.pearl { protectEnabled = v } } }
        }
    }
    item {
        val shop = policy.basic.shop.cultivateShop
        SectionCard("商城", defaultOpen = false) {
            Note("激励视频和广告礼包不提供自动化：系统不会伪造广告 SDK 回调或 token。仅支持协议明确允许直接领取或跳过广告的流程。")
            SwitchRow("材料商店", shop.autoBuy, editable) { v -> edit { it.cultivateShop { autoBuy = v } } }
            BoundedNumberRow("材料单次金币上限", shop.maxSpendGold, editable, "0=不购买金币商品；每次购买都必须低于该上限") { v -> edit { it.cultivateShop { maxSpendGold = v } } }
            IntListRow("材料商品 ID（留空=全部）", shop.itemIdsList, editable, "可填写货架 shopId 或获得物品 itemId") { v -> edit { it.cultivateShop { clearItemIds().addAllItemIds(v) } } }
        }
    }
    item {
        val z = policy.basic.zoo
        SectionCard("宠物", defaultOpen = false) {
            SwitchRow("宠物模块", z.enabled, editable) { v ->
                edit { it.zoo { enabled = v; if (!v) { autoEventEnabled = false; autoFeed = false; autoStroke = false; autoBuyFood = false } } }
            }
            SwitchRow("宠物外出/事件处理", z.autoEventEnabled, editable) { v -> edit { it.zoo { autoEventEnabled = v; enabled = v || z.enabled } } }
            SwitchRow("自动补充食盆", z.autoFeed, editable) { v -> edit { it.zoo { autoFeed = v; enabled = v || z.enabled } } }
            SwitchRow("自动互动", z.autoStroke, editable) { v -> edit { it.zoo { autoStroke = v; enabled = v || z.enabled } } }
            Note("食盆补充优先使用库存。开启购买后，仅在食盆有空位且库存无猫粮时购买商店 9 的金币普通猫粮；不会购买元宝猫粮。")
            StatusSwitchRow("购买普通猫粮", z.autoBuyFood, editable, status = settingStatus(capabilities, "basic.zoo_buy_food")) { v ->
                edit { it.zoo { autoBuyFood = v; autoFeed = v || z.autoFeed; enabled = v || z.enabled } }
            }
            BoundedNumberRow("猫粮单次金币上限", z.maxSpendGold, editable, "普通猫粮每份 100 金币；0=不购买") { v -> edit { it.zoo { maxSpendGold = v } } }
        }
    }
}

private fun plantableOptions(workspace: WorkspaceUiState, catalog: Catalog): List<PickerOption> =
    workspace.state?.takeIf { it.hasGarden() }?.garden?.plantableFlowersList.orEmpty().map { f ->
        PickerOption(
            id = f.flowerId,
            name = f.flowerName.ifBlank { catalog.itemName(f.flowerId) },
            detail = listOf(if (f.lvl > 0) "lv${f.lvl}" else "", if (f.cdSeconds > 0) "成熟 ${Format.duration(f.cdSeconds.toLong())}" else "").filter { it.isNotBlank() }.joinToString(" · "),
            quality = catalog.flowerQuality(f.flowerId),
            stock = f.stock,
            matureSeconds = f.cdSeconds,
        )
    }

private fun catalogFlowerOptions(workspace: WorkspaceUiState, catalog: Catalog): List<PickerOption> {
    val inventory = workspace.state?.takeIf { it.hasWarehouse() }?.warehouse?.inventoryMap.orEmpty()
    return catalog.allFlowers.map { f ->
        val name = catalog.itemName(f.id)
        PickerOption(id = f.id, name = name, detail = catalog.seedName(f.id).takeIf { it != name }.orEmpty(), quality = f.quality, stock = inventory[f.id] ?: 0)
    }
}

private fun LazyListScope.gardenSection(policy: Policy, workspace: WorkspaceUiState, catalog: Catalog, editable: Boolean, edit: ((Policy) -> Policy) -> Unit) {
    val gardenSynced = workspace.state?.hasGarden() == true
    val warehouseSynced = workspace.state?.hasWarehouse() == true
    item { SectionTitle("花园策略") }
    item {
        val p = policy.plant.planting
        SectionCard("土地与种植", defaultOpen = false) {
            SwitchRow("自动种植", p.autoEnabled, editable) { v -> edit { it.planting { autoEnabled = v } } }
            SwitchRow("自动收获", p.autoHarvestEnabled, editable, "关闭后普通农田不自动收；公会竞赛种植任务仍会强制收获竞赛花") { v -> edit { it.planting { autoHarvestEnabled = v } } }
            BoundedNumberRow("延时收获（秒）", p.harvestDelaySeconds.toLong(), editable, "植物成熟后等待多久再收获；0=立即收获。竞赛种植的花朵不受此间隔限制，默认直接收获") { v -> edit { it.planting { harvestDelaySeconds = v.toInt() } } }
            SwitchRow("解锁土地", p.autoUnlockLand, editable) { v -> edit { it.planting { autoUnlockLand = v } } }
            SwitchRow("使用加速券", p.useSpeedUpTicket, editable) { v -> edit { it.planting { useSpeedUpTicket = v } } }
            BoundedNumberRow("加速券上限", p.speedUpTicketMax.toLong(), editable) { v -> edit { it.planting { speedUpTicketMax = v.toInt() } } }
            BoundedNumberRow("保留水滴", p.minWaterDrops.toLong(), editable, "可用水滴=当前−保留，仅限制下种数量") { v -> edit { it.planting { minWaterDrops = v.toInt() } } }
        }
    }
    item {
        val b = policy.basic
        SectionCard("水滴补给", defaultOpen = false) {
            SwitchRow("水车水滴", b.waterwheelEnabled, editable, "广告桶仅使用服务端明确支持的 skip→recv 路径，不触发或伪造广告 SDK 回调；每次约3–7滴，普通桶约30滴") { v -> edit { it.basic { waterwheelEnabled = v } } }
            SwitchRow("限时水滴", b.freeWaterEnabled, editable) { v -> edit { it.basic { freeWaterEnabled = v } } }
            BoundedNumberRow("水滴领取阈值", b.waterClaimThreshold.toLong(), editable, "当前水滴≥该值时暂停水车/限时领取；0=不限制。与自然恢复上限(如130)无关") { v -> edit { it.basic { waterClaimThreshold = v.toInt() } } }
        }
    }
    item {
        val p = policy.plant.planting
        val mode = p.autoReplantMode.or(SelectionMode.SELECTION_MODE_ALL)
        SectionCard("自主补种", defaultOpen = false) {
            SegmentedRow("补种范围", mode, REPLANT_MODES, editable) { v -> edit { it.planting { autoReplantMode = v } } }
            if (mode == SelectionMode.SELECTION_MODE_ALL) {
                QualityRow("补种品质", p.autoReplantQualitiesList, editable, emptyMeansAll = true) { v -> edit { it.planting { clearAutoReplantQualities().addAllAutoReplantQualities(v) } } }
            } else {
                val exclude = mode == SelectionMode.SELECTION_MODE_EXCLUDE
                val options = plantableOptions(workspace, catalog)
                PickerRow(
                    label = if (exclude) "排除补种" else "指定补种",
                    value = if (exclude) p.autoReplantExcludeFlowerIdsList else p.autoReplantFlowerIdsList,
                    options = options,
                    enabled = editable,
                    emptySummary = "未选择时不限制",
                    countBadge = "可种 ${options.size}",
                    nameOf = catalog::itemName,
                    sorts = listOf(PickerSort.MATURE_ASC, PickerSort.MATURE_DESC, PickerSort.STOCK_ASC, PickerSort.STOCK_DESC),
                    qualityFilter = true,
                    synced = gardenSynced,
                ) { v -> edit { it.planting { if (exclude) clearAutoReplantExcludeFlowerIds().addAllAutoReplantExcludeFlowerIds(v) else clearAutoReplantFlowerIds().addAllAutoReplantFlowerIds(v) } } }
            }
            BoundedNumberRow("最低种植等级", p.autoReplantMinLevel.toLong(), editable, "0=不限；设为11则只种培育等级11-20的鲜花", max = 20) { v -> edit { it.planting { autoReplantMinLevel = v.toInt() } } }
            SwitchRow("生产需求优先级", p.demandPriorityEnabled, editable, "开启后按下方排序优先为缺花订单/任务补种；关闭时空地只按库存自主补种") { v -> edit { it.planting { demandPriorityEnabled = v } } }
            if (p.demandPriorityEnabled) {
                val priorities = p.demandPriorityMap
                val ordered = DEMAND_GOALS.sortedWith(compareByDescending<Triple<String, String, Int>> { priorities[it.first]?.takeIf { v -> v != 0 } ?: it.third }.thenBy { DEMAND_GOALS.indexOf(it) })
                Text("调整缺花补种顺序（越靠前越优先）", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ReorderList(ordered, { it.second }, editable) { next ->
                    edit { it.planting { clearDemandPriority(); next.forEachIndexed { index, goal -> putDemandPriority(goal.first, (next.size - index) * 10) } } }
                }
            }
        }
    }
    item {
        val c = policy.plant.cultivate
        SectionCard("培育配置", defaultOpen = false) {
            SwitchRow("自动培育", c.enabled, editable) { v -> edit { it.cultivate { enabled = v } } }
            SwitchRow("鲜花升级", c.upgradeEnabled, editable) { v -> edit { it.cultivate { upgradeEnabled = v } } }
            BoundedNumberRow("目标等级", c.targetLevel.toLong().takeIf { it > 0 } ?: 20, editable, min = 1) { v -> edit { it.cultivate { targetLevel = v.toInt() } } }
        }
    }
    item {
        val f = policy.plant.friendSteal
        val flowerMode = f.mode.or(SelectionMode.SELECTION_MODE_ALL)
        val friendMode = f.friendMode.or(SelectionMode.SELECTION_MODE_ALL)
        val garden = workspace.state?.takeIf { it.hasGarden() }?.garden
        SectionCard("好友摸花", defaultOpen = false) {
            Note("仅自动摸取服务端明确标记为可摸的成熟鲜花；花灵摸取尚缺少状态与成功回包实测，因此不会发送 stealElves=1。")
            StatusSwitchRow("自动摸花", f.enabled, editable, status = settingStatus(workspace.capabilities, "plant.friend_steal")) { v -> edit { it.friendSteal { enabled = v } } }
            SegmentedRow("好友范围", friendMode, FRIEND_MODES, editable) { v -> edit { it.friendSteal { setFriendMode(v) } } }
            SegmentedRow("鲜花范围", flowerMode, SELECTION_MODES, editable) { v -> edit { it.friendSteal { mode = v } } }
            when (flowerMode) {
                SelectionMode.SELECTION_MODE_QUALITY -> QualityRow("指定品质", f.qualitiesList, editable) { v -> edit { it.friendSteal { clearQualities().addAllQualities(v) } } }
                SelectionMode.SELECTION_MODE_SPECIFIC, SelectionMode.SELECTION_MODE_EXCLUDE -> {
                    val exclude = flowerMode == SelectionMode.SELECTION_MODE_EXCLUDE
                    PickerRow(
                        label = if (exclude) "排除鲜花" else "指定鲜花",
                        value = if (exclude) f.excludeFlowerIdsList else f.flowerIdsList,
                        options = catalogFlowerOptions(workspace, catalog),
                        enabled = editable,
                        emptySummary = "未选择时不限制",
                        countBadge = "花库 ${catalog.allFlowers.size}",
                        nameOf = catalog::itemName,
                        qualityFilter = true,
                        synced = warehouseSynced,
                    ) { v -> edit { it.friendSteal { if (exclude) clearExcludeFlowerIds().addAllExcludeFlowerIds(v) else clearFlowerIds().addAllFlowerIds(v) } } }
                }
                else -> Unit
            }
            StatusSwitchRow("友情币兑换次数", f.autoBuyTimes, editable, status = settingStatus(workspace.capabilities, "plant.friend_steal_buy")) { v -> edit { it.friendSteal { autoBuyTimes = v } } }
            BoundedNumberRow("每好友兑换上限", f.maxBuyPerFriend.toLong(), editable, "每次消耗 1 友情币；0 使用静态目录 \$pickMax（当前为 10）", max = 10) { v -> edit { it.friendSteal { maxBuyPerFriend = v.toInt() } } }
            FriendTouchList(
                friends = garden?.friendTouchFriendsList.orEmpty(),
                observed = garden?.friendTouchFriendsObserved ?: false,
                specific = friendMode == SelectionMode.SELECTION_MODE_SPECIFIC,
                counts = f.friendCountsMap,
                excluded = f.excludeUidsList.toSet(),
                autoBuy = f.autoBuyTimes,
                maxBuyPerFriend = f.maxBuyPerFriend.takeIf { it > 0 } ?: 10,
                enabled = editable,
                onCount = { uid, count -> edit { it.friendSteal { if (count <= 0) removeFriendCounts(uid) else putFriendCounts(uid, count) } } },
                onExcluded = { uid, excluded ->
                    edit {
                        it.friendSteal {
                            val current = excludeUidsList
                            val next = if (excluded) (if (uid in current) current else current + uid) else current.filter { v -> v != uid }
                            clearExcludeUids().addAllExcludeUids(next)
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun FriendTouchList(
    friends: List<FriendTouchFriendView>,
    observed: Boolean,
    specific: Boolean,
    counts: Map<Long, Int>,
    excluded: Set<Long>,
    autoBuy: Boolean,
    maxBuyPerFriend: Int,
    enabled: Boolean,
    onCount: (Long, Int) -> Unit,
    onExcluded: (Long, Boolean) -> Unit,
) {
    when {
        !observed -> EmptyState("尚未同步好友列表", "请先开启自动摸花并保存；下一轮会同步好友列表，随后即可配置指定目标。")
        friends.isEmpty() -> EmptyState("暂无好友", "游戏好友列表为空时无法配置摸花目标。")
        else -> {
            Row(Modifier.fillMaxWidth()) {
                Text(if (specific) "为指定好友设置今日摸花次数；可勾选排除不主动摸取" else "自动摸取全部可摘好友；勾选排除后跳过该好友", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text("${friends.size} 人", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            friends.forEach { friend ->
                val isExcluded = friend.uid in excluded
                val target = counts[friend.uid] ?: 0
                val name = friend.name.trim().ifBlank { if (friend.profileObserved) "UID ${friend.uid}" else "好友 ${friend.uid}" }
                val targetMax = friend.baseStealMax + if (autoBuy) maxBuyPerFriend else friend.boughtCount
                Column(
                    Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (isExcluded) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("UID ${friend.uid} · " + if (friend.quotaObserved) "今日 ${friend.stolenCount}/${friend.stealMax}" else "次数未同步", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Badge(if (!friend.availabilityObserved) "状态待同步" else if (friend.canSteal) "可摘" else "暂不可摘")
                            }
                        }
                        Text("排除", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Switch(checked = isExcluded, onCheckedChange = { onExcluded(friend.uid, it) }, enabled = enabled, modifier = Modifier.padding(start = 4.dp))
                    }
                    if (specific && !isExcluded) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("目标次数", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            Stepper(target, enabled, max = targetMax.takeIf { it > 0 }) { onCount(friend.uid, it) }
                        }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.ordersSection(policy: Policy, workspace: WorkspaceUiState, editable: Boolean, edit: ((Policy) -> Policy) -> Unit) {
    val orders = workspace.state?.takeIf { it.hasOrders() }?.orders
    item { SectionTitle("订单经营策略") }
    item {
        val r = policy.order.resident
        SectionCard("居民订单", defaultOpen = false) {
            SwitchRow("普通居民订单", r.normalEnabled, editable) { v -> edit { it.resident { normalEnabled = v } } }
            BoundedNumberRow("普通订单上限", r.normalDailyLimit.toLong(), editable && r.normalEnabled, "需先开启普通居民订单；上限按今日已完成次数生效", min = 1, max = 1200) { v -> edit { it.resident { normalDailyLimit = v.toInt() } } }
            SwitchRow("绸缎订单", r.satinEnabled, editable) { v -> edit { it.resident { satinEnabled = v; if (v && satinDailyLimit <= 0) satinDailyLimit = 120 } } }
            BoundedNumberRow("绸缎订单上限", r.satinDailyLimit.toLong().takeIf { it > 0 } ?: 120, editable && r.satinEnabled, "需先开启绸缎订单；上限按今日已完成次数生效", min = 1, max = 120) { v -> edit { it.resident { satinDailyLimit = v.toInt() } } }
            SwitchRow("建材订单", r.decorateEnabled, editable) { v -> edit { it.resident { decorateEnabled = v; if (v && decorateDailyLimit <= 0) decorateDailyLimit = 120 } } }
            BoundedNumberRow("建材订单上限", r.decorateDailyLimit.toLong().takeIf { it > 0 } ?: 120, editable && r.decorateEnabled, "需先开启建材订单；上限按今日已完成次数生效", min = 1, max = 120) { v -> edit { it.resident { decorateDailyLimit = v.toInt() } } }
            SwitchRow("居民领奖", r.rewardEnabled, editable) { v -> edit { it.resident { rewardEnabled = v } } }
            QualityRow("品质限定", r.qualitiesList, editable) { v -> edit { it.resident { clearQualities().addAllQualities(v) } } }
        }
    }
    item {
        val c = policy.order.customer
        val observedNamespace = workspace.state?.takeIf { it.hasBasic() }?.basic?.observedNamespacesList?.contains("109") ?: false
        val stats = orders?.takeIf { it.hasOrderStatistics() }?.orderStatistics
        val statsObserved = stats?.observed ?: false
        val finished = stats?.customerFinished ?: 0
        val pending = orders?.pendingTasksList?.count { it.category == "顾客订单" } ?: 0
        val (progress, tone) = when {
            orders == null -> "状态未加载" to BadgeTone.NEUTRAL
            !statsObserved -> (if (observedNamespace) "今日进度未同步（当前挂单 $pending）" else "未同步订单统计") to BadgeTone.NEUTRAL
            c.dailyLimit > 0 -> "今日已完成 $finished/${c.dailyLimit}" to if (finished >= c.dailyLimit) BadgeTone.WARNING else BadgeTone.SUCCESS
            else -> "今日已完成 $finished" to BadgeTone.SUCCESS
        }
        SectionCard("顾客订单", defaultOpen = false) {
            SwitchRow("顾客订单", c.enabled, editable) { v -> edit { it.customer { enabled = v } } }
            BoundedNumberRow("每日上限", c.dailyLimit.toLong(), editable && c.enabled, "0 表示不限制；按今日已完成次数生效", max = 9999) { v -> edit { it.customer { dailyLimit = v.toInt() } } }
            BoundedNumberRow("最少花艺", c.minFlowerArtCount.toLong(), editable && c.enabled, "0 不限；设 2 只做需 2/3 件花艺的单，设 3 只做需 3 件的单；已接竞赛顾客任务时不受此限", max = 3) { v -> edit { it.customer { minFlowerArtCount = v.toInt() } } }
            SwitchRow("指定花坊币奖励", c.hasExactFloralCoin(), editable) { v -> edit { it.customer { if (v) exactFloralCoin = 1 else clearExactFloralCoin() } } }
            if (c.hasExactFloralCoin()) {
                BoundedNumberRow("花坊币等于", c.exactFloralCoin, editable, "按整单普通奖励精确匹配，不含广告翻倍；不匹配或奖励未知时保留订单，不制作、不交付、不自动拒绝。竞赛任务也遵守此条件；保留订单可能占满顾客名额。") { v -> edit { it.customer { exactFloralCoin = v } } }
            }
            SwitchRow("暂时无货", c.rejectUnavailableEnabled, editable) { v -> edit { it.customer { rejectUnavailableEnabled = v } } }
            StatusRow("今日进度", progress, tone)
        }
    }
    item {
        val f = policy.order.flowerArt
        val arts = orders?.sellableFlowerArtsList.orEmpty()
        SectionCard("花架售卖", defaultOpen = false) {
            SwitchRow("自动上架花艺", f.sellEnabled, editable) { v -> edit { it.flowerArt { sellEnabled = v } } }
            PickerRow(
                label = "上架花艺",
                value = f.sellArtIdsList,
                options = arts.map { a -> PickerOption(a.artId, a.artName.ifBlank { "#${a.artId}" }, listOf(a.vaseName, if (a.saleValue > 0) "售价 ${a.saleValue}" else "").filter { it.isNotBlank() }.joinToString(" · "), stock = a.stock) },
                enabled = editable,
                emptySummary = "未选择，按库存最多自动上架",
                countBadge = "可选 ${arts.size}",
                nameOf = { id -> arts.firstOrNull { it.artId == id }?.artName?.takeIf { it.isNotBlank() } ?: "#$id" },
                hint = "仅展示当前账号已解锁花瓶对应的花艺；未选择时上架库存数量最多的花艺",
                sorts = listOf(PickerSort.STOCK_DESC, PickerSort.STOCK_ASC),
                synced = orders != null,
            ) { v -> edit { it.flowerArt { clearSellArtIds().addAllSellArtIds(v) } } }
            SwitchRow("自动制作", f.craftEnabled, editable) { v -> edit { it.flowerArt { craftEnabled = v } } }
            SwitchRow("0-8点关闭自动上架花艺", f.sellNightPauseEnabled, editable && f.sellEnabled, "需同时开启自动上架花艺；仅在 0:00-8:00（北京时间）暂停上架，领取收益不受影响") { v -> edit { it.flowerArt { sellNightPauseEnabled = v } } }
            SwitchRow("花艺经验", f.createRewardEnabled, editable) { v -> edit { it.flowerArt { createRewardEnabled = v } } }
            SwitchRow("图鉴奖励", f.collectRewardEnabled, editable) { v -> edit { it.flowerArt { collectRewardEnabled = v } } }
        }
    }
}

private fun LazyListScope.unionSection(policy: Policy, workspace: WorkspaceUiState, catalog: Catalog, editable: Boolean, edit: ((Policy) -> Policy) -> Unit) {
    val unionView = workspace.state?.takeIf { it.hasUnion() }?.union
    item { SectionTitle("公会策略") }
    if (unionView == null || !unionView.membershipObserved || !unionView.inUnion) {
        item { Note("设置可随时修改；只有确认当前周期的公会成员资格后，服务端才会执行公会操作。") }
    }
    item {
        val l = policy.union.land
        val options = plantableOptions(workspace, catalog)
        SectionCard("公会土地", defaultOpen = false) {
            SwitchRow("自动收获", l.harvestEnabled, editable) { v -> edit { it.unionLand { harvestEnabled = v } } }
            SwitchRow("自动种植", l.autoPlantEnabled, editable) { v -> edit { it.unionLand { autoPlantEnabled = v } } }
            BoundedNumberRow("成熟时长(分钟)", l.minMaturityMinutes.toLong().takeIf { it > 0 } ?: 20, editable, "未满11级时优先选择低等级花练级；全部达到11级后才按成熟时长选种。指定花朵非空时只种这些 ID（莹白露薇=23117）。", min = 1) { v -> edit { it.unionLand { minMaturityMinutes = v.toInt() } } }
            BoundedNumberRow("改种冷却(分钟)", l.minReplantMinutes.toLong().takeIf { it > 0 } ?: 60, editable, "空地随时补种；已种地块需无待收获花、距下次成熟超过2分钟且达到此冷却后才能改种。练级与普通轮种遵守同一安全边界。", min = 1) { v -> edit { it.unionLand { minReplantMinutes = v.toInt() } } }
            PickerRow(
                label = "指定花朵",
                value = l.flowerIdsList,
                options = options,
                enabled = editable,
                emptySummary = "未选择时不限制",
                countBadge = "可种 ${options.size}",
                nameOf = catalog::itemName,
                sorts = listOf(PickerSort.MATURE_ASC, PickerSort.MATURE_DESC, PickerSort.STOCK_ASC, PickerSort.STOCK_DESC),
                qualityFilter = true,
                synced = workspace.state?.hasGarden() == true,
            ) { v -> edit { it.unionLand { clearFlowerIds().addAllFlowerIds(v) } } }
            QualityRow("指定品质", l.qualitiesList, editable) { v -> edit { it.unionLand { clearQualities().addAllQualities(v) } } }
            BoundedNumberRow("最高花朵等级", l.maxFlowerLevel.toLong(), editable, "0 表示不限制；设置后只种培育等级不超过该值的花") { v -> edit { it.unionLand { maxFlowerLevel = v.toInt() } } }
        }
    }
    item {
        val b = policy.union.build
        SectionCard("公会建设", defaultOpen = false) {
            SwitchRow("金币建设", b.goldEnabled, editable) { v -> edit { it.unionBuild { goldEnabled = v } } }
            BoundedNumberRow("金币上限", b.maxSpendGold, editable) { v -> edit { it.unionBuild { maxSpendGold = v } } }
        }
    }
    item {
        val f = policy.union.flower
        SectionCard("公会分享与摸花", defaultOpen = false) {
            SwitchRow("自动摸花", f.takeEnabled, editable) { v -> edit { it.unionFlower { takeEnabled = v } } }
            SegmentedRow("摸花模式", f.takeMode.or(SelectionMode.SELECTION_MODE_QUALITY), SELECTION_MODES, editable) { v -> edit { it.unionFlower { takeMode = v } } }
            QualityRow("摸花品质", f.takeQualitiesList, editable) { v -> edit { it.unionFlower { clearTakeQualities().addAllTakeQualities(v) } } }
            PickerRow(
                label = "摸花花朵",
                value = f.takeFlowerIdsList,
                options = catalogFlowerOptions(workspace, catalog),
                enabled = editable,
                emptySummary = "未选择时不限制",
                countBadge = "花库 ${catalog.allFlowers.size}",
                nameOf = catalog::itemName,
                qualityFilter = true,
                synced = workspace.state?.hasWarehouse() == true,
            ) { v -> edit { it.unionFlower { clearTakeFlowerIds().addAllTakeFlowerIds(v) } } }
        }
    }
    item {
        val r = policy.union.race
        val deleteStatus = when {
            unionView == null || !unionView.memberPositionObserved -> SettingStatus("待同步", "公会职位尚未同步，服务端不会发送删除请求。", syncOnly = true)
            !unionView.raceDeleteAllowed -> SettingStatus("无权限", "当前职位" + (if (unionView.memberPositionLabel.isNotBlank()) "“${unionView.memberPositionLabel}”" else "") + "没有删除竞赛任务的权限。", syncOnly = false)
            else -> null
        }
        SectionCard("公会竞赛", defaultOpen = false) {
            SwitchRow("任务池同步", r.enabled, editable, "竞赛期间同步任务池与当前已接任务（只读展示）；关闭后不再拉取竞赛数据") { v -> edit { it.unionRace { enabled = v } } }
            SwitchRow("显示个人得分排名", r.showPersonalScoreRank, editable, "开启后在竞赛页展示当期个人累计得分与公会内排名；默认关闭") { v -> edit { it.unionRace { showPersonalScoreRank = v } } }
            SwitchRow("自动完成", r.autoEnableModules, editable, "自动接取、推进并提交竞赛任务；默认关闭。未开启时仍会同步并显示任务，但不会自动完成") { v -> edit { it.unionRace { autoEnableModules = v } } }
            SwitchRow("自动放弃", r.autoGiveUpTask, editable, "独立于自动完成；放弃不符合当前分数、类型或完成条件的已接任务。也会作用于在游戏客户端手动接取的任务，默认关闭") { v -> edit { it.unionRace { autoGiveUpTask = v } } }
            SwitchRow("自动启停", r.autoStopOnQuotaDone, editable, "按基础次数加游戏内已购买次数判断，全部用完后停止接取；同步到新增可用次数后继续，不会自动购买次数。已接任务仍继续处理。关闭后仅在服务端提示次数用尽时停止接取") { v -> edit { it.unionRace { autoStopOnQuotaDone = v } } }
            SwitchRow("避免接取已有进度任务", if (r.hasAvoidProgressedTasks()) r.avoidProgressedTasks else true, editable, "跳过其他成员退出后留下进度的任务，同时约束自动与手动接取；已经持有的任务不受影响") { v -> edit { it.unionRace { avoidProgressedTasks = v } } }
            SwitchRow("种植任务使用加速卡", r.useSpeedupTicketInTask, editable, "已接种植收获任务全程可用加速卡。关闭时仍强制保底：任务最后 10 分钟自动对竞赛花使用加速卡") { v -> edit { it.unionRace { useSpeedupTicketInTask = v } } }
            BoundedNumberRow("最低任务分", r.minTaskScore.toLong(), editable, "自动接取会跳过分数不高于此值的任务；只有另行开启自动放弃后，已接任务才会受此限制。0 表示不限制") { v -> edit { it.unionRace { minTaskScore = v.toInt() } } }
            SwitchRow("只接已升级任务", r.onlyUpgradeTask, editable, "只接取已被升级的任务（积分加成更高）") { v -> edit { it.unionRace { onlyUpgradeTask = v } } }
            SwitchRow("排除他人升级任务", r.excludeOthersUpgradeTask, editable, "仅排除明确由其他成员升级的任务；未记录升级人的任务仍按其余条件筛选，已被接取的任务始终跳过。适用于自动与手动接取，不影响已持有任务") { v -> edit { it.unionRace { excludeOthersUpgradeTask = v } } }
            StatusSwitchRow("自动升级任务", r.upgradeTask, editable, "独立于自动完成；升级当前持有的未完成任务，消耗元宝。结果未确认时不会重复提交", settingStatus(workspace.capabilities, "union.race.upgrade")) { v -> edit { it.unionRace { upgradeTask = v } } }
            BoundedNumberRow("单次升级元宝上限", r.maxSpendDiamond, editable, "0 表示禁止消费；每个任务升级前核对实际费用与可用余额") { v -> edit { it.unionRace { maxSpendDiamond = v } } }
            if (r.upgradeTask && r.maxSpendDiamond <= 0) Note("已打开升级开关，但预算为 0，不会执行升级。请明确设置允许的单次元宝上限并保存。")
            unionView?.takeIf { it.hasRace() }?.race?.autoUpgradeStatus?.takeIf { it.isNotBlank() }?.let {
                Text("当前执行状态（已保存配置）：$it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusSwitchRow("删除低分任务", r.deleteLowScoreTask, editable, "独立于自动完成；定期删除无人接取且分数不高于上限的任务，仅会长和副会长可用", deleteStatus) { v -> edit { it.unionRace { deleteLowScoreTask = v } } }
            BoundedNumberRow("删除分数上限", r.deleteTaskMaxScore.toLong(), editable, "只处理已同步、无人接取且分数明确大于 0 的任务；0 表示不删除") { v -> edit { it.unionRace { deleteTaskMaxScore = v.toInt() } } }
            BoundedNumberRow("删除间隔（秒）", r.deleteIntervalSeconds.toLong().takeIf { it > 0 } ?: 120, editable, "默认 120 秒，可设 30～3600 秒；自动与手动删除共用账号间隔，重启后仍保留。此为本地保护策略，不代表服务端安全阈值", min = 30, max = 3600) { v -> edit { it.unionRace { deleteIntervalSeconds = v.toInt() } } }
            Text("类型优先级：数字越大越优先接取；0 表示不接取。当前支持自动推进：种植收获、顾客订单、珍珠雇佣、花艺制作/售卖；花种培育仅接取与提交。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val priorities = r.taskTypePriorityMap
            RACE_TASK_TYPES.forEach { type ->
                BoundedNumberRow(type.label, (priorities[type.id] ?: type.defaultPriority).toLong(), editable, type.note) { v -> edit { it.unionRace { putTaskTypePriority(type.id, v.toInt()) } } }
            }
        }
    }
    item {
        SectionCard("公会其他", defaultOpen = false) {
            SwitchRow("能量森林", policy.union.forestEnabled, editable) { v -> edit { it.union { forestEnabled = v } } }
        }
    }
}

private fun LazyListScope.activitiesSection(policy: Policy, capabilities: List<FeatureCapability>, editable: Boolean, edit: ((Policy) -> Policy) -> Unit) {
    item { SectionTitle("活动策略") }
    item {
        val n = policy.activity.cyclicNote
        SectionCard("花笺集芳", defaultOpen = false) {
            StatusSwitchRow("启用", n.enabled, editable, status = settingStatus(capabilities, "activity.cyclicNote")) { v -> edit { it.cyclicNote { enabled = v } } }
            SwitchRow("自动领取任务奖励", n.autoClaimTaskRewards, editable) { v -> edit { it.cyclicNote { autoClaimTaskRewards = v } } }
            SwitchRow("自动领取积分奖励", n.autoClaimProgressBoxes, editable) { v -> edit { it.cyclicNote { autoClaimProgressBoxes = v } } }
            SwitchRow("驱动已启用模块完成任务", n.satisfyTasks, editable) { v -> edit { it.cyclicNote { satisfyTasks = v } } }
        }
    }
    item {
        val st = policy.activity.cyclicStory
        SectionCard("莳花纪闻", defaultOpen = false) {
            StatusSwitchRow("启用", st.enabled, editable, status = settingStatus(capabilities, "activity.actCyclicStory")) { v -> edit { it.cyclicStory { enabled = v } } }
            SwitchRow("自动领取订单奖励", st.autoClaimOrderRewards, editable) { v -> edit { it.cyclicStory { autoClaimOrderRewards = v } } }
            SwitchRow("自动领取积分奖励", st.autoClaimProgressBoxes, editable) { v -> edit { it.cyclicStory { autoClaimProgressBoxes = v } } }
            BoundedNumberRow("分数上限（0=不限制）", st.maxScore, editable) { v -> edit { it.cyclicStory { maxScore = v } } }
        }
    }
}
