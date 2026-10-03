package tallyvane.authentication.application

import tallyvane.authentication.application.port.Attempts
import tallyvane.authentication.contract.NothingToRedeem
import tallyvane.authentication.contract.Proof
import tallyvane.authentication.contract.Redeemed
import tallyvane.authentication.contract.Redemption
import tallyvane.authentication.contract.SignIns
import tallyvane.authentication.domain.FactorKind
import tallyvane.authentication.domain.Progress
import tallyvane.authentication.domain.Purpose
import tallyvane.identity.contract.Accounts
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.Digest
import tallyvane.platform.kernel.Secret
import kotlin.time.Instant

/**
 * Hands a completed sign-in to the module that grants access, and forgets it (ADR-076).
 *
 * Only a sign-in, or a confirmation, the policy of its own purpose calls complete, and whose person has
 * an account, is handed over. A registration that Google identified but whose welcome form is not finished has no
 * account yet, so it stays where it is for the form to finish.
 *
 * Runs inside the caller's transaction, as the ports it reads do.
 */
public class Redemptions(
    private val attempts: Attempts,
    private val policies: ActivePolicies,
    private val accounts: Accounts,
    private val clock: Clock,
    private val keys: SignInKeys,
) : SignIns {
    override fun redeem(secret: Secret): Redemption = redeemFor(secret, SIGN_INS)

    override fun redeemStepUp(secret: Secret): Redemption = redeemFor(secret, STEP_UPS)

    private fun redeemFor(secret: Secret, redeemable: List<Purpose>): Redemption {
        val key = keys.keyOf(secret)
        val attempt = attempts.find(key)
        val purpose = attempt?.let { found -> redeemable.firstOrNull(found::isFor) }
        return when {
            attempt == null || purpose == null -> NothingToRedeem()
            else -> policies.progressOf(attempt, purpose, clock.now()).reportTo(Taking(key))
        }
    }

    override fun toString(): String = "Redemptions(attempts=$attempts)"

    /**
     * Takes the sign-in only when it is complete, and only for someone who has an account.
     */
    private inner class Taking(private val key: Digest) : Progress.Report<Redemption> {
        override fun complete(factors: Set<FactorKind>, authenticatedAt: Instant, subject: String): Redemption {
            val account = accounts.withGoogle(subject)
            // Taken only by the request whose deletion removed it: two that read it together, with
            // different Idempotency-Keys, must not both get a session out of one sign-in.
            return when {
                account == null || !attempts.forget(key) -> NothingToRedeem()
                else -> Redeemed(account, factors.mapTo(mutableSetOf(), ::proofOf), authenticatedAt)
            }
        }

        override fun restricted(
            factors: Set<FactorKind>,
            authenticatedAt: Instant,
            subject: String,
            toSetUp: Set<FactorKind>,
        ): Redemption = NothingToRedeem()

        override fun awaiting(accepted: Set<FactorKind>): Redemption = NothingToRedeem()

        override fun paused(accepted: Set<FactorKind>, until: Instant): Redemption = NothingToRedeem()

        override fun exhausted(): Redemption = NothingToRedeem()

        override fun expired(): Redemption = NothingToRedeem()
    }

    private fun proofOf(kind: FactorKind): Proof = when (kind) {
        FactorKind.Google -> Proof.Google
        FactorKind.Totp -> Proof.Totp
        FactorKind.RecoveryCode -> Proof.RecoveryCode
    }

    private companion object {
        /**
         * The purposes whose completion is a session: signing in, and registering, which ends signed in.
         */
        val SIGN_INS = listOf(Purpose.Login, Purpose.Registration)

        /**
         * The purpose whose completion is a confirmation of a session that already exists (ADR-092).
         */
        val STEP_UPS = listOf(Purpose.StepUp)
    }
}
