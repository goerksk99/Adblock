package de.adshield.data

import android.content.Context
import androidx.work.*
import de.adshield.AdShieldApp
import java.util.concurrent.TimeUnit

class FilterWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
        val app=applicationContext as AdShieldApp
        app.ready.await()
        return if(app.filters.update())Result.success() else if(runAttemptCount<2)Result.retry() else Result.failure()
    }
    companion object {
        fun schedule(context:Context,hours:Long){
            val manager=WorkManager.getInstance(context)
            if(hours==0L){manager.cancelUniqueWork("filter-update");return}
            val request=PeriodicWorkRequestBuilder<FilterWorker>(hours,TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
                .setInitialDelay(hours,TimeUnit.HOURS).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,1,TimeUnit.HOURS).build()
            manager.enqueueUniquePeriodicWork("filter-update",ExistingPeriodicWorkPolicy.UPDATE,request)
        }
    }
}
