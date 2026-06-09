package com.exmworkspace.exmwsmail.data.mail

import com.exmworkspace.exmwsmail.util.AppLog

import com.exmworkspace.exmwsmail.data.prefs.CredentialStore
import com.sun.mail.imap.IMAPFolder
import com.sun.mail.imap.IMAPStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream
import javax.mail.Address
import javax.mail.FetchProfile
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.UIDFolder
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart

data class FolderSyncBatch(
    val uidValidity: Long,
    val serverMessageCount: Int,
    val headers: List<RemoteMessageHeader>,
    val syncedUidRange: LongRange?,
)

class ImapGateway(
    private val credentialStore: CredentialStore,
) {
    private val mutex = Mutex()
    private var store: IMAPStore? = null

    suspend fun listFolders(): List<RemoteFolder> = withStore { store ->
        val roots = mutableListOf<Pair<String, Folder>>()
        runCatching { roots += "default" to store.defaultFolder }
        runCatching {
            store.personalNamespaces?.forEachIndexed { i, ns -> roots += "personal[$i]" to ns }
        }
        runCatching {
            store.sharedNamespaces?.forEachIndexed { i, ns -> roots += "shared[$i]" to ns }
        }
        // "Other users" namespace (second element of IMAP NAMESPACE). Some servers
        // (Mailcow/Dovecot with `shared/` prefix) expose folders shared with this account here.
        runCatching {
            store.getUserNamespaces(null)?.forEachIndexed { i, ns -> roots += "user[$i]" to ns }
        }

        // Some servers (Cyrus / Dovecot with subscriptions-only) only expose
        // user-created folders via LSUB. Pull both LIST and LSUB and union them.
        val rawSeen = mutableMapOf<String, Pair<String, IMAPFolder>>()
        for ((label, root) in roots) {
            runCatching {
                root.list("*").forEach { f ->
                    (f as? IMAPFolder)?.let {
                        rawSeen.getOrPut(it.fullName) { "$label/LIST" to it }
                    }
                }
            }
            runCatching {
                root.listSubscribed("*").forEach { f ->
                    (f as? IMAPFolder)?.let {
                        rawSeen.getOrPut(it.fullName) { "$label/LSUB" to it }
                    }
                }
            }
        }

        val folders = rawSeen.values.toList()

        AppLog.d(
            "ImapGateway",
            "listFolders: server returned ${folders.size} folders",
        )
        folders.forEach { (origin, imap) ->
            AppLog.d(
                "ImapGateway",
                "  - [$origin] fullName='${imap.fullName}' name='${imap.name}' attrs=${imap.attributes?.toList()} type=${imap.type}",
            )
        }

        folders.mapNotNull { (_, imap) ->
            val attrs = imap.attributes.orEmpty().map { it.lowercase() }
            // \Noselect (RFC 3501) / \NonExistent (RFC 5258) → cannot be opened, hide it.
            if ("\\noselect" in attrs || "\\nonexistent" in attrs) return@mapNotNull null
            RemoteFolder(
                fullName = imap.fullName,
                name = imap.name,
                kind = inferKind(imap.fullName, attrs),
                holdsMessages = true,
            )
        }
    }

    suspend fun syncFolderTop(folderFullName: String, limit: Int): FolderSyncBatch = withStore { store ->
        val folder = store.getFolder(folderFullName) as IMAPFolder
        folder.open(Folder.READ_ONLY)
        try {
            val uidValidity = folder.uidValidity
            val total = folder.messageCount
            AppLog.d("ImapGateway", "$folderFullName: total=$total uidValidity=$uidValidity")
            if (total == 0) {
                return@withStore FolderSyncBatch(uidValidity, 0, emptyList(), null)
            }
            val start = maxOf(1, total - limit + 1)
            val msgs = folder.getMessages(start, total)
            prefetch(folder, msgs)
            val headers = msgs.map { toHeader(folder, it) }.sortedByDescending { it.uid }
            val range = headers.last().uid..headers.first().uid
            AppLog.d(
                "ImapGateway",
                "$folderFullName: fetched ${headers.size} uids, range=$range, max=${headers.first().uid}",
            )
            FolderSyncBatch(uidValidity, total, headers, range)
        } finally {
            runCatching { folder.close(false) }
        }
    }

    suspend fun syncFolderOlderThan(
        folderFullName: String,
        beforeUid: Long,
        limit: Int,
    ): FolderSyncBatch = withStore { store ->
        val folder = store.getFolder(folderFullName) as IMAPFolder
        folder.open(Folder.READ_ONLY)
        try {
            val uidValidity = folder.uidValidity
            val total = folder.messageCount
            if (beforeUid <= 1) {
                return@withStore FolderSyncBatch(uidValidity, total, emptyList(), null)
            }
            val candidates = folder.getMessagesByUID(1L, beforeUid - 1)
            if (candidates.isEmpty()) {
                return@withStore FolderSyncBatch(uidValidity, total, emptyList(), null)
            }
            val window = candidates.takeLast(limit).toTypedArray()
            prefetch(folder, window)
            val headers = window.map { toHeader(folder, it) }.sortedByDescending { it.uid }
            val range = headers.last().uid..headers.first().uid
            FolderSyncBatch(uidValidity, total, headers, range)
        } finally {
            runCatching { folder.close(false) }
        }
    }

    suspend fun fetchMessageContent(folderFullName: String, uid: Long): RemoteMessageContent = withStore { store ->
        val folder = store.getFolder(folderFullName) as IMAPFolder
        folder.open(Folder.READ_ONLY)
        try {
            val msg = folder.getMessageByUID(uid) ?: error("Mensaje no encontrado")
            val collector = ContentCollector()
            walk(msg, collector)
            RemoteMessageContent(
                text = collector.text,
                html = collector.html,
                attachments = collector.attachments,
            )
        } finally {
            runCatching { folder.close(false) }
        }
    }

    suspend fun setSeen(folderFullName: String, uid: Long, seen: Boolean) = withStore { store ->
        val folder = store.getFolder(folderFullName) as IMAPFolder
        folder.open(Folder.READ_WRITE)
        try {
            val msg = folder.getMessageByUID(uid) ?: return@withStore
            msg.setFlag(Flags.Flag.SEEN, seen)
        } finally {
            runCatching { folder.close(false) }
        }
    }

    suspend fun setFlagged(folderFullName: String, uid: Long, flagged: Boolean) = withStore { store ->
        val folder = store.getFolder(folderFullName) as IMAPFolder
        folder.open(Folder.READ_WRITE)
        try {
            val msg = folder.getMessageByUID(uid) ?: return@withStore
            msg.setFlag(Flags.Flag.FLAGGED, flagged)
        } finally {
            runCatching { folder.close(false) }
        }
    }

    suspend fun moveMessage(
        fromFolderFullName: String,
        uid: Long,
        toFolderFullName: String,
    ) = withStore { store ->
        val from = store.getFolder(fromFolderFullName) as IMAPFolder
        val to = store.getFolder(toFolderFullName) as IMAPFolder
        from.open(Folder.READ_WRITE)
        try {
            val msg = from.getMessageByUID(uid) ?: return@withStore
            // Try MOVE extension first; fall back to COPY + DELETE + EXPUNGE.
            val moved = runCatching {
                from.moveMessages(arrayOf(msg), to)
            }.isSuccess
            if (!moved) {
                from.copyMessages(arrayOf(msg), to)
                msg.setFlag(Flags.Flag.DELETED, true)
                runCatching { from.expunge() }
            }
        } finally {
            runCatching { from.close(false) }
        }
    }

    suspend fun searchHeaders(
        folderFullName: String,
        query: String,
        limit: Int = 100,
    ): FolderSyncBatch = withStore { store ->
        val folder = store.getFolder(folderFullName) as IMAPFolder
        folder.open(Folder.READ_ONLY)
        try {
            val uidValidity = folder.uidValidity
            val total = folder.messageCount
            if (total == 0 || query.isBlank()) {
                return@withStore FolderSyncBatch(uidValidity, total, emptyList(), null)
            }
            val term = javax.mail.search.OrTerm(
                javax.mail.search.SubjectTerm(query),
                javax.mail.search.FromStringTerm(query),
            )
            val msgs = folder.search(term).takeLast(limit).toTypedArray()
            if (msgs.isEmpty()) {
                return@withStore FolderSyncBatch(uidValidity, total, emptyList(), null)
            }
            prefetch(folder, msgs)
            val headers = msgs.map { toHeader(folder, it) }.sortedByDescending { it.uid }
            val range = headers.last().uid..headers.first().uid
            FolderSyncBatch(uidValidity, total, headers, range)
        } finally {
            runCatching { folder.close(false) }
        }
    }

    suspend fun deletePermanently(folderFullName: String, uid: Long) = withStore { store ->
        val folder = store.getFolder(folderFullName) as IMAPFolder
        folder.open(Folder.READ_WRITE)
        try {
            val msg = folder.getMessageByUID(uid) ?: return@withStore
            msg.setFlag(Flags.Flag.DELETED, true)
            runCatching { folder.expunge() }
        } finally {
            runCatching { folder.close(false) }
        }
    }

    suspend fun appendMessage(folderFullName: String, mime: javax.mail.internet.MimeMessage) =
        withStore { store ->
            val folder = store.getFolder(folderFullName) as IMAPFolder
            folder.open(Folder.READ_WRITE)
            try {
                mime.setFlag(Flags.Flag.SEEN, true)
                folder.appendMessages(arrayOf(mime))
            } finally {
                runCatching { folder.close(false) }
            }
        }

    suspend fun findFolderByKind(kind: FolderKind): String? {
        return listFolders().firstOrNull { it.kind == kind }?.fullName
    }

    suspend fun close() = mutex.withLock {
        runCatching { store?.close() }
        store = null
    }

    private suspend fun <T> withStore(block: (IMAPStore) -> T): T = mutex.withLock {
        withContext(Dispatchers.IO) {
            block(ensureStore())
        }
    }

    private suspend fun ensureStore(): IMAPStore {
        val current = store
        if (current != null && current.isConnected) return current
        runCatching { current?.close() }
        val creds = credentialStore.load() ?: error("Sin credenciales almacenadas")
        val s = MailSessions.imapSession().getStore("imaps") as IMAPStore
        s.connect(MailConfig.HOST, MailConfig.IMAP_PORT, creds.email, creds.password)
        store = s
        return s
    }

    private fun prefetch(folder: IMAPFolder, msgs: Array<Message>) {
        val fp = FetchProfile().apply {
            add(FetchProfile.Item.ENVELOPE)
            add(FetchProfile.Item.FLAGS)
            add(UIDFolder.FetchProfileItem.UID)
        }
        folder.fetch(msgs, fp)
    }

    private fun toHeader(folder: IMAPFolder, msg: Message): RemoteMessageHeader =
        RemoteMessageHeader(
            uid = folder.getUID(msg),
            subject = msg.subject?.takeIf { it.isNotBlank() } ?: "(sin asunto)",
            from = friendlyFrom(msg.from?.firstOrNull()),
            to = msg.getRecipients(Message.RecipientType.TO)?.joinToString(", ") { friendlyFrom(it) },
            cc = msg.getRecipients(Message.RecipientType.CC)?.joinToString(", ") { friendlyFrom(it) },
            date = msg.sentDate ?: msg.receivedDate,
            seen = msg.flags.contains(Flags.Flag.SEEN),
            flagged = msg.flags.contains(Flags.Flag.FLAGGED),
            answered = msg.flags.contains(Flags.Flag.ANSWERED),
        )

    private fun inferKind(fullName: String, lowerAttrs: List<String>): FolderKind = when {
        fullName.equals("INBOX", ignoreCase = true) -> FolderKind.INBOX
        "\\sent" in lowerAttrs -> FolderKind.SENT
        "\\drafts" in lowerAttrs -> FolderKind.DRAFTS
        "\\trash" in lowerAttrs -> FolderKind.TRASH
        "\\junk" in lowerAttrs -> FolderKind.JUNK
        "\\archive" in lowerAttrs -> FolderKind.ARCHIVE
        else -> FolderKind.OTHER
    }

    private fun friendlyFrom(addr: Address?): String {
        if (addr == null) return ""
        if (addr is InternetAddress) {
            return addr.personal?.takeIf { it.isNotBlank() } ?: addr.address ?: addr.toString()
        }
        return addr.toString()
    }

    private class ContentCollector {
        var text: String? = null
        var html: String? = null
        val attachments = mutableListOf<RemoteAttachment>()
        private var nextIndex = 0
        fun nextPartIndex(): Int = nextIndex++
    }

    private fun walk(part: Part, collector: ContentCollector) {
        val disposition = part.disposition?.lowercase()
        val filename = runCatching { part.fileName }.getOrNull()
        val hasFilename = !filename.isNullOrBlank()
        val isAttachment = disposition == Part.ATTACHMENT.lowercase() ||
            (hasFilename && !part.isMimeType("multipart/*"))

        if (isAttachment) {
            val bytes = readBytes(part)
            val mime = part.contentType.lowercase().substringBefore(';').trim().ifBlank { "application/octet-stream" }
            val cid = (part as? MimeBodyPart)?.contentID?.removePrefix("<")?.removeSuffix(">")
            collector.attachments += RemoteAttachment(
                partIndex = collector.nextPartIndex(),
                filename = filename ?: "adjunto",
                mimeType = mime,
                sizeBytes = part.size.toLong().coerceAtLeast(bytes.size.toLong()),
                inline = disposition == Part.INLINE.lowercase(),
                contentId = cid,
                bytes = bytes,
            )
            return
        }

        if (part.isMimeType("text/plain") && collector.text == null) {
            collector.text = readString(part)
            return
        }
        if (part.isMimeType("text/html") && collector.html == null) {
            collector.html = readString(part)
            return
        }
        if (part.isMimeType("multipart/*")) {
            val mp = runCatching { part.content as? Multipart }.getOrNull() ?: return
            for (i in 0 until mp.count) {
                walk(mp.getBodyPart(i), collector)
            }
        }
    }

    private fun readString(part: Part): String? {
        val obj = runCatching { part.content }.getOrNull()
        return when (obj) {
            is String -> obj
            is InputStream -> obj.bufferedReader().use { it.readText() }
            null -> null
            else -> obj.toString()
        }
    }

    private fun readBytes(part: Part): ByteArray =
        part.inputStream.use { it.readBytes() }
}
