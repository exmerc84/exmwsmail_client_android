package com.exmworkspace.exmwsmail.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The slice of the session the token refresher touches. Narrow on purpose: it keeps the
 * renewal rules testable without Android's keystore.
 */
interface TokenSession {
    val accessToken: String?
    val refreshToken: String?

    /** True with enough headroom that a request will not race the expiry (§7). */
    fun accessTokenIsFresh(): Boolean

    fun saveTokens(access: String, refresh: String?, expiresInSeconds: Long)

    /** Ends the session. Only the refresher may call this — see its docs. */
    fun clear()
}

/**
 * Persists the JWT pair in EncryptedSharedPreferences (§11.5 — a leaked refresh token is
 * valid for 60 days). Reads are synchronous because the OkHttp interceptor and
 * authenticator run on network threads and need the token inline.
 */
class TokenStore(context: Context) : TokenSession {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _isLoggedIn = MutableStateFlow(prefs.contains(KEY_REFRESH))
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _displayName = MutableStateFlow(prefs.getString(KEY_DISPLAY_NAME, null).orEmpty())
    val displayName: StateFlow<String> = _displayName.asStateFlow()

    private val _email = MutableStateFlow(prefs.getString(KEY_EMAIL, null))
    val email: StateFlow<String?> = _email.asStateFlow()

    override val accessToken: String? get() = prefs.getString(KEY_ACCESS, null)
    override val refreshToken: String? get() = prefs.getString(KEY_REFRESH, null)

    /** Epoch millis at which the access token stops being accepted. */
    val accessExpiresAt: Long get() = prefs.getLong(KEY_ACCESS_EXPIRES_AT, 0L)

    /** True with ~2 min of headroom, as §7 recommends. */
    override fun accessTokenIsFresh(): Boolean =
        accessToken != null && System.currentTimeMillis() < accessExpiresAt - REFRESH_MARGIN_MS

    var deviceId: Long?
        get() = prefs.getLong(KEY_DEVICE_ID, -1L).takeIf { it >= 0 }
        set(value) {
            prefs.edit().putLong(KEY_DEVICE_ID, value ?: -1L).apply()
        }

    /** The FCM token already registered with the backend, to skip redundant re-registration. */
    var registeredFcmToken: String?
        get() = prefs.getString(KEY_FCM_TOKEN, null)
        set(value) {
            prefs.edit().putString(KEY_FCM_TOKEN, value).apply()
        }

    @Synchronized
    override fun saveTokens(access: String, refresh: String?, expiresInSeconds: Long) {
        prefs.edit()
            .putString(KEY_ACCESS, access)
            .apply {
                // /refresh always rotates; keep the previous one only if the server omitted it.
                if (refresh != null) putString(KEY_REFRESH, refresh)
            }
            .putLong(KEY_ACCESS_EXPIRES_AT, System.currentTimeMillis() + expiresInSeconds * 1000L)
            .commit()
        _isLoggedIn.value = prefs.contains(KEY_REFRESH)
    }

    /**
     * The auxiliary mailbox the UI is standing in (§4.23), or null for the primary account.
     * Persisted so a restart reopens the same mailbox; the synchronous getter is for the
     * OkHttp interceptor that stamps `X-Account-Id` on mail requests.
     */
    private val _activeAccount = MutableStateFlow(readActiveAccount())
    val activeAccount: StateFlow<ActiveAccount?> = _activeAccount.asStateFlow()

    val activeAccountServerId: Long?
        get() = prefs.getLong(KEY_ACTIVE_ACCOUNT_ID, -1L).takeIf { it >= 0 }

    /**
     * Server id of the primary mailbox (`is_default`), learned from `GET /api/accounts/`.
     *
     * Needed by the push path: FCM's `account_id` says which mailbox a message landed in,
     * and comparing it against the mailbox the app is pointed at is the only way to know
     * whether syncing is safe. Null until the account list has been fetched once.
     */
    var primaryAccountServerId: Long?
        get() = prefs.getLong(KEY_PRIMARY_ACCOUNT_ID, -1L).takeIf { it >= 0 }
        set(value) {
            prefs.edit().putLong(KEY_PRIMARY_ACCOUNT_ID, value ?: -1L).apply()
        }

    /** Server id of the mailbox every mail request currently resolves to. */
    val scopedAccountServerId: Long?
        get() = activeAccountServerId ?: primaryAccountServerId

    fun setActiveAccount(account: ActiveAccount?) {
        prefs.edit()
            .putLong(KEY_ACTIVE_ACCOUNT_ID, account?.serverId ?: -1L)
            .putString(KEY_ACTIVE_ACCOUNT_EMAIL, account?.email)
            .commit()
        _activeAccount.value = account
    }

    private fun readActiveAccount(): ActiveAccount? {
        val id = prefs.getLong(KEY_ACTIVE_ACCOUNT_ID, -1L).takeIf { it >= 0 } ?: return null
        val email = prefs.getString(KEY_ACTIVE_ACCOUNT_EMAIL, null) ?: return null
        return ActiveAccount(id, email)
    }

    fun saveUser(email: String, displayName: String?) {
        val name = displayName?.trim().orEmpty()
        prefs.edit()
            .putString(KEY_EMAIL, email)
            .putString(KEY_DISPLAY_NAME, name)
            .commit()
        _email.value = email
        _displayName.value = name
    }

    fun saveDisplayName(name: String) {
        val trimmed = name.trim()
        prefs.edit().putString(KEY_DISPLAY_NAME, trimmed).commit()
        _displayName.value = trimmed
    }

    @Synchronized
    override fun clear() {
        prefs.edit().clear().commit()
        _isLoggedIn.value = false
        _displayName.value = ""
        _email.value = null
        _activeAccount.value = null
    }

    companion object {
        private const val FILE_NAME = "exmws_tokens"
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_ACCESS_EXPIRES_AT = "access_expires_at"
        private const val KEY_EMAIL = "email"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_FCM_TOKEN = "fcm_token"
        private const val KEY_ACTIVE_ACCOUNT_ID = "active_account_id"
        private const val KEY_ACTIVE_ACCOUNT_EMAIL = "active_account_email"
        private const val KEY_PRIMARY_ACCOUNT_ID = "primary_account_id"
        private const val REFRESH_MARGIN_MS = 120_000L
    }
}

/** An auxiliary mailbox selected as active (§4.23). The primary account is `null`. */
data class ActiveAccount(val serverId: Long, val email: String)
