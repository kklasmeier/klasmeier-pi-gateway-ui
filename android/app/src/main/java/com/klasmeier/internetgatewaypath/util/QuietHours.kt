package com.klasmeier.internetgatewaypath.util

object QuietHours {
    fun isQuietNow(
        enabled: Boolean,
        startMinutes: Int,
        endMinutes: Int,
        hourOfDay: Int,
        minuteOfHour: Int,
    ): Boolean {
        if (!enabled) return false
        val minutes = hourOfDay * 60 + minuteOfHour
        return if (startMinutes == endMinutes) {
            false
        } else if (startMinutes < endMinutes) {
            minutes in startMinutes until endMinutes
        } else {
            minutes >= startMinutes || minutes < endMinutes
        }
    }

    fun isQuietNow(
        enabled: Boolean,
        startMinutes: Int,
        endMinutes: Int,
    ): Boolean {
        val cal = java.util.Calendar.getInstance()
        return isQuietNow(enabled, startMinutes, endMinutes, cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
    }

    fun formatMinutes(minutes: Int): String {
        val hour24 = (minutes / 60) % 24
        val minute = minutes % 60
        val hour12 = when {
            hour24 == 0 -> 12
            hour24 > 12 -> hour24 - 12
            else -> hour24
        }
        val amPm = if (hour24 < 12) "AM" else "PM"
        return String.format("%d:%02d %s", hour12, minute, amPm)
    }

    fun hourOptions(): List<Int> = (0..23).toList()
    fun minuteStepOptions(): List<Int> = listOf(0, 15, 30, 45)
}
