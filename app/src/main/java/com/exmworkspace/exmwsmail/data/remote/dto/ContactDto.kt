package com.exmworkspace.exmwsmail.data.remote.dto

import com.exmworkspace.exmwsmail.data.mail.cleanContactEmail
import com.exmworkspace.exmwsmail.data.mail.cleanContactLabel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A contact (§5). Shape confirmed against live `/api/contacts` responses — the doc points at
 * the backend's Pydantic models instead of spelling it out. Everything except the id and
 * email is optional in practice, including for imported contacts.
 */
@Serializable
data class ContactDto(
    val id: Long? = null,
    val email: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    val phone: String? = null,
    val mobile: String? = null,
    val company: String? = null,
    @SerialName("job_title") val jobTitle: String? = null,
    val department: String? = null,
    val address: String? = null,
    val website: String? = null,
    val notes: String? = null,
    /** Matches [ContactGroupDto.name]; the backend groups by email domain. */
    @SerialName("group_name") val groupName: String? = null,
    @SerialName("is_favorite") val isFavorite: Boolean = false,
    /** "imported" for contacts mined from the mailbox, "manual" for hand-created ones. */
    val source: String? = null,
    @SerialName("avatar_color") val avatarColor: String? = null,
    /** How often the user corresponds with them — the backend's own ranking signal. */
    val frequency: Int = 0,
) {
    /**
     * Best label for a row: the name when there is one, the address otherwise. Imported
     * contacts carry the raw `From` header in [email], so it is parsed rather than printed.
     */
    val displayLabel: String
        get() = listOfNotNull(firstName, lastName).joinToString(" ").takeIf { it.isNotBlank() }
            ?: cleanContactLabel(displayName, email)

    /** The bare address, without the display name the header wrapped around it. */
    val cleanEmail: String get() = cleanContactEmail(email)

    val isImported: Boolean get() = source == "imported"
}

/**
 * Payload for create/update.
 *
 * Separate from [ContactDto] on purpose: that one carries `is_favorite` with a `false`
 * default, and since defaults are encoded it would silently un-favourite a contact on every
 * edit.
 *
 * Fields are non-null empty strings rather than nulls: the JSON config drops nulls
 * (`explicitNulls = false`), so a cleared field would vanish from the body and the backend
 * would keep its old value — there would be no way to erase anything. Empty string is also
 * what the backend itself stores for "unset" (`"group_name": ""`).
 */
@Serializable
data class ContactUpsertDto(
    val email: String,
    @SerialName("display_name") val displayName: String = "",
    val phone: String = "",
    val mobile: String = "",
    val company: String = "",
    @SerialName("job_title") val jobTitle: String = "",
    val department: String = "",
    val address: String = "",
    val website: String = "",
    val notes: String = "",
    @SerialName("group_name") val groupName: String = "",
)

/** §5. The backend names the total `count`, not `total`. */
@Serializable
data class ContactCountsDto(
    @SerialName("count") val total: Int = 0,
    val manual: Int = 0,
    val imported: Int = 0,
    val favorites: Int = 0,
)

/**
 * Write payload for `/api/contacts/groups/manage` (§5.1).
 *
 * `domain` and `color` are optional on create; on update the body may carry only the fields
 * being changed. Sending an empty `domain` is meaningful — it releases the contacts that were
 * auto-assigned by the old domain — so blanks travel rather than being dropped.
 */
@Serializable
data class ContactGroupUpsertDto(
    val name: String,
    val domain: String = "",
    /** Hex `#rrggbb`, as the backend returns it. Defaults to #6366f1 server-side. */
    val color: String = DEFAULT_GROUP_COLOR,
) {
    companion object {
        const val DEFAULT_GROUP_COLOR = "#6366f1"
    }
}

@Serializable
data class ContactGroupDto(
    val id: Long? = null,
    val name: String? = null,
    /** Groups are derived from the email domain. */
    val domain: String? = null,
    val color: String? = null,
    @SerialName("contact_count") val contactCount: Int = 0,
)
