package com.exmworkspace.exmwsmail.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.exmworkspace.exmwsmail.util.AppLog as Log
import androidx.core.content.ContextCompat
import com.exmworkspace.exmwsmail.MailApplication
import com.exmworkspace.exmwsmail.data.mail.MailConfig
import com.exmworkspace.exmwsmail.data.mail.MailSessions
import com.sun.mail.imap.IMAPFolder
import com.sun.mail.imap.IMAPStore
import javax.mail.Folder
import javax.mail.event.MessageCountAdapter
import javax.mail.event.MessageCountEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MailIdleService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var idleJob: Job? = null

    // Coalesce concurrent sync triggers (so flurry of IMAP events doesn't pile up).
    private val syncMutex = Mutex()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        MailNotifications.ensureChannels(this)
        val notification = MailNotifications.buildForegroundNotification(this)
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                // remoteMessaging is permitted from BOOT_COMPLETED on Android 14+;
                // dataSync is not.
                startForeground(
                    MailNotifications.FG_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING,
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    MailNotifications.FG_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            } else {
                startForeground(MailNotifications.FG_NOTIFICATION_ID, notification)
            }
            Log.d(TAG, "startForeground OK")
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        if (idleJob == null || idleJob?.isActive != true) {
            idleJob = scope.launch { runIdleLoop() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        idleJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun runIdleLoop() {
        Log.d(TAG, "runIdleLoop start")
        val app = applicationContext as? MailApplication ?: return
        val container = app.container
        var backoff = 5_000L
        while (scope.isActive) {
            val creds = try {
                container.credentialStore.load()
            } catch (e: Exception) {
                Log.e(TAG, "creds load failed", e); null
            }
            if (creds == null) {
                Log.d(TAG, "no credentials — stopping")
                stopSelf()
                return
            }
            try {
                Log.d(TAG, "connecting to IMAP…")
                val session = MailSessions.imapSession()
                val store = (session.getStore("imaps") as IMAPStore).also {
                    it.connect(MailConfig.HOST, MailConfig.IMAP_PORT, creds.email, creds.password)
                }
                Log.d(TAG, "IMAP connected, opening INBOX")
                try {
                    val inbox = store.getFolder("INBOX") as IMAPFolder
                    inbox.open(Folder.READ_ONLY)
                    inbox.addMessageCountListener(object : MessageCountAdapter() {
                        override fun messagesAdded(e: MessageCountEvent) {
                            val count = e.messages?.size ?: 1
                            Log.d(TAG, "messagesAdded=$count")
                            scope.launch { checkInboxForNewMail() }
                        }
                    })
                    backoff = 5_000L
                    // Always sync on (re)connect so we never miss messages that
                    // arrived during the gap between IDLE drops, regardless of
                    // whether the server pushed EXISTS during IDLE.
                    scope.launch { checkInboxForNewMail() }
                    val deadline = System.currentTimeMillis() + 28 * 60 * 1000L
                    Log.d(TAG, "entering IDLE loop")
                    while (System.currentTimeMillis() < deadline && scope.isActive) {
                        try {
                            withContext(Dispatchers.IO) { inbox.idle() }
                        } catch (e: Exception) {
                            Log.w(TAG, "idle() failed: ${e.message}")
                            break
                        }
                    }
                    Log.d(TAG, "IDLE loop ended (refresh)")
                    runCatching { inbox.close(false) }
                } finally {
                    runCatching { store.close() }
                }
            } catch (e: javax.mail.AuthenticationFailedException) {
                Log.e(TAG, "auth failed — stopping", e)
                stopSelf()
                return
            } catch (e: Exception) {
                Log.w(TAG, "IMAP error, backoff ${backoff}ms: ${e.message}")
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(5 * 60_000L)
            }
        }
        Log.d(TAG, "runIdleLoop exiting")
    }

    private suspend fun checkInboxForNewMail() {
        val app = applicationContext as? MailApplication ?: return
        val container = app.container
        syncMutex.withLock {
            val prefs = getSharedPreferences("exm_mail_state", MODE_PRIVATE)
            val lastNotifiedUid = prefs.getLong(KEY_LAST_NOTIFIED_UID, -1L)
            try {
                container.mailRepository.syncInboxTop(limit = 50)
            } catch (e: Exception) {
                Log.w(TAG, "syncInboxTop failed: ${e.message}")
                return
            }
            val currentMaxUid = runCatching { container.mailRepository.inboxMaxUid() }
                .getOrDefault(0L)
            // First run: just remember the current max — don't flood with notifications
            // for everything that's already in INBOX.
            if (lastNotifiedUid < 0L) {
                prefs.edit().putLong(KEY_LAST_NOTIFIED_UID, currentMaxUid).apply()
                Log.d(TAG, "first sync, baselined lastNotifiedUid=$currentMaxUid")
                return
            }
            if (currentMaxUid <= lastNotifiedUid) {
                Log.d(TAG, "no new mail (lastNotified=$lastNotifiedUid, max=$currentMaxUid)")
                return
            }
            val newest = runCatching {
                container.mailRepository.newestInboxAboveUid(lastNotifiedUid)
            }.getOrNull()
            if (newest != null) {
                Log.d(TAG, "new mail: uid=${newest.uid} ${newest.from} / ${newest.subject}")
                MailNotifications.postNewMailNotification(
                    context = this,
                    title = newest.from.ifBlank { "Nuevo correo" },
                    text = newest.subject.ifBlank { "(sin asunto)" },
                    count = 1,
                )
            }
            prefs.edit().putLong(KEY_LAST_NOTIFIED_UID, currentMaxUid).apply()
        }
    }

    companion object {
        private const val TAG = "MailIdleService"
        private const val KEY_LAST_NOTIFIED_UID = "last_notified_inbox_uid"

        fun start(context: Context) {
            Log.d(TAG, "start() called")
            ContextCompat.startForegroundService(
                context,
                Intent(context, MailIdleService::class.java),
            )
        }

        fun stop(context: Context) {
            Log.d(TAG, "stop() called")
            context.stopService(Intent(context, MailIdleService::class.java))
        }
    }
}
