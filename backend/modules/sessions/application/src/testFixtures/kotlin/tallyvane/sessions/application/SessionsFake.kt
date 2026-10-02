package tallyvane.sessions.application

import tallyvane.platform.kernel.Digest
import tallyvane.sessions.application.port.Sessions
import tallyvane.sessions.domain.Factor
import tallyvane.sessions.domain.Session
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * [Sessions] in a map, for tests of the code that uses the port (ADR-044). [SessionsFakeSpec] holds it
 * to the suite the adapter over Postgres passes.
 */
class SessionsFake : Sessions {
    private val kept = mutableMapOf<Digest, Session>()

    override fun find(key: Digest): Session? = kept[key]

    override fun add(key: Digest, session: Session) {
        check(key !in kept) { "A session is already kept under this key." }
        kept[key] = session
    }

    override fun forget(key: Digest) {
        kept.remove(key)
    }

    override fun saw(key: Digest, at: Instant) {
        kept[key]?.let { session ->
            if (at - lastUseOf(session) >= Session.USE_GRAIN) {
                kept[key] = session.seenAt(at)
            }
        }
    }

    /**
     * When each kept session was last used, for a test that wants to see what was noted.
     */
    fun lastUses(): List<Instant> = kept.values.map(::lastUseOf)

    /**
     * How many sessions are kept, for a test that wants to say none was made.
     */
    fun count(): Int = kept.size

    override fun toString(): String = "SessionsFake(kept=${kept.size})"
}

/**
 * When [session] was last used, as it tells.
 */
fun lastUseOf(session: Session): Instant {
    val told = mutableListOf<Instant>()
    session.writeTo(
        object : Session.Record {
            override fun session(account: Uuid, authenticatedAt: Instant, lastActiveAt: Instant) {
                told += lastActiveAt
            }

            override fun proved(factor: Factor) = Unit
        },
    )
    return told.single()
}
