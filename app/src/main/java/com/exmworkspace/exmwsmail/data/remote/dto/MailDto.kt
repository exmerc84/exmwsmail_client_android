package com.exmworkspace.exmwsmail.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FolderDto(
    val name: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("message_count") val messageCount: Int = 0,
    @SerialName("unseen_count") val unseenCount: Int = 0,
    @SerialName("is_shared") val isShared: Boolean = false,
    @SerialName("shared_owner") val sharedOwner: String? = null,
    @SerialName("shared_rights") val sharedRights: String? = null,
)

@Serializable
data class MessageDto(
    val uid: String,
    @SerialName("message_id") val messageId: String? = null,
    @SerialName("from_address") val fromAddress: String? = null,
    @SerialName("from_name") val fromName: String? = null,
    val to: List<String> = emptyList(),
    val cc: List<String> = emptyList(),
    val subject: String? = null,
    val date: String? = null,
    val snippet: String? = null,
    val folder: String? = null,
    @SerialName("is_read") val isRead: Boolean = false,
    @SerialName("has_attachments") val hasAttachments: Boolean = false,
    @SerialName("is_pinned") val isPinned: Boolean = false,
    val color: String? = null,
    @SerialName("answered_at") val answeredAt: String? = null,
    @SerialName("forwarded_at") val forwardedAt: String? = null,
    val references: String? = null,
    @SerialName("thread_id") val threadId: String? = null,
    @SerialName("thread_count") val threadCount: Int = 1,
    @SerialName("ia_category") val iaCategory: String? = null,
)

@Serializable
data class AttachmentDto(
    val index: Int,
    val filename: String? = null,
    @SerialName("content_type") val contentType: String? = null,
    val size: Long = 0,
)

/** `GET /messages/{uid}` — every `MessageDto` field plus the rendered body. */
@Serializable
data class MessageDetailDto(
    val uid: String,
    @SerialName("message_id") val messageId: String? = null,
    @SerialName("from_address") val fromAddress: String? = null,
    @SerialName("from_name") val fromName: String? = null,
    val to: List<String> = emptyList(),
    val cc: List<String> = emptyList(),
    val subject: String? = null,
    val date: String? = null,
    val snippet: String? = null,
    val folder: String? = null,
    @SerialName("is_read") val isRead: Boolean = false,
    @SerialName("has_attachments") val hasAttachments: Boolean = false,
    @SerialName("is_pinned") val isPinned: Boolean = false,
    val color: String? = null,
    @SerialName("answered_at") val answeredAt: String? = null,
    @SerialName("forwarded_at") val forwardedAt: String? = null,
    val references: String? = null,
    @SerialName("thread_id") val threadId: String? = null,
    @SerialName("thread_count") val threadCount: Int = 1,
    @SerialName("ia_category") val iaCategory: String? = null,
    @SerialName("body_html") val bodyHtml: String? = null,
    @SerialName("body_text") val bodyText: String? = null,
    val attachments: List<AttachmentDto> = emptyList(),
) {
    fun header(): MessageDto = MessageDto(
        uid = uid,
        messageId = messageId,
        fromAddress = fromAddress,
        fromName = fromName,
        to = to,
        cc = cc,
        subject = subject,
        date = date,
        snippet = snippet,
        folder = folder,
        isRead = isRead,
        hasAttachments = hasAttachments,
        isPinned = isPinned,
        color = color,
        answeredAt = answeredAt,
        forwardedAt = forwardedAt,
        references = references,
        threadId = threadId,
        threadCount = threadCount,
        iaCategory = iaCategory,
    )
}

@Serializable
data class SimpleMessageDto(
    val message: String? = null,
)

@Serializable
data class DraftSavedDto(
    val message: String? = null,
    val uid: String? = null,
)

@Serializable
data class DraftLookupDto(
    val uid: String? = null,
)

/** §4.17 — both come wrapped in a one-field object, not as plain text. */
@Serializable
data class MessageSourceDto(val source: String = "")

@Serializable
data class MessageHeadersDto(val headers: String = "")

/** One row of the mailbox-wide attachment browser (§4.17). */
@Serializable
data class AttachmentBrowseDto(
    val uid: String = "",
    val folder: String = "",
    val subject: String = "",
    @SerialName("from_address") val fromAddress: String? = null,
    @SerialName("from_name") val fromName: String? = null,
    @SerialName("date_parsed") val dateParsed: String? = null,
    val filename: String = "",
    @SerialName("content_type") val contentType: String? = null,
    val size: Long = 0,
    /** Position of the part inside its message — needed to download or save it. */
    val index: Int = 0,
)

/**
 * §4.15. The backend names these `used_bytes` / `limit_bytes` (it also sends the same figures
 * pre-converted to MB/GB and a percent, which we derive instead of trusting twice).
 */
@Serializable
data class QuotaDto(
    @SerialName("used_bytes") val used: Long = 0,
    @SerialName("limit_bytes") val total: Long = 0,
)

@Serializable
data class DeviceRegisterRequest(
    @SerialName("fcm_token") val fcmToken: String,
    val platform: String = "android",
    @SerialName("device_name") val deviceName: String,
    @SerialName("app_version") val appVersion: String,
)

@Serializable
data class DeviceDto(
    val id: Long,
    val platform: String? = null,
    @SerialName("device_name") val deviceName: String? = null,
    @SerialName("app_version") val appVersion: String? = null,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)
