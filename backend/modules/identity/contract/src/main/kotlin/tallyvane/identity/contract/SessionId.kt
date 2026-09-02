package tallyvane.identity.contract

import kotlin.uuid.Uuid

/**
 * One session's identity — the value [PrincipalResolver] returns alongside a [Principal] so a
 * caller can act on this particular session (revoke it, show it in a "connected devices" list)
 * without ever reading `identity`'s own session table.
 *
 * `contract`'s own value object, for the same reason [UserId]'s KDoc gives: `identity:domain`'s
 * eventual `Session` entity cannot see this type, since `domain` may depend on nothing but
 * `platform:kernel`.
 */
@JvmInline
public value class SessionId(public val value: Uuid)
