package tallyvane.identity.domain.email

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

public data class EmailChallengePolicy(
    public val lifetime: Duration = 10.minutes,
    public val resendDelay: Duration = 60.seconds,
    public val maxAttempts: Int = 5,
) {
    init {
        require(lifetime.isPositive() && lifetime.isFinite())
        require(resendDelay.isPositive() && resendDelay.isFinite())
        require(maxAttempts > 0)
    }
}
