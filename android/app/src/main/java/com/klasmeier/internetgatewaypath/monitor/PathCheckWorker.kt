package com.klasmeier.internetgatewaypath.monitor

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class PathCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        PathMonitor.runCheckAndNotify(applicationContext)
        return Result.success()
    }

    companion object {
        private const val PERIODIC_NAME = "path_check_periodic"
        private const val ONE_TIME_NAME = "path_check_once"

        fun schedule(context: Context) {
            val periodic = PeriodicWorkRequestBuilder<PathCheckWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodic,
            )
        }

        fun enqueueNow(context: Context) {
            val once = OneTimeWorkRequestBuilder<PathCheckWorker>().build()
            WorkManager.getInstance(context).enqueue(once)
        }

        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_NAME)
            WorkManager.getInstance(context).cancelAllWorkByTag(ONE_TIME_NAME)
        }
    }
}
