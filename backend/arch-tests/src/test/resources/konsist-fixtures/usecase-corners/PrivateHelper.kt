package tallyvane.jobs.application

import tallyvane.platform.kernel.UseCase

// Forbidden, and it compiles: Kotlin permits a `private` interface member that has
// a body. That is implementation inside an abstraction, so a use-case interface may
// not carry one — the rule counts every function, not only the published ones.
interface JobSaveUseCase : UseCase {
    suspend fun save(job: Job): SaveOutcome

    private fun normalised(job: Job): Job = job
}
