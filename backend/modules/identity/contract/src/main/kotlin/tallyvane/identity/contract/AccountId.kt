package tallyvane.identity.contract

import kotlin.uuid.Uuid

/**
 * The name the system knows an account by, the same in every module that refers to it.
 *
 * Its [value] is public, unlike the fields of the objects that carry behaviour: an identifier has no
 * rule to protect and nothing to hide, and the one thing anyone does with it is store or compare it.
 */
@JvmInline
public value class AccountId(public val value: Uuid)
