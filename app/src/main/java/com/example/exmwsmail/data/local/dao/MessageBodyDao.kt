package com.example.exmwsmail.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.exmwsmail.data.local.entity.MessageBodyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageBodyDao {
    @Query("SELECT * FROM message_bodies WHERE messageId = :messageId LIMIT 1")
    fun observe(messageId: Long): Flow<MessageBodyEntity?>

    @Query("SELECT * FROM message_bodies WHERE messageId = :messageId LIMIT 1")
    suspend fun find(messageId: Long): MessageBodyEntity?

    @Upsert
    suspend fun upsert(body: MessageBodyEntity)
}
