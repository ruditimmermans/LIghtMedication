package com.light.medication

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.light.medication.data.AppDatabase
import com.light.medication.data.MedicationLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getIntExtra("REMINDER_ID", -1)
        if (reminderId == -1) return

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(reminderId)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val reminders = db.reminderDao().getAllReminders().first()
                val reminder = reminders.find { it.id == reminderId }
                
                if (reminder != null) {
                    val timestamp = System.currentTimeMillis()
                    val action = if (intent.action == "ACTION_DISMISSED") "Skipped" else "Taken"
                    
                    val updatedReminder = if (action == "Skipped") {
                        reminder.copy(lastSkippedTimestamp = timestamp)
                    } else {
                        reminder.copy(lastTakenTimestamp = timestamp)
                    }
                    
                    db.reminderDao().update(updatedReminder)
                    
                    db.medicationLogDao().insert(MedicationLog(
                        reminderId = reminder.id,
                        medicationName = reminder.medicationName,
                        timestamp = timestamp,
                        action = action
                    ))
                    
                    // Reschedule for the next occurrence
                    val scheduler = ReminderScheduler(context)
                    scheduler.scheduleReminder(updatedReminder, forceNext = true)
                }

                // In Android 15, starting an activity from a background receiver is restricted.
                // We've moved the main activity launch to the Notification's contentIntent.
                // The "Mark as Taken" action now just updates the database and closes the notification.
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
