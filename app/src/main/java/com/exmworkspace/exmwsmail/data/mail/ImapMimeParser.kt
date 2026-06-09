package com.exmworkspace.exmwsmail.data.mail

import com.sun.mail.imap.IMAPFolder
import java.io.InputStream
import javax.mail.Address
import javax.mail.FetchProfile
import javax.mail.Flags
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.UIDFolder
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart

// JavaMail MIME parsing helpers: header mapping, body/attachment walking.
// Pure (no IMAP session state) so they can live apart from ImapGateway.

internal fun prefetch(folder: IMAPFolder, msgs: Array<Message>) {
    val fp = FetchProfile().apply {
        add(FetchProfile.Item.ENVELOPE)
        add(FetchProfile.Item.FLAGS)
        add(UIDFolder.FetchProfileItem.UID)
    }
    folder.fetch(msgs, fp)
}

internal fun toHeader(folder: IMAPFolder, msg: Message): RemoteMessageHeader =
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

private fun friendlyFrom(addr: Address?): String {
    if (addr == null) return ""
    if (addr is InternetAddress) {
        return addr.personal?.takeIf { it.isNotBlank() } ?: addr.address ?: addr.toString()
    }
    return addr.toString()
}

internal class ContentCollector {
    var text: String? = null
    var html: String? = null
    val attachments = mutableListOf<RemoteAttachment>()
    private var nextIndex = 0
    fun nextPartIndex(): Int = nextIndex++
}

internal fun walk(part: Part, collector: ContentCollector) {
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
