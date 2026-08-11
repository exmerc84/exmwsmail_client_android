package com.exmworkspace.exmwsmail.ui.mail.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.exmworkspace.exmwsmail.data.local.entity.AttachmentEntity
import com.exmworkspace.exmwsmail.data.local.entity.FolderEntity
import com.exmworkspace.exmwsmail.data.local.entity.MessageEntity
import com.exmworkspace.exmwsmail.data.repository.AuthRepository
import com.exmworkspace.exmwsmail.data.repository.MailRepository
import com.exmworkspace.exmwsmail.data.repository.MessageDetail
import com.exmworkspace.exmwsmail.ui.appContainer
import com.exmworkspace.exmwsmail.ui.describeFailure
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.exmworkspace.exmwsmail.data.local.entity.MessageBodyEntity
import com.exmworkspace.exmwsmail.data.mail.plainText
import com.exmworkspace.exmwsmail.data.mail.splitHtmlText
import com.exmworkspace.exmwsmail.data.remote.dto.AiMessageDto
import com.exmworkspace.exmwsmail.ui.ai.AiResult

@OptIn(ExperimentalCoroutinesApi::class)
class MessageDetailViewModel(
    private val messageId: Long,
    private val mailRepository: MailRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    val detail: StateFlow<MessageDetail> = mailRepository
        .observeMessageDetail(messageId)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            MessageDetail(null, null, emptyList()),
        )

    private val _loadingBody = MutableStateFlow(true)
    val loadingBody: StateFlow<Boolean> = _loadingBody.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    /** Transient confirmation, e.g. after saving an attachment to the cloud. */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Id of the attachment currently downloading, so the row can show progress. */
    private val _downloadingAttachment = MutableStateFlow<Long?>(null)
    val downloadingAttachment: StateFlow<Long?> = _downloadingAttachment.asStateFlow()

    private val accountId = MutableStateFlow<Long?>(null)

    private val _currentFolder = MutableStateFlow<FolderEntity?>(null)
    val currentFolder: StateFlow<FolderEntity?> = _currentFolder.asStateFlow()

    /** Move targets for the actions sheet. */
    val folders: StateFlow<List<FolderEntity>> = accountId
        .filterNotNull()
        .flatMapLatest { mailRepository.observeFolders(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            // One sequence, folder first: marking read is a write, and §4.20 answers 403 to
            // any write in a read-only shared folder — so the auto-mark must know where it
            // is before it fires. Reading someone's shared mail must not mark it for them.
            val msg = mailRepository.findMessage(messageId)
            val folder = msg?.let { mailRepository.findFolder(it.folderId) }
            if (folder != null) {
                _currentFolder.value = folder
                accountId.value = folder.accountId
            }
            if (folder?.canWrite != false) {
                runCatching { mailRepository.markRead(messageId) }
            }
            try {
                mailRepository.ensureBodyDownloaded(messageId)
            } catch (e: Exception) {
                _error.update { authRepository.describeFailure(e) }
            } finally {
                _loadingBody.value = false
            }
        }
    }

    fun toggleFlag() {
        viewModelScope.launch {
            try {
                mailRepository.toggleFlag(messageId)
            } catch (e: Exception) {
                _error.update { authRepository.describeFailure(e) }
            }
        }
    }

    fun markUnread() {
        viewModelScope.launch {
            try {
                mailRepository.markRead(messageId, seen = false)
            } catch (e: Exception) {
                _error.update { authRepository.describeFailure(e) }
            }
        }
    }

    fun deleteMessage() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                mailRepository.deleteMessage(messageId)
                _deleted.value = true
            } catch (e: Exception) {
                _error.update { authRepository.describeFailure(e) }
            } finally {
                _busy.value = false
            }
        }
    }

    fun markThreadRead() = runAction { mailRepository.markThreadRead(messageId) }

    fun setColor(color: String?) = runAction { mailRepository.setColor(messageId, color) }

    /** After a move or a spam report the message is gone from here, so the screen closes. */
    fun move(destination: FolderEntity) = runAction {
        mailRepository.moveThread(messageId, destination.fullName)
        _deleted.value = true
    }

    fun markSpam() = runAction {
        mailRepository.markSpam(messageId)
        _deleted.value = true
    }

    private fun runAction(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                _error.update { authRepository.describeFailure(e) }
            }
        }
    }

    /**
     * Fetches the bytes if this is the first time the attachment is opened — the API sends
     * only metadata with the message — then hands the local file back to the caller.
     */
    fun openAttachment(attachment: AttachmentEntity, onReady: (AttachmentEntity) -> Unit) {
        if (_downloadingAttachment.value != null) return
        viewModelScope.launch {
            _downloadingAttachment.value = attachment.id
            try {
                mailRepository.downloadAttachment(attachment.id)?.let(onReady)
            } catch (e: Exception) {
                _error.update { authRepository.describeFailure(e) }
            } finally {
                _downloadingAttachment.value = null
            }
        }
    }

    /** The message rendered in another language; null means the original is on screen. */
    private val _translatedBody = MutableStateFlow<MessageBodyEntity?>(null)
    val translatedBody: StateFlow<MessageBodyEntity?> = _translatedBody.asStateFlow()

    private val _translating = MutableStateFlow(false)
    val translating: StateFlow<Boolean> = _translating.asStateFlow()

    /** Result of the last AI action, shown in the sheet (§4.21). */
    private val _aiResult = MutableStateFlow<AiResult?>(null)
    val aiResult: StateFlow<AiResult?> = _aiResult.asStateFlow()

    private val _aiTitle = MutableStateFlow("")
    val aiTitle: StateFlow<String> = _aiTitle.asStateFlow()

    fun summarize(title: String) = runAi(title) { message, body ->
        mailRepository.summarize(
            subject = message.subject,
            messages = listOf(
                AiMessageDto(
                    sender = message.from,
                    date = java.util.Date(message.internalDate).toString(),
                    body = body,
                ),
            ),
        )
    }

    /**
     * Translates the message where it stands, rather than into a separate panel.
     *
     * The HTML is split locally and only its text nodes travel (§4.21); what comes back is
     * reinserted in place, so the layout, images and links are exactly the ones that arrived.
     * The original is kept so the banner can switch back to it.
     */
    fun translateInline(language: String) {
        if (_translating.value) return
        viewModelScope.launch {
            _translating.value = true
            try {
                val body = detail.value.body ?: return@launch
                val html = body.html
                _translatedBody.value = if (!html.isNullOrBlank()) {
                    val split = splitHtmlText(html)
                    if (split.segments.isEmpty()) return@launch
                    val translated = mailRepository.translateSegments(split.segments, language)
                    body.copy(html = split.withTranslations(translated))
                } else {
                    val text = body.text.orEmpty()
                    if (text.isBlank()) return@launch
                    body.copy(text = mailRepository.translate(text, language))
                }
            } catch (e: Exception) {
                _error.update { authRepository.describeFailure(e) }
            } finally {
                _translating.value = false
            }
        }
    }

    fun showOriginal() {
        _translatedBody.value = null
    }

    /**
     * Both AI actions need the message as plain text. The HTML body is stripped rather than
     * sent as-is: §4.21 is explicit that the model receives text, never markup.
     */
    private fun runAi(title: String, block: suspend (MessageEntity, String) -> String) {
        _aiTitle.value = title
        _aiResult.value = AiResult.Loading
        viewModelScope.launch {
            try {
                val current = detail.value
                val message = current.message ?: error("Mensaje no disponible")
                val body = current.body?.plainText().orEmpty()
                if (body.isBlank()) {
                    _aiResult.value = AiResult.Failed("El correo no tiene texto que procesar")
                    return@launch
                }
                _aiResult.value = AiResult.Text(block(message, body))
            } catch (e: Exception) {
                _aiResult.value = AiResult.Failed(authRepository.describeFailure(e))
            }
        }
    }

    fun dismissAi() {
        _aiResult.value = null
    }

    /** Creates a "remind me" for this message (§4.19). */
    fun remindMe(dueAtIso: String, createdLabel: String) {
        viewModelScope.launch {
            try {
                val message = detail.value.message ?: return@launch
                mailRepository.createFollowup(message, dueAtIso, note = "")
                _notice.update { createdLabel }
            } catch (e: Exception) {
                _error.update { authRepository.describeFailure(e) }
            }
        }
    }

    /** Copies the attachment into the user's EXMWS Cloud (§4.17). */
    fun saveAttachmentToCloud(attachment: AttachmentEntity, savedLabel: String) {
        viewModelScope.launch {
            try {
                val message = mailRepository.findMessage(messageId) ?: return@launch
                val folder = mailRepository.findFolder(message.folderId) ?: return@launch
                mailRepository.saveAttachmentToCloud(
                    uid = message.uid,
                    index = attachment.partIndex,
                    folderFullName = folder.fullName,
                )
                _notice.update { savedLabel }
            } catch (e: Exception) {
                _error.update { authRepository.describeFailure(e) }
            }
        }
    }

    fun dismissNotice() {
        _notice.value = null
    }

    companion object {
        fun factoryFor(messageId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                MessageDetailViewModel(messageId, container.mailRepository, container.authRepository)
            }
        }
    }
}
