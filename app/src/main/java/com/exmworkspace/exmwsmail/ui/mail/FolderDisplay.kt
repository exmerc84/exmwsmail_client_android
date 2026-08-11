package com.exmworkspace.exmwsmail.ui.mail

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Drafts
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Drafts
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Report
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.exmworkspace.exmwsmail.R
import com.exmworkspace.exmwsmail.data.local.entity.FolderEntity
import com.exmworkspace.exmwsmail.data.mail.FolderKind
import com.exmworkspace.exmwsmail.data.mail.inferFolderKind
import com.exmworkspace.exmwsmail.ui.theme.Tint
import com.exmworkspace.exmwsmail.ui.theme.TintAmber
import com.exmworkspace.exmwsmail.ui.theme.TintGreen
import com.exmworkspace.exmwsmail.ui.theme.TintIndigo
import com.exmworkspace.exmwsmail.ui.theme.TintPlum
import com.exmworkspace.exmwsmail.ui.theme.TintRose
import com.exmworkspace.exmwsmail.ui.theme.TintStone
import com.exmworkspace.exmwsmail.ui.theme.TintTeal

fun folderIcon(kind: FolderKind): ImageVector = when (kind) {
    FolderKind.INBOX -> Icons.Default.Inbox
    FolderKind.SENT -> Icons.AutoMirrored.Filled.Send
    FolderKind.DRAFTS -> Icons.Default.Drafts
    FolderKind.TRASH -> Icons.Default.Delete
    FolderKind.JUNK -> Icons.Default.Report
    FolderKind.ARCHIVE -> Icons.Default.Archive
    FolderKind.OTHER -> Icons.Default.Folder
}

/**
 * Stroke variants for the drawer, where the rows read Apple-Mail-light: outlined glyphs in
 * one accent instead of filled icons in tinted boxes. The filled set stays for dense
 * contexts (the move-to-folder sheet) where outlines get lost at small sizes.
 */
fun folderIconOutlined(kind: FolderKind): ImageVector = when (kind) {
    FolderKind.INBOX -> Icons.Outlined.Inbox
    FolderKind.SENT -> Icons.AutoMirrored.Outlined.Send
    FolderKind.DRAFTS -> Icons.Outlined.Drafts
    FolderKind.TRASH -> Icons.Outlined.Delete
    FolderKind.JUNK -> Icons.Outlined.Report
    FolderKind.ARCHIVE -> Icons.Outlined.Archive
    FolderKind.OTHER -> Icons.Outlined.Folder
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

/**
 * Localized name for a raw IMAP path, for screens that only carry the string — the
 * attachments browser printed "INBOX" and "Sent" between Spanish labels. Inferred by name
 * because that is all those payloads have.
 */
@Composable
fun folderNameLabel(fullName: String): String = when (inferFolderKind(fullName)) {
    FolderKind.INBOX -> stringResource(R.string.folder_inbox)
    FolderKind.SENT -> stringResource(R.string.folder_sent)
    FolderKind.DRAFTS -> stringResource(R.string.folder_drafts)
    FolderKind.TRASH -> stringResource(R.string.folder_trash)
    FolderKind.JUNK -> stringResource(R.string.folder_junk)
    FolderKind.ARCHIVE -> stringResource(R.string.folder_archive)
    FolderKind.OTHER -> fullName
}

/**
 * Accent for a folder's drawer icon. By meaning, not by hash: the inbox is *always* the
 * indigo one, so the colour becomes a location cue rather than decoration.
 */
fun folderTint(kind: FolderKind): Tint = when (kind) {
    FolderKind.INBOX -> TintIndigo
    FolderKind.SENT -> TintTeal
    FolderKind.DRAFTS -> TintAmber
    FolderKind.ARCHIVE -> TintGreen
    FolderKind.JUNK -> TintPlum
    FolderKind.TRASH -> TintRose
    FolderKind.OTHER -> TintStone
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
