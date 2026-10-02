package tallyvane.authentication.infrastructure

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import tallyvane.platform.kernel.Digest
import kotlin.uuid.Uuid

/**
 * The row an attempt is kept in, found by the digest of the secret its browser holds.
 */
internal class AttemptRows {
    /**
     * The id of the row kept for [key], or null when there is none.
     */
    fun idOf(key: Digest): Uuid? {
        val told = DigestColumns()
        key.writeTo(told)
        return AttemptsTable.selectAll()
            .where { (AttemptsTable.secretDigest eq told.bytes()) and (AttemptsTable.pepperVersion eq told.version()) }
            .singleOrNull()
            ?.get(AttemptsTable.id)
    }

    override fun toString(): String = "AttemptRows"
}
