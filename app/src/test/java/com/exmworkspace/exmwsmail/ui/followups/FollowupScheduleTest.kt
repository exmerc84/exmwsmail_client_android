package com.exmworkspace.exmwsmail.ui.followups

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowupScheduleTest {

    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    private fun at(value: String) = fmt.parse(value)!!

    private fun Calendar.hour() = get(Calendar.HOUR_OF_DAY)

    private fun cal(date: java.util.Date) = Calendar.getInstance().apply { time = date }

    @Test
    fun this_evening_is_six_pm_today_when_the_day_still_has_room() {
        val due = followupDueDate(FollowupWhen.THIS_EVENING, at("2026-08-10 09:30"))
        assertEquals("2026-08-10 18:00", fmt.format(due))
    }

    /** Past 18:00 the evening is gone; firing at once would be useless as a reminder. */
    @Test
    fun after_six_pm_this_evening_rolls_to_tomorrow_morning() {
        val due = followupDueDate(FollowupWhen.THIS_EVENING, at("2026-08-10 21:15"))
        assertEquals("2026-08-11 09:00", fmt.format(due))
    }

    @Test
    fun tomorrow_is_nine_in_the_morning() {
        val due = followupDueDate(FollowupWhen.TOMORROW, at("2026-08-10 21:15"))
        assertEquals("2026-08-11 09:00", fmt.format(due))
    }

    @Test
    fun next_week_lands_on_the_coming_monday() {
        // 2026-08-12 is a Wednesday.
        val due = followupDueDate(FollowupWhen.NEXT_WEEK, at("2026-08-12 10:00"))
        assertEquals(Calendar.MONDAY, cal(due).get(Calendar.DAY_OF_WEEK))
        assertEquals(9, cal(due).hour())
        assertTrue(due.after(at("2026-08-12 10:00")))
    }

    /** From a Monday it must mean the next one, not the day it already is. */
    @Test
    fun next_week_from_a_monday_skips_to_the_following_monday() {
        val monday = at("2026-08-10 10:00")
        assertEquals(Calendar.MONDAY, cal(monday).get(Calendar.DAY_OF_WEEK))
        val due = followupDueDate(FollowupWhen.NEXT_WEEK, monday)
        assertEquals("2026-08-17 09:00", fmt.format(due))
    }

    @Test
    fun the_due_date_is_serialised_as_utc_iso() {
        val formatted = formatDueAt(at("2026-08-10 18:00"))
        assertTrue(formatted, formatted.matches(Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z""")))
    }
}
