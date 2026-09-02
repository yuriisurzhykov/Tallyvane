package tallyvane.identity.domain.secondfactor

import tallyvane.identity.domain.session.DeviceLabel
import tallyvane.identity.domain.user.UserId
import kotlin.time.Instant

/**
 * A primary credential that checked out, for an account with at least one second factor enabled —
 * the entity behind the eventual `identity.pending_authentications` row, and what
 * [tallyvane.identity.domain.outcome.AuthenticationOutcome.RequiresSecondFactor] points at.
 *
 * [device] is carried here rather than re-sent on the later `/auth/mfa/verify` call — a correction
 * against the design plan's own field list, which named only "who, which factors are still
 * available, when it expires": the eventual [tallyvane.identity.domain.session.Session] needs a
 * device label regardless, and re-collecting it from the client on a second request risks it
 * silently disagreeing with the first — `application/README.md`.
 *
 * [availableMethods] is never empty: nothing constructs this case unless at least one
 * [SecondFactorKind] is actually enrolled for [userId], per the design's own rule that
 * `RequiresSecondFactor` is the *alternative* to issuing a session, not something layered on top of
 * it — `application/README.md`.
 *
 * ```
 * PendingAuthentication(id, userId, device, availableMethods = emptySet(), createdAt, expiresAt) // throws
 * ```
 */
public data class PendingAuthentication(
    public val id: PendingAuthenticationId,
    public val userId: UserId,
    public val device: DeviceLabel,
    public val availableMethods: Set<SecondFactorKind>,
    public val createdAt: Instant,
    public val expiresAt: Instant,
) {
    init {
        require(availableMethods.isNotEmpty()) { "PendingAuthentication needs at least one available method" }
    }
}
