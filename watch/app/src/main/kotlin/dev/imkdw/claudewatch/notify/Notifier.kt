package dev.imkdw.claudewatch.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.imkdw.claudewatch.R

class Notifier(private val context: Context) : AlertSink {

    override fun post(alerts: List<Alert>) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        ensureChannel()
        val manager = NotificationManagerCompat.from(context)
        for (alert in alerts) {
            val (title, text) = when (alert) {
                is Alert.Threshold -> "${alert.account} 세션 ${alert.pct}%" to "${alert.threshold}%를 넘었습니다"
                is Alert.Reset -> "${alert.account} 세션 리셋" to "다시 쓸 수 있습니다"
            }
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_usage)
                .setContentTitle(title)
                .setContentText(text)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setAutoCancel(true)
                .build()
            manager.notify(notificationId(alert), notification)
        }
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "사용량 알림", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "세션 80%, 95% 도달과 리셋"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 200, 100, 200)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun notificationId(alert: Alert): Int {
        val kind = if (alert is Alert.Reset) 1 else 0
        return alert.account.hashCode() * 2 + kind
    }

    companion object {
        const val CHANNEL_ID = "usage_alerts"
    }
}
