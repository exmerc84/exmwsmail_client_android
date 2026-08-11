package com.exmworkspace.exmwsmail.ui.attachments

import com.exmworkspace.exmwsmail.data.remote.dto.AttachmentBrowseDto
import java.text.SimpleDateFormat
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AttachmentGroupsTest {

    private val clock = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        .parse("2026-08-09 10:00")!!

    private fun attachment(name: String, date: String?) = AttachmentBrowseDto(
        uid = name,
        folder = "INBOX",
        filename = name,
        dateParsed = date,
    )

    @Test
    fun today_and_yesterday_get_their_own_sections_and_older_ones_group_by_month() {
        val groups = groupAttachmentsByDate(
            items = listOf(
                attachment("hoy.pdf", "2026-08-09T08:00:00+00:00"),
                attachment("ayer.pdf", "2026-08-08T23:59:00+00:00"),
                attachment("julio.pdf", "2026-07-04T10:00:00+00:00"),
                attachment("julio2.pdf", "2026-07-01T10:00:00+00:00"),
            ),
            now = clock,
            locale = Locale.US,
            todayLabel = "Hoy",
            yesterdayLabel = "Ayer",
            undatedLabel = "Sin fecha",
        )

        assertEquals(listOf("Hoy", "Ayer", "July 2026"), groups.map { it.label })
        assertEquals(listOf("julio.pdf", "julio2.pdf"), groups[2].items.map { it.filename })
    }

    /** The endpoint answers newest first; grouping must not reshuffle that. */
    @Test
    fun the_servers_order_is_preserved_inside_each_section() {
        val groups = groupAttachmentsByDate(
            items = listOf(
                attachment("a.pdf", "2026-08-09T09:00:00+00:00"),
                attachment("b.pdf", "2026-08-09T07:00:00+00:00"),
            ),
            now = clock,
            locale = Locale.US,
            todayLabel = "Hoy",
            yesterdayLabel = "Ayer",
            undatedLabel = "Sin fecha",
        )

        assertEquals(listOf("a.pdf", "b.pdf"), groups.single().items.map { it.filename })
    }

    @Test
    fun an_attachment_with_no_usable_date_lands_in_its_own_section() {
        val groups = groupAttachmentsByDate(
            items = listOf(attachment("x.pdf", null), attachment("y.pdf", "")),
            now = clock,
            locale = Locale.US,
            todayLabel = "Hoy",
            yesterdayLabel = "Ayer",
            undatedLabel = "Sin fecha",
        )

        assertEquals(listOf("Sin fecha"), groups.map { it.label })
        assertEquals(2, groups.single().items.size)
    }

    /** The offset carries a colon, which SimpleDateFormat's Z does not accept. */
    @Test
    fun the_iso_offset_with_a_colon_is_parsed() {
        assertNotNull(parseIsoDate("2026-08-08T21:02:29+00:00"))
        assertNotNull(parseIsoDate("2026-08-08T21:02:29-06:00"))
        assertNotNull(parseIsoDate("2026-08-08T21:02:29Z"))
        assertNull(parseIsoDate("no es una fecha"))
    }
}
