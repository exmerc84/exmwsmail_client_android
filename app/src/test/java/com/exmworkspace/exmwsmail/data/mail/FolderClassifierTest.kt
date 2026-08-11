package com.exmworkspace.exmwsmail.data.mail

import org.junit.Assert.assertEquals
import org.junit.Test

class FolderClassifierTest {

    @Test
    fun inbox_is_detected_case_insensitively() {
        assertEquals(FolderKind.INBOX, inferFolderKind("INBOX"))
        assertEquals(FolderKind.INBOX, inferFolderKind("inbox"))
    }

    @Test
    fun canonical_backend_names_map_to_kinds() {
        assertEquals(FolderKind.SENT, inferFolderKind("Sent"))
        assertEquals(FolderKind.DRAFTS, inferFolderKind("Drafts"))
        assertEquals(FolderKind.TRASH, inferFolderKind("Trash"))
        assertEquals(FolderKind.JUNK, inferFolderKind("Junk"))
        assertEquals(FolderKind.JUNK, inferFolderKind("Spam"))
        assertEquals(FolderKind.ARCHIVE, inferFolderKind("Archive"))
    }

    @Test
    fun localized_names_map_to_kinds() {
        assertEquals(FolderKind.SENT, inferFolderKind("Enviados"))
        assertEquals(FolderKind.TRASH, inferFolderKind("Papelera"))
        assertEquals(FolderKind.DRAFTS, inferFolderKind("Borradores"))
        assertEquals(FolderKind.ARCHIVE, inferFolderKind("Archivo"))
    }

    /**
     * Name-only, so a path out of a shared namespace answers by its leaf like any other.
     * Whether that folder is really *my* Sent is [resolveFolderKinds]'s call, not this one's.
     */
    @Test
    fun nested_paths_classify_on_their_leaf() {
        assertEquals(FolderKind.SENT, inferFolderKind("shared/otro@dominio.com/Sent"))
        assertEquals(FolderKind.TRASH, inferFolderKind("INBOX.Trash"))
    }

    @Test
    fun unknown_folder_is_other() {
        assertEquals(FolderKind.OTHER, inferFolderKind("Proyectos"))
        assertEquals(FolderKind.OTHER, inferFolderKind("Clientes/2026"))
    }

    /** My own folders, as `/folders` reports them when nothing is shared. */
    private fun mine(vararg names: String) = names.map { FolderRef(it) }

    /** The real account: both `Archive` and a personal `Archivo` with year subfolders. */
    @Test
    fun canonical_name_wins_the_system_slot_over_a_localized_twin() {
        val kinds = resolveFolderKinds(
            mine("INBOX", "Archive", "Archivo", "Archivo/2023", "Archivo/2024")
        )
        assertEquals(FolderKind.ARCHIVE, kinds["Archive"])
        assertEquals(FolderKind.OTHER, kinds["Archivo"])
        assertEquals(FolderKind.OTHER, kinds["Archivo/2023"])
        assertEquals(FolderKind.OTHER, kinds["Archivo/2024"])
    }

    @Test
    fun a_localized_folder_keeps_the_slot_when_it_is_the_only_claimant() {
        val kinds = resolveFolderKinds(mine("INBOX", "Archivo", "Papelera", "Enviados"))
        assertEquals(FolderKind.ARCHIVE, kinds["Archivo"])
        assertEquals(FolderKind.TRASH, kinds["Papelera"])
        assertEquals(FolderKind.SENT, kinds["Enviados"])
    }

    @Test
    fun the_winner_does_not_depend_on_the_order_the_server_answers_in() {
        val a = resolveFolderKinds(mine("Archivo", "Archive"))
        val b = resolveFolderKinds(mine("Archive", "Archivo"))
        assertEquals(FolderKind.ARCHIVE, a["Archive"])
        assertEquals(FolderKind.ARCHIVE, b["Archive"])
        assertEquals(a, b)
    }

    /** With no canonical claimant the shallowest path wins, never a nested one. */
    @Test
    fun a_nested_twin_never_takes_the_slot_from_a_top_level_one() {
        val kinds = resolveFolderKinds(mine("Archivo", "Respaldo/Archivados"))
        assertEquals(FolderKind.ARCHIVE, kinds["Archivo"])
        assertEquals(FolderKind.OTHER, kinds["Respaldo/Archivados"])
    }

    /**
     * The one this guard exists for: a shared `Sent` has the canonical leaf, which is the top
     * tiebreak, so it would have won the SENT role outright and pushed the user's own Enviados
     * out to OTHER.
     */
    @Test
    fun a_shared_folder_never_takes_a_system_slot_from_my_own() {
        val kinds = resolveFolderKinds(
            listOf(
                FolderRef("Enviados"),
                FolderRef("shared/ana@exmerc.com/Sent", sharedOwner = "ana@exmerc.com"),
            )
        )
        assertEquals(FolderKind.SENT, kinds["Enviados"])
        assertEquals(FolderKind.OTHER, kinds["shared/ana@exmerc.com/Sent"])
    }

    /** Not even when it is the only claimant — someone else's Trash is not my Trash. */
    @Test
    fun a_shared_folder_is_other_even_with_no_competitor() {
        val kinds = resolveFolderKinds(
            listOf(FolderRef("shared/ana@exmerc.com/Trash", sharedOwner = "ana@exmerc.com"))
        )
        assertEquals(FolderKind.OTHER, kinds["shared/ana@exmerc.com/Trash"])
    }

    /** Two people sharing their Sent must not knock each other around either. */
    @Test
    fun shared_folders_do_not_compete_among_themselves() {
        val kinds = resolveFolderKinds(
            listOf(
                FolderRef("shared/ana@exmerc.com/Sent", sharedOwner = "ana@exmerc.com"),
                FolderRef("shared/zoe@exmerc.com/Sent", sharedOwner = "zoe@exmerc.com"),
            )
        )
        assertEquals(FolderKind.OTHER, kinds["shared/ana@exmerc.com/Sent"])
        assertEquals(FolderKind.OTHER, kinds["shared/zoe@exmerc.com/Sent"])
    }

    /** A folder I shared out is still mine, so it keeps its role. */
    @Test
    fun sharing_my_own_folder_does_not_cost_it_its_role() {
        val kinds = resolveFolderKinds(listOf(FolderRef("Archive", sharedOwner = null)))
        assertEquals(FolderKind.ARCHIVE, kinds["Archive"])
    }

    /** Shape taken from a real `/source` response on this backend. */
    @Test
    fun the_imap_fetch_preamble_is_dropped_from_the_raw_source() {
        val raw = "5320 FETCH (UID 5826 BODY[] {72436}Return-Path: <a@b.com>\r\n" +
            "Subject: Hola\r\n\r\nCuerpo\r\n)"
        val out = stripImapFetchPreamble(raw)
        assertEquals("Return-Path: <a@b.com>\r\nSubject: Hola\r\n\r\nCuerpo", out)
    }

    @Test
    fun a_source_without_the_preamble_is_left_untouched() {
        val raw = "Return-Path: <a@b.com>\r\nSubject: Hola\r\n\r\nCuerpo"
        assertEquals(raw, stripImapFetchPreamble(raw))
    }

    /** A body that legitimately ends in ")" must not lose it. */
    @Test
    fun only_the_preambles_own_closing_paren_is_removed() {
        val withPreamble = stripImapFetchPreamble("1 FETCH (UID 2 BODY[] {9}Subject: (x)\r\n)")
        assertEquals("Subject: (x)", withPreamble)
        assertEquals("Subject: (x)", stripImapFetchPreamble("Subject: (x)"))
    }
}
