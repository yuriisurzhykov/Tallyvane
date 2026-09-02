package tallyvane.identity.domain.user

import kotlin.time.Instant

/**
 * A human account, as `identity`'s own domain model names it — the entity behind the eventual
 * `identity.users` row.
 *
 * [displayName] is nullable: nothing in this pass's registration flow collects one, and inventing
 * a requirement registration itself does not ask for would be a rule with no caller behind it.
 */
public data class User(
    public val id: UserId,
    public val email: Email,
    public val displayName: String?,
    public val createdAt: Instant,
    public val disabledAt: Instant?,
)
