package com.exmworkspace.exmwsmail.data.mail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlSegmentsTest {

    @Test
    fun only_the_text_nodes_are_offered_for_translation() {
        val split = splitHtmlText("<p>Hola <b>mundo</b></p>")
        assertEquals(listOf("Hola", "mundo"), split.segments)
    }

    /** The markup must come back byte for byte; only the words change. */
    @Test
    fun the_markup_survives_the_round_trip() {
        val html = "<p class=\"x\">Hola <b>mundo</b></p>"
        val split = splitHtmlText(html)
        val out = split.withTranslations(listOf("Hello", "world"))
        assertEquals("<p class=\"x\">Hello <b>world</b></p>", out)
    }

    /** Collapsing the spacing around a node would run words into their neighbours. */
    @Test
    fun the_whitespace_around_each_node_is_preserved() {
        val split = splitHtmlText("<p>\n  Hola  \n</p>")
        assertEquals(listOf("Hola"), split.segments)
        assertEquals("<p>\n  Hello  \n</p>", split.withTranslations(listOf("Hello")))
    }

    /** Script and style hold code, not prose; sending them would be noise and a leak. */
    @Test
    fun script_and_style_contents_are_never_sent() {
        val html = "<style>.a{color:red}</style><script>var x = 'hola';</script><p>Hola</p>"
        val split = splitHtmlText(html)
        assertEquals(listOf("Hola"), split.segments)
        assertTrue(split.withTranslations(listOf("Hello")).contains("var x = 'hola';"))
    }

    @Test
    fun nodes_without_letters_are_not_worth_a_round_trip() {
        val split = splitHtmlText("<p>  </p><td>123</td><p>Hola</p>")
        assertEquals(listOf("Hola"), split.segments)
    }

    /** A short answer must not drop the rest of the document. */
    @Test
    fun a_truncated_answer_leaves_the_remaining_nodes_untouched() {
        val split = splitHtmlText("<p>Hola</p><p>Adios</p>")
        assertEquals("<p>Hello</p><p>Adios</p>", split.withTranslations(listOf("Hello")))
    }

    @Test
    fun an_unterminated_tag_is_kept_verbatim_instead_of_being_guessed() {
        val html = "<p>Hola</p><div"
        assertEquals(html, splitHtmlText(html).withTranslations(emptyList()))
    }

    @Test
    fun plain_text_with_no_markup_still_works() {
        val split = splitHtmlText("Hola mundo")
        assertEquals(listOf("Hola mundo"), split.segments)
        assertEquals("Hello world", split.withTranslations(listOf("Hello world")))
    }
}
