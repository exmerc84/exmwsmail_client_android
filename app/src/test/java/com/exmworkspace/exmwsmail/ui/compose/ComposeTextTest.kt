package com.exmworkspace.exmwsmail.ui.compose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComposeTextTest {

    @Test
    fun addPrefix_adds_when_missing() {
        assertEquals("Re: Hola", addPrefixIfMissing("Hola", "Re:"))
        assertEquals("Fwd: Reporte", addPrefixIfMissing("  Reporte  ", "Fwd:"))
    }

    @Test
    fun addPrefix_is_idempotent_and_case_insensitive() {
        assertEquals("Re: Hola", addPrefixIfMissing("Re: Hola", "Re:"))
        assertEquals("re: Hola", addPrefixIfMissing("re: Hola", "Re:"))
    }

    @Test
    fun parseFirstAddress_extracts_angle_bracketed_email() {
        assertEquals("a@b.com", parseFirstAddress("Juan Perez <a@b.com>"))
    }

    @Test
    fun parseFirstAddress_takes_first_of_a_list() {
        assertEquals("a@b.com", parseFirstAddress("a@b.com, c@d.com"))
        assertEquals("a@b.com", parseFirstAddress("a@b.com; c@d.com"))
    }

    @Test
    fun parseFirstAddress_returns_null_for_blank() {
        assertNull(parseFirstAddress(""))
        assertNull(parseFirstAddress("   "))
    }
}
