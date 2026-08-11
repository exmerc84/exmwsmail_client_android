package com.exmworkspace.exmwsmail.data.remote

import com.exmworkspace.exmwsmail.BuildConfig
import com.exmworkspace.exmwsmail.data.prefs.TokenStore
import com.exmworkspace.exmwsmail.data.remote.dto.RefreshRequest
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * Wires the HTTP stack. Two clients on purpose: [refreshClient] has no auth interceptor so
 * the refresh call itself can never recurse into another refresh.
 */
class NetworkModule(tokenStore: TokenStore) {

    private val converter = ApiJson.asConverterFactory("application/json".toMediaType())

    private val logging = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BASIC
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
        // Tokens must never reach logcat, even in debug builds.
        redactHeader("Authorization")
    }

    private val refreshClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HeadersInterceptor())
        .addInterceptor(logging)
        .build()

    private val refreshAuthApi: AuthApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(refreshClient)
        .addConverterFactory(converter)
        .build()
        .create(AuthApi::class.java)

    val tokenRefresher = TokenRefresher(tokenStore) { token ->
        refreshAuthApi.refresh(RefreshRequest(token))
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        // Sending with attachments can take a while on mobile uplinks.
        .writeTimeout(120, TimeUnit.SECONDS)
        .addInterceptor(HeadersInterceptor())
        .addInterceptor(AuthInterceptor(tokenStore, tokenRefresher))
        // Scopes mail requests to the active auxiliary account (§4.23).
        .addInterceptor(AccountHeaderInterceptor(tokenStore))
        // After auth (it only reads responses); repairs mis-encoded JSON before Retrofit.
        .addInterceptor(CharsetFallbackInterceptor())
        .addInterceptor(logging)
        .authenticator(TokenAuthenticator(tokenRefresher))
        .build()

    /** Shared with the WebView so inline `cid:` images are fetched with the same session. */
    val httpClient: OkHttpClient get() = client

    private val retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(converter)
        .build()

    val authApi: AuthApi = retrofit.create(AuthApi::class.java)
    val mailApi: MailApi = retrofit.create(MailApi::class.java)
    val contactsApi: ContactsApi = retrofit.create(ContactsApi::class.java)
}
