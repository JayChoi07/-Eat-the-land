package com.jaychoi.eattheland.feature.settings

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jaychoi.eattheland.feature.settings.ui.notificationsAllowed
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** 12 이하는 알림 권한이 없어도 시스템 설정에서 알림을 끌 수 있다 — 그 상태를 "허용됨" 으로 보이면 안 된다. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [31])
class NotificationsAllowedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = shadowOf(context.getSystemService(NotificationManager::class.java))

    @Test
    fun `12 이하에서 알림을 꺼 두면 허용이 아니다`() {
        manager.setNotificationsEnabled(false)
        assertFalse(context.notificationsAllowed())
    }

    @Test
    fun `12 이하에서 알림이 켜져 있으면 허용이다`() {
        manager.setNotificationsEnabled(true)
        assertTrue(context.notificationsAllowed())
    }
}
