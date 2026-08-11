package com.exmworkspace.exmwsmail.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
    // Without client="mobile" the backend falls back to the legacy web mode:
    // a 24h access token and no refresh token at all.
    val client: String = "mobile",
    @SerialName("captcha_token") val captchaToken: String? = null,
)

@Serializable
data class TokenPairDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "bearer",
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long = 900,
    val user: UserDto? = null,
)

@Serializable
data class UserDto(
    val id: Long,
    val email: String,
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    val phone: String? = null,
    val mobile: String? = null,
    @SerialName("job_title") val jobTitle: String? = null,
    val department: String? = null,
    val birthday: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

@Serializable
data class RefreshRequest(
    @SerialName("refresh_token") val refreshToken: String,
)

@Serializable
data class LogoutRequest(
    @SerialName("refresh_token") val refreshToken: String? = null,
)

@Serializable
data class CaptchaChallengeDto(
    @SerialName("challenge_id") val challengeId: String,
)

@Serializable
data class CaptchaPointDto(
    val x: Int,
    val y: Int,
    val t: Long,
)

@Serializable
data class CaptchaVerifyRequest(
    @SerialName("challenge_id") val challengeId: String,
    @SerialName("duration_ms") val durationMs: Long,
    val points: List<CaptchaPointDto>,
)

@Serializable
data class CaptchaTokenDto(
    @SerialName("captcha_token") val captchaToken: String,
)

/** Backend error envelope. `detail` is already user-facing Spanish text. */
@Serializable
data class ErrorDto(
    val detail: String? = null,
)
