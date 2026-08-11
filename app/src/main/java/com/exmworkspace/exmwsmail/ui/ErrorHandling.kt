package com.exmworkspace.exmwsmail.ui

import com.exmworkspace.exmwsmail.data.remote.ApiException
import com.exmworkspace.exmwsmail.data.repository.AuthRepository
import java.io.IOException

/**
 * Turns a thrown failure into text worth showing.
 *
 * Deliberately does NOT end the session. Only [com.exmworkspace.exmwsmail.data.remote
 * .TokenRefresher] may do that, and only when `/refresh` itself answers 401 — a refresh token
 * is good for 60 days (§1.2), and a 401 arriving here can just as easily be a network blip
 * that stopped the renewal from going through. Signing out on it threw away perfectly valid
 * sessions and forced a fresh login every time the connection hiccuped.
 */
fun AuthRepository.describeFailure(e: Throwable): String = when {
    e is ApiException && (e.code == 401 || e.isRevokedSession) ->
        "No se pudo autenticar la petición, reintenta en un momento"

    e is ApiException -> e.userMessage
    e is IOException -> "Sin conexión con el servidor"
    else -> e.message ?: e::class.java.simpleName
}
