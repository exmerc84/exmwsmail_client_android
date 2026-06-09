package com.exmworkspace.exmwsmail.ui.compose

/**
 * Pure text helpers for composing replies/forwards. Kept free of Android types
 * so they can be unit-tested on the JVM.
 */

/** Adds [prefix] (e.g. "Re:" / "Fwd:") to [subject] unless it is already present. */
fun addPrefixIfMissing(subject: String, prefix: String): String {
    val trimmed = subject.trim()
    return if (trimmed.startsWith(prefix, ignoreCase = true)) trimmed
    else "$prefix $trimmed"
}

/**
 * Extracts a bare email address from a header value.
 * "Name <a@b.com>" → "a@b.com"; "a@b.com, c@d.com" → "a@b.com"; blank → null.
 */
fun parseFirstAddress(raw: String): String? {
    if (raw.isBlank()) return null
    val lt = raw.indexOf('<')
    val gt = raw.indexOf('>')
    return if (lt in 0 until gt) raw.substring(lt + 1, gt).trim()
    else raw.trim().split(',', ';').firstOrNull()?.trim()
}
