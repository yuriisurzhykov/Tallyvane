package tallyvane.identity.domain.user

import kotlin.uuid.Uuid

/**
 * A user's identity as `identity`'s own domain model names it — the primary key behind the
 * eventual `identity.users` row.
 *
 * A different type from `identity:contract`'s `UserId`, on purpose: `domain/README.md`.
 */
@JvmInline
public value class UserId(public val value: Uuid)
