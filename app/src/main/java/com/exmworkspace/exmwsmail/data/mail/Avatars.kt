package com.exmworkspace.exmwsmail.data.mail

/**
 * The letter shown inside a sender/contact avatar.
 *
 * The display name wins over the address, but senders love decorating names with emoji,
 * quotes and brackets ("🚀 Ofertas", "\"SYSCOM\""), so the initial is the first *letter or
 * digit* rather than the first character. When nothing usable remains the address's local
 * part is tried the same way, and "?" is the last resort so the circle never renders empty.
 */
fun avatarInitial(name: String?, address: String?): String {
    val fromName = name?.firstOrNull { it.isLetterOrDigit() }
    val fromAddress = address?.firstOrNull { it.isLetterOrDigit() }
    return (fromName ?: fromAddress)?.uppercaseChar()?.toString() ?: "?"
}

/**
 * Stable palette slot for a sender: same key, same colour, every launch — that stability is
 * what lets the eye learn "SYSCOM is the teal one". Keyed on the address (lowercased: mail
 * addresses are case-insensitive) because display names vary between campaigns while the
 * address does not. `String.hashCode` is specified by the JVM, so the mapping survives
 * restarts and app updates.
 */
fun avatarColorIndex(key: String, paletteSize: Int): Int {
    if (paletteSize <= 0) return 0
    val h = key.trim().lowercase().hashCode()
    return ((h % paletteSize) + paletteSize) % paletteSize
}
