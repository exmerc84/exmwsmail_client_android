package com.exmworkspace.exmwsmail.data.mail

/**
 * An HTML document split into tags and text, so the text can be translated and put back
 * without the markup ever leaving the device (§4.21: the app sends the **text nodes**, never
 * HTML, and reinserts each translated fragment in its place).
 *
 * @param parts every token in order — tags and text alike.
 * @param textIndices positions in [parts] that hold translatable text.
 */
data class HtmlText(
    val parts: List<String>,
    val textIndices: List<Int>,
) {
    /** What to send, trimmed: the surrounding whitespace is restored on the way back. */
    val segments: List<String> get() = textIndices.map { parts[it].trim() }

    /**
     * Rebuilds the document with [translated] in place of the originals, keeping each node's
     * original leading and trailing whitespace so the layout does not collapse.
     *
     * A short or truncated answer is not an error worth throwing over: the fragments that did
     * come back are used and the rest stay in their original language.
     */
    fun withTranslations(translated: List<String>): String {
        val out = parts.toMutableList()
        textIndices.forEachIndexed { i, position ->
            val replacement = translated.getOrNull(i) ?: return@forEachIndexed
            val original = parts[position]
            out[position] = original.takeWhile { it.isWhitespace() } +
                replacement +
                original.takeLastWhile { it.isWhitespace() }
        }
        return out.joinToString("")
    }
}

/**
 * Splits [html] into tags and text nodes. Text inside `<script>` and `<style>` is left alone —
 * it is code, not prose — and nodes without a single letter (spacing, bullets, numbers) are
 * not worth a round trip to the model.
 */
fun splitHtmlText(html: String): HtmlText {
    val parts = mutableListOf<String>()
    val textIndices = mutableListOf<Int>()
    var i = 0
    var skipDepth = 0

    while (i < html.length) {
        val open = html.indexOf('<', i)
        if (open < 0) {
            html.substring(i).takeIf { it.isNotEmpty() }?.let { text ->
                parts += text
                if (skipDepth == 0 && text.isTranslatable()) textIndices += parts.lastIndex
            }
            break
        }
        if (open > i) {
            val text = html.substring(i, open)
            parts += text
            if (skipDepth == 0 && text.isTranslatable()) textIndices += parts.lastIndex
        }
        val close = html.indexOf('>', open)
        if (close < 0) {
            // Unterminated tag: keep the rest verbatim rather than guessing where it ends.
            parts += html.substring(open)
            break
        }
        val tag = html.substring(open, close + 1)
        parts += tag
        val name = tag.tagName()
        if (name == "script" || name == "style") {
            if (tag.startsWith("</")) skipDepth = (skipDepth - 1).coerceAtLeast(0) else skipDepth++
        }
        i = close + 1
    }

    return HtmlText(parts, textIndices)
}

private fun String.tagName(): String {
    val body = removePrefix("<").removePrefix("/").removeSuffix(">")
    return body.takeWhile { it.isLetterOrDigit() }.lowercase()
}

private fun String.isTranslatable(): Boolean = any { it.isLetter() }
