package tallyvane.authentication.application

import tallyvane.authentication.domain.Attempt
import tallyvane.authentication.domain.FactorKind
import tallyvane.authentication.domain.Purpose
import tallyvane.identity.contract.AccountId
import tallyvane.identity.contract.Accounts
import kotlin.time.Instant

/**
 * Whose account an attempt is, once Google has said.
 *
 * An attempt does not hand out the Google subject it holds: it tells it, with the rest of what it holds,
 * to whoever it is told to (ADR-085), and this is who listens for it. Runs inside the caller's
 * transaction, as [Accounts] does.
 */
public class AttemptOwners(private val accounts: Accounts) {
    /**
     * The account [attempt] is for, or null when Google has not yet vouched for anybody, or vouched for
     * somebody who has no account.
     */
    public fun of(attempt: Attempt): AccountId? {
        val heard = Hearing()
        attempt.writeTo(heard)
        return heard.subject()?.let(accounts::withGoogle)
    }

    override fun toString(): String = "AttemptOwners"

    private class Hearing : Attempt.Record {
        private var heard: String? = null

        fun subject(): String? = heard

        override fun started(purpose: Purpose, at: Instant) = Unit

        override fun identified(kind: FactorKind, subject: String, at: Instant) {
            heard = subject
        }

        override fun verified(kind: FactorKind, at: Instant) = Unit

        override fun failed(at: Instant) = Unit
    }
}
