package com.example.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.Routine
import com.example.receiver.RoutineNotificationReceiver
import java.util.Calendar

object AlarmSchedulerHelper {
    private const val TAG = "AlarmSchedulerHelper"

    fun scheduleAlarm(context: Context, routine: Routine) {
        val reminderTime = routine.reminderTime ?: return
        val parts = reminderTime.split(":")
        if (parts.size != 2) return

        val hour = parts[0].toIntOrNull() ?: return
        val minute = parts[1].toIntOrNull() ?: return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val intent = Intent(context, RoutineNotificationReceiver::class.java).apply {
            putExtra("routine_id", routine.id)
            putExtra("routine_title", "Time for ${routine.title}!")
            putExtra("routine_description", routine.description.ifEmpty { "Keep up your routine flow!" })
            putExtra("routine_repeat_days", routine.repeatDays)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            routine.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // If time is in the past today, schedule for tomorrow
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        try {
            // Schedule repeating daily alarm
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            )
            Log.d(TAG, "Scheduled alarm for routine ${routine.title} (ID: ${routine.id}) at $reminderTime")
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to schedule alarm: exact alarm permission not granted", e)
        }
    }

    fun cancelAlarm(context: Context, routine: Routine) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val intent = Intent(context, RoutineNotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            routine.id,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Cancelled alarm for routine ${routine.title} (ID: ${routine.id})")
        }
    }
}
