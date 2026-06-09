package com.exmworkspace.exmwsmail.ui.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.exmworkspace.exmwsmail.data.mail.OutgoingMessage
import com.exmworkspace.exmwsmail.data.repository.AuthRepository
import com.exmworkspace.exmwsmail.data.mail.FileAttachment
import android.net.Uri
import android.content.Context
import com.exmworkspace.exmwsmail.data.repository.MailRepository
import com.exmworkspace.exmwsmail.data.repository.MessageDetail
import kotlinx.coroutines.Dispatchers
import com.exmworkspace.exmwsmail.ui.appContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

class ComposeViewModel(
    private val mailRepository: MailRepository,
    private val authRepository: AuthRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(ComposeUiState())
    val state: StateFlow<ComposeUiState> = _state.asStateFlow()

    private val _userEmail = MutableStateFlow<String?>(null)
    val userEmail: StateFlow<String?> = _userEmail.asStateFlow()

    private val originalMessageId: Long = savedStateHandle.get<Long>("messageId") ?: -1L
    private val mode: ComposeMode = when (savedStateHandle.get<String>("mode")) {
        "reply" -> ComposeMode.REPLY
        "forward" -> ComposeMode.FORWARD
        else -> ComposeMode.NEW
    }

    init {
        viewModelScope.launch {
            _userEmail.value = authRepository.currentCredentials()?.email
            if (mode != ComposeMode.NEW && originalMessageId > 0) {
                prefillFromOriginal()
            }
        }
    }

    private suspend fun prefillFromOriginal() {
        try {
            val initialDetail = mailRepository.observeMessageDetail(originalMessageId)
                .first { it.message != null }
            if (initialDetail.body == null) {
                runCatching { mailRepository.ensureBodyDownloaded(originalMessageId) }
            }
            val detail = mailRepository.observeMessageDetail(originalMessageId)
                .first { it.message != null && (it.body != null || mode == ComposeMode.FORWARD) }
            val msg = detail.message ?: return
            val fromAddr = parseFirstAddress(msg.from)
            val whenLine = SimpleDateFormat("d MMM yyyy 'a las' HH:mm", Locale.getDefault())
                .format(Date(msg.internalDate.takeIf { it > 0 } ?: System.currentTimeMillis()))
            val originalHtml = buildOriginalHtml(detail.body?.text, detail.body?.html)
            val (newSubject, prefilledTo, header) = when (mode) {
                ComposeMode.REPLY -> Triple(
                    addPrefixIfMissing(msg.subject, "Re:"),
                    listOfNotNull(fromAddr),
                    "El $whenLine, ${msg.from} escribió:",
                )
                ComposeMode.FORWARD -> Triple(
                    addPrefixIfMissing(msg.subject, "Fwd:"),
                    emptyList(),
                    "---------- Mensaje reenviado ----------\n" +
                        "De: ${msg.from}\n" +
                        "Asunto: ${msg.subject}\n" +
                        "Fecha: $whenLine",
                )
                ComposeMode.NEW -> Triple(msg.subject, emptyList(), null)
            }
            _state.update {
                it.copy(
                    to = prefilledTo,
                    subject = newSubject,
                    body = TextFieldValue("", selection = androidx.compose.ui.text.TextRange(0, 0)),
                    quotedHtml = if (mode == ComposeMode.NEW) null else originalHtml,
                    quotedHeader = header,
                )
            }
        } catch (_: Exception) {
            // best-effort prefill — leave state empty if anything goes wrong
        }
    }

    fun removeQuote() {
        _state.update { it.copy(quotedHtml = null, quotedHeader = null) }
    }

    private fun buildOriginalHtml(text: String?, html: String?): String {
        if (!html.isNullOrBlank()) {
            return sanitizeHtml(extractBody(html))
        }
        if (!text.isNullOrBlank()) {
            // Wrap plain text as preformatted HTML so newlines/spaces are preserved.
            val escaped = text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
            return "<pre style=\"font-family: sans-serif; white-space: pre-wrap; margin: 0;\">$escaped</pre>"
        }
        return ""
    }

    private fun extractBody(html: String): String {
        val match = Regex(
            "<body[^>]*>(.*?)</body>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).find(html)
        return match?.groupValues?.get(1)?.trim() ?: html
    }

    private fun sanitizeHtml(html: String): String =
        html
            .replace(
                Regex(
                    "<script\\b[^>]*>.*?</script>",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
                ),
                "",
            )
            .replace(
                Regex(
                    "<head\\b[^>]*>.*?</head>",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
                ),
                "",
            )
            .replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")

    private fun quoteLines(text: String): String =
        if (text.isEmpty()) "" else text.lines().joinToString("\n") { "> $it" }

    private fun extractReadableBody(text: String?, html: String?): String {
        // Prefer plain text only if it really looks like plain text. Otherwise
        // strip HTML — many messages have a poor text/plain alternative or only
        // text/html, and the previous code was leaking <style>/<script> noise.
        val hasUsableText = !text.isNullOrBlank() && !looksLikeHtml(text)
        return when {
            hasUsableText -> text!!.trim()
            !html.isNullOrBlank() -> stripHtml(html)
            !text.isNullOrBlank() -> stripHtml(text)
            else -> ""
        }
    }

    private fun looksLikeHtml(s: String): Boolean =
        Regex("<\\s*/?[a-zA-Z][^>]*>").containsMatchIn(s)

    private fun stripHtml(html: String): String {
        // Drop blocks whose contents would otherwise leak through.
        val cleaned = html
            .replace(
                Regex(
                    "<script\\b[^>]*>.*?</script>",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
                ),
                "",
            )
            .replace(
                Regex(
                    "<style\\b[^>]*>.*?</style>",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
                ),
                "",
            )
            .replace(
                Regex(
                    "<head\\b[^>]*>.*?</head>",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
                ),
                "",
            )
            .replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        // Use Android's HTML parser for entities + tag handling.
        val plain = android.text.Html
            .fromHtml(cleaned, android.text.Html.FROM_HTML_MODE_LEGACY)
            .toString()
        // Final cleanup of whitespace.
        return plain
            .replace(" ", " ")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("[ \\t]+\n"), "\n")
            .replace(Regex("\n[ \\t]+"), "\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    fun setDraft(field: RecipientField, value: String) {
        val ends = value.lastOrNull()
        if (ends != null && (ends == ',' || ends == ' ' || ends == '\n' || ends == ';')) {
            val candidate = value.dropLast(1).trim().trimEnd(',', ';', ' ', '\n')
            if (candidate.isNotEmpty() && isValidEmail(candidate)) {
                addRecipient(field, candidate)
                return
            }
        }
        _state.update { current ->
            when (field) {
                RecipientField.TO -> current.copy(toDraft = value, error = null)
                RecipientField.CC -> current.copy(ccDraft = value, error = null)
                RecipientField.BCC -> current.copy(bccDraft = value, error = null)
            }
        }
    }

    fun commitDraft(field: RecipientField) {
        val draft = draftOf(field).trim().trimEnd(',', ';', ' ', '\n')
        if (draft.isEmpty()) return
        if (!isValidEmail(draft)) {
            _state.update { it.copy(error = "Email no válido: $draft") }
            return
        }
        addRecipient(field, draft)
    }

    fun addRecipient(field: RecipientField, email: String) {
        val trimmed = email.trim().trimEnd(',', ';', ' ', '\n')
        if (trimmed.isEmpty()) return
        if (!isValidEmail(trimmed)) {
            _state.update { it.copy(error = "Email no válido: $trimmed") }
            return
        }
        _state.update { current ->
            when (field) {
                RecipientField.TO -> current.copy(
                    to = (current.to + trimmed).distinct(),
                    toDraft = "",
                    error = null,
                )
                RecipientField.CC -> current.copy(
                    cc = (current.cc + trimmed).distinct(),
                    ccDraft = "",
                    error = null,
                )
                RecipientField.BCC -> current.copy(
                    bcc = (current.bcc + trimmed).distinct(),
                    bccDraft = "",
                    error = null,
                )
            }
        }
    }

    fun removeRecipientAt(field: RecipientField, index: Int) {
        _state.update { current ->
            when (field) {
                RecipientField.TO -> current.copy(to = current.to.removeAtSafe(index))
                RecipientField.CC -> current.copy(cc = current.cc.removeAtSafe(index))
                RecipientField.BCC -> current.copy(bcc = current.bcc.removeAtSafe(index))
            }
        }
    }

    fun popLastRecipientIfDraftEmpty(field: RecipientField) {
        val draft = draftOf(field)
        if (draft.isNotEmpty()) return
        val list = recipientsOf(field)
        if (list.isEmpty()) return
        removeRecipientAt(field, list.lastIndex)
    }

    fun toggleCcBccExpanded() {
        _state.update { it.copy(ccBccExpanded = !it.ccBccExpanded) }
    }

    fun openAddDialog(field: RecipientField) {
        _state.update { it.copy(showAddDialogFor = field) }
    }

    fun dismissAddDialog() {
        _state.update { it.copy(showAddDialogFor = null) }
    }

    fun setSubject(value: String) = _state.update { it.copy(subject = value, error = null) }

    fun addAttachment(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val contentResolver = context.contentResolver
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
                val name = queryFileName(context, uri) ?: "attachment"
                val type = contentResolver.getType(uri) ?: "application/octet-stream"

                val attachment = FileAttachment(name, type, bytes)
                _state.update { it.copy(attachments = it.attachments + attachment) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun removeAttachment(attachment: FileAttachment) {
        _state.update { it.copy(attachments = it.attachments - attachment) }
    }

    private fun queryFileName(context: Context, uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) it.getString(index) else null
            } else null
        }
    }

    fun setBody(value: TextFieldValue) {
        val previous = _state.value.body
        val oldText = previous.text
        val newText = value.text
        val updated = if (oldText == newText) {
            _state.value.copy(body = value)
        } else {
            val current = _state.value
            // Detect: user pressed Enter on an empty list line → exit list
            val exitInfo = detectListExit(current.listSpans, oldText, newText)
            val newListSpans = if (exitInfo != null) {
                // Replace the affected span with truncated version (skip shift since we
                // already pre-computed the result in original-text coordinates, then shift).
                val withoutOld = current.listSpans - exitInfo.span
                val truncated = exitInfo.truncated?.let { listOf(it) } ?: emptyList()
                mergeAdjacentLists(
                    shiftListSpans(withoutOld + truncated, oldText, newText),
                    newText,
                )
            } else {
                mergeAdjacentLists(
                    shiftListSpans(current.listSpans, oldText, newText),
                    newText,
                )
            }
            current.copy(
                body = value,
                bodyStyles = shiftSpans(current.bodyStyles, oldText, newText) {
                    s, e -> StyleSpan(s, e, type)
                },
                colorSpans = shiftSpans(current.colorSpans, oldText, newText) {
                    s, e -> ColorSpan(s, e, argb)
                },
                sizeSpans = shiftSpans(current.sizeSpans, oldText, newText) {
                    s, e -> SizeSpan(s, e, sp)
                },
                familySpans = shiftSpans(current.familySpans, oldText, newText) {
                    s, e -> FamilySpan(s, e, family)
                },
                alignSpans = shiftAlignSpans(current.alignSpans, oldText, newText),
                listSpans = newListSpans,
                error = null,
            )
        }
        _state.value = updated
    }

    private data class ListExitInfo(val span: ListSpan, val truncated: ListSpan?)

    private fun detectListExit(
        listSpans: List<ListSpan>,
        oldText: String,
        newText: String,
    ): ListExitInfo? {
        if (listSpans.isEmpty()) return null
        if (newText.length != oldText.length + 1) return null
        val diffStart = (0 until oldText.length).firstOrNull { oldText[it] != newText[it] }
            ?: oldText.length
        if (diffStart >= newText.length) return null
        if (newText[diffStart] != '\n') return null
        val (lineStart, lineEnd) = lineRangeFor(oldText, diffStart, diffStart)
        if (lineStart != lineEnd) return null // line not empty
        val span = listSpans.firstOrNull { it.start <= lineStart && it.end >= lineEnd }
            ?: return null
        val truncatePoint = if (lineStart > 0 && oldText[lineStart - 1] == '\n')
            lineStart - 1 else lineStart
        val truncated = if (truncatePoint > span.start)
            span.copy(end = truncatePoint) else null
        return ListExitInfo(span, truncated)
    }

    fun exitListAtCurrentLine(): Boolean {
        val sel = _state.value.body.selection
        if (!sel.collapsed) return false
        val text = _state.value.body.text
        val (lineStart, lineEnd) = lineRangeFor(text, sel.start, sel.start)
        if (sel.start != lineStart) return false
        val active = _state.value.listSpans.firstOrNull {
            it.start <= lineStart && it.end >= lineEnd
        } ?: return false
        val replacements = splitSpanExcludingLine(active, lineStart, lineEnd, text)
        _state.update {
            it.copy(listSpans = (it.listSpans - active + replacements))
        }
        return true
    }

    fun toggleStyle(type: StyleType) {
        val sel = _state.value.body.selection
        if (sel.collapsed) return
        val start = sel.min
        val end = sel.max
        val current = _state.value.bodyStyles
        val newList = toggleSpan(current, start, end, type) { s, e -> StyleSpan(s, e, type) }
        _state.update { it.copy(bodyStyles = newList) }
    }

    fun setColor(argb: Long) {
        val sel = _state.value.body.selection
        if (sel.collapsed) return
        val start = sel.min
        val end = sel.max
        val current = _state.value.colorSpans
        // Replace any existing color in range with new color
        val cleared = clearSpansIn(current, start, end)
        val merged = mergeSpan(cleared, ColorSpan(start, end, argb)) { a, b -> a.argb == b.argb }
        _state.update { it.copy(colorSpans = merged) }
    }

    fun changeSize(delta: Int) {
        val sel = _state.value.body.selection
        if (sel.collapsed) return
        val start = sel.min
        val end = sel.max
        val current = _state.value.sizeSpans
        val currentSize = sizeAtRange(current, start, end) ?: DefaultFontSize
        val newSize = (currentSize + delta).coerceIn(MinFontSize, MaxFontSize)
        if (newSize == currentSize) return
        val cleared = clearSpansIn(current, start, end)
        val merged = mergeSpan(cleared, SizeSpan(start, end, newSize)) { a, b -> a.sp == b.sp }
        _state.update { it.copy(sizeSpans = merged) }
    }

    fun setFamily(family: FontFamilyChoice) {
        val sel = _state.value.body.selection
        if (sel.collapsed) return
        val start = sel.min
        val end = sel.max
        val current = _state.value.familySpans
        val cleared = clearSpansIn(current, start, end)
        val merged = mergeSpan(cleared, FamilySpan(start, end, family)) { a, b -> a.family == b.family }
        _state.update { it.copy(familySpans = merged) }
    }

    fun setAlignment(align: TextAlignChoice) {
        val sel = _state.value.body.selection
        if (sel.collapsed) return
        val text = _state.value.body.text
        val (lineStart, lineEnd) = lineRangeFor(text, sel.min, sel.max)
        val current = _state.value.alignSpans
        val cleared = clearSpansIn(current, lineStart, lineEnd)
        val merged = mergeSpan(cleared, AlignSpan(lineStart, lineEnd, align)) {
            a, b -> a.align == b.align
        }
        _state.update { it.copy(alignSpans = merged) }
    }

    fun toggleList(kind: ListKind) {
        val sel = _state.value.body.selection
        val text = _state.value.body.text
        val (lineStart, lineEnd) = lineRangeFor(text, sel.min, sel.max)
        val current = _state.value.listSpans
        val active = current.firstOrNull {
            it.kind == kind && it.start <= lineStart && it.end >= lineEnd
        }
        val newList = if (active != null) {
            // Remove only the current line from the active span (split if multi-line)
            val replacements = splitSpanExcludingLine(active, lineStart, lineEnd, text)
            current - active + replacements
        } else {
            // Split any other list span (any kind) that intersects this line
            val rebuilt = current.flatMap { sp ->
                if (sp.end > lineStart && sp.start < lineEnd) {
                    splitSpanExcludingLine(sp, lineStart, lineEnd, text)
                } else listOf(sp)
            }
            mergeAdjacentLists(rebuilt + ListSpan(lineStart, lineEnd, kind), text)
        }
        _state.update { it.copy(listSpans = newList) }
    }

    private fun splitSpanExcludingLine(
        span: ListSpan,
        lineStart: Int,
        lineEnd: Int,
        text: String,
    ): List<ListSpan> {
        val result = mutableListOf<ListSpan>()
        if (span.start < lineStart) {
            val prevEnd = if (lineStart > 0 && lineStart - 1 < text.length &&
                text[lineStart - 1] == '\n') lineStart - 1 else lineStart
            if (prevEnd > span.start) result += span.copy(end = prevEnd)
            else if (prevEnd == span.start) {
                // keep zero-length leading span only if it represents a list line at start
                // (rare); skip otherwise
            }
        }
        if (span.end > lineEnd) {
            val nextStart = if (lineEnd < text.length && text[lineEnd] == '\n')
                lineEnd + 1 else lineEnd
            if (nextStart < span.end) result += span.copy(start = nextStart)
        }
        return result
    }

    fun isStyleActiveOnSelection(type: StyleType): Boolean {
        val sel = _state.value.body.selection
        if (sel.collapsed) return false
        return _state.value.bodyStyles.any {
            it.type == type && it.start <= sel.min && it.end >= sel.max
        }
    }

    fun isAlignActive(align: TextAlignChoice): Boolean {
        val sel = _state.value.body.selection
        val text = _state.value.body.text
        val (lineStart, lineEnd) = lineRangeFor(text, sel.min, sel.max)
        return _state.value.alignSpans.any {
            it.align == align && it.start <= lineStart && it.end >= lineEnd
        }
    }

    fun isListActive(kind: ListKind): Boolean {
        val sel = _state.value.body.selection
        val text = _state.value.body.text
        val (lineStart, lineEnd) = lineRangeFor(text, sel.min, sel.max)
        return _state.value.listSpans.any {
            it.kind == kind && it.start <= lineStart && it.end >= lineEnd
        }
    }

    fun currentFamily(): FontFamilyChoice {
        val sel = _state.value.body.selection
        if (sel.collapsed) return FontFamilyChoice.DEFAULT
        val span = _state.value.familySpans.firstOrNull {
            it.start <= sel.min && it.end >= sel.max
        }
        return span?.family ?: FontFamilyChoice.DEFAULT
    }

    fun currentColor(): Long? {
        val sel = _state.value.body.selection
        if (sel.collapsed) return null
        val span = _state.value.colorSpans.firstOrNull {
            it.start <= sel.min && it.end >= sel.max
        }
        return span?.argb
    }

    fun send() {
        val current = _state.value
        if (current.sending) return
        val toList = mergeWithDraft(current.to, current.toDraft)
        val ccList = mergeWithDraft(current.cc, current.ccDraft)
        val bccList = mergeWithDraft(current.bcc, current.bccDraft)
        if (toList.isEmpty()) {
            _state.update { it.copy(error = "Añade al menos un destinatario en Para") }
            return
        }
        _state.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            try {
                val isHtml = current.bodyStyles.isNotEmpty() ||
                    current.colorSpans.isNotEmpty() ||
                    current.sizeSpans.isNotEmpty() ||
                    current.familySpans.isNotEmpty() ||
                    current.alignSpans.isNotEmpty() ||
                    current.listSpans.isNotEmpty() ||
                    current.quotedHtml != null
                val bodyContent = if (isHtml) bodyToHtml(current) else current.body.text
                mailRepository.sendMessage(
                    OutgoingMessage(
                        to = toList,
                        cc = ccList,
                        bcc = bccList,
                        subject = current.subject,
                        body = bodyContent,
                        isHtml = isHtml,
                        attachments = current.attachments,
                    )
                )
                _state.update { it.copy(sending = false, sent = true) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(sending = false, error = e.message ?: e::class.java.simpleName)
                }
            }
        }
    }

    private fun draftOf(field: RecipientField): String = when (field) {
        RecipientField.TO -> _state.value.toDraft
        RecipientField.CC -> _state.value.ccDraft
        RecipientField.BCC -> _state.value.bccDraft
    }

    private fun recipientsOf(field: RecipientField): List<String> = when (field) {
        RecipientField.TO -> _state.value.to
        RecipientField.CC -> _state.value.cc
        RecipientField.BCC -> _state.value.bcc
    }

    private fun mergeWithDraft(current: List<String>, draft: String): List<String> {
        val candidate = draft.trim().trimEnd(',', ';', ' ', '\n')
        return if (candidate.isNotEmpty() && isValidEmail(candidate)) {
            (current + candidate).distinct()
        } else current
    }

    private fun List<String>.removeAtSafe(index: Int): List<String> {
        if (index !in indices) return this
        return toMutableList().also { it.removeAt(index) }
    }

    private fun isValidEmail(value: String): Boolean {
        if (value.isBlank()) return false
        val at = value.indexOf('@')
        if (at <= 0 || at == value.lastIndex) return false
        if (value.indexOf(' ') >= 0) return false
        val dot = value.indexOf('.', at)
        return dot in (at + 2)..(value.length - 2)
    }

    private fun sizeAtRange(spans: List<SizeSpan>, start: Int, end: Int): Int? =
        spans.firstOrNull { it.start <= start && it.end >= end }?.sp

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                ComposeViewModel(
                    container.mailRepository,
                    container.authRepository,
                    createSavedStateHandle(),
                )
            }
        }
    }
}

private interface RangeSpan {
    val start: Int
    val end: Int
}

// Block-level shift: for ListSpan and AlignSpan we want the span to extend
// when text is inserted at or within its boundary, so newly typed characters
// stay inside the same list/paragraph.
private fun shiftListSpans(
    spans: List<ListSpan>,
    oldText: String,
    newText: String,
): List<ListSpan> = shiftBlockSpans(spans, oldText, newText) { sp, s, e -> sp.copy(start = s, end = e) }

private fun shiftAlignSpans(
    spans: List<AlignSpan>,
    oldText: String,
    newText: String,
): List<AlignSpan> = shiftBlockSpans(spans, oldText, newText) { sp, s, e -> sp.copy(start = s, end = e) }

private inline fun <T> shiftBlockSpans(
    spans: List<T>,
    oldText: String,
    newText: String,
    crossinline copyAt: (T, Int, Int) -> T,
): List<T> where T : Any {
    if (spans.isEmpty()) return spans
    val oldLen = oldText.length
    val newLen = newText.length
    if (oldLen == newLen) return spans
    val delta = newLen - oldLen
    val diffStart = (0 until minOf(oldLen, newLen)).firstOrNull { oldText[it] != newText[it] }
        ?: minOf(oldLen, newLen)
    return spans.mapNotNull { span ->
        val s = startOf(span)
        val e = endOf(span)
        when {
            e < diffStart -> span
            s > diffStart -> {
                val ns = (s + delta)
                val ne = (e + delta).coerceAtMost(newLen)
                if (ne >= ns && ns >= 0) copyAt(span, ns, ne) else null
            }
            else -> {
                // change at or within the span: extend end
                val ne = (e + delta).coerceAtLeast(s).coerceAtMost(newLen)
                copyAt(span, s, ne)
            }
        }
    }
}

private fun mergeAdjacentLists(spans: List<ListSpan>, text: String): List<ListSpan> {
    if (spans.size < 2) return spans
    val sorted = spans.sortedBy { it.start }
    val merged = mutableListOf<ListSpan>()
    for (sp in sorted) {
        val last = merged.lastOrNull()
        if (last != null && last.kind == sp.kind &&
            (last.end == sp.start ||
                (last.end + 1 == sp.start && text.getOrNull(last.end) == '\n') ||
                (last.end < sp.start && (last.end until sp.start).all {
                    text.getOrNull(it) == '\n'
                }))
        ) {
            merged[merged.lastIndex] = last.copy(end = sp.end)
        } else {
            merged += sp
        }
    }
    return merged
}

// Generic helpers operating on any "span with start/end". We use reified copy lambdas.
private inline fun <T> shiftSpans(
    spans: List<T>,
    oldText: String,
    newText: String,
    crossinline construct: T.(start: Int, end: Int) -> T,
): List<T> where T : Any {
    if (spans.isEmpty()) return spans
    val oldLen = oldText.length
    val newLen = newText.length
    if (oldLen == newLen) return spans
    val delta = newLen - oldLen
    val diffStart = (0 until minOf(oldLen, newLen)).firstOrNull { oldText[it] != newText[it] }
        ?: minOf(oldLen, newLen)
    return spans.mapNotNull { span ->
        val s = startOf(span)
        val e = endOf(span)
        when {
            e <= diffStart -> span
            s >= diffStart -> {
                val ns = (s + delta)
                val ne = (e + delta).coerceAtMost(newLen)
                if (ne > ns && ns >= 0) span.construct(ns, ne) else null
            }
            else -> {
                val ne = (e + delta).coerceAtLeast(s)
                if (ne > s) span.construct(s, ne.coerceAtMost(newLen)) else null
            }
        }
    }
}

private fun startOf(span: Any): Int = when (span) {
    is StyleSpan -> span.start
    is ColorSpan -> span.start
    is SizeSpan -> span.start
    is FamilySpan -> span.start
    is AlignSpan -> span.start
    is ListSpan -> span.start
    else -> 0
}

private fun endOf(span: Any): Int = when (span) {
    is StyleSpan -> span.end
    is ColorSpan -> span.end
    is SizeSpan -> span.end
    is FamilySpan -> span.end
    is AlignSpan -> span.end
    is ListSpan -> span.end
    else -> 0
}

// Toggle inline same-type span: if a span fully covers selection, split/remove; else add.
private inline fun <T> toggleSpan(
    list: List<T>,
    start: Int,
    end: Int,
    type: StyleType,
    construct: (Int, Int) -> T,
): List<T> where T : Any {
    val matching = list.filter {
        startOf(it) < end && endOf(it) > start && (it as? StyleSpan)?.type == type
    }
    val containing = matching.firstOrNull { startOf(it) <= start && endOf(it) >= end }
    return if (containing != null) {
        val replacements = mutableListOf<T>()
        val cs = startOf(containing)
        val ce = endOf(containing)
        if (cs < start) replacements += construct(cs, start)
        if (end < ce) replacements += construct(end, ce)
        list - containing + replacements
    } else {
        val mergedStart = (matching.minOfOrNull { startOf(it) } ?: start).coerceAtMost(start)
        val mergedEnd = (matching.maxOfOrNull { endOf(it) } ?: end).coerceAtLeast(end)
        list - matching.toSet() + construct(mergedStart, mergedEnd)
    }
}

private inline fun <T> clearSpansIn(
    list: List<T>,
    start: Int,
    end: Int,
): List<T> where T : Any {
    if (list.isEmpty()) return list
    @Suppress("UNCHECKED_CAST")
    val rebuilt = mutableListOf<T>()
    list.forEach { span ->
        val s = startOf(span)
        val e = endOf(span)
        when {
            e <= start || s >= end -> rebuilt += span
            s >= start && e <= end -> { /* drop */ }
            s < start && e > end -> {
                rebuilt += copyWith(span, s, start)
                rebuilt += copyWith(span, end, e)
            }
            s < start -> rebuilt += copyWith(span, s, start)
            else -> rebuilt += copyWith(span, end, e)
        }
    }
    return rebuilt
}

private inline fun <T> mergeSpan(
    list: List<T>,
    new: T,
    sameAttrs: (T, T) -> Boolean,
): List<T> where T : Any {
    val ns = startOf(new)
    val ne = endOf(new)
    val toMerge = list.filter {
        sameAttrs(it, new) && startOf(it) <= ne && endOf(it) >= ns
    }
    val mergedStart = (toMerge.minOfOrNull { startOf(it) } ?: ns).coerceAtMost(ns)
    val mergedEnd = (toMerge.maxOfOrNull { endOf(it) } ?: ne).coerceAtLeast(ne)
    return list - toMerge.toSet() + copyWith(new, mergedStart, mergedEnd)
}

@Suppress("UNCHECKED_CAST")
private fun <T> copyWith(span: T, s: Int, e: Int): T = when (span) {
    is StyleSpan -> span.copy(start = s, end = e) as T
    is ColorSpan -> span.copy(start = s, end = e) as T
    is SizeSpan -> span.copy(start = s, end = e) as T
    is FamilySpan -> span.copy(start = s, end = e) as T
    is AlignSpan -> span.copy(start = s, end = e) as T
    is ListSpan -> span.copy(start = s, end = e) as T
    else -> span
}

private fun lineRangeFor(text: String, selStart: Int, selEnd: Int): Pair<Int, Int> {
    val length = text.length
    if (length == 0) return 0 to 0
    val s = selStart.coerceIn(0, length)
    val e = selEnd.coerceIn(0, length)
    var lineStart = s
    while (lineStart > 0 && text[lineStart - 1] != '\n') lineStart--
    var lineEnd = e
    while (lineEnd < length && text[lineEnd] != '\n') lineEnd++
    return lineStart to lineEnd
}

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
