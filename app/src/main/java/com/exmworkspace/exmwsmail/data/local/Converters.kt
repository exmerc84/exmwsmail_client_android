package com.exmworkspace.exmwsmail.data.local

import androidx.room.TypeConverter
import com.exmworkspace.exmwsmail.data.mail.FolderKind

class Converters {
    @TypeConverter fun fromFolderKind(value: FolderKind): String = value.name
    @TypeConverter fun toFolderKind(value: String): FolderKind = FolderKind.valueOf(value)
}
