// GATE STUB — androidx.work.
package androidx.work

import android.content.Context
import java.util.concurrent.TimeUnit

open class Data {
    open fun getLong(key: String, defaultValue: Long): Long = defaultValue
    open fun getInt(key: String, defaultValue: Int): Int = defaultValue
    open fun getString(key: String): String? = null
    open fun getBoolean(key: String, defaultValue: Boolean): Boolean = defaultValue

    open class Builder {
        open fun putLong(key: String, value: Long): Builder = this
        open fun putInt(key: String, value: Int): Builder = this
        open fun putString(key: String, value: String?): Builder = this
        open fun putBoolean(key: String, value: Boolean): Builder = this
        open fun build(): Data = Data()
    }
}

open class WorkerParameters

abstract class WorkerFactory

open class ListenableWorker(context: Context, workerParams: WorkerParameters) {

    val applicationContext: Context = context

    val inputData: Data
        get() = Data()

    abstract class Result {
        companion object {
            fun success(): Result = object : Result() {}
            fun retry(): Result = object : Result() {}
            fun failure(): Result = object : Result() {}
            fun failure(data: Data): Result = object : Result() {}
        }
    }
}

abstract class Worker(context: Context, workerParams: WorkerParameters) :
    ListenableWorker(context, workerParams) {
    abstract fun doWork(): Result
}

abstract class CoroutineWorker(context: Context, workerParams: WorkerParameters) :
    ListenableWorker(context, workerParams) {
    abstract suspend fun doWork(): Result
}

open class Constraints {
    open class Builder {
        open fun build(): Constraints = Constraints()
        open fun setRequiredNetworkType(networkType: NetworkType): Builder = this
        open fun setRequiresBatteryNotLow(requiresBatteryNotLow: Boolean): Builder = this
    }
}

enum class NetworkType {
    CONNECTED, METERED, NOT_REQUIRED, NOT_ROAMING, UNMETERED
}

enum class ExistingWorkPolicy {
    REPLACE, KEEP, APPEND, APPEND_OR_REPLACE
}

enum class ExistingPeriodicWorkPolicy {
    REPLACE, KEEP, UPDATE, CANCEL_AND_REENQUEUE
}

open class WorkRequest

open class OneTimeWorkRequest : WorkRequest() {

    open class Builder(workerClass: Class<out ListenableWorker>) {
        open fun setConstraints(constraints: Constraints): Builder = this
        open fun setInitialDelay(duration: Long, timeUnit: TimeUnit): Builder = this
        open fun setInputData(data: Data): Builder = this
        open fun addTag(tag: String): Builder = this
        open fun build(): OneTimeWorkRequest = OneTimeWorkRequest()
    }
}

open class PeriodicWorkRequest : WorkRequest() {

    open class Builder(
        workerClass: Class<out ListenableWorker>,
        repeatInterval: Long,
        repeatIntervalTimeUnit: TimeUnit
    ) {
        open fun setConstraints(constraints: Constraints): Builder = this
        open fun setInitialDelay(duration: Long, timeUnit: TimeUnit): Builder = this
        open fun setInputData(data: Data): Builder = this
        open fun addTag(tag: String): Builder = this
        open fun build(): PeriodicWorkRequest = PeriodicWorkRequest()
    }
}

inline fun <reified W : ListenableWorker> OneTimeWorkRequestBuilder(): OneTimeWorkRequest.Builder =
    OneTimeWorkRequest.Builder(W::class.java)

inline fun <reified W : ListenableWorker> PeriodicWorkRequestBuilder(
    repeatInterval: Long,
    repeatIntervalTimeUnit: TimeUnit
): PeriodicWorkRequest.Builder =
    PeriodicWorkRequest.Builder(W::class.java, repeatInterval, repeatIntervalTimeUnit)

open class Configuration {
    open class Builder {
        open fun setWorkerFactory(workerFactory: WorkerFactory): Builder = this
        open fun build(): Configuration = Configuration()
    }

    interface Provider {
        val workManagerConfiguration: Configuration
    }
}

open class WorkManager {
    open fun enqueueUniquePeriodicWork(
        uniqueWorkName: String,
        existingPeriodicWorkPolicy: ExistingPeriodicWorkPolicy,
        periodicWork: PeriodicWorkRequest
    ) {
    }

    open fun enqueueUniqueWork(
        uniqueWorkName: String,
        existingWorkPolicy: ExistingWorkPolicy,
        work: OneTimeWorkRequest
    ) {
    }

    open fun enqueue(request: WorkRequest) {}
    open fun cancelUniqueWork(uniqueWorkName: String) {}
    open fun cancelAllWork() {}

    companion object {
        fun getInstance(context: Context): WorkManager = WorkManager()
    }
}
