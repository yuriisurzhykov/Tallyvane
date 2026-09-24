package tallyvane.identity.application.port

import tallyvane.identity.domain.email.EmailChallenge
import tallyvane.platform.kernel.Secret
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** All methods participate in the caller's transaction. Implementations serialize per address/purpose/binding. */
public interface EmailChallengeStore {
    public suspend fun issue(challenge: EmailChallenge, hash: Secret, now: Instant, resendAt: Instant, maxAttempts: Int): Boolean

    public suspend fun find(id: Uuid): EmailChallenge?

    /** A wrong digest spends an attempt; success consumes once. Expired/exhausted/consumed rows reject. */
    public suspend fun consume(id: Uuid, hash: Secret, now: Instant): Boolean

    public suspend fun revoke(id: Uuid)
}
