package com.example.exmwsmail

import android.app.Application
import com.example.exmwsmail.di.AppContainer
import com.example.exmwsmail.work.MailSyncWorker

class MailApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(applicationContext)
        MailSyncWorker.schedule(this)
    }
}
