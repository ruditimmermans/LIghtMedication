package com.light.medication

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.light.medication.data.Reminder
import java.util.Calendar

class ReminderScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleReminder(reminder: Reminder, forceNext: Boolean = false) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                // If we can't schedule exact alarms, we might want to fall back to inexact
                // or just wait for the user to grant permission.
                // For now, we'll try to schedule anyway, which might fail or be inexact.
            }
        }

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("REMINDER_ID", reminder.id)
            putExtra("MEDICATION_NAME", reminder.medicationName)
            putExtra("PILL_COUNT", reminder.pillCount)
            putExtra("HOUR", reminder.hour)
            putExtra("MINUTE", reminder.minute)
            putExtra("FREQUENCY", reminder.frequency)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, reminder.hour)
            set(Calendar.MINUTE, reminder.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            
            // Ensure the alarm is set in the future
            if (timeInMillis <= System.currentTimeMillis() || forceNext) {
                when (reminder.frequency) {
                    "Daily" -> add(Calendar.DAY_OF_YEAR, 1)
                    "Weekly" -> add(Calendar.WEEK_OF_YEAR, 1)
                    "Monthly" -> add(Calendar.MONTH, 1)
                    else -> add(Calendar.DAY_OF_YEAR, 1)
                }
            }
        }

        val alarmClockInfo = AlarmManager.AlarmClockInfo(
            calendar.timeInMillis,
            pendingIntent
        )
        alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
    }

    fun cancelReminder(reminder: Reminder) {
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}
