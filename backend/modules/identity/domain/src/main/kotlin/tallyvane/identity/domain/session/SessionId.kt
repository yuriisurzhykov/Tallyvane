package tallyvane.identity.domain.session

import kotlin.uuid.Uuid

/**
 * One session's identity as `identity`'s own domain model names it — the primary key behind the
 * eventual `identity.sessions` row.
 *
 * Deliberately a different type from `identity:contract`'s `SessionId`, for the same reason
 * `identity.domain.user.UserId`'s own KDoc gives: `domain` cannot see `contract`, so the published
 * boundary type and this module's internal one are independent by construction, not by oversight.
 */
@JvmInline
public value class SessionId(public val value: Uuid)
