package com.exmworkspace.exmwsmail.data.mail

import com.exmworkspace.exmwsmail.data.local.entity.FolderEntity

/** The three blocks the drawer draws, in the order it draws them. */
data class DrawerFolders(
    val system: List<FolderEntity>,
    val own: List<FolderEntity>,
    val sharedWithMe: List<FolderEntity>,
)

/**
 * Splits the drawer list into system roles, the user's own folders, and folders another user
 * shared with them.
 *
 * `shared_owner` is the discriminator (§4.20): it carries the owner's email for a folder out of
 * someone else's namespace. `is_shared` is the opposite direction — a folder of *mine* that I
 * granted to someone — so those stay under "Tus carpetas", which is where their owner expects
 * to find them.
 *
 * Shared folders are pulled out before the system block is taken, so a shared `.../Sent` cannot
 * land among the user's own roles just because [resolveFolderKinds] classified it by its leaf.
 *
 * Input order is preserved inside each block (callers pass a list already sorted for the
 * drawer); the shared block is regrouped by owner so several folders from one person sit
 * together instead of interleaving alphabetically.
 */
fun splitDrawerFolders(folders: List<FolderEntity>): DrawerFolders {
    val (shared, mine) = folders.partition { it.sharedOwner != null }
    val (system, own) = mine.partition { it.kind != FolderKind.OTHER }
    return DrawerFolders(
        system = system,
        own = own,
        sharedWithMe = shared.sortedWith(
            compareBy({ it.sharedOwner.orEmpty().lowercase() }, { it.name.lowercase() })
        ),
    )
}

/**
 * The owner shown under a shared folder's name. The local part alone is friendlier than the
 * full address and is what fits on one drawer line; the domain is dropped only when there is
 * one to drop, so a malformed value still renders as itself rather than as an empty line.
 */
fun sharedOwnerLabel(email: String): String =
    email.substringBefore('@').takeIf { it.isNotBlank() } ?: email
