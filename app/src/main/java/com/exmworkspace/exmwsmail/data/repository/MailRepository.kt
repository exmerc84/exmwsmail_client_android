package com.exmworkspace.exmwsmail.data.repository

import com.exmworkspace.exmwsmail.BuildConfig
import com.exmworkspace.exmwsmail.data.local.dao.AccountDao
import com.exmworkspace.exmwsmail.data.local.dao.AttachmentDao
import com.exmworkspace.exmwsmail.data.local.dao.FolderDao
import com.exmworkspace.exmwsmail.data.local.dao.MessageBodyDao
import com.exmworkspace.exmwsmail.data.local.dao.MessageDao
import com.exmworkspace.exmwsmail.data.local.entity.AccountEntity
import com.exmworkspace.exmwsmail.data.local.entity.AttachmentEntity
import com.exmworkspace.exmwsmail.data.local.entity.FolderEntity
import com.exmworkspace.exmwsmail.data.local.entity.MessageBodyEntity
import com.exmworkspace.exmwsmail.data.local.entity.MessageEntity
import com.exmworkspace.exmwsmail.data.mail.CalendarInvite
import com.exmworkspace.exmwsmail.data.mail.FolderKind
import com.exmworkspace.exmwsmail.data.mail.InviteReply
import com.exmworkspace.exmwsmail.data.mail.isCalendarAttachment
import com.exmworkspace.exmwsmail.data.mail.parseCalendarInvite
import com.exmworkspace.exmwsmail.data.mail.stripImapFetchPreamble
import com.exmworkspace.exmwsmail.data.remote.dto.AiDraftOptionDto
import com.exmworkspace.exmwsmail.data.remote.dto.AiDraftRequest
import com.exmworkspace.exmwsmail.data.remote.dto.AiMessageDto
import com.exmworkspace.exmwsmail.data.remote.dto.AttachmentBrowseDto
import com.exmworkspace.exmwsmail.data.remote.dto.CalendarReplyRequest
import com.exmworkspace.exmwsmail.data.remote.dto.FolderShareDto
import com.exmworkspace.exmwsmail.data.remote.dto.FolderShareRequest
import com.exmworkspace.exmwsmail.data.remote.dto.FollowupCreateDto
import com.exmworkspace.exmwsmail.data.remote.dto.FollowupListDto
import com.exmworkspace.exmwsmail.data.remote.dto.FollowupUpdateDto
import com.exmworkspace.exmwsmail.data.remote.dto.SummarizeRequest
import com.exmworkspace.exmwsmail.data.remote.dto.TranslateRequest
import com.exmworkspace.exmwsmail.data.remote.dto.TranslateSegmentsRequest
import com.exmworkspace.exmwsmail.data.mail.InlineImages
import com.exmworkspace.exmwsmail.data.mail.MailDates
import com.exmworkspace.exmwsmail.data.mail.OutgoingMessage
import com.exmworkspace.exmwsmail.data.mail.FolderRef
import com.exmworkspace.exmwsmail.data.mail.pruneWindowStart
import com.exmworkspace.exmwsmail.data.mail.resolveFolderKinds
import com.exmworkspace.exmwsmail.data.remote.MailApi
import com.exmworkspace.exmwsmail.data.remote.bodyOrNull
import com.exmworkspace.exmwsmail.data.remote.dto.FolderDto
import com.exmworkspace.exmwsmail.data.remote.dto.MessageDetailDto
import com.exmworkspace.exmwsmail.data.remote.dto.MessageDto
import com.exmworkspace.exmwsmail.data.remote.dto.QuotaDto
import com.exmworkspace.exmwsmail.data.remote.dto.RemoteAccountDto
import com.exmworkspace.exmwsmail.data.remote.requireBody
import com.exmworkspace.exmwsmail.data.remote.requireSuccess
import com.exmworkspace.exmwsmail.util.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

data class MessageDetail(
    val message: MessageEntity?,
    val body: MessageBodyEntity?,
    val attachments: List<AttachmentEntity>,
)

/**
 * Single source of truth for mail. Room stays the read model the UI observes; every write
 * goes to the REST backend first (or is rolled back), and reads refresh the cache.
 */
class MailRepository(
    private val api: MailApi,
    private val accountDao: AccountDao,
    private val folderDao: FolderDao,
    private val messageDao: MessageDao,
    private val messageBodyDao: MessageBodyDao,
    private val attachmentDao: AttachmentDao,
    private val attachmentsDir: File,
) {
    /** Highest page fetched per folder — the API pages, it has no UID cursor. */
    private val loadedPages = mutableMapOf<Long, Int>()

    // ---- Observation ----

    fun observeFolders(accountId: Long): Flow<List<FolderEntity>> =
        folderDao.observeForAccount(accountId)

    fun observeMessages(folderId: Long): Flow<List<MessageEntity>> =
        messageDao.observeByFolder(folderId)

    fun searchMessages(accountId: Long, query: String): Flow<List<MessageEntity>> =
        messageDao.search(accountId, query)

    fun observeMessageDetail(messageId: Long): Flow<MessageDetail> = combine(
        messageDao.observeById(messageId),
        messageBodyDao.observe(messageId),
        attachmentDao.observe(messageId),
    ) { msg, body, atts -> MessageDetail(msg, body, atts) }

    suspend fun findFolder(folderId: Long): FolderEntity? = folderDao.findById(folderId)

    suspend fun findFolderByName(accountId: Long, fullName: String): FolderEntity? =
        folderDao.findByFullName(accountId, fullName)

    suspend fun findMessage(messageId: Long): MessageEntity? = messageDao.findById(messageId)

    /** Lookup for the push path, which only knows the (folder, uid) pair the FCM data carries. */
    suspend fun findMessageByUid(folder: FolderEntity, uid: String): MessageEntity? =
        messageDao.findByUid(folder.id, uid)

    suspend fun ensureAccount(email: String): Long {
        accountDao.findByEmail(email)?.let { return it.id }
        val id = accountDao.insert(AccountEntity(email = email))
        if (id != -1L) return id
        return accountDao.findByEmail(email)?.id ?: error("No se pudo crear cuenta")
    }

    // ---- Sync ----

    suspend fun syncFolders(accountId: Long) = withContext(Dispatchers.IO) {
        val remote = api.folders().requireBody()
        if (remote.isEmpty()) return@withContext
        val existing = folderDao.findFoldersForAccount(accountId).associateBy { it.fullName }
        // Resolved over the whole list: two folders can otherwise claim the same system role,
        // and the owner has to come along so another user's folders stay out of that contest.
        val kinds = resolveFolderKinds(remote.map { FolderRef(it.name, it.sharedOwner) })
        folderDao.upsert(
            remote.map { dto ->
                dto.toEntity(
                    accountId = accountId,
                    existing = existing[dto.name],
                    // The map covers every name; OTHER is the safe answer if one ever slipped.
                    kind = kinds[dto.name] ?: FolderKind.OTHER,
                )
            }
        )
        folderDao.deleteMissing(accountId, remote.map { it.name })
    }

    /**
     * Refreshes only the counters. Cheap — they come from the backend cache with no IMAP
     * round-trip — so it is safe to call right after any action that moves badges (§4.1).
     */
    suspend fun refreshFolderCounters(accountId: Long) = withContext(Dispatchers.IO) {
        runCatching { syncFolders(accountId) }
    }

    /** Pull-to-refresh: reload the first page. */
    suspend fun syncFolderTop(folder: FolderEntity, limit: Int = PAGE_SIZE) =
        withContext(Dispatchers.IO) {
            val dtos = api.messages(folder.fullName, page = 1, perPage = limit).requireBody()
            loadedPages[folder.id] = 1
            applyPage(folder, dtos, isFirstPage = true)
        }

    /** @return how many messages the next page added; 0 means the folder is exhausted. */
    suspend fun syncOlder(folder: FolderEntity, limit: Int = PAGE_SIZE): Int =
        withContext(Dispatchers.IO) {
            val nextPage = (loadedPages[folder.id] ?: 1) + 1
            val dtos = api.messages(folder.fullName, page = nextPage, perPage = limit).requireBody()
            if (dtos.isEmpty()) return@withContext 0
            loadedPages[folder.id] = nextPage
            applyPage(folder, dtos, isFirstPage = false)
            dtos.size
        }

    /**
     * Asks the backend to pull from IMAP. Returns immediately — the backend queues the sync
     * — so callers should re-fetch shortly after (§4.13).
     */
    suspend fun requestServerSync(folderFullName: String?) = withContext(Dispatchers.IO) {
        runCatching { api.sync(folderFullName).requireSuccess() }
    }

    private suspend fun applyPage(
        folder: FolderEntity,
        dtos: List<MessageDto>,
        isFirstPage: Boolean,
    ) {
        folderDao.update(folder.copy(lastSyncedAt = System.currentTimeMillis()))
        if (dtos.isEmpty()) {
            if (isFirstPage) messageDao.deleteAllForFolder(folder.id)
            return
        }

        // These rows come from the paged list, which is the only view the prune below may
        // reason about — see [MessageDao.deleteStaleInWindow].
        val mapped = dtos.map { it.toEntity(folder.id).copy(pagedIn = true) }
        if (isFirstPage) {
            // Anything the server no longer lists inside this window was deleted or moved
            // elsewhere. Bounded by date because the API pages by date, not by UID range.
            pruneWindowStart(mapped.map { it.internalDate })?.let { oldestInPage ->
                messageDao.deleteStaleInWindow(folder.id, oldestInPage, mapped.map { it.uid })
            }
        }

        // Merged like every other write: the list is authoritative for what it carries, but
        // it still omits fields other responses filled in, and overwriting them loses data.
        upsertPreservingDetail(folder.id, mapped)
        AppLog.d(TAG, "${folder.fullName}: page applied, ${mapped.size} messages")
    }

    /** Carries over primary keys so `@Upsert` updates instead of silently no-op'ing. */
    private suspend fun withExistingIds(
        folderId: Long,
        messages: List<MessageEntity>,
    ): List<MessageEntity> {
        if (messages.isEmpty()) return messages
        val ids = messageDao.idsForUids(folderId, messages.map { it.uid })
            .associate { it.uid to it.id }
        return messages.map { msg -> ids[msg.uid]?.let { msg.copy(id = it) } ?: msg }
    }

    /**
     * Upsert for endpoints whose payload is thinner than `/messages`.
     *
     * `/thread` and `/search` omit fields the list provides — `message_id`, `snippet`,
     * `thread_count`, sometimes the date — and writing those blanks over a good cached row
     * loses real data: a missing `message_id` breaks replying, and a `thread_count` reset to
     * 1 makes the list row stop opening as a conversation. Anything the response does not
     * carry keeps its cached value.
     */
    private suspend fun upsertPreservingDetail(
        folderId: Long,
        incoming: List<MessageEntity>,
        threadCountOverride: Int? = null,
    ) {
        if (incoming.isEmpty()) return
        val cached = messageDao.rowsForUids(folderId, incoming.map { it.uid })
            .associateBy { it.uid }
        val merged = incoming.map { msg ->
            val old = cached[msg.uid]
            val withCount = if (threadCountOverride != null) {
                msg.copy(threadCount = threadCountOverride)
            } else {
                msg
            }
            withCount.mergedWith(old, threadCountOverride)
        }
        messageDao.upsert(merged)
    }

    /**
     * Keeps whatever a thinner payload left out.
     *
     * Every endpoint that is not `/messages` returns a reduced record — `/thread`, `/search`,
     * the category-filtered list and the message detail all omit fields the list provides —
     * so any write that does not merge silently erases them. That is how the AI category was
     * being wiped: each refresh preloaded bodies, wrote the detail's header over the cached
     * row with a null `ia_category`, and the message stopped matching its own filter chip.
     */
    private fun MessageEntity.mergedWith(
        old: MessageEntity?,
        threadCountOverride: Int? = null,
    ): MessageEntity {
        if (old == null) return this
        return copy(
            id = old.id,
            messageId = messageId ?: old.messageId,
            snippet = snippet?.takeIf { it.isNotBlank() } ?: old.snippet,
            references = references ?: old.references,
            threadId = threadId ?: old.threadId,
            threadCount = threadCountOverride ?: maxOf(threadCount, old.threadCount),
            internalDate = internalDate.takeIf { it > 0 } ?: old.internalDate,
            fromAddress = fromAddress ?: old.fromAddress,
            iaCategory = iaCategory ?: old.iaCategory,
            color = color ?: old.color,
            // Once a row has been seen in the paged list it stays prunable, even if a later
            // category or search response rewrites it.
            pagedIn = pagedIn || old.pagedIn,
        )
    }

    // ---- Threads ----

    fun observeThread(threadId: String): Flow<List<MessageEntity>> =
        messageDao.observeThread(threadId)

    /**
     * Loads a whole conversation. Replies the user sent live in Sent while the received
     * copies live in INBOX, so each message is filed under the folder the API reports rather
     * than the one the thread was opened from.
     */
    suspend fun syncThread(accountId: Long, threadId: String, folderFullName: String) =
        withContext(Dispatchers.IO) {
            val dtos = api.thread(threadId, folderFullName).requireBody()
            if (dtos.isEmpty()) return@withContext
            val foldersByName = folderDao.findFoldersForAccount(accountId).associateBy { it.fullName }
            val fallback = foldersByName[folderFullName]
            dtos.groupBy { it.folder ?: folderFullName }.forEach { (name, group) ->
                val folder = foldersByName[name] ?: fallback ?: return@forEach
                // The response IS the conversation, so its size is the authoritative count —
                // more reliable than the per-message thread_count, which comes back as 1 here.
                upsertPreservingDetail(
                    folderId = folder.id,
                    incoming = group.map { it.toEntity(folder.id) },
                    threadCountOverride = dtos.size,
                )
            }
        }

    // ---- Search ----

    /**
     * Server-side FTS (§4.11). The endpoint is per-folder, so the mailbox's folders are
     * queried in parallel — results are cached 120s server-side, which keeps this cheap.
     */
    suspend fun searchOnServer(accountId: Long, query: String): Int = withContext(Dispatchers.IO) {
        if (query.length < MIN_SEARCH_LENGTH) return@withContext 0
        val folders = folderDao.findFoldersForAccount(accountId)
            .filter { it.kind != FolderKind.JUNK }
            .sortedBy { searchPriority(it.kind) }
            .take(MAX_SEARCH_FOLDERS)

        val batches = coroutineScope {
            folders.map { folder ->
                async {
                    val dtos = runCatching {
                        api.search(query, folder.fullName).requireBody()
                    }.getOrElse {
                        AppLog.w(TAG, "search failed in ${folder.fullName}: ${it.message}")
                        emptyList()
                    }
                    folder to dtos
                }
            }.map { it.await() }
        }

        var total = 0
        for ((folder, dtos) in batches) {
            if (dtos.isEmpty()) continue
            // Search returns a reduced record (no message_id, no snippet, folder null), so
            // merge rather than overwrite whatever the list already cached.
            upsertPreservingDetail(folder.id, dtos.map { it.toEntity(folder.id) })
            total += dtos.size
        }
        total
    }

    // ---- Message body & attachments ----

    suspend fun ensureBodyDownloaded(messageId: Long) = withContext(Dispatchers.IO) {
        val msg = messageDao.findById(messageId) ?: return@withContext
        if (messageBodyDao.find(messageId) != null) return@withContext
        val folder = folderDao.findById(msg.folderId) ?: return@withContext

        val detail = api.message(msg.uid, folder.fullName).requireBody()
        storeDetail(messageId, msg.uid, folder, detail)
    }

    /**
     * Fills the body cache for the newest messages of a folder that do not have one, so they
     * open instantly and still open with no connection.
     *
     * One `messages/batch` call carries every body and attachment list at once (§4.17) — the
     * alternative is one request per message, which is why this is worth doing at all. Bodies
     * are heavy, so only [limit] of them are pulled, and the rest of the page is handed to
     * `prefetch`, which warms the *server's* cache without shipping anything to the phone.
     */
    suspend fun preloadBodies(folder: FolderEntity, limit: Int = PRELOAD_BODIES) =
        withContext(Dispatchers.IO) {
            val pending = messageDao.withoutBody(folder.id, limit + PREFETCH_AHEAD)
            if (pending.isEmpty()) return@withContext
            val toStore = pending.take(limit)
            val toWarm = pending.drop(limit)

            runCatching {
                val details = api.messageBatch(folder.fullName, toStore.map { it.uid })
                    .requireBody()
                val byUid = toStore.associateBy { it.uid }
                details.forEach { detail ->
                    val message = byUid[detail.uid] ?: return@forEach
                    storeDetail(message.id, message.uid, folder, detail)
                }
            }.onFailure { AppLog.d(TAG, "preload failed for ${folder.fullName}: $it") }

            if (toWarm.isNotEmpty()) {
                runCatching { api.prefetch(folder.fullName, toWarm.map { it.uid }).requireSuccess() }
            }
        }

    /** Writes one message detail into the cache: header, attachments and body. */
    private suspend fun storeDetail(
        messageId: Long,
        uid: String,
        folder: FolderEntity,
        detail: MessageDetailDto,
    ) {
        // The detail carries fields the list omits (references, message_id) — and omits some
        // the list carries, so it merges rather than overwrites.
        val header = detail.header().toEntity(folder.id).copy(id = messageId)
        messageDao.upsert(listOf(header.mergedWith(messageDao.findById(messageId))))

        attachmentDao.deleteForMessage(messageId)
        if (detail.attachments.isNotEmpty()) {
            attachmentDao.insertAll(
                detail.attachments.map { att ->
                    AttachmentEntity(
                        messageId = messageId,
                        partIndex = att.index,
                        filename = att.filename.orEmpty().ifBlank { "adjunto_${att.index}" },
                        mimeType = att.contentType.orEmpty().ifBlank { "application/octet-stream" },
                        sizeBytes = att.size,
                        localPath = null,
                    )
                }
            )
        }

        messageBodyDao.upsert(
            MessageBodyEntity(
                messageId = messageId,
                text = detail.bodyText,
                // Resolve inline parts once, on the way into the cache, so the renderer
                // never has to know the uid or folder they belong to.
                html = InlineImages.rewrite(
                    html = detail.bodyHtml,
                    baseUrl = BuildConfig.API_BASE_URL,
                    uid = uid,
                    folder = folder.fullName,
                ),
                downloadedAt = System.currentTimeMillis(),
            )
        )
    }

    /**
     * Downloads an attachment on demand. Over IMAP the bytes arrived with the body; the API
     * returns metadata only, so this fills in [AttachmentEntity.localPath] the first time.
     */
    suspend fun downloadAttachment(attachmentId: Long): AttachmentEntity? =
        withContext(Dispatchers.IO) {
            val att = attachmentDao.findById(attachmentId) ?: return@withContext null
            att.localPath?.let { path ->
                if (File(path).exists()) return@withContext att
            }
            val msg = messageDao.findById(att.messageId) ?: return@withContext null
            val folder = folderDao.findById(msg.folderId) ?: return@withContext null

            val body = api.attachment(msg.uid, att.partIndex, folder.fullName).requireBody()
            val dir = File(attachmentsDir, att.messageId.toString()).apply { mkdirs() }
            val safeName = att.filename
                .replace(Regex("[/\\\\\\u0000]"), "_")
                .ifBlank { "adjunto" }
            val file = File(dir, "${att.partIndex}_$safeName")
            body.byteStream().use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            attachmentDao.updateLocalPath(att.id, file.absolutePath)
            att.copy(localPath = file.absolutePath)
        }

    // ---- Per-message actions (optimistic, rolled back on failure) ----

    suspend fun markRead(messageId: Long, seen: Boolean = true) = withContext(Dispatchers.IO) {
        val msg = messageDao.findById(messageId) ?: return@withContext
        if (msg.seen == seen) return@withContext
        val folder = folderDao.findById(msg.folderId) ?: return@withContext
        messageDao.updateSeen(messageId, seen)
        try {
            if (seen) {
                api.markRead(msg.uid, folder.fullName).requireSuccess()
            } else {
                api.markUnread(msg.uid, folder.fullName).requireSuccess()
            }
            refreshFolderCounters(folder.accountId)
        } catch (e: Exception) {
            messageDao.updateSeen(messageId, !seen)
            throw e
        }
    }

    /** Marks every message of the thread read in one call (§4.4). */
    suspend fun markThreadRead(messageId: Long) = withContext(Dispatchers.IO) {
        val msg = messageDao.findById(messageId) ?: return@withContext
        val threadId = msg.threadId ?: return@withContext markRead(messageId, true)
        val folder = folderDao.findById(msg.folderId) ?: return@withContext
        messageDao.updateSeenForThread(threadId, true)
        try {
            api.threadRead(threadId).requireSuccess()
            refreshFolderCounters(folder.accountId)
        } catch (e: Exception) {
            messageDao.updateSeenForThread(threadId, msg.seen)
            throw e
        }
    }

    /** The star maps onto the server-side pin — the API exposes no generic \Flagged. */
    suspend fun toggleFlag(messageId: Long) = withContext(Dispatchers.IO) {
        val msg = messageDao.findById(messageId) ?: return@withContext
        val folder = folderDao.findById(msg.folderId) ?: return@withContext
        val pinned = !msg.flagged
        messageDao.updateFlagged(messageId, pinned)
        try {
            if (pinned) {
                api.pin(msg.uid, folder.fullName).requireSuccess()
            } else {
                api.unpin(msg.uid, folder.fullName).requireSuccess()
            }
        } catch (e: Exception) {
            messageDao.updateFlagged(messageId, !pinned)
            throw e
        }
    }

    /** @param color one of red/orange/green/blue/purple, or null to clear it (§4.18). */
    suspend fun setColor(messageId: Long, color: String?) = withContext(Dispatchers.IO) {
        val msg = messageDao.findById(messageId) ?: return@withContext
        val folder = folderDao.findById(msg.folderId) ?: return@withContext
        val previous = msg.color
        messageDao.updateColor(messageId, color)
        try {
            api.setColor(msg.uid, folder.fullName, color).requireSuccess()
        } catch (e: Exception) {
            messageDao.updateColor(messageId, previous)
            throw e
        }
    }

    /**
     * Deletes the whole thread — a list row represents one, and the visible uid is only its
     * representative (§4.4). Falls back to the single message when there is no thread id.
     */
    suspend fun deleteThread(messageId: Long) = withContext(Dispatchers.IO) {
        val msg = messageDao.findById(messageId) ?: return@withContext
        val folder = folderDao.findById(msg.folderId) ?: return@withContext
        val threadId = msg.threadId
        if (threadId == null) {
            deleteMessage(messageId)
            return@withContext
        }
        api.threadDelete(threadId, folder.fullName).requireSuccess()
        messageDao.deleteThreadInFolder(folder.id, threadId)
        refreshFolderCounters(folder.accountId)
    }

    /** Deletes a single message — used from the detail screen, where one is on screen. */
    suspend fun deleteMessage(messageId: Long) = withContext(Dispatchers.IO) {
        val msg = messageDao.findById(messageId) ?: return@withContext
        val folder = folderDao.findById(msg.folderId) ?: return@withContext
        // The backend decides between Trash and purge; deleting from Junk does NOT purge.
        api.delete(msg.uid, folder.fullName).requireSuccess()
        messageDao.deleteById(messageId)
        refreshFolderCounters(folder.accountId)
    }

    suspend fun moveThread(messageId: Long, destination: String) = withContext(Dispatchers.IO) {
        val msg = messageDao.findById(messageId) ?: return@withContext
        val folder = folderDao.findById(msg.folderId) ?: return@withContext
        val threadId = msg.threadId
        if (threadId != null) {
            api.threadMove(threadId, destination, sourceFolder = folder.fullName).requireSuccess()
            messageDao.deleteThreadInFolder(folder.id, threadId)
        } else {
            api.move(msg.uid, folder.fullName, destination).requireSuccess()
            messageDao.deleteById(messageId)
        }
        refreshFolderCounters(folder.accountId)
    }

    suspend fun markSpam(messageId: Long) = withContext(Dispatchers.IO) {
        val msg = messageDao.findById(messageId) ?: return@withContext
        val folder = folderDao.findById(msg.folderId) ?: return@withContext
        api.markSpam(msg.uid, folder.fullName).requireSuccess()
        messageDao.deleteById(messageId)
        refreshFolderCounters(folder.accountId)
    }

    // ---- Sending ----

    suspend fun sendMessage(message: OutgoingMessage) = withContext(Dispatchers.IO) {
        val files = message.attachments.map { att ->
            MultipartBody.Part.createFormData(
                "files",
                att.name,
                att.bytes.toRequestBody(att.mimeType.toMediaTypeOrNull()),
            )
        }
        api.send(
            to = message.to.joinToString(",").asFormField(),
            cc = message.cc.joinToString(",").asFormField(),
            bcc = message.bcc.joinToString(",").asFormField(),
            subject = message.subject.asFormField(),
            body = message.body.asFormField(),
            isHtml = message.isHtml.toString().asFormField(),
            inReplyTo = message.inReplyTo.orEmpty().asFormField(),
            references = message.references.joinToString(" ").asFormField(),
            forwardOf = message.forwardOf.orEmpty().asFormField(),
            files = files,
        ).requireSuccess()

        // The backend files the copy in Sent itself; just refresh so it shows up locally.
        folderDao.findFirstByKind(FolderKind.SENT)?.let { sent ->
            runCatching { syncFolderTop(sent) }
        }
    }

    // ---- Drafts ----

    /** @return the uid of the saved draft, to pass as `prev_uid` on the next save. */
    suspend fun saveDraft(
        message: OutgoingMessage,
        clientDraftId: String,
        previousUid: String?,
        uploadAttachments: Boolean,
    ): String? = withContext(Dispatchers.IO) {
        // "keep" makes the backend carry the previous draft's attachments over, so periodic
        // autosaves do not re-upload megabytes on every keystroke pause (§4.16).
        val mode = if (uploadAttachments) "replace" else "keep"
        val files = if (uploadAttachments) {
            message.attachments.map { att ->
                MultipartBody.Part.createFormData(
                    "files",
                    att.name,
                    att.bytes.toRequestBody(att.mimeType.toMediaTypeOrNull()),
                )
            }
        } else {
            emptyList()
        }
        api.saveDraft(
            to = message.to.joinToString(",").asFormField(),
            cc = message.cc.joinToString(",").asFormField(),
            bcc = message.bcc.joinToString(",").asFormField(),
            subject = message.subject.asFormField(),
            body = message.body.asFormField(),
            inReplyTo = message.inReplyTo.orEmpty().asFormField(),
            references = message.references.joinToString(" ").asFormField(),
            clientDraftId = clientDraftId.asFormField(),
            attachmentsMode = mode.asFormField(),
            prevUid = previousUid.orEmpty().asFormField(),
            files = files,
        ).requireBody().uid
    }

    /**
     * Drops the draft server-side and locally. [uid] takes priority over the tracked id, so
     * a draft opened from the Drafts folder — created by another client, with a
     * `client_draft_id` we never saw — still gets removed.
     */
    suspend fun deleteDraft(
        clientDraftId: String,
        uid: String?,
        localMessageId: Long? = null,
    ) = withContext(Dispatchers.IO) {
        runCatching { api.deleteDraft(clientDraftId, uid).requireSuccess() }
        localMessageId?.let { messageDao.deleteById(it) }
        // Autosaves rotate the uid, so the row still on screen may not be the one the caller
        // knows about — drop whatever the final uid points at too.
        val drafts = folderDao.findFirstByKind(FolderKind.DRAFTS)
        if (drafts != null && uid != null) {
            messageDao.findByUid(drafts.id, uid)?.let { messageDao.deleteById(it.id) }
        }
        // Deliberately no folder refresh here: the backend expunges asynchronously, so
        // re-reading now would list the draft again and put it straight back.
    }

    /** Re-reads the Drafts folder so a just-saved or just-removed draft shows up. */
    suspend fun refreshDraftsFolder() = withContext(Dispatchers.IO) {
        folderDao.findFirstByKind(FolderKind.DRAFTS)?.let { drafts ->
            runCatching { syncFolderTop(drafts) }
        }
        Unit
    }

    suspend fun trackedDraftUid(clientDraftId: String): String? = withContext(Dispatchers.IO) {
        runCatching { api.draft(clientDraftId).bodyOrNull()?.uid }.getOrNull()
    }

    // ---- Folder management ----

    suspend fun createFolder(accountId: Long, name: String) = withContext(Dispatchers.IO) {
        api.createFolder(name).requireSuccess()
        syncFolders(accountId)
    }

    suspend fun renameFolder(accountId: Long, oldName: String, newName: String) =
        withContext(Dispatchers.IO) {
            api.renameFolder(oldName, newName).requireSuccess()
            syncFolders(accountId)
        }

    suspend fun deleteFolder(accountId: Long, name: String) = withContext(Dispatchers.IO) {
        api.deleteFolder(name).requireSuccess()
        syncFolders(accountId)
    }

    suspend fun emptyFolder(folder: FolderEntity) = withContext(Dispatchers.IO) {
        api.emptyFolder(folder.fullName).requireSuccess()
        messageDao.deleteAllForFolder(folder.id)
        refreshFolderCounters(folder.accountId)
    }

    /**
     * Pulls a folder's messages for one AI category straight from the server (§4.2
     * `?category=`).
     *
     * The chips filter the local cache, and the cache only holds the pages paged in so far,
     * so a match sitting deeper in the folder is simply invisible — an INBOX message tagged
     * `Urgente` did not show up under its own chip for exactly this reason. Asking the server
     * for the category is the only way to see all of them without paging the whole mailbox.
     */
    suspend fun syncCategory(folder: FolderEntity, category: String) = withContext(Dispatchers.IO) {
        // §4.2 caps per_page at 100, so a category with more than that needs paging — asking
        // once left "Comercial" showing 100 of its 232. Each page is written as it lands so
        // the list fills in progressively instead of after the last one.
        var page = 1
        while (page <= CATEGORY_MAX_PAGES) {
            val dtos = api.messages(
                folder = folder.fullName,
                page = page,
                perPage = CATEGORY_PAGE_SIZE,
                category = category,
            ).requireBody()
            if (dtos.isEmpty()) return@withContext
            // The filtered response does not echo `ia_category`, so it is stamped from the
            // query: these are the messages the server itself selected for this category.
            upsertPreservingDetail(
                folder.id,
                dtos.map { it.toEntity(folder.id).copy(iaCategory = it.iaCategory ?: category) },
            )
            if (dtos.size < CATEGORY_PAGE_SIZE) return@withContext
            page++
        }
        AppLog.w(TAG, "$category: stopped at $CATEGORY_MAX_PAGES pages, more may remain")
    }

    /**
     * Pulls every given category into the cache, sequentially and tolerating per-category
     * failures.
     *
     * This exists for the chip badges: they count cached rows (the only number that cannot
     * disagree with what tapping the chip shows — `/category-counts` answers for the whole
     * mailbox), so until a category has been synced its badge only counts what ordinary
     * paging happened to bring in. That looked like a bug: "Comercial" said 61 until it was
     * opened, then jumped to 232. Warming the categories right after a folder opens makes
     * the badges converge on their own.
     *
     * Sequential on purpose — this is background warming and should never compete with the
     * user-visible sync for connections.
     *
     * @return false if any category failed, so the caller can schedule a retry.
     */
    suspend fun syncCategories(folder: FolderEntity, categories: List<String>): Boolean =
        withContext(Dispatchers.IO) {
            var allOk = true
            for (category in categories) {
                try {
                    syncCategory(folder, category)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    allOk = false
                    AppLog.w(TAG, "Precalentando $category: ${e.message}")
                }
            }
            allOk
        }

    /**
     * The message exactly as it arrived (§4.17).
     *
     * The backend hands back what the IMAP FETCH returned, so the payload still carries the
     * command preamble — `5320 FETCH (UID 5826 BODY[] {72436}` — in front of the real RFC822.
     * Showing that as "the original" would be showing our transport, not the message.
     */
    suspend fun messageSource(message: MessageEntity): String = withContext(Dispatchers.IO) {
        val folder = folderDao.findById(message.folderId) ?: error("Carpeta desconocida")
        stripImapFetchPreamble(api.messageSource(message.uid, folder.fullName).requireBody().source)
    }

    /** Raw headers of a message (§4.17). */
    suspend fun messageHeaders(message: MessageEntity): String = withContext(Dispatchers.IO) {
        val folder = folderDao.findById(message.folderId) ?: error("Carpeta desconocida")
        api.messageHeaders(message.uid, folder.fullName).requireBody().headers.trim()
    }

    /** Every attachment in the mailbox (§4.17), newest first. Junk and Trash are excluded. */
    suspend fun browseAttachments(page: Int, search: String?): List<AttachmentBrowseDto> =
        withContext(Dispatchers.IO) {
            api.browseAttachments(
                page = page,
                perPage = ATTACHMENTS_PAGE_SIZE,
                search = search?.trim()?.takeIf { it.isNotEmpty() },
            ).requireBody()
        }

    /**
     * Downloads an attachment that is not attached to a cached message — the browser lists
     * parts from folders that were never synced, so there is no local row to hang it off.
     * Returns the file it was written to.
     */
    suspend fun downloadAttachmentTo(
        uid: String,
        index: Int,
        folderFullName: String,
        filename: String,
    ): File = withContext(Dispatchers.IO) {
        val body = api.attachment(uid, index, folderFullName).requireBody()
        // The same filename appears in many messages, so the uid and part number disambiguate
        // through the *directory*: putting them in the name would show the user
        // "1462_4_factura.pdf" in whatever app opens it.
        val dir = File(attachmentsDir, "browse/$uid-$index").apply { mkdirs() }
        val target = File(dir, filename.substringAfterLast('/').ifBlank { "adjunto" })
        body.byteStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        target
    }

    /** Copies an attachment into the user's EXMWS Cloud (§4.17). */
    suspend fun saveAttachmentToCloud(
        uid: String,
        index: Int,
        folderFullName: String,
        subfolder: String? = null,
    ) = withContext(Dispatchers.IO) {
        api.saveAttachmentToCloud(
            uid = uid,
            index = index,
            folder = folderFullName,
            subfolder = subfolder,
        ).requireSuccess()
    }

    // ---- Shared folders (§4.20) ----

    suspend fun folderShares(folder: FolderEntity): List<FolderShareDto> =
        withContext(Dispatchers.IO) {
            api.folderShares(folder.fullName).requireBody()
        }

    /**
     * Grants another user access to one of my folders. Re-granting to the same address
     * changes the permission, which is why this doubles as the "change to write" action.
     */
    suspend fun shareFolder(folder: FolderEntity, grantee: String, canWrite: Boolean) =
        withContext(Dispatchers.IO) {
            api.shareFolder(
                FolderShareRequest(
                    folder = folder.fullName,
                    grantee = grantee.trim(),
                    permission = if (canWrite) "write" else "read",
                )
            ).requireSuccess()
        }

    suspend fun unshareFolder(folder: FolderEntity, grantee: String) = withContext(Dispatchers.IO) {
        api.unshareFolder(folder.fullName, grantee).requireSuccess()
    }

    // ---- Calendar RSVP (§4.22) ----

    /**
     * Reads the meeting request out of a message's `.ics`, or null when there is none to
     * answer. The parsing is the client's job (§4.22), so the file is fetched and handed to
     * [parseCalendarInvite]; a 5 MB ceiling keeps a mislabelled attachment from being pulled
     * into memory whole.
     */
    suspend fun calendarInviteFor(
        message: MessageEntity,
        attachments: List<AttachmentEntity>,
    ): CalendarInvite? = withContext(Dispatchers.IO) {
        val folder = folderDao.findById(message.folderId) ?: return@withContext null
        val part = attachments.firstOrNull { isCalendarAttachment(it.mimeType, it.filename) }
            ?.takeIf { it.sizeBytes in 1..MAX_ICS_BYTES }
            ?: return@withContext null
        runCatching {
            val body = api.attachment(message.uid, part.partIndex, folder.fullName).requireBody()
            parseCalendarInvite(body.string())
        }.getOrNull()
    }

    /** Sends the iTIP reply; the backend mails it from the user's own mailbox (§4.22). */
    suspend fun replyToInvite(invite: CalendarInvite, status: InviteReply) =
        withContext(Dispatchers.IO) {
            api.calendarReply(
                CalendarReplyRequest(
                    organizerEmail = invite.organizerEmail,
                    organizerName = invite.organizerName,
                    summary = invite.summary,
                    icalUid = invite.icalUid,
                    status = status.apiValue,
                    startAt = invite.startAt,
                    endAt = invite.endAt,
                    sequence = invite.sequence,
                )
            ).requireSuccess()
        }

    // ---- Accounts (§4.23) ----

    /**
     * The user's mailboxes — primary plus auxiliaries. Read-only: alta and baja happen
     * server-side, so `POST`/`DELETE /api/accounts` are deliberately not wired.
     */
    suspend fun remoteAccounts(): List<RemoteAccountDto> = withContext(Dispatchers.IO) {
        api.accounts().requireBody()
    }

    // ---- Followups (§4.19) ----

    suspend fun followups(): FollowupListDto = withContext(Dispatchers.IO) {
        api.followups().requireBody()
    }

    /**
     * "Remind me about this mail on X". Upserts by (folder, uid), so asking twice about the
     * same message moves the existing reminder instead of stacking a second one.
     *
     * The uid travels as an int here — the only place in the API where it is not a string.
     */
    suspend fun createFollowup(message: MessageEntity, dueAt: String, note: String) =
        withContext(Dispatchers.IO) {
            val folder = folderDao.findById(message.folderId) ?: error("Carpeta desconocida")
            api.createFollowup(
                FollowupCreateDto(
                    folder = folder.fullName,
                    uid = message.uid.toLongOrNull() ?: error("uid no numérico"),
                    dueAt = dueAt,
                    note = note.take(FOLLOWUP_NOTE_MAX),
                )
            ).requireBody()
        }

    suspend fun updateFollowup(id: Long, dueAt: String?, note: String?) =
        withContext(Dispatchers.IO) {
            api.updateFollowup(id, FollowupUpdateDto(dueAt, note?.take(FOLLOWUP_NOTE_MAX)))
                .requireSuccess()
        }

    suspend fun followupDone(id: Long) = withContext(Dispatchers.IO) {
        api.followupDone(id).requireSuccess()
    }

    suspend fun deleteFollowup(id: Long) = withContext(Dispatchers.IO) {
        api.deleteFollowup(id).requireSuccess()
    }

    // ---- AI (§4.21) ----

    /** Summary of a conversation. The model is given plain text only, never HTML. */
    suspend fun summarize(subject: String, messages: List<AiMessageDto>): String =
        withContext(Dispatchers.IO) {
            api.summarize(SummarizeRequest(subject, messages)).requireBody().summary
        }

    /**
     * Three suggested replies. The backend answers 502 when `messages` is empty — verified
     * against the live endpoint — so the caller must supply the thread being answered.
     */
    suspend fun aiDraft(
        subject: String,
        messages: List<AiMessageDto>,
        myDraft: String,
        isReply: Boolean,
    ): List<AiDraftOptionDto> = withContext(Dispatchers.IO) {
        api.aiDraft(AiDraftRequest(subject, messages, myDraft, isReply)).requireBody().options
    }

    suspend fun translate(text: String, language: String): String = withContext(Dispatchers.IO) {
        api.translate(TranslateRequest(text, language)).requireBody().text
    }

    /** Keeps the order and count of [segments], so each one can go back where it came from. */
    suspend fun translateSegments(segments: List<String>, language: String): List<String> =
        withContext(Dispatchers.IO) {
            api.translateSegments(TranslateSegmentsRequest(segments, language))
                .requireBody().segments
        }

    /** Mailbox storage usage (§4.15). Null when the backend does not report it. */
    suspend fun quota(): QuotaDto? = withContext(Dispatchers.IO) {
        runCatching { api.quota().requireBody() }.getOrNull()?.takeIf { it.total > 0 }
    }

    suspend fun wipeLocalData() {
        loadedPages.clear()
        accountDao.deleteAll()
        runCatching { attachmentsDir.deleteRecursively() }
    }

    private companion object {
        const val TAG = "MailRepository"
        const val PAGE_SIZE = 30
        /** A meeting request is a few KB; anything larger is not an .ics worth reading. */
        const val MAX_ICS_BYTES = 5L * 1024 * 1024
        /** The largest page §4.2 allows. */
        const val CATEGORY_PAGE_SIZE = 100
        /** Ceiling on the paging loop — 1200 messages in one category, then it gives up loudly. */
        const val CATEGORY_MAX_PAGES = 12
        const val ATTACHMENTS_PAGE_SIZE = 50
        /** §4.19 caps the note at 2000 characters. */
        const val FOLLOWUP_NOTE_MAX = 2000
        /** Bodies pulled into the cache per folder visit — enough to cover a screenful. */
        const val PRELOAD_BODIES = 15
        /** Warmed server-side beyond those, which costs the phone nothing. */
        const val PREFETCH_AHEAD = 15
        const val MIN_SEARCH_LENGTH = 2
        const val MAX_SEARCH_FOLDERS = 8
    }
}

private fun searchPriority(kind: FolderKind): Int = when (kind) {
    FolderKind.INBOX -> 0
    FolderKind.SENT -> 1
    FolderKind.ARCHIVE -> 2
    FolderKind.DRAFTS -> 3
    FolderKind.OTHER -> 4
    FolderKind.TRASH -> 5
    FolderKind.JUNK -> 6
}

private fun String.asFormField(): RequestBody =
    toRequestBody("text/plain".toMediaTypeOrNull())

private fun FolderDto.toEntity(
    accountId: Long,
    existing: FolderEntity?,
    kind: FolderKind,
) = FolderEntity(
    id = existing?.id ?: 0,
    accountId = accountId,
    fullName = name,
    name = displayName?.takeIf { it.isNotBlank() } ?: name.substringAfterLast('/'),
    kind = kind,
    holdsMessages = true,
    messageCount = messageCount,
    unseenCount = unseenCount,
    isShared = isShared,
    sharedOwner = sharedOwner,
    sharedRights = sharedRights,
    lastSyncedAt = existing?.lastSyncedAt,
)

private fun MessageDto.toEntity(folderId: Long) = MessageEntity(
    folderId = folderId,
    uid = uid,
    messageId = messageId,
    subject = subject.orEmpty(),
    from = fromName?.takeIf { it.isNotBlank() } ?: fromAddress.orEmpty(),
    fromAddress = fromAddress,
    to = to.takeIf { it.isNotEmpty() }?.joinToString(", "),
    cc = cc.takeIf { it.isNotEmpty() }?.joinToString(", "),
    internalDate = MailDates.parse(date),
    seen = isRead,
    flagged = isPinned,
    answered = answeredAt != null,
    snippet = snippet,
    hasAttachments = hasAttachments,
    color = color,
    threadId = threadId,
    threadCount = threadCount,
    iaCategory = iaCategory,
    references = references,
    answeredAt = MailDates.parseOrNull(answeredAt),
    forwardedAt = MailDates.parseOrNull(forwardedAt),
)
