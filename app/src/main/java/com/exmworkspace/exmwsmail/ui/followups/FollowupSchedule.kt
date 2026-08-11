package com.exmworkspace.exmwsmail.ui.followups

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** A ready-made "remind me" moment, as offered in the sheet. */
enum class FollowupWhen { THIS_EVENING, TOMORROW, NEXT_WEEK }

/**
 * Turns a quick choice into the instant the reminder is due.
 *
 * The boundaries are deliberate: "this evening" is 18:00 today, but after 18:00 that moment
 * has passed, so it rolls to tomorrow morning rather than firing immediately. Everything is
 * computed from an injected [now] so the rules are testable instead of clock-dependent.
 */
fun followupDueDate(choice: FollowupWhen, now: Date): Date {
    val cal = Calendar.getInstance().apply { time = now }
    return when (choice) {
        FollowupWhen.THIS_EVENING -> {
            val evening = cal.atTime(EVENING_HOUR)
            if (evening.after(now)) evening else cal.plusDays(1).atTime(MORNING_HOUR)
        }

        FollowupWhen.TOMORROW -> cal.plusDays(1).atTime(MORNING_HOUR)

        FollowupWhen.NEXT_WEEK -> {
            // The coming Monday; from a Monday it means the next one, not today.
            val target = cal.plusDays(1)
            while (target.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) target.add(Calendar.DAY_OF_YEAR, 1)
            target.atTime(MORNING_HOUR)
        }
    }
}

/** `due_at` travels as UTC ISO 8601, which is what the backend echoes back (§4.19). */
fun formatDueAt(date: Date): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    fmt.timeZone = TimeZone.getTimeZone("UTC")
    return fmt.format(date)
}

private fun Calendar.plusDays(days: Int): Calendar =
    (clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, days) }

private fun Calendar.atTime(hour: Int): Date = (clone() as Calendar).apply {
    set(Calendar.HOUR_OF_DAY, hour)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.time

private const val EVENING_HOUR = 18
private const val MORNING_HOUR = 9
