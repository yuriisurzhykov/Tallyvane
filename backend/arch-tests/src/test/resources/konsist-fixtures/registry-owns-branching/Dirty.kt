package tallyvane.jobs.application

enum class JobSourceKind { LinkedIn, Manual }

class CaptureJob {
    fun label(kind: JobSourceKind): String = when (kind) {
        JobSourceKind.LinkedIn -> "li"
        JobSourceKind.Manual -> "manual"
    }
}
