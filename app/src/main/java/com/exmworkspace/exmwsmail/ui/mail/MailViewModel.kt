package com.exmworkspace.exmwsmail.ui.mail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.exmworkspace.exmwsmail.data.local.entity.FolderEntity
import com.exmworkspace.exmwsmail.data.local.entity.MessageEntity
import com.exmworkspace.exmwsmail.data.mail.FolderKind
import com.exmworkspace.exmwsmail.data.repository.AuthRepository
import com.exmworkspace.exmwsmail.data.repository.MailRepository
import com.exmworkspace.exmwsmail.ui.appContainer
import com.exmworkspace.exmwsmail.ui.describeFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import com.exmworkspace.exmwsmail.data.remote.dto.FolderShareDto
import com.exmworkspace.exmwsmail.data.prefs.ActiveAccount
import com.exmworkspace.exmwsmail.data.remote.dto.QuotaDto
import com.exmworkspace.exmwsmail.data.remote.dto.RemoteAccountDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MailViewModel(
    private val mailRepository: MailRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val accountId = MutableStateFlow<Long?>(null)
    private val selectedFolderId = MutableStateFlow<Long?>(null)
    private val foldersExhausted = MutableStateFlow<Set<Long>>(emptySet())

    private val _selectedCategory = MutableStateFlow(MailCategory.ALL)
    val selectedCategory: StateFlow<MailCategory> = _selectedCategory.asStateFlow()

    private val _userEmail = MutableStateFlow<String?>(null)
    val userEmail: StateFlow<String?> = _userEmail.asStateFlow()

    val displayName: StateFlow<String> = authRepository.displayName

    fun selectCategory(category: MailCategory) {
        _selectedCategory.value = category
        // The chip filters the cache, which stops at the pages paged in so far. Ask the
        // server for the category as well, or matches deeper in the folder stay invisible.
        val apiValue = category.apiValue ?: return
        val folder = selectedFolder.value ?: return
        viewModelScope.launch {
            try {
                mailRepository.syncCategory(folder, apiValue)
            } catch (e: Exception) {
                _error.update { authRepository.describeFailure(e) }
            }
        }
    }

    // ---- Multi-select ----

    /**
     * Rows the user has picked out for a batch action. Empty means selection mode is off —
     * there is no separate flag to keep in sync with it.
     */
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    fun toggleSelection(messageId: Long) {
        _selectedIds.update { current ->
            if (messageId in current) current - messageId else current + messageId
        }
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun selectAllVisible() {
        _selectedIds.value = messages.value.map { it.id }.toSet()
    }

    /**
     * Runs [action] over every selected row, then clears the selection.
     *
     * Sequential, and each row is caught on its own: the API has no batch endpoint for these
     * (§4.4 acts per uid), and one failure — a message already gone from the server, say —
     * must not abandon the rest of the user's selection. The first error is surfaced once at
     * the end rather than once per row.
     */
    private fun runOnSelection(action: suspend (Long) -> Unit) {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return
        clearSelection()
        viewModelScope.launch {
            var firstFailure: Exception? = null
            for (id in ids) {
                try {
                    action(id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (firstFailure == null) firstFailure = e
                }
            }
            accountId.value?.let { mailRepository.refreshFolderCounters(it) }
            firstFailure?.let { e -> _error.update { authRepository.describeFailure(e) } }
        }
    }

    /** A list row stands for a thread, so a batch delete takes whole threads (§4.4). */
    fun deleteSelected() = runOnSelection { mailRepository.deleteThread(it) }

    fun moveSelected(destination: FolderEntity) =
        runOnSelection { mailRepository.moveThread(it, destination.fullName) }

    fun markSelectedRead(seen: Boolean) = runOnSelection { mailRepository.markRead(it, seen) }

    fun markSelectedSpam() = runOnSelection { mailRepository.markSpam(it) }

    private val _refreshing = MutableStateFlow(false)
    private val _loadingOlder = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    val folders: StateFlow<List<FolderEntity>> = accountId
        .filterNotNull()
        .flatMapLatest { mailRepository.observeFolders(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedFolder: StateFlow<FolderEntity?> = combine(selectedFolderId, folders) { id, list ->
        list.firstOrNull { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Everything cached for the open folder, before the category chip narrows it. */
    private val folderMessages: Flow<List<MessageEntity>> = selectedFolderId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else mailRepository.observeMessages(id)
        }

    val messages: StateFlow<List<MessageEntity>> = folderMessages
        // Filtering the cached page locally keeps the chips instant; re-querying the server
        // with ?category= would reset pagination on every tap.
        .combine(_selectedCategory) { list, category ->
            val wanted = category.apiValue ?: return@combine list
            list.filter { it.iaCategory == wanted }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    val loadingOlder: StateFlow<Boolean> = _loadingOlder.asStateFlow()
    val error: StateFlow<String?> = _error.asStateFlow()

    val endReached: StateFlow<Boolean> = combine(selectedFolderId, foldersExhausted) { id, set ->
        id != null && id in set
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _quota = MutableStateFlow<QuotaDto?>(null)
    val quota: StateFlow<QuotaDto?> = _quota.asStateFlow()

    /**
     * Badge on the drawer's "Recordatorios" entry (§4.19) — the reminders still pending.
     *
     * Done ones are excluded even though the list screen keeps showing them struck through: a
     * badge is what is left to deal with, and one that never returned to zero after finishing
     * everything would just be noise.
     */
    private val _followupCount = MutableStateFlow(0)
    val followupCount: StateFlow<Int> = _followupCount.asStateFlow()

    /** Cheap enough to re-ask every time the drawer opens, which is when the badge is read. */
    fun refreshFollowupCount() {
        viewModelScope.launch {
            // A decoration like the quota: it must never fail the screen, and a lost call
            // leaves the previous number rather than blanking it to a wrong zero.
            runCatching { mailRepository.followups().items.count { !it.isDone } }
                .onSuccess { _followupCount.value = it }
        }
    }

    /**
     * Badge count per category chip, counted over the very list the chip filters.
     *
     * `GET /category-counts` (§4.17) looks like the right source but answers for the whole
     * mailbox even when asked for one folder — it reported `Urgente=1` while the open INBOX
     * held no urgent message at all, so the chip advertised one and opened empty. Counting
     * what is on screen can never disagree with what tapping shows.
     */
    val categoryCounts: StateFlow<Map<String, Int>> = folderMessages
        .map { list ->
            list.mapNotNull { it.iaCategory }
                .groupingBy { it }
                .eachCount()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Grants on the folder whose share sheet is open (§4.20). */
    private val _shares = MutableStateFlow<List<FolderShareDto>>(emptyList())
    val shares: StateFlow<List<FolderShareDto>> = _shares.asStateFlow()

    private val _sharesLoading = MutableStateFlow(false)
    val sharesLoading: StateFlow<Boolean> = _sharesLoading.asStateFlow()

    fun loadShares(folder: FolderEntity) {
        viewModelScope.launch {
            _sharesLoading.value = true
            _shares.value = emptyList()
            try {
                _shares.value = mailRepository.folderShares(folder)
            } catch (e: Exception) {
                _error.update { authRepository.describeFailure(e) }
            } finally {
                _sharesLoading.value = false
            }
        }
    }

    /** Granting reaches another person's mailbox, so failures must be visible, not silent. */
    fun shareFolder(folder: FolderEntity, grantee: String, canWrite: Boolean) = runAction {
        mailRepository.shareFolder(folder, grantee, canWrite)
        _shares.value = mailRepository.folderShares(folder)
    }

    fun revokeShare(folder: FolderEntity, grantee: String) = runAction {
        mailRepository.unshareFolder(folder, grantee)
        _shares.value = mailRepository.folderShares(folder)
    }

    private var topSyncJob: Job? = null
    private var olderSyncJob: Job? = null

    init {
        viewModelScope.launch { bootstrap() }
        // A decoration: it loads on its own and never blocks or fails the screen.
        viewModelScope.launch { _quota.value = mailRepository.quota() }
        refreshFollowupCount()
        refreshAccounts()
        // Auto-select INBOX once folders are available, but don't override user selection.
        viewModelScope.launch { selectInboxWhenReady() }
    }

    /**
     * Waits for the folder list *of the current account* and opens its inbox.
     *
     * The account check is the whole point: `folders` is a StateFlow that keeps serving the
     * previous account's list until Room emits the new one, so waiting for "any non-empty
     * list" hands back the old folders. On a switch that selected the previous account's
     * inbox and the screen snapped straight back to the mailbox the user had just left.
     *
     * Both waits have to suspend. Reading `accountId.value` outright looked equivalent and
     * was not: on a cold start this runs in its own coroutine, races [bootstrap] and finds
     * null, so it returned without ever selecting anything — the app opened to an empty list
     * until the user picked a folder by hand.
     */
    private suspend fun selectInboxWhenReady() {
        val target = accountId.filterNotNull().first()
        val list = folders.first { it.isNotEmpty() && it.first().accountId == target }
        if (selectedFolderId.value != null) return
        val inbox = list.firstOrNull { it.kind == FolderKind.INBOX } ?: list.firstOrNull()
        inbox?.let { selectFolder(it.id) }
    }

    private suspend fun bootstrap() {
        try {
            // The active auxiliary mailbox if one is selected (§4.23); the interceptor is
            // already stamping X-Account-Id on every mail request, so the local partition
            // must match or the cache would file another account's mail under this one.
            val email = authRepository.activeMailEmail() ?: return
            _userEmail.value = email
            val id = mailRepository.ensureAccount(email)
            accountId.value = id
            mailRepository.syncFolders(id)
        } catch (e: Exception) {
            _error.update { authRepository.describeFailure(e) }
        }
    }

    // ---- Accounts (§4.23) ----

    private val _accounts = MutableStateFlow<List<RemoteAccountDto>>(emptyList())
    val accounts: StateFlow<List<RemoteAccountDto>> = _accounts.asStateFlow()

    /** Null when standing in the primary mailbox. */
    val activeAccount: StateFlow<ActiveAccount?> = authRepository.activeAccount


    fun refreshAccounts() {
        viewModelScope.launch {
            // Decorative like the quota: a failed load keeps the previous list.
            runCatching { mailRepository.remoteAccounts() }
                .onSuccess {
                    _accounts.value = it
                    authRepository.rememberPrimaryAccountId(
                        it.firstOrNull { account -> account.isDefault }?.id
                    )
                    dropActiveAccountIfGone(it)
                }
        }
    }

    /**
     * Re-points the whole screen at another mailbox. Order matters: the store first (the
     * interceptor reads it), then a fresh bootstrap so folders/messages re-partition, then
     * folder auto-select — the old selection belongs to the previous account.
     */
    fun switchAccount(account: RemoteAccountDto?) {
        val current = activeAccount.value?.serverId
        val target = account?.takeIf { !it.isDefault }
        if (current == target?.id) return
        authRepository.setActiveAccount(target?.let { ActiveAccount(it.id, it.email) })
        selectedFolderId.value = null
        _selectedCategory.value = MailCategory.ALL
        _error.value = null
        viewModelScope.launch {
            bootstrap()
            selectInboxWhenReady()
        }
    }

    /**
     * Mailboxes are provisioned server-side, so the app only reads the list — but it must
     * survive one disappearing: standing in a mailbox the server no longer lists would keep
     * stamping a dead `X-Account-Id` on every request.
     */
    private fun dropActiveAccountIfGone(list: List<RemoteAccountDto>) {
        val active = activeAccount.value ?: return
        if (list.none { it.id == active.serverId }) switchAccount(null)
    }

    fun selectFolder(folderId: Long) {
        if (selectedFolderId.value == folderId) return
        selectedFolderId.value = folderId
        _error.value = null
        triggerTopSync(folderId)
    }

    fun refresh() {
        val folderId = selectedFolderId.value ?: return
        triggerTopSync(folderId, isRefresh = true)
    }

    /**
     * A list row is a thread, so deleting from the list must take the whole thread with it —
     * the visible uid is only its representative (§4.4).
     */
    fun deleteMessage(messageId: Long) {
        viewModelScope.launch {
            try {
                mailRepository.deleteThread(messageId)
            } catch (e: Exception) {
                _error.update { authRepository.describeFailure(e) }
            }
        }
    }

    /** Marks every message of the conversation read in one call (§4.4). */
    fun markThreadRead(messageId: Long) = runAction { mailRepository.markThreadRead(messageId) }

    fun moveToFolder(messageId: Long, destination: FolderEntity) = runAction {
        mailRepository.moveThread(messageId, destination.fullName)
    }

    fun markSpam(messageId: Long) = runAction { mailRepository.markSpam(messageId) }

    fun setColor(messageId: Long, color: String?) = runAction {
        mailRepository.setColor(messageId, color)
    }

    /** Pin / unpin (§4.6). The list query floats pinned messages to the top. */
    fun togglePin(messageId: Long) = runAction {
        mailRepository.toggleFlag(messageId)
    }

    fun createFolder(name: String) = runAction {
        val id = accountId.value ?: return@runAction
        mailRepository.createFolder(id, name)
    }

    fun renameFolder(folder: FolderEntity, newName: String) = runAction {
        val id = accountId.value ?: return@runAction
        mailRepository.renameFolder(id, folder.fullName, newName)
    }

    fun deleteFolder(folder: FolderEntity) = runAction {
        val id = accountId.value ?: return@runAction
        if (folder.id == selectedFolderId.value) {
            folders.value.firstOrNull { it.kind == FolderKind.INBOX }?.let { selectFolder(it.id) }
        }
        mailRepository.deleteFolder(id, folder.fullName)
    }

    fun emptyFolder(folder: FolderEntity) = runAction { mailRepository.emptyFolder(folder) }

    fun dismissError() {
        _error.value = null
    }

    private fun runAction(block: suspend () -> Unit): Job = viewModelScope.launch {
        try {
            block()
        } catch (e: Exception) {
            _error.update { authRepository.describeFailure(e) }
        }
    }

    fun toggleRead(messageId: Long) {
        viewModelScope.launch {
            try {
                val msg = messages.value.find { it.id == messageId } ?: return@launch
                mailRepository.markRead(messageId, !msg.seen)
            } catch (e: Exception) {
                _error.update { authRepository.describeFailure(e) }
            }
        }
    }

    fun loadMore() {
        val folderId = selectedFolderId.value ?: return
        if (_loadingOlder.value) return
        if (folderId in foldersExhausted.value) return
        // Claim the slot synchronously: setting it inside the coroutine let a burst of
        // scroll callbacks all pass the guard before the first one had started.
        _loadingOlder.value = true
        olderSyncJob = viewModelScope.launch {
            try {
                val folder = mailRepository.findFolder(folderId) ?: return@launch
                val added = mailRepository.syncOlder(folder)
                if (added == 0) {
                    foldersExhausted.update { it + folderId }
                }
            } catch (e: Exception) {
                _error.update { authRepository.describeFailure(e) }
            } finally {
                _loadingOlder.value = false
            }
        }
    }

    /** Folders whose categories were already warmed this session — once each is enough. */
    private val warmedCategoryFolders = mutableSetOf<Long>()

    /**
     * Fire-and-forget on its own job: it must survive [topSyncJob] being cancelled by a
     * folder switch, and a failure only costs the badge accuracy it was going to add, so
     * the user never sees an error for it — the folder is just allowed to try again.
     */
    private fun warmCategoryBadges(folder: FolderEntity) {
        if (!warmedCategoryFolders.add(folder.id)) return
        viewModelScope.launch {
            val allOk = mailRepository.syncCategories(
                folder,
                MailCategory.entries.mapNotNull { it.apiValue },
            )
            if (!allOk) warmedCategoryFolders.remove(folder.id)
        }
    }

    private fun triggerTopSync(folderId: Long, isRefresh: Boolean = false) {
        topSyncJob?.cancel()
        topSyncJob = viewModelScope.launch {
            if (isRefresh) _refreshing.value = true
            try {
                val folder = mailRepository.findFolder(folderId) ?: return@launch
                if (isRefresh) {
                    // Ask the backend to poll IMAP, then give it a moment before reading:
                    // /sync queues the work and returns immediately (§4.13).
                    mailRepository.requestServerSync(folder.fullName)
                    delay(SYNC_SETTLE_MS)
                }
                mailRepository.syncFolderTop(folder)
                foldersExhausted.update { it - folderId }
                accountId.value?.let { mailRepository.refreshFolderCounters(it) }
                // The chip badges count cached rows, and the cache only holds the pages
                // brought in so far — so every badge undercounted until its chip was tapped.
                // Warm all categories in the background and the badges converge on their own.
                warmCategoryBadges(folder)
                // Bodies for the top of the list, so opening a message is instant and still
                // works offline. Runs after the list is on screen and never blocks it.
                mailRepository.preloadBodies(folder)
            } catch (e: Exception) {
                _error.update { authRepository.describeFailure(e) }
            } finally {
                if (isRefresh) _refreshing.value = false
            }
        }
    }

    companion object {
        private const val SYNC_SETTLE_MS = 1_200L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                MailViewModel(container.mailRepository, container.authRepository)
            }
        }
    }
}
