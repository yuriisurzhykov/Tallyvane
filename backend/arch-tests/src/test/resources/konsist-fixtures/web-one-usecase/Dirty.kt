package tallyvane.jobs.web

import tallyvane.platform.kernel.UseCase

class JobsRoutes(
    private val jobCapture: JobCaptureUseCase,
    private val jobSave: JobSaveUseCase,
)

interface JobCaptureUseCase : UseCase

interface JobSaveUseCase : UseCase
