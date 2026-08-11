package com.exmworkspace.exmwsmail.ui.attachments

import com.exmworkspace.exmwsmail.data.remote.dto.AttachmentBrowseDto
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import com.exmworkspace.exmwsmail.ui.mail.DisplayLocale
import java.util.Locale

/** A date heading plus the attachments that fall under it. */
data class AttachmentGroup(val label: String, val items: List<AttachmentBrowseDto>)

/**
 * Splits the browser's flat list into dated sections.
 *
 * The endpoint answers newest first, so the groups only need to preserve that order rather
 * than sort again. Today and yesterday get their own heading — that is how people look for
 * "the file I was just sent" — and everything older collapses to its month, which keeps a
 * mailbox spanning years from turning into hundreds of one-row sections.
 *
 * @param now injected so the boundaries are testable instead of depending on the clock.
 */
fun groupAttachmentsByDate(
    items: List<AttachmentBrowseDto>,
    now: Date,
    // Defaults to the app-wide display locale so "agosto 2026" cannot sit next to "Ayer"
    // in English on a phone set to another language; still injectable for the unit tests.
    locale: Locale = DisplayLocale,
    todayLabel: String,
    yesterdayLabel: String,
    undatedLabel: String,
): List<AttachmentGroup> {
    val monthFormat = SimpleDateFormat("LLLL yyyy", locale)
    val startOfToday = now.startOfDay()
    val startOfYesterday = Calendar.getInstance(locale).apply {
        time = startOfToday
        add(Calendar.DAY_OF_YEAR, -1)
    }.time

    return items
        .map { it to parseIsoDate(it.dateParsed) }
        .groupBy { (_, date) ->
            when {
                date == null -> undatedLabel
                !date.before(startOfToday) -> todayLabel
                !date.before(startOfYesterday) -> yesterdayLabel
                else -> monthFormat.format(date).replaceFirstChar { it.titlecase(locale) }
            }
        }
        .map { (label, entries) -> AttachmentGroup(label, entries.map { it.first }) }
}

private fun Date.startOfDay(): Date = Calendar.getInstance().apply {
    time = this@startOfDay
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.time

/**
 * `date_parsed` is ISO 8601 with an offset (`2026-08-08T21:02:29+00:00`). minSdk 24 rules out
 * java.time, and SimpleDateFormat's `Z` does not accept the colon in the offset, so it is
 * removed before parsing.
 */
internal fun parseIsoDate(value: String?): Date? {
    val raw = value?.trim().orEmpty()
    if (raw.isEmpty()) return null
    val normalized = raw
        .replace("Z", "+0000")
        .replace(Regex("([+-]\\d{2}):(\\d{2})$"), "$1$2")
    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ssZ",
        "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
        "yyyy-MM-dd'T'HH:mm:ss",
    )
    for (pattern in patterns) {
        runCatching {
            return SimpleDateFormat(pattern, Locale.US).parse(normalized)
        }
    }
    return null
}
