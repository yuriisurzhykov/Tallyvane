package tallyvane.jobs.application

import tallyvane.platform.kernel.UseCase

interface ExportUseCase : UseCase {
    suspend fun export(request: ExportRequest): ExportOutcome
}

// Forbidden: the implementation is nested, but inside an unrelated class —
// `usecase-is-interface` must check WHERE it is nested, not only that it exists.
class Registry {
    class Export(private val jobs: Jobs) : ExportUseCase {
        override suspend fun export(request: ExportRequest): ExportOutcome = TODO()
    }
}
