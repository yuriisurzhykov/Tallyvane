package tallyvane.jobs.contract

import tallyvane.platform.kernel.UseCase

interface JobCaptureUseCase : UseCase {
    suspend fun capture(url: JobUrl): CaptureOutcome
}
