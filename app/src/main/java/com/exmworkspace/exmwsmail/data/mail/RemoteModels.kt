package com.exmworkspace.exmwsmail.data.mail

import java.util.Date

enum class FolderKind {
    INBOX, SENT, DRAFTS, TRASH, JUNK, ARCHIVE, OTHER
}

data class RemoteFolder(
    val fullName: String,
    val name: String,
    val kind: FolderKind,
    val holdsMessages: Boolean,
)

data class RemoteMessageHeader(
    val uid: Long,
    val subject: String,
    val from: String,
    val to: String? = null,
    val cc: String? = null,
    val date: Date?,
    val seen: Boolean,
    val flagged: Boolean,
    val answered: Boolean,
)

data class RemoteAttachment(
    val partIndex: Int,
    val filename: String,
    val mimeType: String,
    val sizeBytes: Long,
    val inline: Boolean,
    val contentId: String?,
    val bytes: ByteArray,
)

data class RemoteMessageContent(
    val text: String?,
    val html: String?,
    val attachments: List<RemoteAttachment>,
)
