package tallyvane.identity.domain.email

import tallyvane.identity.domain.user.Email
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Public metadata; never contains a code or its digest. Binding identifies the pending operation. */
public data class EmailChallenge(
    public val id: Uuid,
    public val email: Email,
    public val purpose: EmailChallengePurpose,
    public val binding: String,
    public val expiresAt: Instant,
)
