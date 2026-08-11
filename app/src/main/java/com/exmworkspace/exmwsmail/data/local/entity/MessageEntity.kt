package com.exmworkspace.exmwsmail.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("folderId"),
        Index(value = ["folderId", "uid"], unique = true),
        Index(value = ["folderId", "internalDate"]),
        Index("threadId"),
    ],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folderId: Long,
    /** IMAP UID. String because the API serializes it as one. */
    val uid: String,
    /** RFC 5322 Message-Id — needed as `in_reply_to` / `forward_of` when sending. */
    val messageId: String? = null,
    val subject: String,
    /** Sender as shown in the list: display name when present, address otherwise. */
    val from: String,
    val fromAddress: String? = null,
    val to: String? = null,
    val cc: String? = null,
    val internalDate: Long,
    val seen: Boolean,
    /** Backed by the server-side pin (§4.6) — the API has no generic \Flagged. */
    val flagged: Boolean,
    val answered: Boolean,
    val snippet: String? = null,
    val hasAttachments: Boolean = false,
    /** Colour flag: red | orange | green | blue | purple (§4.18). */
    val color: String? = null,
    /**
     * A list row is a THREAD, and this uid is only its representative — list-level actions
     * must go through the thread endpoints (§4.4).
     */
    val threadId: String? = null,
    val threadCount: Int = 1,
    /** Backend AI category: Personal, Comercial, Social, Notificación, Transaccional, Urgente. */
    val iaCategory: String? = null,
    /**
     * True when the row arrived through the paged folder list. Only those may be pruned as
     * stale: the category, search and thread endpoints return rows the paged list never
     * shows (non-representative members of a thread, for one), and pruning by date alone
     * deleted them on the next refresh.
     */
    val pagedIn: Boolean = false,
    /** Full References chain; resend it when replying so the thread does not split. */
    val references: String? = null,
    val answeredAt: Long? = null,
    val forwardedAt: Long? = null,
)
