package com.exmworkspace.exmwsmail.data.remote

import kotlinx.serialization.json.Json

/**
 * JSON configuration for every API call.
 *
 * `encodeDefaults = true` is load-bearing, not cosmetic: kotlinx.serialization omits
 * default values otherwise, which silently dropped `client":"mobile"` from the login body.
 * The backend then answered in legacy web mode — a 24h token with no refresh token — and the
 * app had no session to keep (§1.1). `DeviceRegisterRequest.platform` had the same problem.
 *
 * Kept separate from the network wiring so the contract can be unit-tested without Android.
 */
val ApiJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = true
    // Optional fields left null (captcha_token, source_folder…) are omitted rather than
    // sent as an explicit null.
    explicitNulls = false
}
