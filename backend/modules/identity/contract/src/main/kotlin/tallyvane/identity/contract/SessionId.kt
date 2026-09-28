package tallyvane.identity.contract

import kotlin.uuid.Uuid

/**
 * One session's identity — the value [PrincipalResolver] returns alongside a [Principal] so a
 * caller can act on this particular session (revoke it, show it in a "connected devices" list)
 * without ever reading `identity`'s own session table.
 *
 * A different type from `identity:domain`'s own `SessionId`, for the same reason [UserId]'s KDoc
 * gives: `contract/README.md`.
 */
@JvmInline
public value class SessionId(public val value: Uuid)
