package com.exmworkspace.exmwsmail.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.exmworkspace.exmwsmail.data.mail.FolderKind

@Entity(
    tableName = "folders",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("accountId"),
        Index(value = ["accountId", "fullName"], unique = true),
    ],
)
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val fullName: String,
    val name: String,
    val kind: FolderKind,
    val holdsMessages: Boolean,
    val uidValidity: Long? = null,
    val lastSyncedAt: Long? = null,
)
