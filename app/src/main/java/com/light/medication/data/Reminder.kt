package com.light.medication.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "reminders")
@Serializable
data class Reminder(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val medicationName: String,
    val pillCount: String,
    val hour: Int,
    val minute: Int,
    val isEnabled: Boolean = true,
    val frequency: String = "Daily",
    val lastTakenTimestamp: Long? = null,
    val lastSkippedTimestamp: Long? = null
)
