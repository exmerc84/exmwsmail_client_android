package com.exmworkspace.exmwsmail.data.mail

/** Well-known mailbox roles, inferred from the folder name (see [inferFolderKind]). */
enum class FolderKind {
    INBOX, SENT, DRAFTS, TRASH, JUNK, ARCHIVE, OTHER
}
