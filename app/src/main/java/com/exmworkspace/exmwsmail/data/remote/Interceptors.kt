package com.exmworkspace.exmwsmail.data.remote

import android.os.Build
import com.exmworkspace.exmwsmail.BuildConfig
import com.exmworkspace.exmwsmail.data.prefs.TokenStore
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.Route

/** Endpoints that must never carry (or refresh) a Bearer token. */
private fun Request.isAuthEndpoint(): Boolean {
    val path = url.encodedPath
    return path.startsWith("/api/auth/login") ||
        path.startsWith("/api/auth/refresh") ||
        path.startsWith("/api/auth/captcha")
}

/**
 * Descriptive User-Agent so `login_attempts` can tell app traffic from scraping (§11.3).
 */
class HeadersInterceptor : Interceptor {
    private val userAgent =
        "EXMWS-Mobile/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE}; ${Build.MODEL})"

    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(
            chain.request().newBuilder()
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .build()
        )
}

/**
 * Re-decodes JSON responses whose bytes are not actually valid UTF-8 (see
 * [decodeUtf8OrWindows1252]). Runs on JSON only: attachment downloads and other binary
 * responses pass through untouched, and streaming stays intact for them because their body is
 * never buffered here.
 */
class CharsetFallbackInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val body = response.body ?: return response
        val contentType = body.contentType() ?: return response
        if (contentType.subtype != "json") return response
        val text = decodeUtf8OrWindows1252(body.bytes())
        return response.newBuilder()
            .body(text.toResponseBody(contentType))
            .build()
    }
}

/**
 * Attaches the Bearer token, renewing it proactively when it is within the refresh margin
 * so the common path never spends a round-trip on a 401 (§7).
 */
class AuthInterceptor(
    private val tokenStore: TokenStore,
    private val refresher: TokenRefresher,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.isAuthEndpoint()) return chain.proceed(request)

        val token = if (tokenStore.accessTokenIsFresh()) {
            tokenStore.accessToken
        } else {
            refresher.refreshIfNeeded(staleToken = null) ?: tokenStore.accessToken
        }

        val authed = if (token != null) {
            request.newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            request
        }
        return chain.proceed(authed)
    }
}

/**
 * Reactive fallback: a 401 that slipped past the proactive refresh (clock skew, a token
 * revoked server-side) triggers one refresh and one retry. Returning null surfaces the 401,
 * which the repositories translate into a forced re-login.
 */
class TokenAuthenticator(
    private val refresher: TokenRefresher,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.isAuthEndpoint()) return null
        // Retry exactly once: a second 401 means refreshing did not help.
        if (response.priorResponse != null) return null

        val stale = response.request.header("Authorization")?.removePrefix("Bearer ")
        val fresh = refresher.refreshIfNeeded(stale) ?: return null
        if (fresh == stale) return null

        return response.request.newBuilder()
            .header("Authorization", "Bearer $fresh")
            .build()
    }
}
