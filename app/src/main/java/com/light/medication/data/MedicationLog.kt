package com.light.medication.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "medication_logs")
@Serializable
data class MedicationLog(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val reminderId: Int,
    val medicationName: String,
    val timestamp: Long,
    val action: String // "Taken" or "Skipped"
)
