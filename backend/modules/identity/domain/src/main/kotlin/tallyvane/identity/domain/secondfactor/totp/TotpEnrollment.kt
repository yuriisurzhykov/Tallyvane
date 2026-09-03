package tallyvane.identity.domain.secondfactor.totp

import tallyvane.identity.domain.secondfactor.EncryptedSecret
import tallyvane.identity.domain.user.UserId
import kotlin.time.Instant

/**
 * One account's TOTP seed, encrypted, and whether it has cleared confirmation yet.
 *
 * [active] starts `false`: [tallyvane.identity.application.port.SecondFactorMethod.startEnrollment]
 * creates one, but nothing may treat the account as TOTP-protected until
 * [tallyvane.identity.application.port.SecondFactorMethod.confirmEnrollment] flips it — proof the
 * account holder actually captured the seed correctly, not merely that one was generated.
 */
public data class TotpEnrollment(
    public val userId: UserId,
    public val secret: EncryptedSecret,
    public val active: Boolean,
    public val createdAt: Instant,
)
