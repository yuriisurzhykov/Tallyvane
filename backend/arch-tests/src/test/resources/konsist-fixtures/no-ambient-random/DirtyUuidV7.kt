package tallyvane.jobs.domain

import kotlin.uuid.Uuid

class SnapshotId {
    fun id(): Uuid = Uuid.generateV7()
}
