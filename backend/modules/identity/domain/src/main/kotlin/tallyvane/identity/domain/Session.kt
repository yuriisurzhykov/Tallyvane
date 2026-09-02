package tallyvane.identity.domain

import kotlin.time.Instant

/**
 * One signed-in device or browser, as `identity`'s own domain model names it — the entity behind
 * the eventual `identity.sessions` row.
 *
 * [id] and [userId] are this module's own domain-local value objects, not `identity:contract`'s
 * published `SessionId`/`UserId` — see those types' own KDoc for why the two are independent by
 * design rather than shared.
 *
 * Carries no token or hash of any kind on purpose, matching the design's own description of the
 * `identity.sessions` row: "principal reference, a human-readable label, last_used_at, current
 * token family id" — nothing about a token value. Where a token's hash is looked up from, for
 * validating a presented access token or detecting a reused refresh token, is a storage design
 * this pass has not made yet; see `SessionIssuer`'s own KDoc for the corrected first draft this
 * produced.
 */
public data class Session(
    public val id: SessionId,
    public val userId: UserId,
    public val device: DeviceLabel,
    public val tokenFamilyId: TokenFamilyId,
    public val createdAt: Instant,
    public val lastUsedAt: Instant,
)
