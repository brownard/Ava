package com.example.ava.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import androidx.core.app.NotificationCompat
import com.example.ava.R

private const val VOICE_SATELLITE_SERVICE_CHANNEL_ID = "VoiceSatelliteService"

fun createVoiceSatelliteServiceNotificationChannel(context: Context) {
    val channelName = "Voice Satellite Background Service"
    val chan = NotificationChannel(
        VOICE_SATELLITE_SERVICE_CHANNEL_ID,
        channelName,
        NotificationManager.IMPORTANCE_NONE
    )
    chan.lightColor = Color.BLUE
    chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.createNotificationChannel(chan)
}

fun createVoiceSatelliteServiceNotification(context: Context, content: String): Notification {
    val notificationBuilder =
        NotificationCompat.Builder(context, VOICE_SATELLITE_SERVICE_CHANNEL_ID)
    val notification = notificationBuilder.setOngoing(true)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(content)
        .setPriority(NotificationManager.IMPORTANCE_LOW)
        .setCategory(Notification.CATEGORY_SERVICE)
        .build()
    return notification
}