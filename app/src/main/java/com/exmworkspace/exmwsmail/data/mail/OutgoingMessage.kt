package com.exmworkspace.exmwsmail.data.mail

/**
 * A message to hand to `POST /api/emails/send` (§4.12). The backend builds the MIME and
 * files the copy in Sent, so nothing here is IMAP-aware.
 */
data class OutgoingMessage(
    val to: List<String>,
    val cc: List<String> = emptyList(),
    val bcc: List<String> = emptyList(),
    val subject: String,
    val body: String,
    val isHtml: Boolean = false,
    /** Message-Id of the original when replying. */
    val inReplyTo: String? = null,
    /**
     * The original's full References chain plus its Message-Id — without it the backend
     * cannot keep replies-to-replies in the same thread (§4.12).
     */
    val references: List<String> = emptyList(),
    /** Message-Id of the original when forwarding. */
    val forwardOf: String? = null,
    val attachments: List<FileAttachment> = emptyList(),
)

data class FileAttachment(
    val name: String,
    val mimeType: String,
    val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FileAttachment) return false
        if (name != other.name) return false
        if (mimeType != other.mimeType) return false
        if (!bytes.contentEquals(other.bytes)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}
