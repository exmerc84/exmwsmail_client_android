package com.exmworkspace.exmwsmail.service

import android.os.Build
import com.exmworkspace.exmwsmail.BuildConfig
import com.exmworkspace.exmwsmail.data.prefs.TokenStore
import com.exmworkspace.exmwsmail.data.remote.MailApi
import com.exmworkspace.exmwsmail.data.remote.dto.DeviceRegisterRequest
import com.exmworkspace.exmwsmail.data.remote.requireBody
import com.exmworkspace.exmwsmail.util.AppLog
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Registers this device for FCM push (§2.2). Registration is an upsert keyed by the FCM
 * token, so calling it on every launch is safe and is what keeps `last_seen_at` current.
 */
class DeviceRegistrar(
    private val tokenStore: TokenStore,
    private val mailApi: MailApi,
) {

    /**
     * Registers unconditionally: the POST is an upsert keyed by token, it keeps
     * `last_seen_at` current, and — decisively — it heals a registration the server no
     * longer has. An "already registered" skip once left this device silently push-less
     * after the backend cleaned its device rows: the app's local flag said registered,
     * the server's table said nothing.
     */
    suspend fun register() = withContext(Dispatchers.IO) {
        if (tokenStore.accessToken == null) return@withContext
        val fcmToken = currentFcmToken() ?: run {
            // No google-services.json, or Play Services missing on this device.
            AppLog.w(TAG, "no FCM token available — push disabled")
            return@withContext
        }
        runCatching {
            val device = mailApi.registerDevice(
                DeviceRegisterRequest(
                    fcmToken = fcmToken,
                    deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                    appVersion = BuildConfig.VERSION_NAME,
                )
            ).requireBody()
            tokenStore.deviceId = device.id
            tokenStore.registeredFcmToken = fcmToken
            AppLog.d(TAG, "device registered id=${device.id}")
        }.onFailure { AppLog.w(TAG, "device registration failed: ${it.message}") }
    }

    suspend fun registerToken(fcmToken: String) = withContext(Dispatchers.IO) {
        if (tokenStore.accessToken == null) return@withContext
        runCatching {
            val device = mailApi.registerDevice(
                DeviceRegisterRequest(
                    fcmToken = fcmToken,
                    deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                    appVersion = BuildConfig.VERSION_NAME,
                )
            ).requireBody()
            tokenStore.deviceId = device.id
            tokenStore.registeredFcmToken = fcmToken
        }.onFailure { AppLog.w(TAG, "token refresh registration failed: ${it.message}") }
    }

    private suspend fun currentFcmToken(): String? = suspendCancellableCoroutine { cont ->
        val messaging = runCatching { FirebaseMessaging.getInstance() }.getOrNull()
        if (messaging == null) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        messaging.token.addOnCompleteListener { task ->
            cont.resume(if (task.isSuccessful) task.result else null)
        }
    }

    private companion object {
        const val TAG = "DeviceRegistrar"
    }
}
