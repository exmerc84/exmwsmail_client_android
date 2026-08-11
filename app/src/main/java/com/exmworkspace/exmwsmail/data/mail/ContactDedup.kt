package com.exmworkspace.exmwsmail.data.mail

import com.exmworkspace.exmwsmail.data.remote.dto.ContactDto

/**
 * Collapses contacts that are the same person to one row per address.
 *
 * The import mines the mailbox, so the same address arrives once per shape it was written in —
 * `Administración General <generalthmex@outlook.com>` and the bare address end up as two
 * records. Matching is on the parsed address, lower-cased, because that is what actually
 * identifies a person; the raw field differs precisely in the noise being removed.
 *
 * The survivor is the most useful record, not the first one seen: a real name beats a repeated
 * address, a favourite beats a plain one, hand-created beats imported, and more filled-in
 * fields beat fewer. Ties fall back to the id so the result never depends on server order.
 *
 * Nothing is deleted — this is a display concern; the duplicates stay on the server.
 */
fun dedupeContactsByAddress(contacts: List<ContactDto>): List<ContactDto> {
    val best = LinkedHashMap<String, ContactDto>()
    val unkeyed = mutableListOf<ContactDto>()

    for (contact in contacts) {
        val key = contact.cleanEmail.lowercase().takeIf { it.isNotBlank() }
        if (key == null) {
            // No usable address: nothing to match on, so it stays as its own row.
            unkeyed += contact
            continue
        }
        val current = best[key]
        if (current == null || contact.richerThan(current)) best[key] = contact
    }

    return best.values + unkeyed
}

private fun ContactDto.richerThan(other: ContactDto): Boolean {
    val mine = score()
    val theirs = other.score()
    if (mine != theirs) return mine > theirs
    // Same usefulness: the older record wins, so repeated runs settle on the same row.
    val myId = id ?: Long.MAX_VALUE
    val theirId = other.id ?: Long.MAX_VALUE
    return myId < theirId
}

private fun ContactDto.score(): Int {
    var score = 0
    if (displayLabel.isNotBlank() && !displayLabel.equals(cleanEmail, ignoreCase = true)) score += 8
    if (isFavorite) score += 4
    if (!isImported) score += 2
    score += listOf(company, jobTitle, department, phone, mobile, website, notes, groupName)
        .count { !it.isNullOrBlank() }
    return score
}
