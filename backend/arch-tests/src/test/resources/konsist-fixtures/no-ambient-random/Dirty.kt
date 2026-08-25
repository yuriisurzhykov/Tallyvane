package tallyvane.jobs.application

import java.util.UUID

class CaptureJob {
    fun id(): String = UUID.randomUUID().toString()
}
