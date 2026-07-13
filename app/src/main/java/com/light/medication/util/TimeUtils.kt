package com.light.medication.util

import android.content.Context
import java.util.Calendar

object TimeUtils {
    fun formatTime(hour: Int, minute: Int): String {
        return String.format(java.util.Locale.getDefault(), "%02d:%02d", hour, minute)
    }

    fun isTakenToday(timestamp: Long?): Boolean {
        if (timestamp == null) return false
        val cal1 = Calendar.getInstance()
        val cal2 = Calendar.getInstance().apply { timeInMillis = timestamp }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }
}
