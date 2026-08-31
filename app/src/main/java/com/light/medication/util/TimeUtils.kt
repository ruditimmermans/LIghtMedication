package com.light.medication.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimeUtils {
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.getDefault())

    fun formatTime(hour: Int, minute: Int): String {
        return String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
    }

    fun isTakenToday(timestamp: Long?): Boolean {
        if (timestamp == null) return false
        val now = LocalDateTime.now(ZoneId.systemDefault())
        val takenTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())
        return now.toLocalDate() == takenTime.toLocalDate()
    }

    fun formatFullDateTime(timestamp: Long): String {
        val dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())
        return dateTime.format(dateTimeFormatter)
    }
}
