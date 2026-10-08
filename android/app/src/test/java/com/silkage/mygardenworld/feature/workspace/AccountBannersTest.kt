package com.silkage.mygardenworld.feature.workspace

import com.mygardenworld.v1.Policy
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountBannersTest {
    private val restricted = listOf("账号请求已暂停（服务端 5000），09/28 10:17:32 后验证恢复")

    private fun policy(automation: Boolean, freshLogin: Boolean): Policy = Policy.newBuilder()
        .setAutomationEnabled(automation)
        .setBasic(Policy.getDefaultInstance().basic.toBuilder().setServerErrorFreshLoginEnabled(freshLogin))
        .build()

    @Test
    fun `no hint without a 5000 issue`() {
        assertNull(restrictionHint(listOf("会话已失效"), policy(automation = false, freshLogin = false)))
        assertNull(restrictionHint(emptyList(), null))
    }

    @Test
    fun `manual start recovers when the fresh login switch is off`() {
        for (automation in listOf(true, false)) {
            val hint = restrictionHint(restricted, policy(automation = automation, freshLogin = false))!!
            assertTrue(hint, hint.contains("手动启动账号") && hint.contains("开启「5000 异常后允许重新登录」"))
            assertTrue(hint, !hint.contains("不会解除保护"))
        }
    }

    @Test
    fun `explains waiting when recovery is already authorized or policy unknown`() {
        assertTrue(restrictionHint(restricted, policy(automation = true, freshLogin = true))!!.contains("等待冷却结束"))
        assertTrue(restrictionHint(restricted, policy(automation = false, freshLogin = true))!!.contains("启用自动化"))
        assertTrue(restrictionHint(restricted, null)!!.contains("手动启动"))
    }
}
