package com.exmworkspace.exmwsmail.service

import com.exmworkspace.exmwsmail.MailApplication
import com.exmworkspace.exmwsmail.data.local.entity.MessageEntity
import com.exmworkspace.exmwsmail.util.AppLog
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Replaces the old foreground IMAP IDLE service. The backend pushes on new mail (§3), so
 * the app no longer holds a socket open or drains the battery to stay current.
 *
 * Note that when the app is backgrounded, FCM renders the `notification` payload itself and
 * this callback never fires — the work here is the foreground path: refresh the cache so the
 * open list updates, and notify only when the system did not.
 */
class MailMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        AppLog.d(TAG, "FCM token rotated")
        val app = applicationContext as? MailApplication ?: return
        scope.launch { app.container.deviceRegistrar.registerToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data["type"] != TYPE_NEW_EMAIL) return

        val folder = data["folder"] ?: "INBOX"
        val uid = data["uid"]
        val app = applicationContext as? MailApplication ?: return
        val container = app.container

        scope.launch {
            // Sync first, notify after: the freshly cached row supplies the sender and
            // subject §3's data payload does not carry, and the folder's unseen counter
            // becomes the launcher badge number. The pause this costs is within the push's
            // own latency budget (§3.1 allows up to ~60s).
            var unread = 0
            var cached: MessageEntity? = null
            val accountEmail = container.tokenStore.email.value
            if (accountEmail != null) {
                runCatching {
                    val accountId = container.mailRepository.ensureAccount(accountEmail)
                    val local = container.mailRepository.findFolderByName(accountId, folder)
                    local?.let { container.mailRepository.syncFolderTop(it) }
                    container.mailRepository.refreshFolderCounters(accountId)
                    // Re-read: the counter sync just refreshed unseenCount.
                    val fresh = container.mailRepository.findFolderByName(accountId, folder)
                    unread = fresh?.unseenCount ?: 0
                    if (fresh != null && uid != null) {
                        cached = container.mailRepository.findMessageByUid(fresh, uid)
                    }
                }.onFailure { AppLog.w(TAG, "push sync failed: ${it.message}") }
            }

            // Only post our own notification when FCM did not already display one.
            if (message.notification != null) return@launch
            MailNotifications.ensureChannels(this@MailMessagingService)
            MailNotifications.postNewMailNotification(
                context = this@MailMessagingService,
                title = cached?.from?.ifBlank { null } ?: data["from"] ?: "Nuevo correo",
                text = cached?.subject?.ifBlank { null } ?: data["subject"]
                    ?: "Tienes un correo nuevo",
                folder = folder,
                uid = uid,
                unreadCount = unread,
            )
        }
    }

    override fun onDestroy() {
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "MailMessaging"
        const val TYPE_NEW_EMAIL = "new_email"
    }
}
