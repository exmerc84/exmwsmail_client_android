package com.exmworkspace.exmwsmail.data.mail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncWindowTest {

    @Test
    fun the_bound_is_the_oldest_real_date_on_the_page() {
        assertEquals(100L, pruneWindowStart(listOf(300L, 100L, 200L)))
    }

    /**
     * The real failure: one backend notification with `"date": ""` sat in page 1, so the
     * bound fell to 0 and the prune swept the whole folder on every refresh.
     */
    @Test
    fun a_dateless_message_on_the_page_does_not_drag_the_bound_to_zero() {
        assertEquals(100L, pruneWindowStart(listOf(300L, 0L, 100L, 200L)))
    }

    @Test
    fun negative_dates_are_ignored_too() {
        assertEquals(50L, pruneWindowStart(listOf(-1L, 50L)))
    }

    /** Nothing to trust means nothing to prune — better a stale row than an empty folder. */
    @Test
    fun a_page_with_no_usable_date_prunes_nothing() {
        assertNull(pruneWindowStart(listOf(0L, 0L)))
        assertNull(pruneWindowStart(emptyList()))
    }
}
