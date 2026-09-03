package tallyvane.identity.domain.session

import tallyvane.identity.domain.token.TokenFamilyId
import tallyvane.identity.domain.user.UserId
import kotlin.time.Instant

/**
 * One signed-in device or browser, as `identity`'s own domain model names it — the entity behind
 * the eventual `identity.sessions` row.
 *
 * Carries no token or hash of any kind — [tallyvane.identity.application.port.SessionStore]'s own
 * `attachAccessToken`/`findByAccessTokenHash` carry that instead, deliberately outside this type.
 * Why: `domain/README.md`.
 *
 * [revokedAt] is a domain-visible fact instead: unlike a token hash, a session's own revocation
 * history is exactly what [tallyvane.identity.application.port.SessionStore.listFor] exists to show
 * an account holder ("you signed this device out"), not a security detail worth hiding from it.
 */
public data class Session(
    public val id: SessionId,
    public val userId: UserId,
    public val device: DeviceLabel,
    public val tokenFamilyId: TokenFamilyId,
    public val createdAt: Instant,
    public val lastUsedAt: Instant,
    public val revokedAt: Instant?,
)
