package com.exmworkspace.exmwsmail.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountHeaderTest {

    /** §4.23 grants `X-Account-Id` to the mail endpoints. */
    @Test
    fun mail_endpoints_take_the_account_header() {
        assertTrue(wantsAccountHeader("/api/emails/messages"))
        assertTrue(wantsAccountHeader("/api/emails/folders"))
        assertTrue(wantsAccountHeader("/api/emails/messages/438/source"))
        assertTrue(wantsAccountHeader("/api/emails/attachments/browse"))
    }

    /**
     * Everything else stays on the primary user: the doc scopes the header to "endpoints de
     * correo", and stamping it wider would silently change what those endpoints answer.
     */
    @Test
    fun non_mail_endpoints_never_take_it() {
        assertFalse(wantsAccountHeader("/api/auth/login"))
        assertFalse(wantsAccountHeader("/api/auth/refresh"))
        assertFalse(wantsAccountHeader("/api/contacts/"))
        assertFalse(wantsAccountHeader("/api/followups"))
        assertFalse(wantsAccountHeader("/api/devices/register"))
        assertFalse(wantsAccountHeader("/api/accounts/"))
    }

    /** A hypothetical `/api/emailsomething` must not match by prefix accident. */
    @Test
    fun the_match_is_on_the_path_segment_not_the_prefix() {
        assertFalse(wantsAccountHeader("/api/emailsync"))
    }
}
