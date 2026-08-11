package com.exmworkspace.exmwsmail.data.mail

import com.exmworkspace.exmwsmail.data.local.entity.FolderEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderSectionsTest {

    private fun folder(
        name: String,
        kind: FolderKind = FolderKind.OTHER,
        sharedOwner: String? = null,
        isShared: Boolean = false,
    ) = FolderEntity(
        accountId = 1L,
        fullName = name,
        name = name,
        kind = kind,
        sharedOwner = sharedOwner,
        isShared = isShared,
    )

    /** The report: a folder someone shared sat among the user's own with nothing to tell them apart. */
    @Test
    fun a_folder_shared_with_me_leaves_my_own_folders() {
        val result = splitDrawerFolders(
            listOf(
                folder("INBOX", FolderKind.INBOX),
                folder("Licencias"),
                folder("Test Compartido", sharedOwner = "otro@exmerc.com"),
                folder("Proveedores"),
            )
        )
        assertEquals(listOf("INBOX"), result.system.map { it.name })
        assertEquals(listOf("Licencias", "Proveedores"), result.own.map { it.name })
        assertEquals(listOf("Test Compartido"), result.sharedWithMe.map { it.name })
    }

    /**
     * The other direction of §4.20: `is_shared` means *I* granted this folder to someone. It is
     * still mine, so moving it out of "Tus carpetas" would hide it from the person who made it.
     */
    @Test
    fun a_folder_i_shared_out_stays_mine() {
        val result = splitDrawerFolders(listOf(folder("Proyectos", isShared = true)))
        assertEquals(listOf("Proyectos"), result.own.map { it.name })
        assertTrue(result.sharedWithMe.isEmpty())
    }

    /**
     * `inferFolderKind` classifies by the leaf, so a shared `.../Sent` comes back as SENT. It
     * must not be able to slip into the system block and pose as the user's own Enviados.
     */
    @Test
    fun a_shared_folder_classified_as_a_system_role_is_still_shared() {
        val result = splitDrawerFolders(
            listOf(
                folder("Sent", FolderKind.SENT),
                folder("Enviados de Ana", FolderKind.SENT, sharedOwner = "ana@exmerc.com"),
            )
        )
        assertEquals(listOf("Sent"), result.system.map { it.name })
        assertEquals(listOf("Enviados de Ana"), result.sharedWithMe.map { it.name })
    }

    /** Several folders from one person read as a set, so they group by owner before name. */
    @Test
    fun shared_folders_group_by_owner() {
        val result = splitDrawerFolders(
            listOf(
                folder("Ventas", sharedOwner = "zoe@exmerc.com"),
                folder("Zulu", sharedOwner = "ana@exmerc.com"),
                folder("Actas", sharedOwner = "ana@exmerc.com"),
            )
        )
        assertEquals(listOf("Actas", "Zulu", "Ventas"), result.sharedWithMe.map { it.name })
    }

    @Test
    fun the_drawer_order_of_my_own_folders_is_left_alone() {
        val result = splitDrawerFolders(
            listOf(folder("zeta"), folder("alfa"), folder("mu"))
        )
        assertEquals(listOf("zeta", "alfa", "mu"), result.own.map { it.name })
    }

    @Test
    fun an_empty_mailbox_yields_three_empty_blocks() {
        val result = splitDrawerFolders(emptyList())
        assertTrue(result.system.isEmpty() && result.own.isEmpty() && result.sharedWithMe.isEmpty())
    }

    @Test
    fun the_owner_label_drops_the_domain() {
        assertEquals("ana", sharedOwnerLabel("ana@exmerc.com"))
    }

    /** A value that is not an address at all still has to render as something. */
    @Test
    fun a_label_with_no_local_part_falls_back_to_the_raw_value() {
        assertEquals("@exmerc.com", sharedOwnerLabel("@exmerc.com"))
    }
}
