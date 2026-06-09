package com.exmworkspace.exmwsmail.ui.mail

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun formatMessageDate(date: Date?, now: Date = Date()): String {
    if (date == null) return ""
    val nowCal = Calendar.getInstance().apply { time = now }
    val msgCal = Calendar.getInstance().apply { time = date }
    val sameYear = nowCal.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR)
    val sameDay = sameYear &&
        nowCal.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR)
    val locale = Locale.getDefault()
    val pattern = when {
        sameDay -> "HH:mm"
        sameYear -> "d MMM"
        else -> "d MMM yyyy"
    }
    return SimpleDateFormat(pattern, locale).format(date)
}
