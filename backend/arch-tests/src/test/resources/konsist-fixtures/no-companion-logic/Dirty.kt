package tallyvane.jobs.domain

class JobId(val raw: String) {
    companion object {
        fun parse(raw: String): JobId = when {
            raw.isEmpty() -> JobId("missing")
            else -> JobId(raw)
        }
    }
}
