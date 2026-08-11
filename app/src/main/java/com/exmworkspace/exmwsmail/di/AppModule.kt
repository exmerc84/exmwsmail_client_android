package com.exmworkspace.exmwsmail.di

import android.content.Context
import com.exmworkspace.exmwsmail.data.local.MailDatabase
import com.exmworkspace.exmwsmail.data.prefs.TokenStore
import com.exmworkspace.exmwsmail.data.remote.NetworkModule
import com.exmworkspace.exmwsmail.data.repository.AuthRepository
import com.exmworkspace.exmwsmail.data.repository.ContactsRepository
import com.exmworkspace.exmwsmail.data.repository.MailRepository
import com.exmworkspace.exmwsmail.service.DeviceRegistrar
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import java.io.File

class AppContainer(context: Context) {

    val tokenStore: TokenStore = TokenStore(context)

    private val network = NetworkModule(tokenStore)

    /** Shared with the WebView so inline images ride the authenticated session. */
    val httpClient: OkHttpClient get() = network.httpClient

    private val database: MailDatabase = MailDatabase.create(context)

    val mailRepository: MailRepository = MailRepository(
        api = network.mailApi,
        accountDao = database.accountDao(),
        folderDao = database.folderDao(),
        messageDao = database.messageDao(),
        messageBodyDao = database.messageBodyDao(),
        attachmentDao = database.attachmentDao(),
        attachmentsDir = File(context.filesDir, "attachments"),
    )

    val contactsRepository: ContactsRepository = ContactsRepository(network.contactsApi)

    val authRepository: AuthRepository = AuthRepository(
        tokenStore = tokenStore,
        authApi = network.authApi,
        mailApi = network.mailApi,
        tokenRefresher = network.tokenRefresher,
        mailRepository = mailRepository,
    )

    val deviceRegistrar: DeviceRegistrar = DeviceRegistrar(
        tokenStore = tokenStore,
        mailApi = network.mailApi,
    )

    /**
     * The message a tapped notification asked for, waiting to be opened.
     *
     * It lands here rather than going straight to the navigator because the activity receives
     * the intent before the nav graph exists — and on a warm start it arrives through
     * `onNewIntent`, when the graph is already up. A flow lets both cases be handled the same
     * way: the activity posts, the navigator consumes when it can.
     */
    val pendingNotification = MutableStateFlow<NotificationTarget?>(null)
}

/** The (folder, uid) pair an FCM notification carries — the API's own way to name a message. */
data class NotificationTarget(val folder: String, val uid: String)
