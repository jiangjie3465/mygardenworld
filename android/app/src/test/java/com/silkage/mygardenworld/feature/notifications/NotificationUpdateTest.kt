package com.silkage.mygardenworld.feature.notifications

import com.mygardenworld.v1.NotificationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class NotificationUpdateTest {
    private val custom = NotificationProvider.NOTIFICATION_PROVIDER_CUSTOM
    private val dingtalk = NotificationProvider.NOTIFICATION_PROVIDER_DINGTALK

    @Test
    fun `blank endpoint and secret keep saved values`() {
        val update = notificationUpdate(NotificationDraft(enabled = true, provider = dingtalk, cooldown = "30"), savedProvider = dingtalk)
        assertNull(update.endpoint)
        assertNull(update.signingSecret)
        assertEquals(30, update.cooldownMinutes)
    }

    @Test
    fun `clearing the endpoint disables and clears the secret`() {
        val update = notificationUpdate(NotificationDraft(enabled = true, provider = dingtalk, clearEndpoint = true, signingSecret = "s"), savedProvider = dingtalk)
        assertFalse(update.enabled)
        assertEquals("", update.endpoint)
        assertEquals("", update.signingSecret)
    }

    @Test
    fun `providers without signing always clear the secret`() {
        val update = notificationUpdate(NotificationDraft(provider = custom, endpoint = " https://a.example/hook ", signingSecret = "x"), savedProvider = custom)
        assertEquals("https://a.example/hook", update.endpoint)
        assertEquals("", update.signingSecret)
    }

    @Test
    fun `rejects bad cooldown and provider switch without endpoint`() {
        assertThrows(IllegalArgumentException::class.java) { notificationUpdate(NotificationDraft(cooldown = "0"), custom) }
        assertThrows(IllegalArgumentException::class.java) { notificationUpdate(NotificationDraft(cooldown = "1441"), custom) }
        assertThrows(IllegalArgumentException::class.java) { notificationUpdate(NotificationDraft(provider = dingtalk), custom) }
    }
}
