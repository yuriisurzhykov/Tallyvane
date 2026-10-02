package tallyvane.platform.persistence

import tallyvane.platform.idempotency.Claim
import kotlin.uuid.Uuid

/**
 * A [Claim] as the table holds it: heard from the claim, never read off it (ADR-085).
 */
internal class ClaimFacts private constructor(val owner: String, val key: Uuid, val fingerprint: ByteArray) {
    /**
     * What a claim says to a record, collected.
     */
    private class Hearing : Claim.Record {
        private var heard: ClaimFacts? = null

        override fun claimed(owner: String, key: Uuid, fingerprint: ByteArray) {
            heard = ClaimFacts(owner, key, fingerprint)
        }

        fun facts(): ClaimFacts = checkNotNull(heard) { "A claim must say its facts when it is told to write itself." }
    }

    companion object {
        fun of(claim: Claim): ClaimFacts = Hearing().also { claim.writeTo(it) }.facts()
    }
}
