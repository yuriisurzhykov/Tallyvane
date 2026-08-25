package tallyvane.jobs.application

import tallyvane.platform.kernel.UseCase

class CaptureJob(private val jobs: Jobs) : UseCase {
    suspend fun capture(url: JobUrl): CaptureOutcome = TODO()
}
