package com.exmworkspace.exmwsmail.data.remote

import com.exmworkspace.exmwsmail.data.remote.dto.ErrorDto
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.io.IOException

private val errorJson = Json { ignoreUnknownKeys = true }

/**
 * A non-2xx response. The backend returns `detail` already worded in Spanish for end users
 * (§6), so [detail] is safe to surface directly; [userMessage] falls back per status code.
 */
class ApiException(
    val code: Int,
    val detail: String?,
    val retryAfterSeconds: Long? = null,
) : IOException(detail ?: "HTTP $code") {

    val isCaptchaRequired: Boolean get() = code == 401 && detail == CAPTCHA_REQUIRED

    /** Refresh token revoked/expired, or theft detection tripped → force a re-login. */
    val isRevokedSession: Boolean
        get() = code == 401 && detail?.contains("revoked", ignoreCase = true) == true

    val userMessage: String
        get() = detail ?: when (code) {
            401 -> "Sesión expirada"
            403 -> "No tienes permiso para esta operación"
            404 -> "El recurso ya no existe"
            409 -> "Ya existe un elemento con ese nombre"
            422 -> "Datos inválidos"
            429 -> "Demasiadas peticiones, espera un momento"
            in 500..599 -> "Error del servidor, inténtalo de nuevo"
            else -> "Error de red ($code)"
        }

    companion object {
        const val CAPTCHA_REQUIRED = "captcha_required"
    }
}

/** Unwraps a Retrofit response, converting failures into [ApiException]. */
fun <T> Response<T>.requireBody(): T {
    if (!isSuccessful) throw toApiException()
    return body() ?: throw ApiException(code(), "Respuesta vacía del servidor")
}

/** Like [requireBody] but tolerates an empty/`null` JSON body. */
fun <T> Response<T>.bodyOrNull(): T? {
    if (!isSuccessful) throw toApiException()
    return body()
}

/** For endpoints whose payload we ignore — only success matters. */
fun <T> Response<T>.requireSuccess() {
    if (!isSuccessful) throw toApiException()
}

fun <T> Response<T>.toApiException(): ApiException {
    val raw = runCatching { errorBody()?.string() }.getOrNull()
    val detail = raw?.takeIf { it.isNotBlank() }?.let { text ->
        runCatching { errorJson.decodeFromString<ErrorDto>(text).detail }.getOrNull()
            ?: text.takeIf { it.length < 300 }
    }
    val retryAfter = headers()["Retry-After"]?.toLongOrNull()
    return ApiException(code(), detail, retryAfter)
}
