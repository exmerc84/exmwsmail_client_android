package com.exmworkspace.exmwsmail.data.remote

import com.exmworkspace.exmwsmail.data.prefs.TokenSession
import com.exmworkspace.exmwsmail.data.remote.dto.TokenPairDto
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * A refresh token is valid for 60 days (§1.2), so the cost of dropping a session by mistake
 * is a forced re-login. These pin down exactly when the session may and may not be ended.
 */
class TokenRefresherTest {

    /** In-memory stand-in for [TokenStore]; the real one needs Android's keystore. */
    private class FakeStore {
        var access: String? = "old-access"
        var refresh: String? = "refresh-1"
        var expiresAt: Long = 0
        var cleared = false
    }

    private fun refresher(
        store: FakeStore,
        respond: suspend (String) -> Response<TokenPairDto>,
    ): TokenRefresher {
        val session = object : TokenSession {
            override val accessToken: String? get() = store.access
            override val refreshToken: String? get() = store.refresh
            override fun accessTokenIsFresh() = System.currentTimeMillis() < store.expiresAt
            override fun saveTokens(access: String, refresh: String?, expiresInSeconds: Long) {
                store.access = access
                refresh?.let { store.refresh = it }
                store.expiresAt = System.currentTimeMillis() + expiresInSeconds * 1000
            }
            override fun clear() {
                store.cleared = true
                store.access = null
                store.refresh = null
            }
        }
        return TokenRefresher(session, respond)
    }

    private fun success(access: String, refresh: String) = Response.success(
        TokenPairDto(accessToken = access, refreshToken = refresh, expiresIn = 900)
    )

    private fun error(code: Int): Response<TokenPairDto> = Response.error(
        code,
        """{"detail":"Invalid or revoked refresh token"}"""
            .toResponseBody("application/json".toMediaType()),
    )

    @Test
    fun rotates_and_stores_the_new_pair() {
        val store = FakeStore()
        val token = refresher(store) { success("new-access", "refresh-2") }
            .refreshIfNeeded(staleToken = "old-access")

        assertEquals("new-access", token)
        assertEquals("refresh-2", store.refresh)
        assertTrue(!store.cleared)
    }

    @Test
    fun a_401_from_refresh_ends_the_session() {
        val store = FakeStore()
        val token = refresher(store) { error(401) }.refreshIfNeeded(staleToken = "old-access")

        assertNull(token)
        assertTrue("a rejected refresh token must end the session", store.cleared)
    }

    @Test
    fun a_network_failure_keeps_the_session() {
        val store = FakeStore()
        val token = refresher(store) { throw IOException("offline") }
            .refreshIfNeeded(staleToken = "old-access")

        assertNull(token)
        // The whole point: a blip must not cost the user a 60-day session.
        assertTrue("network failure must not sign the user out", !store.cleared)
        assertEquals("refresh-1", store.refresh)
    }

    @Test
    fun a_server_error_keeps_the_session() {
        val store = FakeStore()
        val token = refresher(store) { error(503) }.refreshIfNeeded(staleToken = "old-access")

        assertNull(token)
        assertTrue(!store.cleared)
    }

    @Test
    fun retries_once_before_giving_up_and_recovers() {
        val store = FakeStore()
        val calls = AtomicInteger(0)
        val token = refresher(store) {
            if (calls.getAndIncrement() == 0) throw IOException("blip")
            success("new-access", "refresh-2")
        }.refreshIfNeeded(staleToken = "old-access")

        assertEquals(2, calls.get())
        assertEquals("new-access", token)
        assertTrue(!store.cleared)
    }

    @Test
    fun reuses_a_token_another_thread_just_obtained() {
        val store = FakeStore()
        val calls = AtomicInteger(0)
        // The caller's token is stale, but the store already holds a newer one.
        store.access = "already-refreshed"
        val token = refresher(store) {
            calls.incrementAndGet()
            success("should-not-happen", "refresh-2")
        }.refreshIfNeeded(staleToken = "old-access")

        assertEquals("already-refreshed", token)
        assertEquals("no second /refresh — that would trip theft detection", 0, calls.get())
    }

    @Test
    fun a_proactive_call_skips_the_network_while_the_token_is_fresh() {
        val store = FakeStore()
        store.expiresAt = System.currentTimeMillis() + 600_000
        val calls = AtomicInteger(0)
        val token = refresher(store) {
            calls.incrementAndGet()
            success("new-access", "refresh-2")
        }.refreshIfNeeded(staleToken = null)

        assertNotNull(token)
        assertEquals(0, calls.get())
    }
}
