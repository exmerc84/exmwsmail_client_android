package com.exmworkspace.exmwsmail.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One row of `GET /api/accounts/`, read off a live response (§4.23) — the doc trails off at
 * "(id, email, display_name, ...)" and the field is really `email_address`:
 * `{"id":1,"email_address":"...","display_name":"...","is_default":true,"is_slave":false}`.
 *
 * The section's `POST`/`DELETE` have no DTOs on purpose: mailboxes are provisioned
 * server-side and the app only reads the list.
 */
@Serializable
data class RemoteAccountDto(
    val id: Long,
    @SerialName("email_address") val email: String = "",
    @SerialName("display_name") val displayName: String? = null,
    /** The login mailbox: never carries `X-Account-Id`. */
    @SerialName("is_default") val isDefault: Boolean = false,
)
