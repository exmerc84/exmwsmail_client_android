package com.exmworkspace.exmwsmail.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.exmworkspace.exmwsmail.data.local.entity.FolderEntity
import com.exmworkspace.exmwsmail.data.mail.FolderKind
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders WHERE accountId = :accountId ORDER BY name")
    fun observeForAccount(accountId: Long): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE accountId = :accountId ORDER BY name")
    suspend fun findFoldersForAccount(accountId: Long): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): FolderEntity?

    @Query("SELECT * FROM folders WHERE accountId = :accountId AND fullName = :fullName LIMIT 1")
    suspend fun findByFullName(accountId: Long, fullName: String): FolderEntity?

    @Query("SELECT * FROM folders WHERE kind = :kind LIMIT 1")
    suspend fun findFirstByKind(kind: FolderKind): FolderEntity?

    @Upsert
    suspend fun upsert(folders: List<FolderEntity>)

    @Update
    suspend fun update(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE accountId = :accountId AND fullName NOT IN (:keepFullNames)")
    suspend fun deleteMissing(accountId: Long, keepFullNames: List<String>)
}
