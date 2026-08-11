package com.exmworkspace.exmwsmail

import android.app.Application
import com.exmworkspace.exmwsmail.di.AppContainer
import com.exmworkspace.exmwsmail.service.MailNotifications
import com.exmworkspace.exmwsmail.work.MailSyncWorker

class MailApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(applicationContext)
        MailNotifications.ensureChannels(this)
        MailSyncWorker.schedule(this)
    }
}
