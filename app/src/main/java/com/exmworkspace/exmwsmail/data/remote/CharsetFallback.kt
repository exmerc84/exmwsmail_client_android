package com.exmworkspace.exmwsmail.data.remote

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Decodes a JSON payload that is supposed to be UTF-8 but may really be Windows-1252.
 *
 * Some senders' messages travel through the backend with their original single-byte encoding
 * intact, so the JSON that carries `body_text`/`body_html` arrives with bytes like `0xE9`
 * (`é` in Latin-1) that are invalid UTF-8. The default lenient decode replaces each of them
 * with U+FFFD — which is how "sírvase" rendered as "s�rvase" — and once that replacement is
 * stored there is nothing left to repair. Decoding strictly first makes the corruption
 * *detectable*: on the first invalid byte the whole payload is re-read as Windows-1252, which
 * agrees with ISO-8859-1 everywhere ISO-8859-1 is defined and also covers the 0x80–0x9F
 * punctuation Word-pasted text loves (smart quotes, em dash).
 *
 * A pure function over bytes so the fallback is unit-tested against real byte sequences, not
 * eyeballed on a device.
 */
fun decodeUtf8OrWindows1252(bytes: ByteArray): String = try {
    Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()
} catch (_: CharacterCodingException) {
    String(bytes, WINDOWS_1252)
}

private val WINDOWS_1252: Charset = Charset.forName("windows-1252")
