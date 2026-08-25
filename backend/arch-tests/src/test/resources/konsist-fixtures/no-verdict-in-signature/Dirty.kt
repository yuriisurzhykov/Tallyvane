package tallyvane.jobs.application.port

import tallyvane.platform.kernel.Verdict

interface Jobs {
    suspend fun save(job: Job): Verdict<JobId>
}
