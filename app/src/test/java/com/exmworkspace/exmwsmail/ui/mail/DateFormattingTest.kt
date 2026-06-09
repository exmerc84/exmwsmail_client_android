package com.exmworkspace.exmwsmail.ui.mail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

class DateFormattingTest {

    private fun dateOf(year: Int, month: Int, day: Int, hour: Int = 9, minute: Int = 5): Date =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, hour, minute, 0)
        }.time

    @Test
    fun null_date_yields_empty_string() {
        assertEquals("", formatMessageDate(null, now = dateOf(2026, Calendar.JUNE, 8)))
    }

    @Test
    fun same_day_uses_time_only() {
        val now = dateOf(2026, Calendar.JUNE, 8, hour = 18)
        val msg = dateOf(2026, Calendar.JUNE, 8, hour = 9, minute = 5)
        // HH:mm — locale-independent shape.
        assertTrue(
            "expected HH:mm, got '${formatMessageDate(msg, now)}'",
            formatMessageDate(msg, now).matches(Regex("""\d{2}:\d{2}""")),
        )
    }

    @Test
    fun same_year_other_day_is_not_a_clock_time() {
        val now = dateOf(2026, Calendar.JUNE, 8)
        val msg = dateOf(2026, Calendar.MARCH, 3)
        val out = formatMessageDate(msg, now)
        assertTrue("should not be HH:mm: '$out'", !out.matches(Regex("""\d{2}:\d{2}""")))
        assertTrue("should contain day '3': '$out'", out.contains("3"))
        assertTrue("should not contain year: '$out'", !out.contains("2026"))
    }

    @Test
    fun previous_year_includes_year() {
        val now = dateOf(2026, Calendar.JUNE, 8)
        val msg = dateOf(2024, Calendar.DECEMBER, 31)
        assertTrue(formatMessageDate(msg, now).contains("2024"))
    }
}
