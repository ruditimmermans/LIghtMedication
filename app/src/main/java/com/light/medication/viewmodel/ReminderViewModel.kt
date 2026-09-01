package com.light.medication.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.light.medication.ReminderScheduler
import com.light.medication.data.AppDatabase
import com.light.medication.data.MedicationLog
import com.light.medication.data.Reminder
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ReminderViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val reminderDao = db.reminderDao()
    private val logDao = db.medicationLogDao()
    private val scheduler = ReminderScheduler(application)

    val allReminders: StateFlow<List<Reminder>> = reminderDao.getAllReminders()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allLogs: StateFlow<List<MedicationLog>> = logDao.getAllLogs()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addReminder(medicationName: String, pillCount: String, hour: Int, minute: Int, frequency: String) {
        viewModelScope.launch {
            val reminder = Reminder(
                medicationName = medicationName,
                pillCount = pillCount,
                hour = hour,
                minute = minute,
                frequency = frequency
            )
            val id = reminderDao.insert(reminder)
            // Schedule the alarm using the ID to avoid collisions
            scheduler.scheduleReminder(reminder.copy(id = id.toInt()))
        }
    }

    fun deleteReminder(reminder: Reminder) {
        viewModelScope.launch {
            reminderDao.delete(reminder)
            scheduler.cancelReminder(reminder)
        }
    }
    
    fun toggleReminder(reminder: Reminder) {
        viewModelScope.launch {
            val updated = reminder.copy(isEnabled = !reminder.isEnabled)
            reminderDao.update(updated)
            if (updated.isEnabled) {
                scheduler.scheduleReminder(updated)
            } else {
                scheduler.cancelReminder(updated)
            }
        }
    }

    fun updateReminder(reminder: Reminder, medicationName: String, pillCount: String, hour: Int, minute: Int, frequency: String) {
        viewModelScope.launch {
            val updated = reminder.copy(
                medicationName = medicationName,
                pillCount = pillCount,
                hour = hour,
                minute = minute,
                frequency = frequency
            )
            reminderDao.update(updated)
            if (updated.isEnabled) {
                scheduler.scheduleReminder(updated)
            }
        }
    }

    fun markAsTaken(reminder: Reminder) {
        viewModelScope.launch {
            val timestamp = System.currentTimeMillis()
            val updated = reminder.copy(lastTakenTimestamp = timestamp)
            reminderDao.update(updated)
            
            logDao.insert(MedicationLog(
                reminderId = reminder.id,
                medicationName = reminder.medicationName,
                timestamp = timestamp,
                action = "Taken"
            ))
            
            // Cancel notification if it's currently showing
            val notificationManager = getApplication<Application>().getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.cancel(reminder.id)

            // Reschedule for the next period, skipping today if it hasn't happened yet
            if (updated.isEnabled) {
                scheduler.scheduleReminder(updated, forceNext = true)
            }
        }
    }

    fun markAsSkipped(reminder: Reminder) {
        viewModelScope.launch {
            val timestamp = System.currentTimeMillis()
            val updated = reminder.copy(lastSkippedTimestamp = timestamp)
            reminderDao.update(updated)

            logDao.insert(MedicationLog(
                reminderId = reminder.id,
                medicationName = reminder.medicationName,
                timestamp = timestamp,
                action = "Skipped"
            ))

            // Cancel notification if it's currently showing
            val notificationManager = getApplication<Application>().getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.cancel(reminder.id)

            // Reschedule for the next period, skipping today if it hasn't happened yet
            if (updated.isEnabled) {
                scheduler.scheduleReminder(updated, forceNext = true)
            }
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            logDao.deleteAll()
        }
    }

    fun deleteLog(log: MedicationLog) {
        viewModelScope.launch {
            logDao.delete(log)
        }
    }

    fun exportBackup(uri: Uri, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        viewModelScope.launch {
            try {
                val reminders = allReminders.value
                val jsonData = Json { prettyPrint = true }.encodeToString(reminders)
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
                    it.write(jsonData.toByteArray())
                }
                onSuccess()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    fun restoreBackup(uri: Uri, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        viewModelScope.launch {
            try {
                val inputStream = getApplication<Application>().contentResolver.openInputStream(uri)
                val jsonData = inputStream?.bufferedReader()?.use { it.readText() } ?: throw Exception("Failed to read file")
                val reminders = Json.decodeFromString<List<Reminder>>(jsonData)

                // Clear existing reminders and their alarms
                allReminders.value.forEach { scheduler.cancelReminder(it) }
                reminderDao.deleteAll()

                // Insert new reminders and schedule them
                reminders.forEach {
                    val newId = reminderDao.insert(it.copy(id = 0))
                    val newReminder = it.copy(id = newId.toInt())
                    if (newReminder.isEnabled) {
                        scheduler.scheduleReminder(newReminder)
                    }
                }
                onSuccess()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }
}
