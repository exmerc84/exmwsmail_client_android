package com.exmworkspace.exmwsmail.data.repository

import com.exmworkspace.exmwsmail.data.mail.dedupeContactsByAddress
import com.exmworkspace.exmwsmail.data.remote.ContactsApi
import com.exmworkspace.exmwsmail.data.remote.dto.ContactCountsDto
import com.exmworkspace.exmwsmail.data.remote.dto.ContactDto
import com.exmworkspace.exmwsmail.data.remote.dto.ContactGroupDto
import com.exmworkspace.exmwsmail.data.remote.dto.ContactGroupUpsertDto
import com.exmworkspace.exmwsmail.data.remote.dto.ContactUpsertDto
import com.exmworkspace.exmwsmail.data.remote.requireBody
import com.exmworkspace.exmwsmail.data.remote.requireSuccess
import com.exmworkspace.exmwsmail.data.remote.toApiException
import com.exmworkspace.exmwsmail.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * The address book. Contacts are read straight from the API rather than cached in Room:
 * they are small, always online-adjacent, and staleness here is more annoying than a
 * round-trip.
 */
class ContactsRepository(
    private val api: ContactsApi,
) {
    suspend fun list(): List<ContactDto> = withContext(Dispatchers.IO) {
        dedupeContactsByAddress(api.list().requireBody().filter { !it.email.isNullOrBlank() })
    }

    suspend fun corporate(): List<ContactDto> = withContext(Dispatchers.IO) {
        dedupeContactsByAddress(api.corporate().requireBody().filter { !it.email.isNullOrBlank() })
    }

    suspend fun counts(): ContactCountsDto? = withContext(Dispatchers.IO) {
        runCatching { api.counts().requireBody() }.getOrNull()
    }

    /**
     * Groups as the server has them — the single source of truth. The two endpoints answer
     * different questions (§5): `/groups/detail` lists the records created through `/manage`,
     * carrying id, colour and domain; `/groups` lists the names actually present on contacts,
     * with counts. A group only ever set as a contact's `group_name` exists in the second and
     * not the first, so asking only for the detailed one hides it. Both are read and merged
     * by name, with the detailed record winning where they overlap.
     */
    suspend fun groups(): List<ContactGroupDto> = withContext(Dispatchers.IO) {
        val detailed = runCatching { api.groupsDetail().requireBody() }.getOrDefault(emptyList())
        val counted = runCatching { api.groups().requireBody() }.getOrDefault(emptyList())
        val byName = LinkedHashMap<String, ContactGroupDto>()
        for (g in detailed) g.name?.takeIf { it.isNotBlank() }?.let { byName[it] = g }
        for (g in counted) g.name?.takeIf { it.isNotBlank() }?.let { byName.putIfAbsent(it, g) }
        byName.values.toList()
    }

    /**
     * Recipient suggestions. The personal book and the corporate directory are separate
     * endpoints, so both are asked and merged — otherwise a colleague who was never saved
     * manually would never autocomplete.
     */
    suspend fun suggest(query: String, limit: Int = 8): List<ContactDto> =
        withContext(Dispatchers.IO) {
            if (query.length < MIN_QUERY) return@withContext emptyList()
            val results = coroutineScope {
                val personal = async {
                    runCatching { api.suggest(query).requireBody() }
                        .onFailure { AppLog.w(TAG, "suggest failed: ${it.message}") }
                        .getOrDefault(emptyList())
                }
                val directory = async {
                    runCatching { api.corporate().requireBody() }
                        .getOrDefault(emptyList())
                        .filter { it.matches(query) }
                }
                personal.await() + directory.await()
            }
            results
                .filter { !it.email.isNullOrBlank() }
                // Same person, one suggestion — matched on the parsed address, since the raw
                // field differs exactly in the noise.
                .let(::dedupeContactsByAddress)
                .sortedWith(
                    // Favourites first, then whoever matches from the start of a word.
                    compareByDescending<ContactDto> { it.isFavorite }
                        .thenByDescending { it.startsWith(query) }
                        .thenBy { it.displayLabel.lowercase() }
                )
                .take(limit)
        }

    /**
     * Creates or updates a group (§5.1). Errors surface to the caller unchanged — a 409 means
     * the name is taken, and the user needs to see that rather than a silent no-op.
     */
    suspend fun saveGroup(
        id: Long?,
        name: String,
        domain: String,
        color: String,
    ): Unit = withContext(Dispatchers.IO) {
        val payload = ContactGroupUpsertDto(
            name = name,
            domain = domain,
            color = color.ifBlank { ContactGroupUpsertDto.DEFAULT_GROUP_COLOR },
        )
        val response = if (id == null) api.createGroup(payload) else api.updateGroup(id, payload)
        response.requireSuccess()
    }

    suspend fun deleteGroup(id: Long) = withContext(Dispatchers.IO) {
        api.deleteGroup(id).requireSuccess()
    }

    suspend fun toggleFavorite(id: Long) = withContext(Dispatchers.IO) {
        api.toggleFavorite(id).requireSuccess()
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        api.delete(id).requireSuccess()
    }

    suspend fun detail(id: Long): ContactDto = withContext(Dispatchers.IO) {
        api.detail(id).requireBody()
    }

    suspend fun create(contact: ContactUpsertDto): ContactDto = withContext(Dispatchers.IO) {
        api.create(contact).requireBody()
    }

    suspend fun update(id: Long, contact: ContactUpsertDto): ContactDto =
        withContext(Dispatchers.IO) { api.update(id, contact).requireBody() }

    suspend fun importFromEmails() = withContext(Dispatchers.IO) {
        api.importFromEmails().requireSuccess()
    }

    private companion object {
        const val TAG = "ContactsRepository"
        const val MIN_QUERY = 2
    }
}

private fun ContactDto.matches(query: String): Boolean {
    val q = query.lowercase()
    return displayLabel.lowercase().contains(q) || email?.lowercase()?.contains(q) == true
}

private fun ContactDto.startsWith(query: String): Boolean {
    val q = query.lowercase()
    if (email?.lowercase()?.startsWith(q) == true) return true
    return displayLabel.lowercase().split(' ', '.', '-').any { it.startsWith(q) }
}
