package com.example.exmwsmail.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class CredentialStore(context: Context) {

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

    private val _isLoggedIn = MutableStateFlow(prefs.contains(KEY_EMAIL))
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _displayName = MutableStateFlow(prefs.getString(KEY_DISPLAY_NAME, null).orEmpty())
    val displayName: StateFlow<String> = _displayName.asStateFlow()

    suspend fun save(credentials: Credentials) = withContext(Dispatchers.IO) {
        prefs.edit()
            .putString(KEY_EMAIL, credentials.email)
            .putString(KEY_PASSWORD, credentials.password)
            .commit()
        _isLoggedIn.value = true
    }

    suspend fun load(): Credentials? = withContext(Dispatchers.IO) {
        val email = prefs.getString(KEY_EMAIL, null) ?: return@withContext null
        val password = prefs.getString(KEY_PASSWORD, null) ?: return@withContext null
        Credentials(email, password)
    }

    suspend fun saveDisplayName(name: String) = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        prefs.edit().putString(KEY_DISPLAY_NAME, trimmed).commit()
        _displayName.value = trimmed
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        prefs.edit().clear().commit()
        _isLoggedIn.value = false
        _displayName.value = ""
    }

    companion object {
        private const val FILE_NAME = "exmws_credentials"
        private const val KEY_EMAIL = "email"
        private const val KEY_PASSWORD = "password"
        private const val KEY_DISPLAY_NAME = "display_name"
    }
}
