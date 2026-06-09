package com.example.exmwsmail.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.exmwsmail.MailApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive action=${intent.action}")
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON" -> Unit
            else -> return
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as? MailApplication
                if (app == null) {
                    Log.e(TAG, "applicationContext not MailApplication")
                    return@launch
                }
                val creds = runCatching { app.container.credentialStore.load() }
                    .onFailure { Log.e(TAG, "creds load error", it) }
                    .getOrNull()
                Log.d(TAG, "creds present=${creds != null}")
                if (creds != null) {
                    runCatching { MailIdleService.start(context.applicationContext) }
                        .onFailure { Log.e(TAG, "start service failed", it) }
                }
            } finally {
                pending.finish()
                Log.d(TAG, "pending finished")
            }
        }
    }

    companion object {
        private const val TAG = "MailBootReceiver"
    }
}
