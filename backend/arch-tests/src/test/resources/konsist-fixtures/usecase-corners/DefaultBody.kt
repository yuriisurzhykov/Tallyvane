package tallyvane.jobs.application

import tallyvane.platform.kernel.UseCase

// Forbidden: one method, so a count alone is satisfied — but it carries a body, so
// the nested class could skip the override and nothing would notice.
interface JobArchiveUseCase : UseCase {
    suspend fun archive(job: Job): ArchiveOutcome = TODO()
}
