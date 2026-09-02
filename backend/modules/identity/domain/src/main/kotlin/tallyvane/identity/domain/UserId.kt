package tallyvane.identity.domain

import kotlin.uuid.Uuid

/**
 * A user's identity as `identity`'s own domain model names it — the primary key behind the
 * eventual `identity.users` row.
 *
 * Deliberately a different type from `identity:contract`'s `UserId`, even though both wrap the
 * same kind of value: `domain` may depend on nothing but `platform:kernel` (`modules.yaml`), so it
 * cannot see the published one even inside this module. Translating between the two is
 * `application`'s job — see `identity/README.md`.
 */
@JvmInline
public value class UserId(public val value: Uuid)
