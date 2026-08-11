package com.exmworkspace.exmwsmail.data.mail

import com.exmworkspace.exmwsmail.data.local.entity.MessageBodyEntity

/**
 * The message as plain text, which is what the AI endpoints take (§4.21 is explicit that the
 * model receives text, never markup).
 *
 * The text part is preferred when the server sent one; otherwise the HTML is reduced to its
 * text nodes. Scripts and styles are dropped whole — their contents are not prose and would
 * be a large chunk of nonsense in the prompt — and block-level tags become line breaks so
 * paragraphs do not run together into one sentence.
 */
fun MessageBodyEntity.plainText(): String =
    text?.takeIf { it.isNotBlank() } ?: htmlToPlainText(html.orEmpty())

fun htmlToPlainText(html: String): String {
    if (html.isBlank()) return ""
    var out = html
    out = Regex("(?is)<(script|style)[^>]*>.*?</\\1>").replace(out, " ")
    out = Regex("(?i)<br\\s*/?>").replace(out, "\n")
    out = Regex("(?i)</(p|div|tr|li|h[1-6]|table)\\s*>").replace(out, "\n")
    out = Regex("<[^>]+>").replace(out, "")
    out = unescapeEntities(out)
    // Collapse the runs of blank lines that stripping the markup leaves behind.
    out = Regex("[ \\t\\x0B\\f\\r]+").replace(out, " ")
    out = Regex("\\n{3,}").replace(out, "\n\n")
    return out.lines().joinToString("\n") { it.trim() }.trim()
}

private fun unescapeEntities(value: String): String {
    var out = value
    NAMED.forEach { (entity, replacement) -> out = out.replace(entity, replacement) }
    out = Regex("&#(\\d+);").replace(out) { match ->
        match.groupValues[1].toIntOrNull()?.takeIf { it in 1..0x10FFFF }
            ?.let { String(Character.toChars(it)) } ?: match.value
    }
    out = Regex("(?i)&#x([0-9a-f]+);").replace(out) { match ->
        match.groupValues[1].toIntOrNull(16)?.takeIf { it in 1..0x10FFFF }
            ?.let { String(Character.toChars(it)) } ?: match.value
    }
    return out
}

private val NAMED = listOf(
    "&nbsp;" to " ",
    "&amp;" to "&",
    "&lt;" to "<",
    "&gt;" to ">",
    "&quot;" to "\"",
    "&#39;" to "'",
    "&apos;" to "'",
)
