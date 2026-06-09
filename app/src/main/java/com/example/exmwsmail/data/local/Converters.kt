package com.example.exmwsmail.data.local

import androidx.room.TypeConverter
import com.example.exmwsmail.data.mail.FolderKind

class Converters {
    @TypeConverter fun fromFolderKind(value: FolderKind): String = value.name
    @TypeConverter fun toFolderKind(value: String): FolderKind = FolderKind.valueOf(value)
}
