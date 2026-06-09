package com.exmworkspace.exmwsmail.data.mail

import com.exmworkspace.exmwsmail.data.prefs.Credentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.mail.AuthenticationFailedException
import javax.mail.MessagingException

sealed class ConnectionResult {
    object Success : ConnectionResult()
    data class AuthFailure(val message: String) : ConnectionResult()
    data class NetworkFailure(val message: String) : ConnectionResult()
    data class UnknownFailure(val message: String) : ConnectionResult()
}

class MailConnectionTester {

    suspend fun test(credentials: Credentials): ConnectionResult = withContext(Dispatchers.IO) {
        try {
            testImap(credentials)
            testSmtp(credentials)
            ConnectionResult.Success
        } catch (e: AuthenticationFailedException) {
            ConnectionResult.AuthFailure(e.message ?: "Authentication failed")
        } catch (e: MessagingException) {
            ConnectionResult.NetworkFailure(e.message ?: "Network error")
        } catch (e: Exception) {
            ConnectionResult.UnknownFailure(e.message ?: e::class.java.simpleName)
        }
    }

    private fun testImap(credentials: Credentials) {
        val store = MailSessions.imapSession().getStore("imaps")
        try {
            store.connect(MailConfig.IMAP_HOST, MailConfig.IMAP_PORT, credentials.email, credentials.password)
        } finally {
            runCatching { store.close() }
        }
    }

    private fun testSmtp(credentials: Credentials) {
        val transport = MailSessions.smtpSession().getTransport("smtps")
        try {
            transport.connect(MailConfig.SMTP_HOST, MailConfig.SMTP_PORT, credentials.email, credentials.password)
        } finally {
            runCatching { transport.close() }
        }
    }
}
