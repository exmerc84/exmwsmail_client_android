package com.exmworkspace.exmwsmail.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.exmworkspace.exmwsmail.data.local.entity.AttachmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE messageId = :messageId ORDER BY partIndex")
    fun observe(messageId: Long): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE messageId = :messageId ORDER BY partIndex")
    suspend fun list(messageId: Long): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): AttachmentEntity?

    @Query("UPDATE attachments SET localPath = :path WHERE id = :id")
    suspend fun updateLocalPath(id: Long, path: String)

    @Insert
    suspend fun insertAll(list: List<AttachmentEntity>)

    @Query("DELETE FROM attachments WHERE messageId = :messageId")
    suspend fun deleteForMessage(messageId: Long)
}
