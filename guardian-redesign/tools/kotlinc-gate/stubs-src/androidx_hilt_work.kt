// GATE STUB — androidx.hilt.work.
package androidx.hilt.work

import androidx.work.WorkerFactory

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class HiltWorker

class HiltWorkerFactory : WorkerFactory()
