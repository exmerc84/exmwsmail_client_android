package com.exmworkspace.exmwsmail.data.mail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class CalendarInviteTest {

    /**
     * Assembled line by line rather than as one raw string: interpolating a multi-line body
     * defeats `trimIndent`, and the leftover leading spaces would be read as RFC 5545 line
     * folding — the harness would be testing the wrong input.
     */
    private fun ics(body: String) = buildString {
        append("BEGIN:VCALENDAR\r\n")
        append("VERSION:2.0\r\n")
        append("METHOD:REQUEST\r\n")
        append("BEGIN:VEVENT\r\n")
        body.trimIndent().lines().forEach { append(it).append("\r\n") }
        append("END:VEVENT\r\n")
        append("END:VCALENDAR\r\n")
    }

    @Test
    fun a_request_with_everything_parses() {
        val invite = parseCalendarInvite(
            ics(
                """
                UID:evento-123@dominio.com
                SUMMARY:Junta de proyecto
                LOCATION:Sala 2
                ORGANIZER;CN=Nombre Apellido:mailto:org@externo.com
                DTSTART:20260815T160000Z
                DTEND:20260815T170000Z
                SEQUENCE:3
                """.trimIndent()
            )
        )
        assertNotNull(invite)
        assertEquals("evento-123@dominio.com", invite!!.icalUid)
        assertEquals("Junta de proyecto", invite.summary)
        assertEquals("org@externo.com", invite.organizerEmail)
        assertEquals("Nombre Apellido", invite.organizerName)
        assertEquals("2026-08-15T16:00:00Z", invite.startAt)
        assertEquals("2026-08-15T17:00:00Z", invite.endAt)
        assertEquals(3, invite.sequence)
        assertEquals("Sala 2", invite.location)
        assertFalse(invite.allDay)
    }

    /**
     * Only REQUEST is an invitation to answer. A REPLY is someone answering *ours*, and a
     * CANCEL is not an RSVP — offering the buttons there would send unasked-for iTIP mail.
     */
    @Test
    fun only_method_request_counts_as_an_invitation() {
        val body = """
            UID:x@y
            SUMMARY:Algo
            ORGANIZER:mailto:org@externo.com
            DTSTART:20260815T160000Z
        """.trimIndent()
        assertNotNull(parseCalendarInvite(ics(body)))
        assertNull(parseCalendarInvite(ics(body).replace("METHOD:REQUEST", "METHOD:REPLY")))
        assertNull(parseCalendarInvite(ics(body).replace("METHOD:REQUEST", "METHOD:CANCEL")))
        assertNull(parseCalendarInvite(ics(body).replace("METHOD:REQUEST\r\n", "")))
    }

    /** Without a UID or an organizer the reply has nowhere to go. */
    @Test
    fun a_request_missing_its_addressing_is_not_answerable() {
        assertNull(
            parseCalendarInvite(ics("SUMMARY:Sin uid\r\nORGANIZER:mailto:org@externo.com"))
        )
        assertNull(parseCalendarInvite(ics("UID:x@y\r\nSUMMARY:Sin organizador")))
    }

    /** RFC 5545 folds long lines; a UID split across two lines must come back whole. */
    @Test
    fun folded_lines_are_rejoined() {
        val raw = "BEGIN:VCALENDAR\r\nMETHOD:REQUEST\r\nBEGIN:VEVENT\r\n" +
            "UID:muy-largo-identificador-de-even\r\n to@dominio.com\r\n" +
            "SUMMARY:Junta\r\nORGANIZER:mailto:org@externo.com\r\n" +
            "END:VEVENT\r\nEND:VCALENDAR"
        assertEquals(
            "muy-largo-identificador-de-evento@dominio.com",
            parseCalendarInvite(raw)?.icalUid,
        )
    }

    @Test
    fun escaped_text_is_unescaped() {
        val invite = parseCalendarInvite(
            ics(
                """
                UID:x@y
                SUMMARY:Junta\, revisión\; y cierre\nSegunda línea
                ORGANIZER:mailto:org@externo.com
                """.trimIndent()
            )
        )
        assertEquals("Junta, revisión; y cierre\nSegunda línea", invite?.summary)
    }

    /** A zoned start converts to UTC using the TZID the property carries. */
    @Test
    fun a_zoned_start_converts_to_utc() {
        val invite = parseCalendarInvite(
            ics(
                """
                UID:x@y
                SUMMARY:Junta
                ORGANIZER:mailto:org@externo.com
                DTSTART;TZID=America/Mexico_City:20260815T100000
                """.trimIndent()
            )
        )
        // Mexico City is UTC-6 in August 2026 (no DST since 2022).
        assertEquals("2026-08-15T16:00:00Z", invite?.startAt)
    }

    @Test
    fun an_all_day_event_is_flagged() {
        val invite = parseCalendarInvite(
            ics(
                """
                UID:x@y
                SUMMARY:Feriado
                ORGANIZER:mailto:org@externo.com
                DTSTART;VALUE=DATE:20261225
                """.trimIndent()
            )
        )
        assertTrue(invite!!.allDay)
        assertNotNull(invite.startAt)
    }

    /** A floating time (no TZID, no Z) reads as the device's zone, like a human would. */
    @Test
    fun a_floating_time_uses_the_device_zone() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Mexico_City"))
            val invite = parseCalendarInvite(
                ics(
                    """
                    UID:x@y
                    SUMMARY:Junta
                    ORGANIZER:mailto:org@externo.com
                    DTSTART:20260815T100000
                    """.trimIndent()
                )
            )
            assertEquals("2026-08-15T16:00:00Z", invite?.startAt)
        } finally {
            TimeZone.setDefault(original)
        }
    }

    /** A VTODO alongside the event must not overwrite the event's own fields. */
    @Test
    fun properties_outside_the_vevent_are_ignored() {
        val raw = "BEGIN:VCALENDAR\r\nMETHOD:REQUEST\r\n" +
            "BEGIN:VTODO\r\nUID:tarea@y\r\nSUMMARY:Tarea\r\nEND:VTODO\r\n" +
            "BEGIN:VEVENT\r\nUID:evento@y\r\nSUMMARY:Junta\r\n" +
            "ORGANIZER:mailto:org@externo.com\r\nEND:VEVENT\r\nEND:VCALENDAR"
        val invite = parseCalendarInvite(raw)
        assertEquals("evento@y", invite?.icalUid)
        assertEquals("Junta", invite?.summary)
    }

    @Test
    fun a_malformed_or_empty_payload_yields_null() {
        assertNull(parseCalendarInvite(""))
        assertNull(parseCalendarInvite("no soy un ics"))
    }

    @Test
    fun the_calendar_attachment_is_recognised_by_mime_or_extension() {
        assertTrue(isCalendarAttachment("text/calendar", "invite.ics"))
        assertTrue(isCalendarAttachment("text/calendar; method=REQUEST; charset=UTF-8", null))
        assertTrue(isCalendarAttachment("application/octet-stream", "invite.ICS"))
        assertFalse(isCalendarAttachment("application/pdf", "factura.pdf"))
        assertFalse(isCalendarAttachment(null, null))
    }

    /** Parameters can be quoted and come in any order. */
    @Test
    fun property_parameters_parse_regardless_of_order_or_quoting() {
        assertEquals("America/Mexico_City", icsParam("TZID=\"America/Mexico_City\"", "TZID"))
        assertEquals("DATE", icsParam("VALUE=DATE;TZID=UTC", "value"))
        assertNull(icsParam("VALUE=DATE", "TZID"))
    }

    @Test
    fun the_summary_may_be_absent_without_losing_the_invitation() {
        val invite = parseCalendarInvite(
            ics("UID:x@y\r\nORGANIZER:mailto:org@externo.com")
        )
        assertEquals("", invite?.summary)
        assertNotNull(invite)
    }

    /** Locale must not leak into the ISO output (Arabic digits, Thai calendar…). */
    @Test
    fun the_iso_output_is_locale_independent() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG"))
            assertEquals("2026-08-15T16:00:00Z", icsDateToIso("20260815T160000Z", null))
        } finally {
            Locale.setDefault(original)
        }
    }
}
