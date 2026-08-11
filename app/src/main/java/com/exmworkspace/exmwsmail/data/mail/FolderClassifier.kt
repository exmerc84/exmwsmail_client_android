package com.exmworkspace.exmwsmail.data.mail

/**
 * Infers a [FolderKind] from the folder's IMAP path.
 *
 * The REST API does not expose SPECIAL-USE attributes (RFC 6154), so the name is all we
 * have. Both the canonical English names the backend documents and the localized ones some
 * mailboxes carry are matched, on the leaf segment because this backend also serves the
 * nested form (`INBOX.Trash`).
 *
 * Name-only, so it answers for any path handed to it — including one out of someone else's
 * namespace, which is not the caller's system folder at all. [resolveFolderKinds] is what
 * knows the difference; prefer it.
 *
 * Pure function (no Android types) so it can be unit-tested directly.
 */
fun inferFolderKind(name: String): FolderKind {
    if (name.equals("INBOX", ignoreCase = true)) return FolderKind.INBOX
    val leaf = name.split('/', '.').last().trim().lowercase()
    return when (leaf) {
        "inbox", "bandeja de entrada" -> FolderKind.INBOX
        in SENT_NAMES -> FolderKind.SENT
        in DRAFT_NAMES -> FolderKind.DRAFTS
        in TRASH_NAMES -> FolderKind.TRASH
        in JUNK_NAMES -> FolderKind.JUNK
        in ARCHIVE_NAMES -> FolderKind.ARCHIVE
        else -> FolderKind.OTHER
    }
}

private val SENT_NAMES = setOf(
    "sent", "sent items", "sent messages", "sent mail",
    "enviados", "elementos enviados", "correo enviado",
)

private val DRAFT_NAMES = setOf("drafts", "draft", "borradores", "borrador")

private val TRASH_NAMES = setOf(
    "trash", "deleted", "deleted items", "deleted messages",
    "papelera", "eliminados", "elementos eliminados",
)

private val JUNK_NAMES = setOf(
    "junk", "spam", "junk e-mail", "bulk mail",
    "correo no deseado", "no deseado", "correo basura",
)

private val ARCHIVE_NAMES = setOf("archive", "archives", "archivo", "archivados")

/** A folder as [resolveFolderKinds] needs to see it: its path, and whose mailbox it is in. */
data class FolderRef(val fullName: String, val sharedOwner: String? = null)

/**
 * Resolves the kind of every folder against the whole list instead of one name at a time.
 *
 * A system role belongs to exactly one mailbox, but the localized aliases above mean an
 * account can have two folders claiming the same one: this backend serves both the canonical
 * `Archive` and a personal `Archivo` (the parent of `Archivo/2023`, `/2024`, `/2025`), and
 * each on its own reads as the archive — so the drawer showed "Archivados" twice, one of
 * which was really the user's own folder.
 *
 * When several folders claim a role the canonical English name wins, then the shallowest
 * path, then the shortest, then alphabetical order so the result never depends on the order
 * the server happened to answer in. The losers fall back to [FolderKind.OTHER] and keep
 * their own name, which is what they always were.
 *
 * A folder someone else shared with me (`shared_owner` set, §4.20) is never a system role of
 * *mine*, whatever it is called: my sent mail is not saved in their Sent and my drafts are not
 * in their Drafts. So it stays [FolderKind.OTHER] and never enters the competition — otherwise
 * a shared `Sent` could win on the canonical-name tiebreak and demote the user's own, and the
 * roles carry behaviour (Drafts opens the composer, Trash deletes for good) that has no
 * business firing on another person's mailbox. It also keeps its real name in the drawer,
 * which is what identifies it there.
 *
 * Pure function, like [inferFolderKind].
 */
fun resolveFolderKinds(folders: List<FolderRef>): Map<String, FolderKind> {
    val inferred = folders.associate { ref ->
        ref.fullName to if (ref.sharedOwner != null) FolderKind.OTHER
        else inferFolderKind(ref.fullName)
    }
    val resolved = inferred.toMutableMap()
    inferred.entries
        .filter { it.value != FolderKind.OTHER }
        .groupBy({ it.value }, { it.key })
        .filterValues { it.size > 1 }
        .forEach { (kind, claimants) ->
            val winner = claimants.minWith(
                compareBy(
                    { if (it.leaf() in CANONICAL_LEAVES) 0 else 1 },
                    { it.count { ch -> ch == '/' || ch == '.' } },
                    { it.length },
                    { it },
                )
            )
            claimants.filter { it != winner }.forEach { resolved[it] = FolderKind.OTHER }
            resolved[winner] = kind
        }
    return resolved
}

private fun String.leaf(): String = split('/', '.').last().trim().lowercase()

/**
 * Drops the IMAP FETCH preamble the backend leaves in front of a message's raw source —
 * `<tag> FETCH (UID <n> BODY[] {<bytes>}` — plus the trailing `)` the command closes with.
 * What remains starts at the first real header line.
 *
 * Pure function so the parsing is unit-tested rather than eyeballed.
 */
fun stripImapFetchPreamble(raw: String): String {
    // Every bracket and brace is escaped explicitly: Android's regex engine rejects the
    // unescaped `]` and `}` that the desktop JVM running the unit tests happily accepts, so
    // a looser pattern passes here and throws on the phone.
    val opener = Regex("""^\s*\d+\s+FETCH\s*\(.*?BODY\[\]\s*\{\d+\}""", RegexOption.IGNORE_CASE)
    val match = opener.find(raw) ?: return raw
    var out = raw.substring(match.range.last + 1).trimStart('\r', '\n')
    // Only the parenthesis this preamble opened is ours to drop — a body can legitimately
    // end in one, and a source with no preamble must come back untouched.
    val tail = out.trimEnd()
    if (tail.endsWith(")")) out = tail.dropLast(1).trimEnd()
    return out
}

/** The names the API itself uses for the system mailboxes (§4.7 `destination`). */
private val CANONICAL_LEAVES = setOf("inbox", "sent", "drafts", "trash", "junk", "archive")
