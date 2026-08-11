package com.exmworkspace.exmwsmail.data.mail

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Parses the timestamps the API returns.
 *
 * The doc says ISO 8601 UTC everywhere (§0), but `EmailMessage.date` is the message's raw
 * RFC 2822 `Date:` header passed straight through — `"Fri, 31 Jul 2026 00:02:08 +0800"` —
 * so both shapes have to be handled. Backend-generated fields (`answered_at`,
 * `forwarded_at`, `last_seen_at`) really are ISO.
 *
 * `java.time` is off the table at minSdk 24 without desugaring, and `SimpleDateFormat` reads
 * `SSSSSS` as a millisecond count — so `…:39.476639Z` would land 476 seconds late.
 * Fractional seconds are therefore truncated to milliseconds before parsing.
 *
 * Pure functions (no Android types) so they can be unit-tested directly.
 */
object MailDates {

    private val FRACTION = Regex("""\.(\d+)""")

    /** RFC 2822 allows a trailing zone comment: `-0500 (CDT)`. */
    private val ZONE_COMMENT = Regex("""\s*\([^)]*\)\s*$""")

    private val WHITESPACE = Regex("""\s+""")

    private val ISO_PATTERNS = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSS",
        "yyyy-MM-dd'T'HH:mm:ss",
    )

    // Day name and seconds are both optional in the wild, and the zone shows up as a numeric
    // offset or an abbreviation.
    private val RFC_PATTERNS = listOf(
        "EEE, d MMM yyyy HH:mm:ss Z",
        "EEE, d MMM yyyy HH:mm:ss XXX",
        "EEE, d MMM yyyy HH:mm:ss zzz",
        "EEE, d MMM yyyy HH:mm Z",
        "EEE, d MMM yyyy HH:mm zzz",
        "d MMM yyyy HH:mm:ss Z",
        "d MMM yyyy HH:mm:ss XXX",
        "d MMM yyyy HH:mm:ss zzz",
        "d MMM yyyy HH:mm Z",
    )

    /** @return epoch millis, or 0 when [value] is absent or unparseable. */
    fun parse(value: String?): Long {
        val raw = value?.trim().orEmpty()
        if (raw.isEmpty()) return 0L
        val normalized = normalize(raw)
        val patterns = if (normalized.firstOrNull()?.isDigit() == true && normalized.contains('-')) {
            // Looks ISO-ish; try those first but still fall back.
            ISO_PATTERNS + RFC_PATTERNS
        } else {
            RFC_PATTERNS + ISO_PATTERNS
        }
        for (pattern in patterns) {
            val parsed = runCatching { formatter(pattern).parse(normalized)?.time }.getOrNull()
            if (parsed != null) return parsed
        }
        return 0L
    }

    /** Same as [parse] but keeps "absent" distinct from "epoch". */
    fun parseOrNull(value: String?): Long? = parse(value).takeIf { it != 0L }

    /** Formats for the API, which always expects UTC. */
    fun format(epochMillis: Long): String =
        formatter("yyyy-MM-dd'T'HH:mm:ss'Z'").format(Date(epochMillis))

    internal fun normalize(raw: String): String {
        var out = ZONE_COMMENT.replace(raw, "")
        out = FRACTION.replace(out) { match ->
            "." + match.groupValues[1].take(3).padEnd(3, '0')
        }
        return WHITESPACE.replace(out, " ").trim()
    }

    private fun formatter(pattern: String) =
        SimpleDateFormat(pattern, Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
            isLenient = false
        }
}
