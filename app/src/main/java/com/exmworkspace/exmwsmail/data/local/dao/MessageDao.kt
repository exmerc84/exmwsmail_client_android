package com.exmworkspace.exmwsmail.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.exmworkspace.exmwsmail.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE folderId = :folderId ORDER BY internalDate DESC")
    fun observeByFolder(folderId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<MessageEntity?>

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): MessageEntity?

    @Query("SELECT MIN(uid) FROM messages WHERE folderId = :folderId")
    suspend fun minUid(folderId: Long): Long?

    @Query("SELECT MAX(uid) FROM messages WHERE folderId = :folderId")
    suspend fun maxUid(folderId: Long): Long?

    @Query("SELECT * FROM messages WHERE folderId = :folderId AND uid > :minUid ORDER BY internalDate DESC LIMIT 1")
    suspend fun newestAboveUid(folderId: Long, minUid: Long): MessageEntity?

    @Query("SELECT uid FROM messages WHERE folderId = :folderId AND uid >= :minUid AND uid <= :maxUid")
    suspend fun uidsInRange(folderId: Long, minUid: Long, maxUid: Long): List<Long>

    @Upsert
    suspend fun upsert(messages: List<MessageEntity>)

    @Query("""
        SELECT m.* FROM messages m
        INNER JOIN folders f ON f.id = m.folderId
        WHERE f.accountId = :accountId
          AND (m.subject LIKE '%' || :q || '%' COLLATE NOCASE
            OR m.`from` LIKE '%' || :q || '%' COLLATE NOCASE)
        ORDER BY m.internalDate DESC
        LIMIT 200
    """)
    fun search(accountId: Long, q: String): Flow<List<MessageEntity>>

    @Query("UPDATE messages SET seen = :seen WHERE id = :id")
    suspend fun updateSeen(id: Long, seen: Boolean)

    @Query("UPDATE messages SET flagged = :flagged WHERE id = :id")
    suspend fun updateFlagged(id: Long, flagged: Boolean)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM messages WHERE folderId = :folderId AND uid IN (:uids)")
    suspend fun deleteByUids(folderId: Long, uids: List<Long>)

    @Query("DELETE FROM messages WHERE folderId = :folderId")
    suspend fun deleteAllForFolder(folderId: Long)

    @Query("SELECT COUNT(*) FROM messages WHERE folderId = :folderId")
    suspend fun count(folderId: Long): Long
}
