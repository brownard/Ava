package com.example.ava.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import androidx.core.app.NotificationCompat
import com.example.ava.R

fun createVoiceSatelliteServiceNotification(context: Context): Notification {
    val channelId = context.packageName
    val channelName = "Voice Satellite Background Service"
    val chan = NotificationChannel(
        channelId,
        channelName,
        NotificationManager.IMPORTANCE_NONE
    )
    chan.lightColor = Color.BLUE
    chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.createNotificationChannel(chan)
    val notificationBuilder = NotificationCompat.Builder(context, channelId)
    val notification = notificationBuilder.setOngoing(true)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle("Android Voice Assistant is running in the background")
        .setPriority(NotificationManager.IMPORTANCE_LOW)
        .setCategory(Notification.CATEGORY_SERVICE)
        .build()
    return notification
}