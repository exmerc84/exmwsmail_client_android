package com.exmworkspace.exmwsmail.data.mail

import com.exmworkspace.exmwsmail.data.remote.dto.ContactDto
import org.junit.Assert.assertEquals
import org.junit.Test

class ContactDedupTest {

    /** The real pair: the same mailbox imported once bare and once inside a From header. */
    @Test
    fun the_same_address_written_two_ways_collapses_to_one_row() {
        val bare = ContactDto(id = 1, email = "generalthmex@outlook.com")
        val header = ContactDto(
            id = 2,
            email = "Administración General <generalthmex@outlook.com>",
        )

        val out = dedupeContactsByAddress(listOf(bare, header))

        assertEquals(1, out.size)
        // The named one survives: it is the record that actually says who this is.
        assertEquals("Administración General", out.single().displayLabel)
    }

    @Test
    fun case_differences_in_the_address_still_match() {
        val a = ContactDto(id = 1, email = "Juana.Garza@FrioServicio.com.mx")
        val b = ContactDto(id = 2, email = "juana.garza@frioservicio.com.mx", displayName = "Juana Garza")
        assertEquals(1, dedupeContactsByAddress(listOf(a, b)).size)
    }

    @Test
    fun a_favourite_wins_over_a_plain_duplicate() {
        val plain = ContactDto(id = 1, email = "x@y.com", displayName = "Ana")
        val favourite = ContactDto(id = 2, email = "x@y.com", displayName = "Ana", isFavorite = true)
        assertEquals(2L, dedupeContactsByAddress(listOf(plain, favourite)).single().id)
    }

    @Test
    fun a_hand_created_record_wins_over_an_imported_one() {
        val imported = ContactDto(id = 1, email = "x@y.com", displayName = "Ana", source = "imported")
        val manual = ContactDto(id = 2, email = "x@y.com", displayName = "Ana", source = "manual")
        assertEquals(2L, dedupeContactsByAddress(listOf(imported, manual)).single().id)
    }

    @Test
    fun the_record_with_more_details_wins_when_all_else_is_equal() {
        val thin = ContactDto(id = 1, email = "x@y.com", displayName = "Ana")
        val full = ContactDto(
            id = 2,
            email = "x@y.com",
            displayName = "Ana",
            company = "Thermomex",
            phone = "555",
        )
        assertEquals(2L, dedupeContactsByAddress(listOf(thin, full)).single().id)
    }

    /** Otherwise the surviving row would change with the server's ordering. */
    @Test
    fun the_result_does_not_depend_on_the_order_they_arrive_in() {
        val a = ContactDto(id = 7, email = "x@y.com", displayName = "Ana")
        val b = ContactDto(id = 3, email = "x@y.com", displayName = "Ana")
        assertEquals(3L, dedupeContactsByAddress(listOf(a, b)).single().id)
        assertEquals(3L, dedupeContactsByAddress(listOf(b, a)).single().id)
    }

    @Test
    fun different_people_are_never_merged() {
        val out = dedupeContactsByAddress(
            listOf(
                ContactDto(id = 1, email = "a@y.com"),
                ContactDto(id = 2, email = "b@y.com"),
            )
        )
        assertEquals(2, out.size)
    }

    /** Records with nothing to match on must not collapse into a single row. */
    @Test
    fun contacts_without_a_usable_address_are_all_kept() {
        val out = dedupeContactsByAddress(
            listOf(
                ContactDto(id = 1, email = ""),
                ContactDto(id = 2, email = null),
            )
        )
        assertEquals(2, out.size)
    }
}
