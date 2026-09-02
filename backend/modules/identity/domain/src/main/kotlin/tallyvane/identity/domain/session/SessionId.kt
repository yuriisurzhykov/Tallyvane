package tallyvane.identity.domain.session

import kotlin.uuid.Uuid

/**
 * One session's identity as `identity`'s own domain model names it — the primary key behind the
 * eventual `identity.sessions` row.
 *
 * A different type from `identity:contract`'s `SessionId`, on purpose: `domain/README.md`.
 */
@JvmInline
public value class SessionId(public val value: Uuid)
