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
import com.exmworkspace.exmwsmail.data.remote.dto.ContactDto
import com.exmworkspace.exmwsmail.data.repository.ContactsRepository
import com.exmworkspace.exmwsmail.data.repository.MailRepository
import com.exmworkspace.exmwsmail.data.repository.MessageDetail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import com.exmworkspace.exmwsmail.ui.appContainer
import com.exmworkspace.exmwsmail.ui.describeFailure
import com.exmworkspace.exmwsmail.util.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.exmworkspace.exmwsmail.data.mail.plainText
import com.exmworkspace.exmwsmail.data.remote.dto.AiDraftOptionDto
import com.exmworkspace.exmwsmail.data.remote.dto.AiMessageDto
import com.exmworkspace.exmwsmail.ui.mail.DisplayLocale
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID


class ComposeViewModel(
    private val mailRepository: MailRepository,
    private val authRepository: AuthRepository,
    private val contactsRepository: ContactsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(ComposeUiState())
    val state: StateFlow<ComposeUiState> = _state.asStateFlow()

    private val _userEmail = MutableStateFlow<String?>(null)
    val userEmail: StateFlow<String?> = _userEmail.asStateFlow()

    /** Which recipient field the suggestions belong to, so they render under the right one. */
    private val _suggestionsFor = MutableStateFlow<RecipientField?>(null)
    val suggestionsFor: StateFlow<RecipientField?> = _suggestionsFor.asStateFlow()

    private val _suggestions = MutableStateFlow<List<ContactDto>>(emptyList())
    val suggestions: StateFlow<List<ContactDto>> = _suggestions.asStateFlow()

    private var suggestJob: Job? = null

    /** Suggested replies from §4.21; null while the sheet is closed. */
    private val _draftOptions = MutableStateFlow<List<AiDraftOptionDto>?>(null)
    val draftOptions: StateFlow<List<AiDraftOptionDto>?> = _draftOptions.asStateFlow()

    private val _draftLoading = MutableStateFlow(false)
    val draftLoading: StateFlow<Boolean> = _draftLoading.asStateFlow()

    /**
     * Asks the model for three ways to write this mail.
     *
     * The message being answered goes along as context — the endpoint returns 502 with an
     * empty `messages`, verified live — and so does whatever the user has typed so far, which
     * is what steers the suggestions towards what they actually meant to say.
     */
    fun requestAiDraft() {
        if (_draftLoading.value) return
        viewModelScope.launch {
            _draftLoading.value = true
            _draftOptions.value = emptyList()
            try {
                val current = _state.value
                val context = originalMessageId.takeIf { it > 0 }?.let { id ->
                    val message = mailRepository.findMessage(id) ?: return@let null
                    val body = mailRepository.observeMessageDetail(id).first().body?.plainText()
                    AiMessageDto(
                        sender = message.from,
                        date = Date(message.internalDate).toString(),
                        body = body.orEmpty(),
                    )
                }
                if (context == null) {
                    _state.update { it.copy(error = "Necesito el correo original para proponer una respuesta") }
                    _draftOptions.value = null
                    return@launch
                }
                _draftOptions.value = mailRepository.aiDraft(
                    subject = current.subject,
                    messages = listOf(context),
                    myDraft = current.body.text,
                    isReply = isAnyReply,
                )
            } catch (e: Exception) {
                _state.update { it.copy(error = authRepository.describeFailure(e)) }
                _draftOptions.value = null
            } finally {
                _draftLoading.value = false
            }
        }
    }

    /** Replaces what is written with the chosen suggestion, keeping the quoted original. */
    fun useDraft(option: AiDraftOptionDto) {
        _state.update { it.copy(body = TextFieldValue(option.text)) }
        _draftOptions.value = null
    }

    fun dismissDraftOptions() {
        _draftOptions.value = null
    }

    private val originalMessageId: Long = savedStateHandle.get<Long>("messageId") ?: -1L

    /** Exposed so the header can say what the user is actually doing. */
    val composeMode: ComposeMode get() = mode

    private val mode: ComposeMode = when (savedStateHandle.get<String>("mode")) {
        "reply" -> ComposeMode.REPLY
        "replyAll" -> ComposeMode.REPLY_ALL
        "forward" -> ComposeMode.FORWARD
        "draft" -> ComposeMode.DRAFT
        else -> ComposeMode.NEW
    }

    /** Both reply flavours quote, thread and prefill the same way; only the CC differs. */
    private val isAnyReply: Boolean
        get() = mode == ComposeMode.REPLY || mode == ComposeMode.REPLY_ALL

    /**
     * Identifies this composer window server-side. Drafts are per-window, not per-user, so
     * the id has to be stable across recompositions and process death (§4.16).
     */
    private val clientDraftId: String =
        savedStateHandle.get<String>(KEY_CLIENT_DRAFT_ID)
            ?: UUID.randomUUID().toString().also { savedStateHandle[KEY_CLIENT_DRAFT_ID] = it }

    /** uid of the last saved revision — sent as `prev_uid` so the backend replaces it. */
    private var draftUid: String? = null

    /** Local row of the draft being edited, so discarding also clears it from the list. */
    private var draftLocalId: Long? = null

    /**
     * Attachments only ride along when they actually changed: periodic autosaves use
     * `attachments_mode=keep` so a 10 MB file is not re-uploaded on every pause (§4.16).
     */
    private var attachmentsDirty = false

    /** RFC 5322 Message-Id of the original — sent as `in_reply_to` / `forward_of` (§4.12). */
    private var originalMessageIdHeader: String? = null

    /** The original's References chain, so a reply-to-a-reply stays in the same thread. */
    private var originalReferences: String? = null

    init {
        viewModelScope.launch {
            _userEmail.value = authRepository.activeMailEmail()
            // Opened from a contact: start with them already in "To".
            savedStateHandle.get<String>("to")?.takeIf { it.isNotBlank() }?.let { address ->
                _state.update { it.copy(to = listOf(address)) }
            }
            if (mode != ComposeMode.NEW && originalMessageId > 0) {
                prefillFromOriginal()
            }
            startAutosave()
        }
    }

    /**
     * Persists the draft a few seconds after the user stops typing. Keyed on the fields that
     * actually reach the server, so cursor moves and formatting-panel toggles don't trigger
     * uploads.
     */
    @OptIn(FlowPreview::class)
    private fun CoroutineScope.startAutosave() {
        launch {
            _state
                .map { s ->
                    DraftSnapshot(
                        to = mergeWithDraft(s.to, s.toDraft),
                        cc = mergeWithDraft(s.cc, s.ccDraft),
                        bcc = mergeWithDraft(s.bcc, s.bccDraft),
                        subject = s.subject,
                        body = s.body.text,
                        attachmentCount = s.attachments.size,
                        empty = s.isEmpty,
                        finished = s.sent || s.discarded,
                    )
                }
                .distinctUntilChanged()
                .debounce(AUTOSAVE_DEBOUNCE_MS)
                .collect { snapshot ->
                    if (snapshot.empty || snapshot.finished) return@collect
                    persistDraft()
                }
        }
    }

    /** Explicit save — used when leaving the composer before the debounce elapses. */
    fun saveDraftAndExit(onDone: () -> Unit) {
        val current = _state.value
        if (current.isEmpty || current.sent) {
            onDone()
            return
        }
        viewModelScope.launch {
            persistDraft()
            mailRepository.refreshDraftsFolder()
            onDone()
        }
    }

    fun discardDraft(onDone: () -> Unit) {
        viewModelScope.launch {
            if (draftUid != null || draftLocalId != null) {
                runCatching {
                    mailRepository.deleteDraft(clientDraftId, draftUid, draftLocalId)
                }
            }
            _state.update { it.copy(discarded = true) }
            onDone()
        }
    }

    private suspend fun persistDraft() {
        val current = _state.value
        if (current.isEmpty || current.sent || current.discarded) return
        _state.update { it.copy(savingDraft = true) }
        try {
            val uploadAttachments = attachmentsDirty
            val saved = mailRepository.saveDraft(
                message = outgoingFrom(current),
                clientDraftId = clientDraftId,
                previousUid = draftUid,
                uploadAttachments = uploadAttachments,
            )
            if (saved != null) {
                draftUid = saved
                // The replacement supersedes the row we opened from the Drafts list.
                draftLocalId = null
                if (uploadAttachments) attachmentsDirty = false
            }
            _state.update { it.copy(savingDraft = false, draftSaved = saved != null) }
        } catch (e: Exception) {
            // Autosave must never interrupt writing — surface nothing, just stop trying now.
            AppLog.w(TAG, "draft autosave failed: ${e.message}")
            _state.update { it.copy(savingDraft = false) }
        }
    }

    private fun outgoingFrom(current: ComposeUiState): OutgoingMessage {
        val isHtml = current.isRichText()
        return OutgoingMessage(
            to = mergeWithDraft(current.to, current.toDraft),
            cc = mergeWithDraft(current.cc, current.ccDraft),
            bcc = mergeWithDraft(current.bcc, current.bccDraft),
            subject = current.subject,
            body = if (isHtml) bodyToHtml(current) else current.body.text,
            isHtml = isHtml,
            inReplyTo = originalMessageIdHeader.takeIf { isAnyReply },
            references = replyReferences(),
            forwardOf = originalMessageIdHeader.takeIf { mode == ComposeMode.FORWARD },
            attachments = current.attachments,
        )
    }

    private fun ComposeUiState.isRichText(): Boolean =
        bodyStyles.isNotEmpty() || colorSpans.isNotEmpty() || sizeSpans.isNotEmpty() ||
            familySpans.isNotEmpty() || alignSpans.isNotEmpty() || listSpans.isNotEmpty() ||
            quotedHtml != null

    private data class DraftSnapshot(
        val to: List<String>,
        val cc: List<String>,
        val bcc: List<String>,
        val subject: String,
        val body: String,
        val attachmentCount: Int,
        val empty: Boolean,
        val finished: Boolean,
    )

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
            originalMessageIdHeader = msg.messageId
            originalReferences = msg.references

            if (mode == ComposeMode.DRAFT) {
                // Continue editing an existing draft: its uid becomes prev_uid so the next
                // save replaces it instead of piling up copies in the Drafts folder.
                draftUid = msg.uid
                draftLocalId = msg.id
                _state.update {
                    it.copy(
                        to = splitAddresses(msg.to),
                        cc = splitAddresses(msg.cc),
                        ccBccExpanded = !msg.cc.isNullOrBlank(),
                        subject = msg.subject,
                        body = TextFieldValue(draftBodyText(detail.body?.text, detail.body?.html)),
                    )
                }
                return
            }

            val fromAddr = parseFirstAddress(msg.fromAddress ?: msg.from)
            val whenLine = SimpleDateFormat("d MMM yyyy 'a las' HH:mm", DisplayLocale)
                .format(Date(msg.internalDate.takeIf { it > 0 } ?: System.currentTimeMillis()))
            val originalHtml = buildOriginalHtml(detail.body?.text, detail.body?.html)
            val (newSubject, prefilledTo, header) = when (mode) {
                ComposeMode.REPLY, ComposeMode.REPLY_ALL -> Triple(
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
                // DRAFT returned above; NEW never reaches prefill.
                ComposeMode.NEW, ComposeMode.DRAFT -> Triple(msg.subject, emptyList(), null)
            }
            // Reply-all keeps everyone who was already on the thread, minus the sender (they
            // are on To) and minus the user's own address — replying to yourself is noise,
            // and on an auxiliary mailbox "yourself" is that mailbox, not the login account.
            val replyAllCc = if (mode == ComposeMode.REPLY_ALL) {
                val me = authRepository.activeMailEmail().orEmpty()
                (splitAddresses(msg.to) + splitAddresses(msg.cc))
                    .map { addr -> parseFirstAddress(addr) ?: addr }
                    .filter { addr ->
                        addr.isNotBlank() &&
                            !addr.equals(fromAddr, ignoreCase = true) &&
                            !addr.equals(me, ignoreCase = true)
                    }
                    .distinctBy { addr -> addr.lowercase() }
            } else {
                emptyList()
            }
            _state.update {
                it.copy(
                    to = prefilledTo,
                    cc = replyAllCc,
                    ccBccExpanded = replyAllCc.isNotEmpty(),
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

    private fun splitAddresses(value: String?): List<String> =
        value?.split(',', ';')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

    /**
     * The editor is plain text plus span metadata, so an HTML draft comes back as text.
     * Formatting applied in a previous session is not restored.
     */
    private fun draftBodyText(text: String?, html: String?): String {
        text?.takeIf { it.isNotBlank() }?.let { return it }
        val source = html?.takeIf { it.isNotBlank() } ?: return ""
        return extractBody(source)
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</p>"), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .trim()
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
        refreshSuggestions(field, value)
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

    /**
     * Looks up contacts for whatever the user is typing into a recipient field (§5).
     * Debounced so a fast typist does not fire a request per keystroke.
     */
    private fun refreshSuggestions(field: RecipientField, draft: String) {
        suggestJob?.cancel()
        val query = draft.trim()
        if (query.length < MIN_SUGGEST_CHARS) {
            _suggestions.value = emptyList()
            _suggestionsFor.value = null
            return
        }
        _suggestionsFor.value = field
        suggestJob = viewModelScope.launch {
            delay(SUGGEST_DEBOUNCE_MS)
            val already = (recipientsOf(field) + _state.value.to + _state.value.cc +
                _state.value.bcc).map { it.lowercase() }.toSet()
            val found = runCatching { contactsRepository.suggest(query) }.getOrDefault(emptyList())
            _suggestions.value = found.filterNot { it.email?.lowercase() in already }
        }
    }

    fun dismissSuggestions() {
        suggestJob?.cancel()
        _suggestions.value = emptyList()
        _suggestionsFor.value = null
    }

    fun acceptSuggestion(field: RecipientField, contact: ContactDto) {
        val email = contact.email ?: return
        addRecipient(field, email)
        dismissSuggestions()
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
                attachmentsDirty = true
                _state.update { it.copy(attachments = it.attachments + attachment) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun removeAttachment(attachment: FileAttachment) {
        attachmentsDirty = true
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
                mailRepository.sendMessage(
                    outgoingFrom(current).copy(to = toList, cc = ccList, bcc = bccList)
                )
                // The message is out; its draft must not linger in the Drafts folder (§7.5).
                if (draftUid != null || draftLocalId != null) {
                    runCatching {
                        mailRepository.deleteDraft(clientDraftId, draftUid, draftLocalId)
                    }
                }
                _state.update { it.copy(sending = false, sent = true) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(sending = false, error = authRepository.describeFailure(e))
                }
            }
        }
    }

    /**
     * The original's References chain with its own Message-Id appended — the backend needs
     * both to keep a reply-to-a-reply in the same thread (§4.12).
     */
    private fun replyReferences(): List<String> {
        if (!isAnyReply) return emptyList()
        val chain = originalReferences?.trim()?.split(Regex("\\s+")).orEmpty()
        return (chain + listOfNotNull(originalMessageIdHeader))
            .filter { it.isNotBlank() }
            .distinct()
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
        private const val TAG = "ComposeViewModel"
        private const val KEY_CLIENT_DRAFT_ID = "client_draft_id"
        private const val AUTOSAVE_DEBOUNCE_MS = 2_500L
        private const val SUGGEST_DEBOUNCE_MS = 220L
        private const val MIN_SUGGEST_CHARS = 2

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                ComposeViewModel(
                    container.mailRepository,
                    container.authRepository,
                    container.contactsRepository,
                    createSavedStateHandle(),
                )
            }
        }
    }
}
