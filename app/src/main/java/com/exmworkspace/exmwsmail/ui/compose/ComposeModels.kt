package com.exmworkspace.exmwsmail.ui.compose

import androidx.compose.ui.text.input.TextFieldValue
import com.exmworkspace.exmwsmail.data.mail.FileAttachment

// Compose-editor models: recipient fields, rich-text enums/spans, palette,
// font-size bounds, and the ComposeUiState. Extracted from ComposeViewModel.

enum class RecipientField { TO, CC, BCC }

enum class StyleType(val htmlTag: String) {
    BOLD("b"),
    ITALIC("i"),
    UNDERLINE("u"),
    STRIKE("s"),
}

enum class FontFamilyChoice(val displayName: String, val cssValue: String) {
    DEFAULT("Predeterminado", "sans-serif"),
    SERIF("Serif", "serif"),
    MONO("Monoespacio", "monospace"),
}

enum class TextAlignChoice(val cssValue: String) {
    LEFT("left"),
    CENTER("center"),
    RIGHT("right"),
}

enum class ListKind(val htmlTag: String) {
    BULLET("ul"),
    NUMBERED("ol"),
}

data class StyleSpan(val start: Int, val end: Int, val type: StyleType)
data class ColorSpan(val start: Int, val end: Int, val argb: Long)
data class SizeSpan(val start: Int, val end: Int, val sp: Int)
data class FamilySpan(val start: Int, val end: Int, val family: FontFamilyChoice)
data class AlignSpan(val start: Int, val end: Int, val align: TextAlignChoice)
data class ListSpan(val start: Int, val end: Int, val kind: ListKind)

val ColorPalette: List<Long> = listOf(
    0xFF111522, // default near black
    0xFFD32F2F, // red
    0xFFEF6C00, // orange
    0xFFF9A825, // yellow
    0xFF2E7D32, // green
    0xFF1565C0, // blue
    0xFF6A1B9A, // purple
    0xFF6D4C41, // brown
)

const val DefaultFontSize = 15
const val MinFontSize = 11
const val MaxFontSize = 28

data class ComposeUiState(
    val to: List<String> = emptyList(),
    val cc: List<String> = emptyList(),
    val bcc: List<String> = emptyList(),
    val toDraft: String = "",
    val ccDraft: String = "",
    val bccDraft: String = "",
    val ccBccExpanded: Boolean = false,
    val subject: String = "",
    val body: TextFieldValue = TextFieldValue(""),
    val bodyStyles: List<StyleSpan> = emptyList(),
    val colorSpans: List<ColorSpan> = emptyList(),
    val sizeSpans: List<SizeSpan> = emptyList(),
    val familySpans: List<FamilySpan> = emptyList(),
    val alignSpans: List<AlignSpan> = emptyList(),
    val listSpans: List<ListSpan> = emptyList(),
    val quotedHtml: String? = null,
    val quotedHeader: String? = null,
    val showAddDialogFor: RecipientField? = null,
    val sending: Boolean = false,
    val sent: Boolean = false,
    val error: String? = null,
    val attachments: List<FileAttachment> = emptyList(),
)

enum class ComposeMode { NEW, REPLY, FORWARD }
