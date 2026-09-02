package tallyvane.identity.domain.session

import tallyvane.identity.domain.token.TokenFamilyId
import tallyvane.identity.domain.user.UserId
import kotlin.time.Instant

/**
 * One signed-in device or browser, as `identity`'s own domain model names it — the entity behind
 * the eventual `identity.sessions` row.
 *
 * Carries no token or hash of any kind. Why, and where a token's hash is looked up from instead:
 * `domain/README.md`.
 */
public data class Session(
    public val id: SessionId,
    public val userId: UserId,
    public val device: DeviceLabel,
    public val tokenFamilyId: TokenFamilyId,
    public val createdAt: Instant,
    public val lastUsedAt: Instant,
)
