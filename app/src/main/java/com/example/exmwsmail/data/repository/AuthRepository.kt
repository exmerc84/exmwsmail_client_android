package com.example.exmwsmail.data.repository

import com.example.exmwsmail.data.mail.ConnectionResult
import com.example.exmwsmail.data.mail.ImapGateway
import com.example.exmwsmail.data.mail.MailConnectionTester
import com.example.exmwsmail.data.prefs.CredentialStore
import com.example.exmwsmail.data.prefs.Credentials
import kotlinx.coroutines.flow.StateFlow

class AuthRepository(
    private val credentialStore: CredentialStore,
    private val connectionTester: MailConnectionTester,
    private val imapGateway: ImapGateway,
    private val mailRepository: MailRepository,
) {
    val isLoggedIn: StateFlow<Boolean> = credentialStore.isLoggedIn
    val displayName: StateFlow<String> = credentialStore.displayName

    suspend fun updateDisplayName(name: String) = credentialStore.saveDisplayName(name)

    suspend fun signIn(email: String, password: String): ConnectionResult {
        val credentials = Credentials(email.trim(), password)
        val result = connectionTester.test(credentials)
        if (result is ConnectionResult.Success) {
            credentialStore.save(credentials)
        }
        return result
    }

    suspend fun signOut() {
        imapGateway.close()
        mailRepository.wipeLocalData()
        credentialStore.clear()
    }

    suspend fun currentCredentials(): Credentials? = credentialStore.load()
}
