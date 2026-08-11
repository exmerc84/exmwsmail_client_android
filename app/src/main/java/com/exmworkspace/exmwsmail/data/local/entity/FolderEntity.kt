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
    /** IMAP path — the value to pass as `?folder=`. */
    val fullName: String,
    /** Server-provided display name, already localized. */
    val name: String,
    val kind: FolderKind,
    val holdsMessages: Boolean = true,
    /** Backend cache counters; refreshed immediately after every action (§4.1). */
    val messageCount: Int = 0,
    val unseenCount: Int = 0,
    val isShared: Boolean = false,
    val sharedOwner: String? = null,
    /** IMAP rights string; without write rights the backend answers 403 (§4.20). */
    val sharedRights: String? = null,
    val lastSyncedAt: Long? = null,
) {
    /** False for a read-only shared folder, so the UI can hide destructive actions. */
    val canWrite: Boolean
        get() = sharedOwner == null || sharedRights?.contains('i') == true
}
