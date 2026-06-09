package com.exmworkspace.exmwsmail.data.mail

/**
 * Infers a [FolderKind] from an IMAP folder's full name and its SPECIAL-USE
 * attributes (RFC 6154), already lower-cased by the caller.
 *
 * Pure function (no JavaMail / Android types) so it can be unit-tested directly.
 */
fun inferFolderKind(fullName: String, lowerAttrs: List<String>): FolderKind = when {
    fullName.equals("INBOX", ignoreCase = true) -> FolderKind.INBOX
    "\\sent" in lowerAttrs -> FolderKind.SENT
    "\\drafts" in lowerAttrs -> FolderKind.DRAFTS
    "\\trash" in lowerAttrs -> FolderKind.TRASH
    "\\junk" in lowerAttrs -> FolderKind.JUNK
    "\\archive" in lowerAttrs -> FolderKind.ARCHIVE
    else -> FolderKind.OTHER
}
