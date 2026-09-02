package tallyvane.identity.contract

import kotlin.uuid.Uuid

/**
 * A user's identity, stable for as long as the account exists — the value every other module
 * stores when it needs to say "this row belongs to that person", without ever seeing a row of
 * `identity`'s own tables.
 *
 * This is `contract`'s own value object, not the one `identity:domain`'s eventual `User` entity
 * carries: `domain` may depend on nothing but `platform:kernel` (`modules.yaml`), so it cannot see
 * this type even inside its own module, and a value object minted for the published boundary is
 * not automatically the right shape for the entity behind it. Translating between the two, if they
 * ever need to differ, is `application`'s job.
 */
@JvmInline
public value class UserId(public val value: Uuid)
