package com.silkage.mygardenworld.core.protocol

import com.mygardenworld.v1.MarketBuyMode
import com.mygardenworld.v1.Policy
import com.mygardenworld.v1.RedeemConnectMode
import com.mygardenworld.v1.SelectionMode
import com.silkage.mygardenworld.feature.workspace.PolicyJson
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtoJsonTest {
    private val codec = ProtoJson(File("build/generated/proto-descriptor/assets/proto.desc").readBytes())

    private fun sample(): Policy = Policy.newBuilder()
        .setAutomationEnabled(true)
        .setDecisionIntervalSeconds(4.5)
        .setSchemaVersion(1)
        .setBasic(
            Policy.getDefaultInstance().basic.toBuilder()
                .setServerErrorFreshLoginEnabled(true)
                .setRedeemConnectMode(RedeemConnectMode.REDEEM_CONNECT_MODE_ONLINE_ONLY)
                .setShop(Policy.getDefaultInstance().basic.shop.toBuilder().setCultivateShop(Policy.getDefaultInstance().basic.shop.cultivateShop.toBuilder().setAutoBuy(true).setMaxSpendGold(9_007_199_254_740_993L).addItemIds(7))),
        )
        .setPlant(
            Policy.getDefaultInstance().plant.toBuilder()
                .setPlanting(Policy.getDefaultInstance().plant.planting.toBuilder().setAutoReplantMode(SelectionMode.SELECTION_MODE_EXCLUDE).addAutoReplantExcludeFlowerIds(23117).putDemandPriority("order.customer", 90))
                .setFriendSteal(Policy.getDefaultInstance().plant.friendSteal.toBuilder().putFriendCounts(123456789012L, 3).addExcludeUids(-5L))
                .setMarket(Policy.getDefaultInstance().plant.market.toBuilder().setBuyMode(MarketBuyMode.MARKET_BUY_MODE_QUALITY).setPutFlowerPassword("密\"码\n")),
        )
        .setOrder(Policy.getDefaultInstance().order.toBuilder().setCustomer(Policy.getDefaultInstance().order.customer.toBuilder().setExactFloralCoin(0)))
        .setUnion(Policy.getDefaultInstance().union.toBuilder().setRace(Policy.getDefaultInstance().union.race.toBuilder().putTaskTypePriority(3036, 5).setAvoidProgressedTasks(false)))
        .setActivity(Policy.getDefaultInstance().activity)
        .build()

    @Test
    fun `round trips a populated policy`() {
        val policy = sample()
        val text = PolicyJson.export(codec, policy)
        assertEquals(policy, PolicyJson.import(codec, text, policy))
    }

    @Test
    fun `exports proto names, implicit defaults and int64 strings`() {
        val text = PolicyJson.export(codec, sample())
        assertTrue(text.startsWith("{\n  \"automation_enabled\": true,\n  \"basic\": {"))
        assertTrue(text.contains("\"server_error_fresh_login_enabled\": true"))
        assertTrue(text.contains("\"redeem_connect_mode\": \"REDEEM_CONNECT_MODE_ONLINE_ONLY\""))
        assertTrue(text.contains("\"max_spend_gold\": \"9007199254740993\""))
        assertTrue(text.contains("\"decision_interval_seconds\": 4.5"))
        // Implicit-presence scalars are emitted even when zero.
        assertTrue(text.contains("\"item_log_enabled\": false"))
        assertTrue(text.contains("\"friend_counts\": {\n"))
        assertTrue(text.contains("\"123456789012\": 3"))
        // Explicit presence: set-to-zero optional is kept, unset optional is omitted.
        assertTrue(text.contains("\"exact_floral_coin\": \"0\""))
        assertTrue(text.contains("\"avoid_progressed_tasks\": false"))
        assertTrue(text.contains("\"put_flower_password\": \"密\\\"码\\n\""))
        val defaults = PolicyJson.export(codec, Policy.getDefaultInstance())
        assertFalse(defaults.contains("exact_floral_coin"))
        assertFalse(defaults.contains("\"basic\""))
        assertTrue(defaults.contains("\"schema_version\": 0"))
    }

    @Test
    fun `imports the server protojson document`() {
        val text = javaClass.classLoader!!.getResource("default-policy.go.json")!!.readText()
        val current = Policy.newBuilder().setSchemaVersion(1).setAutomationEnabled(true).build()
        val imported = PolicyJson.import(codec, text, current)
        assertTrue(imported.automationEnabled)
        assertEquals(80, imported.basic.reputation.threshold)
        assertEquals(300.0, imported.basic.reconnectIntervalSeconds, 0.0)
        assertTrue(imported.union.race.hasAvoidProgressedTasks())
        assertFalse(imported.order.customer.hasExactFloralCoin())
    }

    @Test
    fun `accepts json names, enum numbers and numeric int64`() {
        val current = Policy.newBuilder().setSchemaVersion(1).build()
        val text = """{"automationEnabled": true, "schemaVersion": 1, "basic": {"redeemConnectMode": 2, "shop": {"cultivate_shop": {"max_spend_gold": 12}}},
            "plant": {}, "order": {"customer": {"exactFloralCoin": "7"}}, "union": {}, "activity": {}, "decisionIntervalSeconds": null}"""
        val imported = PolicyJson.import(codec, text, current)
        assertFalse("automation stays with the current account", imported.automationEnabled)
        assertEquals(RedeemConnectMode.REDEEM_CONNECT_MODE_ONLINE_ONLY, imported.basic.redeemConnectMode)
        assertEquals(12L, imported.basic.shop.cultivateShop.maxSpendGold)
        assertEquals(7L, imported.order.customer.exactFloralCoin)
    }

    @Test
    fun `rejects unknown fields, bad types, version and missing sections`() {
        val current = Policy.newBuilder().setSchemaVersion(1).build()
        val full = PolicyJson.export(codec, sample())
        fun rejects(text: String, message: String) {
            val error = assertThrows(ProtoJsonException::class.java) { PolicyJson.import(codec, text, current) }
            assertTrue("${error.message} should mention $message", error.message!!.contains(message))
        }
        rejects(full.replaceFirst("\"automation_enabled\"", "\"automation_enabledx\""), "未知字段 Policy.automation_enabledx")
        rejects(full.replace("\"server_error_fresh_login_enabled\": true", "\"server_error_fresh_login_enabled\": 1"), "必须是布尔值")
        rejects(full.replace("\"schema_version\": 1", "\"schema_version\": 2"), "配置版本")
        rejects("""{"schema_version": 1, "basic": {}, "plant": {}, "order": {}, "union": {}}""", "缺少 activity")
        rejects("""{"schema_version": 1, "basic": {"reputation": {"threshold": 1.5}}}""", "必须是整数")
        rejects("""{"schema_version": 1, "basic": {"redeem_connect_mode": "NOPE"}}""", "枚举值 NOPE")
        rejects("""{"schema_version": 1} trailing""", "多余内容")
        rejects("[]", "顶层必须是对象")
    }
}
