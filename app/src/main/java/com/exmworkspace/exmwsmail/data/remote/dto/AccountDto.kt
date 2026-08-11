package com.exmworkspace.exmwsmail.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One row of `GET /api/accounts/`, read off a live response (§4.23) — the doc trails off at
 * "(id, email, display_name, ...)" and the field is really `email_address`:
 * `{"id":1,"email_address":"...","display_name":"...","is_default":true,"is_slave":false}`.
 */
@Serializable
data class RemoteAccountDto(
    val id: Long,
    @SerialName("email_address") val email: String = "",
    @SerialName("display_name") val displayName: String? = null,
    /** The login mailbox: never carries `X-Account-Id` and cannot be deleted. */
    @SerialName("is_default") val isDefault: Boolean = false,
)

/**
 * `POST /api/accounts/` — alta of an auxiliary IMAP mailbox. The doc does not publish the
 * body, so this mirrors the GET's field names with the minimum any IMAP alta needs; if the
 * backend's validation error names more required fields, they get added here.
 */
@Serializable
data class AccountCreateRequest(
    @SerialName("email_address") val email: String,
    val password: String,
    @SerialName("display_name") val displayName: String? = null,
)
