package tallyvane.jobs.application

import kotlin.uuid.Uuid

class MintJobId {
    fun id(): Uuid = Uuid.random()
}
