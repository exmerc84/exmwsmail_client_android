package com.exmworkspace.exmwsmail.data.mail

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipientsTest {

    @Test
    fun a_one_to_one_message_has_no_one_else_to_reply_to() {
        assertFalse(hasMultipleRecipients("ana@exmerc.com", null))
        assertFalse(hasMultipleRecipients("ana@exmerc.com", ""))
    }

    @Test
    fun two_in_to_counts() {
        assertTrue(hasMultipleRecipients("ana@exmerc.com, zoe@exmerc.com", null))
    }

    @Test
    fun one_in_to_plus_one_in_cc_counts() {
        assertTrue(hasMultipleRecipients("ana@exmerc.com", "zoe@exmerc.com"))
    }

    /** Senders leave trailing separators; a stray comma is not a second person. */
    @Test
    fun trailing_and_repeated_separators_do_not_invent_recipients() {
        assertFalse(hasMultipleRecipients("ana@exmerc.com,", null))
        assertFalse(hasMultipleRecipients("ana@exmerc.com, ,", " "))
        assertFalse(hasMultipleRecipients(" ; ana@exmerc.com ", null))
    }

    /** Some mailers separate with semicolons. */
    @Test
    fun semicolons_separate_too() {
        assertTrue(hasMultipleRecipients("ana@exmerc.com; zoe@exmerc.com", null))
    }

    @Test
    fun a_message_with_no_recipients_at_all_is_not_multiple() {
        assertFalse(hasMultipleRecipients(null, null))
        assertFalse(hasMultipleRecipients("", ""))
    }

    /** Display names carry commas of their own — "Apellido, Nombre <a@b>". */
    @Test
    fun a_quoted_display_name_is_a_known_limit_not_a_crash() {
        // Counted as two, so reply-all is offered where plain reply would have done. Erring
        // towards offering it is the harmless direction: the composer still fills To with the
        // sender and the user sees exactly who is on Cc before sending.
        assertTrue(hasMultipleRecipients("\"Torres, Carlos\" <c@exmerc.com>", null))
    }
}
