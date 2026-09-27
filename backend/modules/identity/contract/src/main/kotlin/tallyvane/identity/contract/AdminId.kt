package tallyvane.identity.contract

import kotlin.uuid.Uuid

/** The opaque public identity of an administrator, independent from [UserId]. */
@JvmInline
public value class AdminId(public val value: Uuid)
