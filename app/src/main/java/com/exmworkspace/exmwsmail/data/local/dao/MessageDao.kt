package com.exmworkspace.exmwsmail.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.exmworkspace.exmwsmail.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

data class MessageIdRow(val id: Long, val uid: String)

@Dao
interface MessageDao {
    /**
     * Pinned first (`is_pinned`, §4.6), then newest first. Without the pin clause a pinned
     * message sinks to wherever its date puts it, which is the same as not having pinned it.
     * The uid tiebreaker keeps the order stable for the handful of messages the backend sends
     * with an empty `date` (its own GPS/followup notifications) — they sort to the bottom
     * either way, but at least deterministically and roughly by arrival.
     */
    @Query(
        "SELECT * FROM messages WHERE folderId = :folderId " +
            "ORDER BY flagged DESC, internalDate DESC, CAST(uid AS INTEGER) DESC"
    )
    fun observeByFolder(folderId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<MessageEntity?>

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): MessageEntity?

    @Query("SELECT * FROM messages WHERE folderId = :folderId AND uid = :uid LIMIT 1")
    suspend fun findByUid(folderId: Long, uid: String): MessageEntity?

    /**
     * Every message of a conversation, oldest first, across folders — a thread normally has
     * the received copies in INBOX and the user's replies in Sent.
     */
    @Query("SELECT * FROM messages WHERE threadId = :threadId ORDER BY internalDate ASC")
    fun observeThread(threadId: String): Flow<List<MessageEntity>>

    @Query("SELECT MIN(internalDate) FROM messages WHERE folderId = :folderId")
    suspend fun oldestDate(folderId: Long): Long?

    /**
     * The newest messages whose body is not cached yet — what an offline preload should ask
     * the server for, newest first because that is what the user opens.
     */
    @Query("""
        SELECT m.* FROM messages m
        LEFT JOIN message_bodies b ON b.messageId = m.id
        WHERE m.folderId = :folderId AND b.messageId IS NULL
        ORDER BY m.internalDate DESC
        LIMIT :limit
    """)
    suspend fun withoutBody(folderId: Long, limit: Int): List<MessageEntity>

    /**
     * Existing row ids for the given uids. `@Upsert` updates by primary key, so a freshly
     * mapped entity with `id = 0` would insert-then-fail-to-update and silently keep stale
     * flags; callers carry these ids over before upserting.
     */
    @Query("SELECT id, uid FROM messages WHERE folderId = :folderId AND uid IN (:uids)")
    suspend fun idsForUids(folderId: Long, uids: List<String>): List<MessageIdRow>

    /** Full cached rows, for merging against endpoints that return a thinner payload. */
    @Query("SELECT * FROM messages WHERE folderId = :folderId AND uid IN (:uids)")
    suspend fun rowsForUids(folderId: Long, uids: List<String>): List<MessageEntity>

    @Upsert
    suspend fun upsert(messages: List<MessageEntity>)

    /**
     * Local search over the cache. Server-side search (§4.11) is the primary path; this
     * keeps results available offline.
     */
    @Query("""
        SELECT m.* FROM messages m
        INNER JOIN folders f ON f.id = m.folderId
        WHERE f.accountId = :accountId
          AND (m.subject LIKE '%' || :q || '%' COLLATE NOCASE
            OR m.`from` LIKE '%' || :q || '%' COLLATE NOCASE
            OR m.snippet LIKE '%' || :q || '%' COLLATE NOCASE)
        ORDER BY m.internalDate DESC
        LIMIT 200
    """)
    fun search(accountId: Long, q: String): Flow<List<MessageEntity>>

    @Query("UPDATE messages SET seen = :seen WHERE id = :id")
    suspend fun updateSeen(id: Long, seen: Boolean)

    @Query("UPDATE messages SET seen = :seen WHERE threadId = :threadId")
    suspend fun updateSeenForThread(threadId: String, seen: Boolean)

    @Query("UPDATE messages SET flagged = :flagged WHERE id = :id")
    suspend fun updateFlagged(id: Long, flagged: Boolean)

    @Query("UPDATE messages SET color = :color WHERE id = :id")
    suspend fun updateColor(id: Long, color: String?)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM messages WHERE folderId = :folderId AND threadId = :threadId")
    suspend fun deleteThreadInFolder(folderId: Long, threadId: String)

    /**
     * Drops rows the server no longer lists inside the freshly synced window. Bounded by
     * date because the REST API pages by date, not by UID range as IMAP did.
     *
     * Restricted to rows that came from the paged list. The paged list shows one row per
     * thread, so a message pulled in by a category or search query can sit inside the window
     * and still never appear among page 1's uids — pruning by date alone deleted exactly
     * those on the next refresh.
     */
    @Query("""
        DELETE FROM messages
        WHERE folderId = :folderId
          AND internalDate >= :oldestSyncedDate
          AND pagedIn = 1
          AND uid NOT IN (:keepUids)
    """)
    suspend fun deleteStaleInWindow(
        folderId: Long,
        oldestSyncedDate: Long,
        keepUids: List<String>,
    )

    @Query("DELETE FROM messages WHERE folderId = :folderId")
    suspend fun deleteAllForFolder(folderId: Long)

    @Query("SELECT COUNT(*) FROM messages WHERE folderId = :folderId")
    suspend fun count(folderId: Long): Long

    @Query("SELECT COUNT(*) FROM messages WHERE folderId = :folderId AND iaCategory = :category")
    suspend fun countByCategory(folderId: Long, category: String): Long
}
