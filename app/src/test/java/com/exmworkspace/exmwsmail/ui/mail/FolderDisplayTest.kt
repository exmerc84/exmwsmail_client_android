package com.exmworkspace.exmwsmail.ui.mail

import com.exmworkspace.exmwsmail.data.local.entity.FolderEntity
import com.exmworkspace.exmwsmail.data.mail.FolderKind
import org.junit.Assert.assertEquals
import org.junit.Test

class FolderDisplayTest {

    private fun folder(name: String, kind: FolderKind) =
        FolderEntity(accountId = 1, fullName = name, name = name, kind = kind, holdsMessages = true)

    @Test
    fun system_folders_are_ordered_by_rank() {
        val sorted = listOf(
            folder("Trash", FolderKind.TRASH),
            folder("Sent", FolderKind.SENT),
            folder("Inbox", FolderKind.INBOX),
            folder("Drafts", FolderKind.DRAFTS),
            folder("Archive", FolderKind.ARCHIVE),
            folder("Junk", FolderKind.JUNK),
        ).sortedForDrawer().map { it.kind }

        assertEquals(
            listOf(
                FolderKind.INBOX,
                FolderKind.DRAFTS,
                FolderKind.SENT,
                FolderKind.ARCHIVE,
                FolderKind.JUNK,
                FolderKind.TRASH,
            ),
            sorted,
        )
    }

    @Test
    fun custom_folders_come_last_sorted_case_insensitively_by_name() {
        val sorted = listOf(
            folder("zeta", FolderKind.OTHER),
            folder("Inbox", FolderKind.INBOX),
            folder("Alpha", FolderKind.OTHER),
        ).sortedForDrawer().map { it.name }

        assertEquals(listOf("Inbox", "Alpha", "zeta"), sorted)
    }
}
