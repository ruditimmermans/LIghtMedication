package com.light.medication

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import com.light.medication.data.AppDatabase
import com.light.medication.data.Reminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == Intent.ACTION_TIME_CHANGED ||
            intent.action == Intent.ACTION_TIMEZONE_CHANGED) {
            rescheduleAll(context)
            return
        }

        val id = intent.getIntExtra("REMINDER_ID", -1)
        if (id == -1) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val reminders = db.reminderDao().getAllReminders().first()
                val reminder = reminders.find { it.id == id }
                
                if (reminder != null && reminder.isEnabled) {
                    if (shouldShowNotification(reminder)) {
                        showNotification(context, reminder.medicationName, reminder.pillCount, reminder.id)
                    }
                    
                    // Always reschedule for the next occurrence
                    val scheduler = ReminderScheduler(context)
                    scheduler.scheduleReminder(reminder)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun shouldShowNotification(reminder: Reminder): Boolean {
        val lastAction = maxOf(reminder.lastTakenTimestamp ?: 0L, reminder.lastSkippedTimestamp ?: 0L)
        val now = Calendar.getInstance()
        
        // If never taken/skipped, check if the scheduled time for today has passed
        if (lastAction == 0L) {
            val scheduledToday = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, reminder.hour)
                set(Calendar.MINUTE, reminder.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            // Only show if we are within a reasonable window (e.g., 1 hour) of the scheduled time
            // or if it's the very first time scheduling.
            return now.timeInMillis >= scheduledToday.timeInMillis
        }

        val lastActionCal = Calendar.getInstance().apply { timeInMillis = lastAction }

        return when (reminder.frequency) {
            "Daily" -> {
                lastActionCal.get(Calendar.YEAR) != now.get(Calendar.YEAR) ||
                lastActionCal.get(Calendar.DAY_OF_YEAR) != now.get(Calendar.DAY_OF_YEAR)
            }
            "Weekly" -> {
                // Check if it's been at least 6 days since the last action
                now.timeInMillis - lastAction > 6 * 24 * 60 * 60 * 1000L
            }
            "Monthly" -> {
                lastActionCal.get(Calendar.YEAR) != now.get(Calendar.YEAR) ||
                lastActionCal.get(Calendar.MONTH) != now.get(Calendar.MONTH)
            }
            else -> true
        }
    }

    private fun rescheduleAll(context: Context) {
        val db = AppDatabase.getDatabase(context)
        val scheduler = ReminderScheduler(context)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reminders = db.reminderDao().getAllReminders().first()
                reminders.filter { it.isEnabled }.forEach { reminder ->
                    scheduler.scheduleReminder(reminder)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showNotification(context: Context, medicationName: String, pillCount: String, id: Int) {
        val channelId = "medication_reminder_channel"
        val notificationId = if (id != -1) id else 1

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                context.getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_description)
                enableVibration(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val activityIntent = Intent(context, ActionReceiver::class.java).apply {
            action = "ACTION_TAKEN"
            putExtra("REMINDER_ID", notificationId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissedIntent = Intent(context, ActionReceiver::class.java).apply {
            action = "ACTION_DISMISSED"
            putExtra("REMINDER_ID", notificationId)
        }
        val dismissedPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 1000,
            dismissedIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Use drawable, not mipmap for small icon
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(context.getString(R.string.notification_content, pillCount, medicationName))
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(context.getString(R.string.notification_content, pillCount, medicationName)))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setDeleteIntent(dismissedPendingIntent)
            .setAutoCancel(true)
            .addAction(
                R.drawable.ic_launcher_foreground,
                context.getString(R.string.mark_as_taken_button),
                pendingIntent
            )
            .build()

        notificationManager.notify(notificationId, notification)
    }
}
