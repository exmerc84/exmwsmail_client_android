package com.example.exmwsmail.data.local.entity

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
    ],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folderId: Long,
    val uid: Long,
    val subject: String,
    val from: String,
    val internalDate: Long,
    val seen: Boolean,
    val flagged: Boolean,
    val answered: Boolean,
    val to: String? = null,
    val cc: String? = null,
)
