package com.exmworkspace.exmwsmail.ui.mail.thread

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Backs the conversation view (§4.4).
 *
 * [rootMessageId] is the list row's representative message; the thread id and folder are
 * resolved from it so navigation never has to URL-encode a server-generated id.
 *
 * Only one message is expanded at a time, so a single detail flow covers the body — no need
 * to hold every message's HTML in memory at once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ThreadViewModel(
    private val rootMessageId: Long,
    private val mailRepository: MailRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val threadId = MutableStateFlow<String?>(null)

    private val _expandedId = MutableStateFlow<Long?>(null)
    val expandedId: StateFlow<Long?> = _expandedId.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Transient confirmation, e.g. after saving an attachment to the cloud. */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private val _subject = MutableStateFlow("")
    val subject: StateFlow<String> = _subject.asStateFlow()

    val messages: StateFlow<List<MessageEntity>> = threadId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else mailRepository.observeThread(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val expandedDetail: StateFlow<MessageDetail> = _expandedId
        .flatMapLatest { id ->
            if (id == null) flowOf(MessageDetail(null, null, emptyList()))
            else mailRepository.observeMessageDetail(id)
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            MessageDetail(null, null, emptyList()),
        )

    init {
        viewModelScope.launch {
            try {
                val root = mailRepository.findMessage(rootMessageId) ?: return@launch
                _subject.value = root.subject
                threadId.value = root.threadId
                // Expand the newest straight away, like the web does.
                expand(rootMessageId)
                val folder = mailRepository.findFolder(root.folderId) ?: return@launch
                accountId.value = folder.accountId
                val id = root.threadId ?: return@launch
                mailRepository.syncThread(folder.accountId, id, folder.fullName)
            } catch (e: Exception) {
                _error.update { authRepository.describeFailure(e) }
            } finally {
                _loading.value = false
            }
        }
    }

    fun toggle(messageId: Long) {
        if (_expandedId.value == messageId) {
            _expandedId.value = null
        } else {
            expand(messageId)
        }
    }

    private fun expand(messageId: Long) {
        _expandedId.value = messageId
        viewModelScope.launch {
            runCatching { mailRepository.markRead(messageId) }
            try {
                mailRepository.ensureBodyDownloaded(messageId)
            } catch (e: Exception) {
                _error.update { authRepository.describeFailure(e) }
            }
        }
    }

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val accountId = MutableStateFlow<Long?>(null)

    /** Move targets for the actions sheet. */
    val folders: StateFlow<List<FolderEntity>> = accountId
        .filterNotNull()
        .flatMapLatest { mailRepository.observeFolders(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Folder of the message being read, not of the conversation: a thread spans INBOX and
     * Sent, and the write permissions that matter are the ones on the open message.
     */
    val currentFolder: StateFlow<FolderEntity?> = combine(_expandedId, messages) { id, list ->
        list.firstOrNull { it.id == id }?.folderId
    }
        .distinctUntilChanged()
        .mapLatest { folderId -> folderId?.let { mailRepository.findFolder(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Set when nothing is left to show and the screen should close. */
    private val _closed = MutableStateFlow(false)
    val closed: StateFlow<Boolean> = _closed.asStateFlow()

    // The bottom bar acts on the message the user is actually reading — the expanded one —
    // the same way reply and forward do inside its card.

    fun toggleFlag() = onExpanded { mailRepository.toggleFlag(it) }

    fun markUnread() = onExpanded { mailRepository.markRead(it, seen = false) }

    fun setColor(color: String?) = onExpanded { mailRepository.setColor(it, color) }

    /** Moving or reporting takes the message out of the conversation, so the screen closes. */
    fun move(destination: FolderEntity) = onExpanded {
        mailRepository.moveThread(it, destination.fullName)
        _closed.value = true
    }

    fun markSpam() = onExpanded {
        mailRepository.markSpam(it)
        _closed.value = true
    }

    fun deleteExpanded() {
        val target = _expandedId.value ?: return
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                mailRepository.deleteMessage(target)
                // Deleting the last message leaves an empty conversation; fall back out.
                val remaining = messages.value.filterNot { it.id == target }
                if (remaining.isEmpty()) {
                    _closed.value = true
                } else {
                    expand(remaining.last().id)
                }
            } catch (e: Exception) {
                _error.update { authRepository.describeFailure(e) }
            } finally {
                _busy.value = false
            }
        }
    }

    private fun onExpanded(block: suspend (Long) -> Unit) {
        val target = _expandedId.value ?: return
        viewModelScope.launch {
            try {
                block(target)
            } catch (e: Exception) {
                _error.update { authRepository.describeFailure(e) }
            }
        }
    }

    fun markThreadRead() {
        viewModelScope.launch {
            try {
                mailRepository.markThreadRead(rootMessageId)
            } catch (e: Exception) {
                _error.update { authRepository.describeFailure(e) }
            }
        }
    }

    fun openAttachment(
        attachment: com.exmworkspace.exmwsmail.data.local.entity.AttachmentEntity,
        onReady: (com.exmworkspace.exmwsmail.data.local.entity.AttachmentEntity) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                mailRepository.downloadAttachment(attachment.id)?.let(onReady)
            } catch (e: Exception) {
                _error.update { authRepository.describeFailure(e) }
            }
        }
    }

    /** Copies the attachment into the user's EXMWS Cloud (§4.17). */
    fun saveAttachmentToCloud(
        attachment: com.exmworkspace.exmwsmail.data.local.entity.AttachmentEntity,
        savedLabel: String,
    ) {
        viewModelScope.launch {
            try {
                val message = mailRepository.findMessage(attachment.messageId) ?: return@launch
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
                ThreadViewModel(messageId, container.mailRepository, container.authRepository)
            }
        }
    }
}
