package com.example.exmwsmail.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.exmwsmail.data.local.dao.AccountDao
import com.example.exmwsmail.data.local.dao.AttachmentDao
import com.example.exmwsmail.data.local.dao.FolderDao
import com.example.exmwsmail.data.local.dao.MessageBodyDao
import com.example.exmwsmail.data.local.dao.MessageDao
import com.example.exmwsmail.data.local.entity.AccountEntity
import com.example.exmwsmail.data.local.entity.AttachmentEntity
import com.example.exmwsmail.data.local.entity.FolderEntity
import com.example.exmwsmail.data.local.entity.MessageBodyEntity
import com.example.exmwsmail.data.local.entity.MessageEntity

@Database(
    entities = [
        AccountEntity::class,
        FolderEntity::class,
        MessageEntity::class,
        MessageBodyEntity::class,
        AttachmentEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class MailDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun folderDao(): FolderDao
    abstract fun messageDao(): MessageDao
    abstract fun messageBodyDao(): MessageBodyDao
    abstract fun attachmentDao(): AttachmentDao

    companion object {
        fun create(context: Context): MailDatabase = Room.databaseBuilder(
            context.applicationContext,
            MailDatabase::class.java,
            "exmws_mail.db",
        )
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }
}
