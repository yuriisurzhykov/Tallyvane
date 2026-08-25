package tallyvane.jobs.application

import tallyvane.platform.kernel.UseCase

interface JobCaptureUseCase : UseCase {
    suspend fun capture(url: JobUrl): CaptureOutcome

    class CaptureJob(private val jobs: Jobs) : JobCaptureUseCase {
        override suspend fun capture(url: JobUrl): CaptureOutcome = TODO()
    }
}
