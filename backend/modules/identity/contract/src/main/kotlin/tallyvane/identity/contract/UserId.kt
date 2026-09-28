package tallyvane.identity.contract

import kotlin.uuid.Uuid

/**
 * A user's identity, stable for as long as the account exists — the value every other module
 * stores when it needs to say "this row belongs to that person", without ever seeing a row of
 * `identity`'s own tables.
 *
 * A different type from `identity:domain`'s own `UserId`, on purpose: `contract/README.md`.
 */
@JvmInline
public value class UserId(public val value: Uuid)
