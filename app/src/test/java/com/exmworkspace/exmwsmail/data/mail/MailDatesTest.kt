package com.exmworkspace.exmwsmail.data.mail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MailDatesTest {

    // ---- RFC 2822: what EmailMessage.date actually carries ----
    // Samples captured from live /api/emails/messages responses.

    @Test
    fun parses_rfc2822_with_numeric_offset() {
        // Fri, 31 Jul 2026 00:02:08 +0800 == 30 Jul 2026 16:02:08 UTC
        assertEquals(
            MailDates.parse("2026-07-30T16:02:08Z"),
            MailDates.parse("Fri, 31 Jul 2026 00:02:08 +0800"),
        )
    }

    @Test
    fun parses_rfc2822_with_trailing_zone_comment() {
        // "-0500 (CDT)" — the parenthetical is a comment and must be ignored.
        assertEquals(
            MailDates.parse("2026-08-07T15:00:54Z"),
            MailDates.parse("Fri, 7 Aug 2026 10:00:54 -0500 (CDT)"),
        )
    }

    @Test
    fun parses_rfc2822_with_padded_and_unpadded_day() {
        assertEquals(
            MailDates.parse("Fri, 7 Aug 2026 10:10:56 +0000"),
            MailDates.parse("Fri, 07 Aug 2026 10:10:56 +0000"),
        )
    }

    @Test
    fun parses_rfc2822_without_day_name() {
        assertEquals(
            MailDates.parse("Thu, 6 Aug 2026 22:15:59 +0000"),
            MailDates.parse("6 Aug 2026 22:15:59 +0000"),
        )
    }

    @Test
    fun parses_rfc2822_with_named_zone() {
        assertEquals(
            MailDates.parse("Thu, 6 Aug 2026 22:15:59 +0000"),
            MailDates.parse("Thu, 6 Aug 2026 22:15:59 GMT"),
        )
    }

    @Test
    fun parses_rfc2822_without_seconds() {
        assertEquals(
            MailDates.parse("Thu, 6 Aug 2026 22:15:00 +0000"),
            MailDates.parse("Thu, 6 Aug 2026 22:15 +0000"),
        )
    }

    // ---- ISO 8601: answered_at / forwarded_at / last_seen_at ----

    @Test
    fun parses_plain_utc_timestamp() {
        assertEquals(1778927400000L, MailDates.parse("2026-05-16T10:30:00Z"))
    }

    @Test
    fun truncates_microseconds_instead_of_reading_them_as_millis() {
        // .476639 must contribute 476 ms, not 476639 ms (≈ 8 minutes of drift).
        val withMicros = MailDates.parse("2026-05-16T10:30:00.476639Z")
        assertEquals(MailDates.parse("2026-05-16T10:30:00.476Z"), withMicros)
        assertEquals(1778927400476L, withMicros)
    }

    @Test
    fun pads_short_fractions() {
        assertEquals(
            MailDates.parse("2026-05-16T10:30:00.500Z"),
            MailDates.parse("2026-05-16T10:30:00.5Z"),
        )
    }

    @Test
    fun honours_explicit_iso_offsets() {
        assertEquals(
            MailDates.parse("2026-05-16T10:30:00Z"),
            MailDates.parse("2026-05-16T12:30:00+02:00"),
        )
    }

    @Test
    fun accepts_timestamps_without_a_zone() {
        assertEquals(1778927400000L, MailDates.parse("2026-05-16T10:30:00"))
    }

    // ---- Failure handling ----

    @Test
    fun unparseable_input_is_zero() {
        assertEquals(0L, MailDates.parse(null))
        assertEquals(0L, MailDates.parse(""))
        assertEquals(0L, MailDates.parse("ayer por la tarde"))
    }

    @Test
    fun parseOrNull_distinguishes_absent_from_epoch() {
        assertNull(MailDates.parseOrNull(null))
        assertNull(MailDates.parseOrNull("nope"))
        assertEquals(1778927400000L, MailDates.parseOrNull("2026-05-16T10:30:00Z"))
    }

    @Test
    fun every_real_sample_parses() {
        val samples = listOf(
            "Fri, 31 Jul 2026 00:02:08 +0800",
            "Fri, 7 Aug 2026 10:00:54 -0500 (CDT)",
            "Fri, 07 Aug 2026 14:36:05 +0200",
            "Fri, 07 Aug 2026 10:10:56 +0000",
            "Thu, 06 Aug 2026 22:15:59 +0000",
        )
        samples.forEach { sample ->
            assertTrue("no se pudo parsear: $sample", MailDates.parse(sample) > 0L)
        }
    }
}
