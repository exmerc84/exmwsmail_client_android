package com.exmworkspace.exmwsmail.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("messageId")],
)
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageId: Long,
    /** The API's attachment `index` within the message. */
    val partIndex: Int,
    val filename: String,
    val mimeType: String,
    val sizeBytes: Long,
    val inline: Boolean = false,
    val contentId: String? = null,
    /**
     * Null until downloaded. The API returns only metadata, so bytes are fetched on demand
     * instead of riding along with the body as they did over IMAP.
     */
    val localPath: String? = null,
)
