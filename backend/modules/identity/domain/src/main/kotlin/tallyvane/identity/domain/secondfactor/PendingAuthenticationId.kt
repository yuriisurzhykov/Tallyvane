package tallyvane.identity.domain.secondfactor

import kotlin.uuid.Uuid

/**
 * One [PendingAuthentication]'s identity — the primary key behind the eventual
 * `identity.pending_authentications` row.
 */
@JvmInline
public value class PendingAuthenticationId(public val value: Uuid)
