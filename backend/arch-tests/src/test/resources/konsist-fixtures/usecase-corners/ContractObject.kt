package tallyvane.jobs.contract

import tallyvane.platform.kernel.UseCase

// Forbidden: a use case in the published contract, arriving as an object so that
// a predicate asking only for classes would miss it.
object CaptureJobFromContract : UseCase {
    suspend fun capture(url: String): Unit = TODO()
}
