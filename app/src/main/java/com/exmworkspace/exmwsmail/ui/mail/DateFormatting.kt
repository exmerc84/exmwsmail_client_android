package com.exmworkspace.exmwsmail.ui.mail

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Every user-visible date renders with this locale, whatever language the phone is set to.
 * The app's copy is hardcoded Spanish, so device-locale dates produced "Ayer" and
 * "August 2026" in the same screen on an English phone. If the strings are ever localized,
 * this is the one constant to swap for a per-language value.
 */
val DisplayLocale: Locale = Locale("es", "MX")

fun formatMessageDate(date: Date?, now: Date = Date()): String {
    if (date == null) return ""
    val nowCal = Calendar.getInstance().apply { time = now }
    val msgCal = Calendar.getInstance().apply { time = date }
    val sameYear = nowCal.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR)
    val sameDay = sameYear &&
        nowCal.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR)
    val pattern = when {
        sameDay -> "HH:mm"
        sameYear -> "d MMM"
        else -> "d MMM yyyy"
    }
    return SimpleDateFormat(pattern, DisplayLocale).format(date)
}
