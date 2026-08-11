package com.exmworkspace.exmwsmail.service

import com.exmworkspace.exmwsmail.MailApplication
import com.exmworkspace.exmwsmail.data.local.entity.MessageEntity
import com.exmworkspace.exmwsmail.util.AppLog
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Replaces the old foreground IMAP IDLE service. The backend pushes on new mail (§3), so
 * the app no longer holds a socket open or drains the battery to stay current.
 *
 * The backend sends data-only messages, so this callback runs for background *and*
 * foreground deliveries and the app renders every notification itself — which is what lets
 * it attach the real sender/subject and the unread count for launcher badges.
 */
class MailMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        AppLog.d(TAG, "FCM token rotated")
        val app = applicationContext as? MailApplication ?: return
        // Blocking on purpose — see onMessageReceived. A single POST, well within budget.
        runBlocking {
            withTimeoutOrNull(SYNC_BUDGET_MS) {
                app.container.deviceRegistrar.registerToken(token)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data["type"] != TYPE_NEW_EMAIL) return

        val folder = data["folder"] ?: "INBOX"
        val uid = data["uid"]
        val app = applicationContext as? MailApplication ?: return
        val container = app.container

        // Which mailbox the mail landed in (§4.23) vs. which one every request currently
        // resolves to. They differ whenever the user is standing in another account, and
        // syncing then would be actively harmful: the interceptor scopes the fetch to the
        // *selected* account while the rows get filed under the pushed one — two mailboxes
        // mixed in one local partition. When they disagree, notify without syncing.
        val pushedAccountId = data["account_id"]?.toLongOrNull()
        val scopedAccountId = container.tokenStore.scopedAccountServerId
        val sameMailbox = pushedAccountId == null || scopedAccountId == null ||
            pushedAccountId == scopedAccountId

        // Sync first, notify after: the freshly cached row supplies sender/subject when the
        // payload lacks them, and the folder's unseen counter becomes the badge number.
        //
        // Blocking, not launched: this callback runs on a background thread and the service
        // is destroyed as soon as it returns — a launched coroutine was cancelled mid-sync
        // ("Job was cancelled") and could take the notification down with it. FCM allows
        // ~20s here; the timeout keeps us far under that, and on timeout the notification
        // still posts from the payload's own fields.
        var unread = 0
        var cached: MessageEntity? = null
        // The mailbox on screen, not the login identity: standing in an auxiliary, its mail
        // must be cached under its own account row.
        val accountEmail = container.authRepository.activeMailEmail()
        if (accountEmail != null && sameMailbox) {
            runBlocking {
                withTimeoutOrNull(SYNC_BUDGET_MS) {
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
                } ?: AppLog.w(TAG, "push sync timed out, notifying from payload")
            }
        }

        // Defensive: should the backend ever send a notification payload again, FCM has
        // already rendered it in background and re-posting would duplicate.
        if (message.notification != null) return
        MailNotifications.ensureChannels(this)
        MailNotifications.postNewMailNotification(
            context = this,
            // "sender", not "from": `from` is a reserved FCM key and can never arrive.
            title = cached?.from?.ifBlank { null } ?: data["sender"] ?: "Nuevo correo",
            text = cached?.subject?.ifBlank { null } ?: data["subject"]
                ?: "Tienes un correo nuevo",
            folder = folder,
            uid = uid,
            unreadCount = unread,
        )
    }

    private companion object {
        const val TAG = "MailMessaging"
        const val TYPE_NEW_EMAIL = "new_email"
        /** Far under FCM's ~20s execution allowance for this callback. */
        const val SYNC_BUDGET_MS = 8_000L
    }
}
