package com.exmworkspace.exmwsmail.data.repository

import com.exmworkspace.exmwsmail.data.prefs.ActiveAccount
import com.exmworkspace.exmwsmail.data.prefs.TokenStore
import com.exmworkspace.exmwsmail.data.remote.ApiException
import com.exmworkspace.exmwsmail.data.remote.AuthApi
import com.exmworkspace.exmwsmail.data.remote.MailApi
import com.exmworkspace.exmwsmail.data.remote.toApiException
import com.exmworkspace.exmwsmail.data.remote.dto.CaptchaPointDto
import com.exmworkspace.exmwsmail.data.remote.dto.CaptchaVerifyRequest
import com.exmworkspace.exmwsmail.data.remote.dto.LoginRequest
import com.exmworkspace.exmwsmail.data.remote.TokenRefresher
import com.exmworkspace.exmwsmail.data.remote.dto.LogoutRequest
import com.exmworkspace.exmwsmail.data.remote.requireBody
import com.exmworkspace.exmwsmail.data.remote.requireSuccess
import com.exmworkspace.exmwsmail.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.IOException

sealed interface LoginResult {
    data object Success : LoginResult
    /** The IP has a recent failed attempt; the slider must be solved first (§1.5). */
    data object CaptchaRequired : LoginResult
    data class RateLimited(val retryAfterSeconds: Long) : LoginResult
    data class Failure(val message: String) : LoginResult
}

class AuthRepository(
    private val tokenStore: TokenStore,
    private val authApi: AuthApi,
    private val mailApi: MailApi,
    private val tokenRefresher: TokenRefresher,
    private val mailRepository: MailRepository,
) {
    val isLoggedIn: StateFlow<Boolean> = tokenStore.isLoggedIn
    val displayName: StateFlow<String> = tokenStore.displayName
    val email: StateFlow<String?> = tokenStore.email

    suspend fun updateDisplayName(name: String) = withContext(Dispatchers.IO) {
        tokenStore.saveDisplayName(name)
    }

    suspend fun currentEmail(): String? = tokenStore.email.value

    /**
     * The mailbox the UI is standing in (§4.23): the active auxiliary account, or the login
     * account when none is selected. Every mail surface that partitions local state by email
     * must use this one — [currentEmail] answers the *login* identity, which stays primary.
     */
    fun activeMailEmail(): String? =
        tokenStore.activeAccount.value?.email ?: tokenStore.email.value

    val activeAccount: StateFlow<ActiveAccount?> = tokenStore.activeAccount

    fun setActiveAccount(account: ActiveAccount?) = tokenStore.setActiveAccount(account)

    suspend fun signIn(
        email: String,
        password: String,
        captchaToken: String? = null,
    ): LoginResult = withContext(Dispatchers.IO) {
        try {
            val response = authApi.login(
                LoginRequest(
                    email = email.trim(),
                    password = password,
                    captchaToken = captchaToken,
                )
            )
            if (!response.isSuccessful) {
                val error = response.toApiException()
                return@withContext when {
                    error.isCaptchaRequired -> LoginResult.CaptchaRequired
                    error.code == 429 -> LoginResult.RateLimited(error.retryAfterSeconds ?: 60)
                    error.code == 401 -> LoginResult.Failure("Credenciales incorrectas")
                    else -> LoginResult.Failure(error.userMessage)
                }
            }
            val pair = response.body()
                ?: return@withContext LoginResult.Failure("Respuesta vacía del servidor")
            if (pair.refreshToken == null) {
                // The backend answered in legacy web mode, which means `client":"mobile"`
                // never reached it. Without a refresh token there is no session to keep, so
                // fail loudly instead of leaving the login screen sitting there.
                AppLog.w(TAG, "login returned no refresh token — client flag not honoured?")
                return@withContext LoginResult.Failure(
                    "El servidor no entregó una sesión móvil. Reintenta o avisa a soporte.",
                )
            }
            tokenStore.saveTokens(pair.accessToken, pair.refreshToken, pair.expiresIn)
            val user = pair.user
            tokenStore.saveUser(
                email = user?.email ?: email.trim(),
                displayName = user?.displayName ?: user?.fullName,
            )
            LoginResult.Success
        } catch (e: IOException) {
            LoginResult.Failure("No se pudo conectar: ${e.message ?: "red no disponible"}")
        } catch (e: Exception) {
            // A malformed payload must not leave the button spinning with nothing on screen.
            AppLog.w(TAG, "login failed unexpectedly", e)
            LoginResult.Failure("Respuesta inesperada del servidor")
        }
    }

    /** @return the challenge id to feed back into [verifyCaptcha]. */
    suspend fun requestCaptchaChallenge(): String = withContext(Dispatchers.IO) {
        authApi.captchaChallenge().requireBody().challengeId
    }

    /**
     * @return a single-use captcha token (TTL 5 min), or null when the backend judged the
     * trace non-human — the caller must ask for a fresh challenge and retry.
     */
    suspend fun verifyCaptcha(
        challengeId: String,
        durationMs: Long,
        points: List<CaptchaPointDto>,
    ): String? = withContext(Dispatchers.IO) {
        val response = authApi.captchaVerify(
            CaptchaVerifyRequest(challengeId, durationMs, points)
        )
        if (response.isSuccessful) response.body()?.captchaToken else null
    }

    /**
     * Restores a session from the stored refresh token on cold start (§11.6).
     * @return false when the user must log in again.
     */
    suspend fun restoreSession(): Boolean = withContext(Dispatchers.IO) {
        if (tokenStore.refreshToken == null) return@withContext false
        try {
            // Must go through the shared refresher, never call /refresh directly: the first
            // screen fires its own requests while this runs, and two concurrent refreshes
            // mean the second presents an already-rotated token. The backend reads that as
            // theft and revokes the whole family, logging the user out (§1.2).
            tokenRefresher.refreshIfNeeded(staleToken = null)
            // The refresher clears the store on a terminal 401; anything else is recoverable.
            if (tokenStore.refreshToken == null) return@withContext false
            runCatching {
                val user = authApi.me().requireBody()
                tokenStore.saveUser(user.email, user.displayName ?: user.fullName)
            }
            true
        } catch (e: IOException) {
            AppLog.w(TAG, "restoreSession offline: ${e.message}")
            // Offline start: keep whatever cache we have rather than bouncing to login.
            true
        }
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        // Unregister the device first so the backend stops pushing to it (§7.6).
        tokenStore.deviceId?.let { id ->
            runCatching { mailApi.deleteDevice(id).requireSuccess() }
        }
        runCatching { authApi.logout(LogoutRequest(tokenStore.refreshToken)) }
        mailRepository.wipeLocalData()
        tokenStore.clear()
    }

    private companion object {
        const val TAG = "AuthRepository"
    }
}
