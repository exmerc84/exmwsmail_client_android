package com.exmworkspace.exmwsmail.ui.compose

import androidx.compose.ui.graphics.Color

// HTML serialization of the rich-text compose state. Extracted from
// ComposeViewModel to keep that file focused on state/editing logic.

internal fun bodyToHtml(state: ComposeUiState): String {
    val text = state.body.text
    val sb = StringBuilder()
    sb.append("<div style=\"font-family: sans-serif; font-size: ${DefaultFontSize}px; line-height: 1.4;\">")
    if (text.isEmpty()) {
        appendQuotedBlock(sb, state)
        sb.append("</div>")
        return sb.toString()
    }
    // Process paragraph by paragraph.
    var pos = 0
    var listOpen: ListKind? = null
    while (pos <= text.length) {
        val nl = text.indexOf('\n', pos)
        val end = if (nl == -1) text.length else nl
        val paragraphRange = pos..end
        val paragraphList = state.listSpans.firstOrNull {
            it.start <= pos && it.end >= end && pos < end
        }
        val paragraphAlign = state.alignSpans.firstOrNull {
            it.start <= pos && it.end >= end && pos < end
        }?.align
        // Manage list open/close
        if (paragraphList?.kind != listOpen) {
            if (listOpen != null) sb.append("</${listOpen.htmlTag}>")
            if (paragraphList != null) sb.append("<${paragraphList.kind.htmlTag}>")
            listOpen = paragraphList?.kind
        }
        val openTag: String
        val closeTag: String
        val styleAttr = if (paragraphAlign != null && paragraphList == null)
            " style=\"text-align: ${paragraphAlign.cssValue}\""
        else ""
        if (paragraphList != null) {
            openTag = "<li${if (paragraphAlign != null) " style=\"text-align: ${paragraphAlign.cssValue}\"" else ""}>"
            closeTag = "</li>"
        } else {
            openTag = "<div$styleAttr>"
            closeTag = "</div>"
        }
        sb.append(openTag)
        appendInlineHtml(sb, text, pos, end, state)
        if (pos == end) sb.append("&nbsp;")
        sb.append(closeTag)
        if (nl == -1) break
        pos = nl + 1
    }
    if (listOpen != null) sb.append("</${listOpen.htmlTag}>")
    appendQuotedBlock(sb, state)
    sb.append("</div>")
    return sb.toString()
}

private fun appendQuotedBlock(sb: StringBuilder, state: ComposeUiState) {
    val quote = state.quotedHtml ?: return
    sb.append("<br/><br/>")
    if (!state.quotedHeader.isNullOrBlank()) {
        sb.append("<div style=\"color: #6b7280; font-size: 13px;\">")
        sb.append(
            state.quotedHeader
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\n", "<br/>"),
        )
        sb.append("</div>")
    }
    sb.append(
        "<blockquote type=\"cite\" style=\"margin: 8px 0 0 0; padding-left: 12px; " +
            "border-left: 2px solid #cbd5e1; color: #1f2937;\">",
    )
    sb.append(quote)
    sb.append("</blockquote>")
}

private fun appendInlineHtml(
    sb: StringBuilder,
    text: String,
    rangeStart: Int,
    rangeEnd: Int,
    state: ComposeUiState,
) {
    if (rangeStart >= rangeEnd) return
    // Build per-character active set, chunk into maximal runs, emit tags.
    data class Active(
        val bold: Boolean,
        val italic: Boolean,
        val underline: Boolean,
        val strike: Boolean,
        val color: Long?,
        val size: Int?,
        val family: FontFamilyChoice?,
    )
    fun activeAt(i: Int): Active = Active(
        bold = state.bodyStyles.any { it.type == StyleType.BOLD && i in it.start until it.end },
        italic = state.bodyStyles.any { it.type == StyleType.ITALIC && i in it.start until it.end },
        underline = state.bodyStyles.any { it.type == StyleType.UNDERLINE && i in it.start until it.end },
        strike = state.bodyStyles.any { it.type == StyleType.STRIKE && i in it.start until it.end },
        color = state.colorSpans.firstOrNull { i in it.start until it.end }?.argb,
        size = state.sizeSpans.firstOrNull { i in it.start until it.end }?.sp,
        family = state.familySpans.firstOrNull { i in it.start until it.end }?.family,
    )

    fun emitOpen(a: Active) {
        val styles = mutableListOf<String>()
        if (a.color != null) styles += "color: ${argbToHex(a.color)}"
        if (a.size != null) styles += "font-size: ${a.size}px"
        if (a.family != null) styles += "font-family: ${a.family.cssValue}"
        if (a.underline && a.strike) styles += "text-decoration: underline line-through"
        else if (a.underline) styles += "text-decoration: underline"
        else if (a.strike) styles += "text-decoration: line-through"
        if (styles.isNotEmpty()) sb.append("<span style=\"${styles.joinToString("; ")}\">")
        if (a.bold) sb.append("<b>")
        if (a.italic) sb.append("<i>")
    }

    fun emitClose(a: Active) {
        if (a.italic) sb.append("</i>")
        if (a.bold) sb.append("</b>")
        val hasSpan = a.color != null || a.size != null || a.family != null ||
            a.underline || a.strike
        if (hasSpan) sb.append("</span>")
    }

    var i = rangeStart
    var current: Active? = null
    while (i < rangeEnd) {
        val a = activeAt(i)
        if (a != current) {
            if (current != null) emitClose(current)
            emitOpen(a)
            current = a
        }
        when (val c = text[i]) {
            '<' -> sb.append("&lt;")
            '>' -> sb.append("&gt;")
            '&' -> sb.append("&amp;")
            else -> sb.append(c)
        }
        i++
    }
    if (current != null) emitClose(current)
}

private fun argbToHex(argb: Long): String {
    val r = ((argb shr 16) and 0xFF).toInt()
    val g = ((argb shr 8) and 0xFF).toInt()
    val b = (argb and 0xFF).toInt()
    return String.format("#%02X%02X%02X", r, g, b)
}

// Utility — Color from Long (used in UI side)
fun colorFromArgb(argb: Long): Color = Color(argb)
