package com.exmworkspace.exmwsmail.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// §4.19 Followups and §4.21 AI. Every shape below was read off a live response rather than
// inferred from the doc, which spells out the requests but not the replies.

/** `GET /api/followups` wraps the list in an object and adds the badge count. */
@Serializable
data class FollowupListDto(
    val items: List<FollowupDto> = emptyList(),
    @SerialName("due_count") val dueCount: Int = 0,
)

@Serializable
data class FollowupDto(
    val id: Long = 0,
    val folder: String = "INBOX",
    /** Int here, unlike the string uid the rest of the API uses (§4.19). */
    val uid: Long = 0,
    @SerialName("message_id") val messageId: String? = null,
    val subject: String? = null,
    @SerialName("from_address") val fromAddress: String? = null,
    @SerialName("from_name") val fromName: String? = null,
    val note: String? = null,
    @SerialName("due_at") val dueAt: String? = null,
    /** "due" while pending; the backend flips it when marked done. */
    val status: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
) {
    val isDone: Boolean get() = status.equals("done", ignoreCase = true)
}

/** Upserts by (folder, uid): creating a second one over the same mail updates the first. */
@Serializable
data class FollowupCreateDto(
    val folder: String,
    val uid: Long,
    @SerialName("due_at") val dueAt: String,
    val note: String = "",
)

@Serializable
data class FollowupUpdateDto(
    @SerialName("due_at") val dueAt: String? = null,
    val note: String? = null,
)

/**
 * One grant on a folder of mine (§4.20).
 *
 * The field names follow the vocabulary the doc uses for the `PUT` body — `grantee` and
 * `permission` — because the account had no shares to read a real response from, so the shape
 * of a populated item is inferred rather than observed. Every field is optional so an
 * unexpected name degrades to a blank row instead of failing the whole screen.
 */
@Serializable
data class FolderShareDto(
    val grantee: String = "",
    val permission: String = "read",
) {
    val canWrite: Boolean get() = permission.equals("write", ignoreCase = true)
}

@Serializable
data class FolderShareRequest(
    val folder: String,
    val grantee: String,
    /** "read" or "write". */
    val permission: String,
)

/** One message of the context handed to the model. */
@Serializable
data class AiMessageDto(
    val sender: String = "",
    val date: String = "",
    /** Plain text — never HTML. */
    val body: String = "",
)

@Serializable
data class SummarizeRequest(
    val subject: String,
    val messages: List<AiMessageDto>,
)

@Serializable
data class SummaryDto(val summary: String = "")

/**
 * `messages` must carry the thread being answered: with an empty list the backend answers
 * 502, so callers send the context or do not ask.
 */
@Serializable
data class AiDraftRequest(
    val subject: String,
    val messages: List<AiMessageDto>,
    @SerialName("my_draft") val myDraft: String = "",
    @SerialName("is_reply") val isReply: Boolean = false,
)

@Serializable
data class AiDraftDto(val options: List<AiDraftOptionDto> = emptyList())

@Serializable
data class AiDraftOptionDto(
    val label: String = "",
    val text: String = "",
)

@Serializable
data class TranslateRequest(
    val text: String,
    val language: String,
)

@Serializable
data class TranslateDto(val text: String = "")

@Serializable
data class TranslateSegmentsRequest(
    val segments: List<String>,
    val language: String,
)

@Serializable
data class TranslateSegmentsDto(val segments: List<String> = emptyList())
