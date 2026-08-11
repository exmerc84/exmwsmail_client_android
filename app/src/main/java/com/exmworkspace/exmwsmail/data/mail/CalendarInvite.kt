package com.exmworkspace.exmwsmail.data.mail

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * A meeting request parsed out of a `text/calendar` attachment (§4.22). Only what the RSVP
 * needs — the app answers invitations, it does not keep a calendar.
 */
data class CalendarInvite(
    val icalUid: String,
    val summary: String,
    val organizerEmail: String,
    val organizerName: String? = null,
    /** ISO-8601 UTC, the shape `POST /calendar/reply` wants; null when the .ics omits it. */
    val startAt: String? = null,
    val endAt: String? = null,
    val sequence: Int = 0,
    val location: String? = null,
    /** True for an all-day event (`DTSTART;VALUE=DATE`), which has no meaningful time. */
    val allDay: Boolean = false,
)

/**
 * Parses an iCalendar payload, returning the invitation only when it is one to answer.
 *
 * Null unless `METHOD:REQUEST` is present: a `REPLY` (someone answering *our* invitation),
 * a `CANCEL` or a plain published event are not things the user RSVPs to, and showing the
 * Accept/Decline card on them would send iTIP traffic nobody asked for. A UID and an
 * organizer are equally required — without either the reply has nowhere to go.
 *
 * Pure, so every quirk below is pinned by a unit test rather than discovered on a device.
 */
fun parseCalendarInvite(ics: String): CalendarInvite? {
    val lines = unfoldIcsLines(ics)
    var method: String? = null
    var uid: String? = null
    var summary = ""
    var location: String? = null
    var organizerEmail: String? = null
    var organizerName: String? = null
    var start: String? = null
    var end: String? = null
    var sequence = 0
    var allDay = false
    // VEVENT only: a VTODO or VALARM in the same file carries its own UID/SUMMARY and would
    // otherwise overwrite the event's.
    var inEvent = false

    for (line in lines) {
        val colon = line.indexOf(':')
        if (colon <= 0) continue
        val rawName = line.substring(0, colon)
        val value = line.substring(colon + 1).trim()
        val name = rawName.substringBefore(';').trim().uppercase()
        val params = rawName.substringAfter(';', "")

        when {
            name == "BEGIN" && value.equals("VEVENT", true) -> inEvent = true
            name == "END" && value.equals("VEVENT", true) -> inEvent = false
            name == "METHOD" -> method = value.uppercase()
            !inEvent -> Unit
            name == "UID" -> uid = unescapeIcsText(value)
            name == "SUMMARY" -> summary = unescapeIcsText(value)
            name == "LOCATION" -> location = unescapeIcsText(value).takeIf { it.isNotBlank() }
            name == "SEQUENCE" -> sequence = value.toIntOrNull() ?: 0
            name == "ORGANIZER" -> {
                organizerEmail = value.substringAfter("mailto:", value)
                    .trim()
                    .takeIf { it.isNotBlank() && '@' in it }
                organizerName = icsParam(params, "CN")?.let(::unescapeIcsText)
            }
            name == "DTSTART" -> {
                allDay = icsParam(params, "VALUE").equals("DATE", true) || value.length == 8
                start = icsDateToIso(value, icsParam(params, "TZID"))
            }
            name == "DTEND" -> end = icsDateToIso(value, icsParam(params, "TZID"))
        }
    }

    if (method != "REQUEST") return null
    val id = uid?.takeIf { it.isNotBlank() } ?: return null
    val organizer = organizerEmail ?: return null
    return CalendarInvite(
        icalUid = id,
        summary = summary,
        organizerEmail = organizer,
        organizerName = organizerName?.takeIf { it.isNotBlank() },
        startAt = start,
        endAt = end,
        sequence = sequence,
        location = location,
        allDay = allDay,
    )
}

/**
 * Joins RFC 5545 folded lines: a CRLF followed by a space or tab continues the previous
 * line. Senders fold aggressively, so a UID or SUMMARY of any length arrives split.
 */
internal fun unfoldIcsLines(ics: String): List<String> {
    val out = mutableListOf<StringBuilder>()
    ics.split("\r\n", "\n", "\r").forEach { raw ->
        if (raw.startsWith(" ") || raw.startsWith("\t")) {
            // A continuation before any content line is malformed; drop it rather than crash.
            out.lastOrNull()?.append(raw.substring(1))
        } else {
            out.add(StringBuilder(raw))
        }
    }
    return out.map { it.toString() }.filter { it.isNotBlank() }
}

/** Value of one property parameter, e.g. `TZID` in `DTSTART;TZID=America/Mexico_City`. */
internal fun icsParam(params: String, key: String): String? = params
    .split(';')
    .firstOrNull { it.substringBefore('=').trim().equals(key, ignoreCase = true) }
    ?.substringAfter('=', "")
    ?.trim()
    ?.trim('"')
    ?.takeIf { it.isNotBlank() }

/** RFC 5545 text escaping: `\n` newline, and literal `\, \; \\`. */
internal fun unescapeIcsText(value: String): String = buildString {
    var i = 0
    while (i < value.length) {
        val c = value[i]
        if (c == '\\' && i + 1 < value.length) {
            when (val next = value[i + 1]) {
                'n', 'N' -> append('\n')
                else -> append(next)
            }
            i += 2
        } else {
            append(c)
            i++
        }
    }
}

/**
 * Converts an iCalendar date-time to the ISO-8601 UTC string the reply endpoint expects.
 *
 * Three shapes arrive: `20260815T160000Z` (already UTC), `20260815T160000` with a `TZID`
 * parameter, and `20260815` for all-day. A floating time with no TZID is read as the
 * device's zone, which is what a human reading the invitation would assume.
 *
 * `java.time` is off the table at minSdk 24, so this goes through SimpleDateFormat — and
 * `TimeZone.getTimeZone` resolves the IANA ids that TZID carries.
 */
internal fun icsDateToIso(value: String, tzid: String?): String? {
    val raw = value.trim()
    val zone = when {
        raw.endsWith("Z") -> TimeZone.getTimeZone("UTC")
        tzid != null -> TimeZone.getTimeZone(tzid)
        else -> TimeZone.getDefault()
    }
    val pattern = when {
        raw.endsWith("Z") -> "yyyyMMdd'T'HHmmss'Z'"
        raw.length == 15 -> "yyyyMMdd'T'HHmmss"
        raw.length == 8 -> "yyyyMMdd"
        else -> return null
    }
    val parsed = runCatching {
        SimpleDateFormat(pattern, Locale.US).apply { timeZone = zone }.parse(raw)
    }.getOrNull() ?: return null
    return isoUtc(parsed)
}

private fun isoUtc(date: Date): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(date)

/** The three answers iTIP allows, in the wording `POST /calendar/reply` expects (§4.22). */
enum class InviteReply(val apiValue: String) {
    ACCEPTED("accepted"),
    TENTATIVE("tentative"),
    DECLINED("declined"),
}

/** True for the attachment that carries a meeting request. */
fun isCalendarAttachment(mimeType: String?, filename: String?): Boolean =
    mimeType.orEmpty().substringBefore(';').trim().equals("text/calendar", ignoreCase = true) ||
        filename.orEmpty().endsWith(".ics", ignoreCase = true)
