package com.exmworkspace.exmwsmail.ui.attachments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.exmworkspace.exmwsmail.data.remote.dto.AttachmentBrowseDto
import com.exmworkspace.exmwsmail.data.repository.AuthRepository
import com.exmworkspace.exmwsmail.data.repository.MailRepository
import com.exmworkspace.exmwsmail.ui.appContainer
import com.exmworkspace.exmwsmail.ui.describeFailure
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The mailbox-wide attachment browser (§4.17). Its own screen rather than a folder view:
 * the endpoint spans every folder except Junk and Trash, so there is no folder to anchor it to.
 */
class AttachmentsViewModel(
    private val mailRepository: MailRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _items = MutableStateFlow<List<AttachmentBrowseDto>>(emptyList())
    val items: StateFlow<List<AttachmentBrowseDto>> = _items.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore.asStateFlow()

    private val _endReached = MutableStateFlow(false)
    val endReached: StateFlow<Boolean> = _endReached.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private var page = 1
    private var searchJob: Job? = null

    init {
        load(reset = true)
    }

    fun setQuery(value: String) {
        _query.value = value
        // Typing must not fire one request per keystroke.
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            load(reset = true)
        }
    }

    fun retry() = load(reset = true)

    fun loadMore() {
        if (_loading.value || _loadingMore.value || _endReached.value) return
        load(reset = false)
    }

    private fun load(reset: Boolean) {
        if (reset) {
            page = 1
            _endReached.value = false
            _loading.value = true
        } else {
            _loadingMore.value = true
        }
        viewModelScope.launch {
            try {
                val batch = mailRepository.browseAttachments(page, _query.value)
                _items.update { if (reset) batch else it + batch }
                if (batch.isEmpty()) _endReached.value = true else page++
                _error.value = null
            } catch (e: Exception) {
                _error.value = authRepository.describeFailure(e)
            } finally {
                _loading.value = false
                _loadingMore.value = false
            }
        }
    }

    /** Id of the row currently downloading, so only that one shows progress. */
    private val _opening = MutableStateFlow<String?>(null)
    val opening: StateFlow<String?> = _opening.asStateFlow()

    fun open(item: AttachmentBrowseDto, onReady: (java.io.File, String?) -> Unit) {
        if (_opening.value != null) return
        viewModelScope.launch {
            _opening.value = item.key()
            try {
                val file = mailRepository.downloadAttachmentTo(
                    uid = item.uid,
                    index = item.index,
                    folderFullName = item.folder,
                    filename = item.filename,
                )
                onReady(file, item.contentType)
            } catch (e: Exception) {
                _error.value = authRepository.describeFailure(e)
            } finally {
                _opening.value = null
            }
        }
    }

    fun saveToCloud(item: AttachmentBrowseDto, savedLabel: String) {
        viewModelScope.launch {
            try {
                mailRepository.saveAttachmentToCloud(
                    uid = item.uid,
                    index = item.index,
                    folderFullName = item.folder,
                )
                _notice.value = savedLabel
            } catch (e: Exception) {
                _error.value = authRepository.describeFailure(e)
            }
        }
    }

    fun dismissNotice() {
        _notice.value = null
    }

    fun dismissError() {
        _error.value = null
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 350L

        /** Stable identity for a part: the same filename repeats across messages. */
        fun AttachmentBrowseDto.key(): String = "$folder/$uid/$index"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                AttachmentsViewModel(container.mailRepository, container.authRepository)
            }
        }
    }
}
