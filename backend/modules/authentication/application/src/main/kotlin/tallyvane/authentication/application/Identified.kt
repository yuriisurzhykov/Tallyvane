package tallyvane.authentication.application

import tallyvane.authentication.domain.Attempt
import tallyvane.authentication.domain.FactorKind
import tallyvane.authentication.domain.Purpose
import tallyvane.authentication.domain.VerifiedFactor
import tallyvane.identity.contract.AccountId
import tallyvane.identity.contract.Accounts
import kotlin.time.Instant

/**
 * A person Google vouched for at [at]: who they are to Google, and what it said about them.
 */
internal class Identified(private val subject: String, val profile: GoogleProfile, private val at: Instant) {
    /**
     * The factor an attempt records for them.
     */
    fun factor(): VerifiedFactor = VerifiedFactor.identifying(FactorKind.Google, subject, at)

    /**
     * Their account, if they have one.
     */
    fun accountIn(accounts: Accounts): AccountId? = accounts.withGoogle(subject)

    /**
     * A registration begun for them, already holding the Google factor that names them.
     */
    fun registration(): Attempt = Attempt(Purpose.Registration, at).withVerified(factor())

    override fun toString(): String = "Identified(at=$at)"
}
