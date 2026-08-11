package com.exmworkspace.exmwsmail.data.remote

import com.exmworkspace.exmwsmail.data.prefs.TokenSession
import com.exmworkspace.exmwsmail.data.remote.dto.TokenPairDto
import com.exmworkspace.exmwsmail.util.AppLog
import kotlinx.coroutines.runBlocking
import retrofit2.Response
import java.io.IOException

/**
 * Owns the one place where the token pair is renewed, and the only place allowed to end a
 * session.
 *
 * Every `/refresh` rotates the refresh token and revokes the previous one, so two concurrent
 * refreshes would make the second present an already-revoked token — which the backend reads
 * as theft and revokes the whole family (§1.2). Hence the single lock, and the "did someone
 * else already refresh?" check inside it.
 *
 * The refresh token lasts 60 days, so losing a session is expensive: only a definitive 401
 * from `/refresh` clears it. Network failures are retried and otherwise left alone — the
 * caller fails that one request instead of the user losing their login.
 */
class TokenRefresher(
    private val tokenStore: TokenSession,
    private val refresh: suspend (String) -> Response<TokenPairDto>,
) {
    private val lock = Any()

    /**
     * Ensures a usable access token, refreshing only if [staleToken] is still the current
     * one. Returns null when this attempt did not produce a token; the session survives
     * unless the backend explicitly rejected the refresh token.
     */
    fun refreshIfNeeded(staleToken: String?): String? = synchronized(lock) {
        val current = tokenStore.accessToken
        if (staleToken == null) {
            // Proactive call: refresh only if the token really is near expiry. Whoever held
            // the lock before us may have just renewed it.
            if (tokenStore.accessTokenIsFresh()) return current
        } else if (current != null && current != staleToken) {
            // Reactive call: another thread refreshed while we waited — reuse its result.
            return current
        }

        val refreshToken = tokenStore.refreshToken ?: return null

        repeat(ATTEMPTS) { attempt ->
            val response = try {
                runBlocking { refresh(refreshToken) }
            } catch (e: IOException) {
                // Offline or a dropped connection. Keep the session; try again shortly.
                AppLog.w(TAG, "refresh attempt ${attempt + 1} failed: ${e.message}")
                if (attempt < ATTEMPTS - 1) Thread.sleep(RETRY_DELAY_MS)
                return@repeat
            }

            if (response.isSuccessful) {
                val pair = response.body() ?: return null
                tokenStore.saveTokens(pair.accessToken, pair.refreshToken, pair.expiresIn)
                return pair.accessToken
            }

            if (response.code() == 401) {
                // The backend rejected the refresh token itself: expired, already used, or
                // the family was revoked. Nothing left to salvage.
                AppLog.w(TAG, "refresh rejected (401) — clearing session")
                tokenStore.clear()
                return null
            }

            // 5xx or anything else: transient as far as the session is concerned.
            AppLog.w(TAG, "refresh failed with ${response.code()}; keeping session")
            if (attempt < ATTEMPTS - 1) Thread.sleep(RETRY_DELAY_MS)
        }
        return null
    }

    private companion object {
        const val TAG = "TokenRefresher"
        const val ATTEMPTS = 2
        const val RETRY_DELAY_MS = 800L
    }
}
