package com.example.exmwsmail.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.exmwsmail.MailApplication
import java.util.concurrent.TimeUnit
import javax.mail.AuthenticationFailedException

class MailSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? MailApplication ?: return Result.success()
        val container = app.container
        return try {
            val email = container.authRepository.currentCredentials()?.email
                ?: return Result.success()
            val accountId = container.mailRepository.ensureAccount(email)
            container.mailRepository.syncFolders(accountId)
            container.mailRepository.syncInboxTop(limit = 50)
            Result.success()
        } catch (_: AuthenticationFailedException) {
            // Bad credentials — don't bang on the server.
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_NAME = "exm-mail-sync"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<MailSyncWorker>(
                15, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES, // flex window so the system can batch
            )
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
