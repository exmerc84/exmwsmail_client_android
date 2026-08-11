package com.exmworkspace.exmwsmail

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.exmworkspace.exmwsmail.di.NotificationTarget
import com.exmworkspace.exmwsmail.service.MailNotifications
import com.exmworkspace.exmwsmail.ui.RootContent
import com.exmworkspace.exmwsmail.ui.theme.EXMWSMailTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Cold start from the shade: the intent is already here, but the nav graph is not,
        // so it is parked for the navigator to pick up.
        captureNotificationTarget(intent)
        setContent {
            EXMWSMailTheme {
                RootContent()
            }
        }
    }

    /** Warm start: the activity is singleTop, so a second tap arrives here, not in onCreate. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureNotificationTarget(intent)
    }

    override fun onResume() {
        super.onResume()
        // Whatever brought the user here, they have now seen their mail: drop the new-mail
        // notification so it stops sitting in the shade and stops feeding the launcher badge.
        MailNotifications.cancelNewMail(this)
    }

    private fun captureNotificationTarget(intent: Intent?) {
        val folder = intent?.getStringExtra(MailNotifications.EXTRA_FOLDER) ?: return
        val uid = intent.getStringExtra(MailNotifications.EXTRA_UID) ?: return
        // Cleared off the intent as well: without this a configuration change re-delivers the
        // same intent and the message reopens itself after the user has navigated away.
        intent.removeExtra(MailNotifications.EXTRA_FOLDER)
        intent.removeExtra(MailNotifications.EXTRA_UID)
        (application as MailApplication).container.pendingNotification.value =
            NotificationTarget(folder = folder, uid = uid)
    }
}
