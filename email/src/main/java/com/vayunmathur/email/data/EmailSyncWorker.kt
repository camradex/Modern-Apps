package com.vayunmathur.email.data

import android.content.Context
import android.util.Log
import androidx.work.*
import com.vayunmathur.email.EmailManager
import java.util.concurrent.TimeUnit

class EmailSyncWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val db = EmailDatabase.getInstance(applicationContext)
        val dao = db.emailDao()
        val accounts = dao.getAccounts()

        if (accounts.isEmpty()) {
            return Result.success()
        }

        val manager = EmailManager()
        var hasErrors = false

        for (account in accounts) {
            try {
                Log.d("EmailSync", "Syncing account: ${account.email}")
                val auth = EmailManager.AuthType.OAuth2(account.accessToken)

                // 1. Sync Folders
                val folders = manager.fetchFolders("imap.gmail.com", account.email, auth)
                dao.insertFolders(folders)

                // 2. Sync Messages for each folder
                for (folder in folders) {
                    if (!folder.holdsMessages) continue
                    
                    try {
                        val messages = manager.fetchMessages(
                            host = "imap.gmail.com",
                            user = account.email,
                            auth = auth,
                            folderName = folder.fullName,
                            limit = 50,
                            offset = 0,
                            fetchBodies = true
                        )
                        dao.insertMessages(messages)
                    } catch (e: Exception) {
                        Log.e("EmailSync", "Failed to sync folder ${folder.fullName} for ${account.email}", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("EmailSync", "Failed to sync account ${account.email}", e)
                hasErrors = true
            }
        }

        return if (hasErrors) Result.retry() else Result.success()
    }

    companion object {
        private const val SYNC_WORK_NAME = "EmailSyncWorker"

        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<EmailSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                SYNC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
        }

        fun runOneOffSync(context: Context) {
            val syncRequest = OneTimeWorkRequestBuilder<EmailSyncWorker>()
                .build()

            WorkManager.getInstance(context).enqueue(syncRequest)
        }
        
        fun cancelSync(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(SYNC_WORK_NAME)
        }
    }
}
