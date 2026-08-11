package com.exmworkspace.exmwsmail.data.mail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Shapes taken from the real imported address book on this account. */
class AddressNamesTest {

    @Test
    fun a_quoted_address_repeated_as_its_own_name_yields_just_the_address() {
        val raw = "\"analistadatos@thermomex.com.mx\" <analistadatos@thermomex.com.mx>"
        val parsed = parseAddress(raw)
        assertNull(parsed.name)
        assertEquals("analistadatos@thermomex.com.mx", parsed.address)
        assertEquals("analistadatos@thermomex.com.mx", cleanContactLabel(null, raw))
    }

    @Test
    fun a_real_name_in_front_of_the_address_is_kept_and_capitalised() {
        val raw = "gabriela gonzalez flores <analistacompras@idealeaseoriente.com>"
        assertEquals("Gabriela Gonzalez Flores", cleanContactLabel(null, raw))
        assertEquals("analistacompras@idealeaseoriente.com", cleanContactEmail(raw))
    }

    /** A name the sender already capitalised must survive untouched. */
    @Test
    fun an_already_capitalised_name_is_left_exactly_as_written() {
        val raw = "Jose Arturo Malpica VIVEROS <aux@idealeaseoriente.com>"
        assertEquals("Jose Arturo Malpica VIVEROS", cleanContactLabel(null, raw))
    }

    @Test
    fun a_bare_address_is_returned_as_is() {
        assertEquals("juana.garza@frioservicio.com.mx", cleanContactLabel(null, "juana.garza@frioservicio.com.mx"))
        assertEquals("juana.garza@frioservicio.com.mx", cleanContactEmail("juana.garza@frioservicio.com.mx"))
    }

    @Test
    fun an_explicit_display_name_wins_over_the_header() {
        val label = cleanContactLabel("Juana Garza", "juana.garza@frioservicio.com.mx <juana.garza@frioservicio.com.mx>")
        assertEquals("Juana Garza", label)
    }

    /** The backend sometimes stores the address as the display name too. */
    @Test
    fun a_display_name_that_only_repeats_the_address_is_ignored() {
        val raw = "enoc hernandez <ehernandez@frioservicio.com.mx>"
        assertEquals("Enoc Hernandez", cleanContactLabel("ehernandez@frioservicio.com.mx", raw))
    }

    @Test
    fun a_header_folded_across_lines_still_parses() {
        val raw = "\"juancarlos.fuentes@autolineasjireh.com\"\r\n <juancarlos.fuentes@autolineasjireh.com>"
        assertEquals("juancarlos.fuentes@autolineasjireh.com", cleanContactEmail(raw))
    }

    @Test
    fun blank_input_does_not_crash() {
        assertEquals("", cleanContactEmail(null))
        assertEquals("", cleanContactLabel(null, ""))
    }
}
