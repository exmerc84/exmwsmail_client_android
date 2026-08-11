package com.exmworkspace.exmwsmail.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class CharsetFallbackTest {

    @Test
    fun valid_utf8_passes_through_untouched() {
        val text = """{"body_text":"confirmación de su pago — «recibos»"}"""
        assertEquals(text, decodeUtf8OrWindows1252(text.toByteArray(Charsets.UTF_8)))
    }

    /** The SYSCOM message: Latin-1 `é`/`ó` bytes inside a payload that claimed UTF-8. */
    @Test
    fun latin1_bytes_fall_back_instead_of_becoming_replacement_chars() {
        val bytes = "sírvase encontrar la confirmación".toByteArray(charset("windows-1252"))
        assertEquals("sírvase encontrar la confirmación", decodeUtf8OrWindows1252(bytes))
    }

    /** 0x80–0x9F is where Windows-1252 and ISO-8859-1 differ; Word-pasted text lives there. */
    @Test
    fun smart_quotes_and_em_dash_survive_the_fallback() {
        val bytes = byteArrayOf(
            0x93.toByte(), 'h'.code.toByte(), 'o'.code.toByte(), 'l'.code.toByte(),
            'a'.code.toByte(), 0x94.toByte(), 0x20, 0x97.toByte(),
        )
        assertEquals("“hola” —", decodeUtf8OrWindows1252(bytes))
    }

    /**
     * A payload that is *mostly* UTF-8 with one stray byte still falls back as a whole:
     * decoding must never mix charsets within one document, so the valid UTF-8 sequences
     * re-read as 1252 mojibake is the accepted cost of the corrupt-input case — it does not
     * occur in practice because senders are consistent within a message.
     */
    @Test
    fun the_fallback_is_all_or_nothing() {
        val bytes = "año ".toByteArray(Charsets.UTF_8) + byteArrayOf(0xE9.toByte())
        assertEquals("aÃ±o é", decodeUtf8OrWindows1252(bytes))
    }

    @Test
    fun empty_payload_decodes_to_empty() {
        assertEquals("", decodeUtf8OrWindows1252(ByteArray(0)))
    }
}
