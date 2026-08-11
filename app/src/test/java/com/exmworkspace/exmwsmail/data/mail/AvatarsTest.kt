package com.exmworkspace.exmwsmail.data.mail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarsTest {

    @Test
    fun the_initial_comes_from_the_display_name() {
        assertEquals("C", avatarInitial("Carlos Wong de SYSCOM", "carlos@syscom.mx"))
    }

    /** Senders decorate names with emoji and punctuation; the initial must skip past them. */
    @Test
    fun emoji_and_symbols_are_not_initials() {
        assertEquals("O", avatarInitial("🚀 Ofertas", "promo@x.com"))
        assertEquals("S", avatarInitial("\"SYSCOM\"", "erp@syscom.mx"))
    }

    @Test
    fun a_useless_name_falls_back_to_the_address() {
        assertEquals("E", avatarInitial("...", "erp@syscom.mx"))
        assertEquals("E", avatarInitial(null, "erp@syscom.mx"))
    }

    @Test
    fun nothing_usable_yields_a_question_mark() {
        assertEquals("?", avatarInitial(null, null))
        assertEquals("?", avatarInitial("★", "@"))
    }

    @Test
    fun accented_and_numeric_initials_work() {
        assertEquals("Á", avatarInitial("ángel", null))
        assertEquals("1", avatarInitial("1Password", null))
    }

    /** The point of the colour: the same sender lands on the same slot forever. */
    @Test
    fun the_colour_slot_is_stable_and_case_insensitive() {
        val a = avatarColorIndex("ERP@Syscom.mx", 8)
        val b = avatarColorIndex("erp@syscom.mx", 8)
        assertEquals(a, b)
        assertEquals(a, avatarColorIndex(" erp@syscom.mx ", 8))
    }

    @Test
    fun every_key_lands_inside_the_palette() {
        listOf("a", "zz@x.com", "ñandú", "", "una-clave-bastante-larga@dominio.mx").forEach {
            val i = avatarColorIndex(it, 8)
            assertTrue("index $i out of range for '$it'", i in 0..7)
        }
    }

    /** Negative hashCodes must not produce negative indices (%, not floorMod, was the trap). */
    @Test
    fun negative_hashes_still_map_into_the_palette() {
        // "b" * 31 accumulations easily go negative for long strings; brute-check a batch.
        (0..50).forEach { n ->
            val i = avatarColorIndex("x".repeat(n) + "@negativo.mx", 8)
            assertTrue(i in 0..7)
        }
    }
}
