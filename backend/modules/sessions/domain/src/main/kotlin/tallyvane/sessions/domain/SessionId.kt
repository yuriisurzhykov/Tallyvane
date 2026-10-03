package tallyvane.sessions.domain

import kotlin.uuid.Uuid

/**
 * The name one session is known by, to the person who holds it and to the list of their devices.
 *
 * Not the secret and not derived from it: knowing an id lets nobody act as the session, and the only
 * thing it is good for is pointing at one (ADR-090). Its [value] is public for the reason an
 * `AccountId`'s is: there is no rule to protect, and the one use is to store or compare it.
 */
@JvmInline
public value class SessionId(public val value: Uuid)
