package app.offgrow.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.offgrow.garden.GardenEngine
import java.util.concurrent.TimeUnit

/** Every 15 minutes: close finished days, update vitality, redraw the garden and widgets. */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            GardenEngine.refresh(applicationContext, render = true)
            Result.success()
        } catch (e: Exception) {
            Log.w("RefreshWorker", "refresh failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val PERIODIC = "offgrow-refresh"
        private const val ONCE = "offgrow-refresh-now"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun runOnce(context: Context) {
            val req = OneTimeWorkRequestBuilder<RefreshWorker>().build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(ONCE, ExistingWorkPolicy.REPLACE, req)
        }
    }
}
