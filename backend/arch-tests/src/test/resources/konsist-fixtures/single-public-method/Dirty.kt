package tallyvane.jobs.application

import tallyvane.platform.kernel.UseCase

interface JobCaptureUseCase : UseCase {
    suspend fun capture(url: JobUrl): CaptureOutcome

    suspend fun recapture(url: JobUrl): CaptureOutcome
}
