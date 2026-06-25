package com.example.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RoutineNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val routineId = intent.getIntExtra("routine_id", 0)
        val title = intent.getStringExtra("routine_title") ?: "Routine Reminder"
        val description = intent.getStringExtra("routine_description") ?: "Time for your scheduled routine!"
        val repeatDays = intent.getStringExtra("routine_repeat_days") ?: "Mon,Tue,Wed,Thu,Fri,Sat,Sun"

        // Check if the current weekday is in the repeat days list
        val todayDay = SimpleDateFormat("E", Locale.US).format(Date())
        if (!repeatDays.contains(todayDay, ignoreCase = true)) {
            // Not scheduled for today, skip notification
            return
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "routine_reminders"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Routine Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                this.description = "Notifications for daily routine reminders"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            routineId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm) // System alarm icon as reliable fallback
            .setContentTitle(title)
            .setContentText(description)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(routineId, notification)
    }
}
