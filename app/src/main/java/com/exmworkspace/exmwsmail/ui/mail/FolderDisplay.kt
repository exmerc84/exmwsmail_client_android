package com.exmworkspace.exmwsmail.ui.mail

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Drafts
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Report
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.exmworkspace.exmwsmail.R
import com.exmworkspace.exmwsmail.data.local.entity.FolderEntity
import com.exmworkspace.exmwsmail.data.mail.FolderKind

fun folderIcon(kind: FolderKind): ImageVector = when (kind) {
    FolderKind.INBOX -> Icons.Default.Inbox
    FolderKind.SENT -> Icons.AutoMirrored.Filled.Send
    FolderKind.DRAFTS -> Icons.Default.Drafts
    FolderKind.TRASH -> Icons.Default.Delete
    FolderKind.JUNK -> Icons.Default.Report
    FolderKind.ARCHIVE -> Icons.Default.Archive
    FolderKind.OTHER -> Icons.Default.Folder
}

@Composable
fun folderLabel(folder: FolderEntity): String = when (folder.kind) {
    FolderKind.INBOX -> stringResource(R.string.folder_inbox)
    FolderKind.SENT -> stringResource(R.string.folder_sent)
    FolderKind.DRAFTS -> stringResource(R.string.folder_drafts)
    FolderKind.TRASH -> stringResource(R.string.folder_trash)
    FolderKind.JUNK -> stringResource(R.string.folder_junk)
    FolderKind.ARCHIVE -> stringResource(R.string.folder_archive)
    FolderKind.OTHER -> folder.name
}

private val folderRank: (FolderKind) -> Int = {
    when (it) {
        FolderKind.INBOX -> 0
        FolderKind.DRAFTS -> 1
        FolderKind.SENT -> 2
        FolderKind.ARCHIVE -> 3
        FolderKind.JUNK -> 4
        FolderKind.TRASH -> 5
        FolderKind.OTHER -> 6
    }
}

fun List<FolderEntity>.sortedForDrawer(): List<FolderEntity> = sortedWith(
    compareBy({ folderRank(it.kind) }, { it.name.lowercase() })
)
