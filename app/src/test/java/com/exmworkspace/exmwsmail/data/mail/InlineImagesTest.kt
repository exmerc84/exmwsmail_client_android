package com.exmworkspace.exmwsmail.data.mail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InlineImagesTest {

    private val base = "https://webmail.exmworkspace.com/"

    @Test
    fun rewrites_a_cid_image_source() {
        val out = InlineImages.rewrite(
            """<img src="cid:logo123" alt="logo">""", base, "42", "INBOX",
        )
        assertEquals(
            """<img src="https://webmail.exmworkspace.com/api/emails/messages/42/cid/logo123?folder=INBOX" alt="logo">""",
            out,
        )
    }

    @Test
    fun strips_angle_brackets_from_the_content_id() {
        val out = InlineImages.rewrite("""<img src="cid:<abc@server>">""", base, "7", "INBOX")
        assertTrue(out!!.contains("/cid/abc%40server?"))
    }

    @Test
    fun encodes_folders_with_spaces_and_separators() {
        val out = InlineImages.rewrite("""<img src="cid:x">""", base, "1", "Clientes/Q2 2026")
        assertTrue(out!!.contains("folder=Clientes%2FQ2%202026"))
    }

    @Test
    fun handles_single_quotes_and_background_attributes() {
        val out = InlineImages.rewrite("""<td background='cid:bg'>""", base, "9", "INBOX")
        assertTrue(out!!.contains("background='https://webmail.exmworkspace.com/api/emails/messages/9/cid/bg?folder=INBOX'"))
    }

    @Test
    fun leaves_ordinary_urls_alone() {
        val html = """<img src="https://cdn.example.com/a.png"><a href="cid:notanimage">x</a>"""
        assertEquals(html, InlineImages.rewrite(html, base, "1", "INBOX"))
    }

    @Test
    fun tolerates_absent_bodies() {
        assertEquals(null, InlineImages.rewrite(null, base, "1", "INBOX"))
        assertEquals("", InlineImages.rewrite("", base, "1", "INBOX"))
    }
}
