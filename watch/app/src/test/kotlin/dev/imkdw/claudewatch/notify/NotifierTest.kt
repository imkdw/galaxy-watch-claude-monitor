package dev.imkdw.claudewatch.notify

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class NotifierTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(NotificationManager::class.java)

    @Test
    fun `진동 채널을 만들고 알림을 올린다`() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Notifier(context).post(listOf(Alert.Threshold("personal", 80, 82), Alert.Reset("work")))

        val channel = manager.getNotificationChannel(Notifier.CHANNEL_ID)
        assertThat(channel.importance).isEqualTo(NotificationManager.IMPORTANCE_HIGH)
        assertThat(channel.shouldVibrate()).isTrue()

        val titles = shadowOf(manager).allNotifications.map { it.extras.getString("android.title") }
        assertThat(titles).containsExactly("personal 세션 82%", "work 세션 리셋")
    }

    @Test
    fun `같은 계정의 같은 종류 알림은 덮어쓴다`() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val notifier = Notifier(context)
        notifier.post(listOf(Alert.Threshold("personal", 80, 82)))
        notifier.post(listOf(Alert.Threshold("personal", 95, 96)))
        assertThat(shadowOf(manager).allNotifications).hasSize(1)
    }

    @Test
    fun `알림 권한이 없으면 올리지 않는다`() {
        shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Notifier(context).post(listOf(Alert.Threshold("personal", 80, 82)))
        assertThat(shadowOf(manager).allNotifications).isEmpty()
    }
}
