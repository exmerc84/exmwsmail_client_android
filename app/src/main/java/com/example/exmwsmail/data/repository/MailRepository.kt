package com.example.exmwsmail.data.repository

import com.example.exmwsmail.data.local.dao.AccountDao
import com.example.exmwsmail.data.local.dao.AttachmentDao
import com.example.exmwsmail.data.local.dao.FolderDao
import com.example.exmwsmail.data.local.dao.MessageBodyDao
import com.example.exmwsmail.data.local.dao.MessageDao
import com.example.exmwsmail.data.local.entity.AccountEntity
import com.example.exmwsmail.data.local.entity.AttachmentEntity
import com.example.exmwsmail.data.local.entity.FolderEntity
import com.example.exmwsmail.data.local.entity.MessageBodyEntity
import com.example.exmwsmail.data.local.entity.MessageEntity
import com.example.exmwsmail.data.mail.FolderKind
import com.example.exmwsmail.data.mail.FolderSyncBatch
import com.example.exmwsmail.data.mail.ImapGateway
import com.example.exmwsmail.data.mail.OutgoingMessage
import com.example.exmwsmail.data.mail.RemoteMessageHeader
import com.example.exmwsmail.data.mail.SmtpGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.io.File

data class MessageDetail(
    val message: MessageEntity?,
    val body: MessageBodyEntity?,
    val attachments: List<AttachmentEntity>,
)

class MailRepository(
    private val gateway: ImapGateway,
    private val smtpGateway: SmtpGateway,
    private val accountDao: AccountDao,
    private val folderDao: FolderDao,
    private val messageDao: MessageDao,
    private val messageBodyDao: MessageBodyDao,
    private val attachmentDao: AttachmentDao,
    private val attachmentsDir: File,
) {
    suspend fun sendMessage(message: OutgoingMessage) {
        val mime = smtpGateway.send(message)
        runCatching {
            val sentName = gateway.findFolderByKind(FolderKind.SENT) ?: return@runCatching
            gateway.appendMessage(sentName, mime)
            folderDao.findFirstByKind(FolderKind.SENT)?.let { folder ->
                syncFolderTop(folder, limit = 50)
            }
        }
    }

    fun observeFolders(accountId: Long): Flow<List<FolderEntity>> =
        folderDao.observeForAccount(accountId)

    fun observeMessages(folderId: Long): Flow<List<MessageEntity>> =
        messageDao.observeByFolder(folderId)

    fun searchMessages(accountId: Long, query: String): Flow<List<MessageEntity>> =
        messageDao.search(accountId, query)

    suspend fun searchOnServer(accountId: Long, query: String): Int {
        if (query.isBlank()) return 0
        val folders = folderDao.findFoldersForAccount(accountId)
        var added = 0
        for (folder in folders) {
            try {
                val batch = gateway.searchHeaders(folder.fullName, query, limit = 100)
                if (batch.headers.isEmpty()) continue
                applyBatch(folder, batch)
                added += batch.headers.size
            } catch (_: Exception) {
                // Skip folder on error and continue.
            }
        }
        return added
    }

    fun observeMessageDetail(messageId: Long): Flow<MessageDetail> = combine(
        messageDao.observeById(messageId),
        messageBodyDao.observe(messageId),
        attachmentDao.observe(messageId),
    ) { msg, body, atts -> MessageDetail(msg, body, atts) }

    suspend fun findFolder(folderId: Long): FolderEntity? = folderDao.findById(folderId)

    suspend fun ensureAccount(email: String): Long {
        accountDao.findByEmail(email)?.let { return it.id }
        val id = accountDao.insert(AccountEntity(email = email))
        if (id != -1L) return id
        return accountDao.findByEmail(email)?.id ?: error("No se pudo crear cuenta")
    }

    suspend fun syncInboxTop(limit: Int = 50) {
        val inbox = folderDao.findFirstByKind(FolderKind.INBOX) ?: return
        syncFolderTop(inbox, limit)
    }

    suspend fun inboxMaxUid(): Long {
        val inbox = folderDao.findFirstByKind(FolderKind.INBOX) ?: return 0L
        return messageDao.maxUid(inbox.id) ?: 0L
    }

    suspend fun newestInboxAboveUid(uid: Long): MessageEntity? {
        val inbox = folderDao.findFirstByKind(FolderKind.INBOX) ?: return null
        return messageDao.newestAboveUid(inbox.id, uid)
    }

    suspend fun syncFolders(accountId: Long) {
        val remote = gateway.listFolders()
        if (remote.isEmpty()) return
        val cached = remote.mapNotNull { rf -> folderDao.findByFullName(accountId, rf.fullName) }
        val cachedByName = cached.associateBy { it.fullName }
        val toUpsert = remote.map { rf ->
            val existing = cachedByName[rf.fullName]
            FolderEntity(
                id = existing?.id ?: 0,
                accountId = accountId,
                fullName = rf.fullName,
                name = rf.name,
                kind = rf.kind,
                holdsMessages = rf.holdsMessages,
                uidValidity = existing?.uidValidity,
                lastSyncedAt = existing?.lastSyncedAt,
            )
        }
        folderDao.upsert(toUpsert)
        folderDao.deleteMissing(accountId, remote.map { it.fullName })
    }

    suspend fun syncFolderTop(folder: FolderEntity, limit: Int = 50) {
        val batch = gateway.syncFolderTop(folder.fullName, limit)
        applyBatch(folder, batch)
    }

    suspend fun syncOlder(folder: FolderEntity, limit: Int = 50): Int {
        val oldest = messageDao.minUid(folder.id) ?: return 0
        val batch = gateway.syncFolderOlderThan(folder.fullName, oldest, limit)
        applyBatch(folder, batch)
        return batch.headers.size
    }

    suspend fun ensureBodyDownloaded(messageId: Long) {
        val msg = messageDao.findById(messageId) ?: return
        if (messageBodyDao.find(messageId) != null) return
        val folder = folderDao.findById(msg.folderId) ?: return
        val content = gateway.fetchMessageContent(folder.fullName, msg.uid)

        val msgDir = File(attachmentsDir, messageId.toString()).apply { mkdirs() }
        attachmentDao.deleteForMessage(messageId)
        if (content.attachments.isNotEmpty()) {
            val entities = content.attachments.map { att ->
                val safeName = att.filename
                    .replace(Regex("[/\\\\\\u0000]"), "_")
                    .ifBlank { "adjunto" }
                val file = File(msgDir, "${att.partIndex}_$safeName")
                file.writeBytes(att.bytes)
                AttachmentEntity(
                    messageId = messageId,
                    partIndex = att.partIndex,
                    filename = att.filename.ifBlank { safeName },
                    mimeType = att.mimeType,
                    sizeBytes = att.sizeBytes,
                    inline = att.inline,
                    contentId = att.contentId,
                    localPath = file.absolutePath,
                )
            }
            attachmentDao.insertAll(entities)
        }

        messageBodyDao.upsert(
            MessageBodyEntity(
                messageId = messageId,
                text = content.text,
                html = content.html,
                downloadedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun markRead(messageId: Long, seen: Boolean = true) {
        val msg = messageDao.findById(messageId) ?: return
        if (msg.seen == seen) return
        messageDao.updateSeen(messageId, seen)
        try {
            val folder = folderDao.findById(msg.folderId) ?: return
            gateway.setSeen(folder.fullName, msg.uid, seen)
        } catch (e: Exception) {
            messageDao.updateSeen(messageId, !seen)
            throw e
        }
    }

    suspend fun toggleFlag(messageId: Long) {
        val msg = messageDao.findById(messageId) ?: return
        val newFlag = !msg.flagged
        messageDao.updateFlagged(messageId, newFlag)
        try {
            val folder = folderDao.findById(msg.folderId) ?: return
            gateway.setFlagged(folder.fullName, msg.uid, newFlag)
        } catch (e: Exception) {
            messageDao.updateFlagged(messageId, !newFlag)
            throw e
        }
    }

    suspend fun deleteMessage(messageId: Long) {
        val msg = messageDao.findById(messageId) ?: return
        val sourceFolder = folderDao.findById(msg.folderId) ?: return
        // Already in Trash → permanent delete via \Deleted + EXPUNGE.
        if (sourceFolder.kind == FolderKind.TRASH) {
            gateway.deletePermanently(sourceFolder.fullName, msg.uid)
            messageDao.deleteById(messageId)
            return
        }
        val trashFullName = gateway.findFolderByKind(FolderKind.TRASH)
            ?: error("No se encontró la carpeta Papelera")
        gateway.moveMessage(sourceFolder.fullName, msg.uid, trashFullName)
        messageDao.deleteById(messageId)
        // Refresh the trash folder so the message appears there if user navigates.
        folderDao.findFirstByKind(FolderKind.TRASH)?.let { trashLocal ->
            runCatching { syncFolderTop(trashLocal, limit = 50) }
        }
    }

    suspend fun wipeLocalData() {
        accountDao.deleteAll()
        runCatching { attachmentsDir.deleteRecursively() }
    }

    private suspend fun applyBatch(folder: FolderEntity, batch: FolderSyncBatch) {
        val cachedValidity = folder.uidValidity
        if (cachedValidity != null && cachedValidity != batch.uidValidity) {
            android.util.Log.w(
                "MailRepo",
                "uidValidity changed (${cachedValidity}→${batch.uidValidity}) wiping ${folder.fullName}",
            )
            messageDao.deleteAllForFolder(folder.id)
        }
        folderDao.update(
            folder.copy(
                uidValidity = batch.uidValidity,
                lastSyncedAt = System.currentTimeMillis(),
            )
        )
        if (batch.headers.isEmpty() || batch.syncedUidRange == null) {
            android.util.Log.d("MailRepo", "applyBatch: empty headers for ${folder.fullName}")
            return
        }

        val serverUids = batch.headers.map { it.uid }.toSet()
        val cachedInRange = messageDao
            .uidsInRange(folder.id, batch.syncedUidRange.first, batch.syncedUidRange.last)
            .toSet()
        val toDelete = cachedInRange - serverUids
        if (toDelete.isNotEmpty()) {
            android.util.Log.d(
                "MailRepo",
                "${folder.fullName}: deleting locally-stale uids=$toDelete",
            )
            messageDao.deleteByUids(folder.id, toDelete.toList())
        }

        android.util.Log.d(
            "MailRepo",
            "${folder.fullName}: upserting ${batch.headers.size} headers, range=${batch.syncedUidRange}, max=${batch.headers.first().uid}",
        )
        messageDao.upsert(batch.headers.map { it.toEntity(folder.id) })
    }
}

private fun RemoteMessageHeader.toEntity(folderId: Long) = MessageEntity(
    folderId = folderId,
    uid = uid,
    subject = subject,
    from = from,
    to = to,
    cc = cc,
    internalDate = date?.time ?: 0L,
    seen = seen,
    flagged = flagged,
    answered = answered,
)
