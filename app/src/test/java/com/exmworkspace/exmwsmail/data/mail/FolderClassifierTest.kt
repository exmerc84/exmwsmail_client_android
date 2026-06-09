package com.exmworkspace.exmwsmail.data.mail

import org.junit.Assert.assertEquals
import org.junit.Test

class FolderClassifierTest {

    @Test
    fun inbox_is_detected_by_name_case_insensitively() {
        assertEquals(FolderKind.INBOX, inferFolderKind("INBOX", emptyList()))
        assertEquals(FolderKind.INBOX, inferFolderKind("inbox", emptyList()))
        // Name wins over attributes for the inbox.
        assertEquals(FolderKind.INBOX, inferFolderKind("INBOX", listOf("\\sent")))
    }

    @Test
    fun special_use_attributes_map_to_kinds() {
        assertEquals(FolderKind.SENT, inferFolderKind("Sent", listOf("\\sent")))
        assertEquals(FolderKind.DRAFTS, inferFolderKind("Drafts", listOf("\\drafts")))
        assertEquals(FolderKind.TRASH, inferFolderKind("Papelera", listOf("\\trash")))
        assertEquals(FolderKind.JUNK, inferFolderKind("Spam", listOf("\\junk")))
        assertEquals(FolderKind.ARCHIVE, inferFolderKind("Archivo", listOf("\\archive")))
    }

    @Test
    fun unknown_folder_without_attributes_is_other() {
        assertEquals(FolderKind.OTHER, inferFolderKind("Proyectos", emptyList()))
        assertEquals(FolderKind.OTHER, inferFolderKind("Sent", emptyList()))
    }

    @Test
    fun first_matching_attribute_wins_in_declared_order() {
        // \sent is checked before \drafts, so it takes precedence.
        assertEquals(FolderKind.SENT, inferFolderKind("X", listOf("\\drafts", "\\sent")))
    }
}
