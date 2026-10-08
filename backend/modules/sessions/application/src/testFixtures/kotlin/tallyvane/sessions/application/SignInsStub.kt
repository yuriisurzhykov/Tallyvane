package tallyvane.sessions.application

import tallyvane.authentication.contract.NothingToRedeem
import tallyvane.authentication.contract.Proof
import tallyvane.authentication.contract.Redeemed
import tallyvane.authentication.contract.Redemption
import tallyvane.authentication.contract.SignIns
import tallyvane.identity.contract.AccountId
import tallyvane.platform.kernel.Secret
import kotlin.time.Instant

/**
 * [SignIns] that has been told which secrets are completed sign-ins, and who they belong to.
 *
 * Single use, as the contract says: redeeming a sign-in takes it away.
 */
class SignInsStub : SignIns {
    private val completed = mutableMapOf<Secret, Redemption>()
    private val administrators = mutableMapOf<Secret, Redemption>()
    private val confirmed = mutableMapOf<Secret, Redemption>()

    /**
     * Makes [secret] a completed sign-in of [account], who proved who they are by [proofs] at [at].
     */
    fun complete(secret: Secret, account: AccountId, proofs: Set<Proof>, at: Instant) {
        completed[secret] = Redeemed(account, proofs, at)
    }

    /**
     * Makes [secret] a completed administrator's sign-in of [account], who proved who they are by [proofs]
     * at [at].
     */
    fun completeAdmin(secret: Secret, account: AccountId, proofs: Set<Proof>, at: Instant) {
        administrators[secret] = Redeemed(account, proofs, at)
    }

    /**
     * Makes [secret] a completed confirmation of [account], who proved who they are by [proofs] at [at].
     */
    fun confirm(secret: Secret, account: AccountId, proofs: Set<Proof>, at: Instant) {
        confirmed[secret] = Redeemed(account, proofs, at)
    }

    override fun redeem(secret: Secret): Redemption = completed.remove(secret) ?: NothingToRedeem()

    override fun redeemAdminLogin(secret: Secret): Redemption = administrators.remove(secret) ?: NothingToRedeem()

    override fun redeemStepUp(secret: Secret): Redemption = confirmed.remove(secret) ?: NothingToRedeem()

    override fun toString(): String = "SignInsStub(completed=${completed.size})"
}
