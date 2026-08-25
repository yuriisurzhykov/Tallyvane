package tallyvane.jobs.application

import tallyvane.platform.kernel.UseCase

interface JobCaptureUseCase : UseCase {
    suspend fun capture(url: JobUrl): CaptureOutcome
}

// Forbidden: the implementation must nest inside the interface, not sit beside it.
// Note the marker is reached indirectly, through JobCaptureUseCase.
class CaptureJob(private val jobs: Jobs) : JobCaptureUseCase {
    override suspend fun capture(url: JobUrl): CaptureOutcome = TODO()
}
