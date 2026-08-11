package com.exmworkspace.exmwsmail.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.exmworkspace.exmwsmail.MailApplication
import com.exmworkspace.exmwsmail.data.remote.ApiException
import java.util.concurrent.TimeUnit

/**
 * Safety net behind FCM: if a push is dropped (or Play Services is unavailable on the
 * device) the inbox still catches up within the period.
 */
class MailSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? MailApplication ?: return Result.success()
        val container = app.container
        if (container.tokenStore.refreshToken == null) return Result.success()

        return try {
            val email = container.tokenStore.email.value ?: return Result.success()
            val accountId = container.mailRepository.ensureAccount(email)
            container.mailRepository.syncFolders(accountId)
            container.mailRepository.findFolderByName(accountId, "INBOX")
                ?.let { container.mailRepository.syncFolderTop(it) }
            Result.success()
        } catch (e: ApiException) {
            // An expired session is not something retrying fixes.
            if (e.code == 401 || e.code == 403) Result.success() else Result.retry()
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
