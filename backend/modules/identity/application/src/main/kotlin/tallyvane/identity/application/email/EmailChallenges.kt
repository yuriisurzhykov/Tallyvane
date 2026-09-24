package tallyvane.identity.application.email

import tallyvane.identity.application.port.AuthenticationCodes
import tallyvane.identity.application.port.EmailChallengeStore
import tallyvane.identity.application.port.EmailDelivery
import tallyvane.identity.domain.email.EmailChallenge
import tallyvane.identity.domain.email.EmailChallengePolicy
import tallyvane.identity.domain.email.EmailChallengePurpose
import tallyvane.identity.domain.user.Email
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.IdGenerator
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.Verdict
import kotlin.uuid.Uuid

public class EmailChallenges(
    private val store: EmailChallengeStore,
    private val delivery: EmailDelivery,
    private val codes: AuthenticationCodes,
    private val transactions: TransactionRunner,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val policy: EmailChallengePolicy = EmailChallengePolicy(),
) {
    /**
     * Null means cooldown.
     * A challenge is returned only after SMTP acceptance.
     * */
    public suspend fun issue(email: Email, purpose: EmailChallengePurpose, binding: String = ""): EmailChallenge? {
        val now = clock.now()
        val challenge = EmailChallenge(ids.next(), Email(email.value.lowercase()), purpose, binding, now + policy.lifetime)
        val code = codes.emailCode()
        val accepted = transactions.inTransaction {
            Verdict.Commit(store.issue(challenge, codes.hash(context(challenge), code), now, now + policy.resendDelay, policy.maxAttempts))
        }
        if (!accepted) return null
        try {
            delivery.sendCode(email, purpose, code)
        } catch (failure: Exception) {
            transactions.inTransaction { store.revoke(challenge.id); Verdict.Commit(Unit) }
            throw failure
        }
        return challenge
    }

    public suspend fun challenge(id: Uuid): EmailChallenge? = transactions.inTransaction {
        Verdict.Commit(store.find(id))
    }

    /**
     * Opens its own transaction so failed guesses commit even when the caller rejects authentication.
     * */
    public suspend fun verify(id: Uuid, email: Email, purpose: EmailChallengePurpose, code: Secret, binding: String = ""): Boolean =
        transactions.inTransaction {
            val challenge = store.find(id)
            if (challenge == null || !challenge.email.value.equals(email.value, ignoreCase = true) ||
                challenge.purpose != purpose || challenge.binding != binding
            ) {
                Verdict.Commit(false)
            } else {
                Verdict.Commit(store.consume(id, codes.hash(context(challenge), code), clock.now()))
            }
        }

    private fun context(challenge: EmailChallenge): String = "email:${challenge.purpose}:${challenge.id}"
}
