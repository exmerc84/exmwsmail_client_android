package com.exmworkspace.exmwsmail.data.mail

/**
 * True when a message went to more than one address, counting To and Cc together.
 *
 * This is what decides whether "responder a todos" is offered: on a one-to-one mail it would
 * do exactly what plain reply does, so showing it costs a slot and teaches nothing.
 *
 * The fields arrive as the comma-separated strings the API stores, and senders leave trailing
 * separators and blank entries in them, so counting commas is not enough — the parts have to
 * be trimmed and the empty ones dropped.
 *
 * Pure, so the rule the UI hangs on is pinned by tests rather than by trying mail on a device.
 */
fun hasMultipleRecipients(to: String?, cc: String?): Boolean =
    recipientCount(to) + recipientCount(cc) > 1

private fun recipientCount(field: String?): Int =
    field.orEmpty()
        .split(',', ';')
        .count { it.isNotBlank() }
