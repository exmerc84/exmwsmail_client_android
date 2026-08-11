package com.exmworkspace.exmwsmail.data.mail

import java.util.Locale

/** A raw address split into the parts worth showing. */
data class ParsedAddress(val name: String?, val address: String)

/**
 * Pulls a person out of an address as it arrives from the mail history.
 *
 * Contacts imported from the mailbox keep the raw `From` header in their `email` field, so the
 * list showed rows like `"analistadatos@thermomex.com.mx" <analistadatos@thermomex.com.mx>` or
 * `gabriela gonzalez flores <analistacompras@idealeaseoriente.com>` — the address twice, or a
 * name buried in punctuation.
 *
 * A display name that is really just the address repeated is dropped rather than shown, and a
 * name that arrived entirely in lower case is capitalised: senders type their own names, and
 * `gabriela gonzalez flores` is the same person as `Gabriela Gonzalez Flores`. Only the
 * presentation changes — nothing is written back to the server.
 *
 * Pure function so the parsing is unit-tested rather than eyeballed against the address book.
 */
fun parseAddress(raw: String?): ParsedAddress {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return ParsedAddress(null, "")

    val open = value.lastIndexOf('<')
    val close = value.lastIndexOf('>')
    if (open < 0 || close < open) {
        // No angle brackets: the whole thing is the address, possibly quoted.
        return ParsedAddress(null, value.unquote())
    }

    val address = value.substring(open + 1, close).trim()
    val name = value.substring(0, open).trim().unquote()

    val useful = name.isNotEmpty() && !name.equals(address, ignoreCase = true)
    return ParsedAddress(
        name = if (useful) name.humanize() else null,
        address = address.ifEmpty { value.unquote() },
    )
}

/** The label for a row: the person's name when there is one, otherwise the bare address. */
fun cleanContactLabel(displayName: String?, rawEmail: String?): String {
    val parsed = parseAddress(rawEmail)
    val explicit = displayName?.trim()?.unquote()?.takeIf {
        it.isNotEmpty() && !it.equals(parsed.address, ignoreCase = true)
    }
    return explicit?.humanize() ?: parsed.name ?: parsed.address
}

/** The bare address, for sending and for the row's second line. */
fun cleanContactEmail(rawEmail: String?): String = parseAddress(rawEmail).address

private fun String.unquote(): String {
    var out = trim()
    while (out.length >= 2 && out.first() == '"' && out.last() == '"') {
        out = out.substring(1, out.length - 1).trim()
    }
    return out
}

/** Title-cases a name that arrived all in lower case; anything else is left as written. */
private fun String.humanize(): String {
    if (any { it.isUpperCase() }) return this
    return split(' ')
        .joinToString(" ") { word ->
            word.replaceFirstChar { it.titlecase(Locale.getDefault()) }
        }
}
