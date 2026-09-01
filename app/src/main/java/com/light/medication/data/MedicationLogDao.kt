package com.light.medication.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationLogDao {
    @Query("SELECT * FROM medication_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<MedicationLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: MedicationLog)

    @Delete
    suspend fun delete(log: MedicationLog)

    @Query("DELETE FROM medication_logs")
    suspend fun deleteAll()
}
