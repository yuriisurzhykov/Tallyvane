package tallyvane.sessions.application.port

import tallyvane.platform.kernel.Digest
import tallyvane.sessions.domain.DeviceName
import tallyvane.sessions.domain.Session
import tallyvane.sessions.domain.SessionId
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Where sessions are kept, each under the [Digest] of the secret its browser holds (ADR-079).
 *
 * The database holds only the keyed hash, so whoever reads the table cannot present anyone's session,
 * and one lookup by that hash is all a request costs.
 *
 * All methods run inside the caller's transaction and block on the database (ADR-058).
 */
public interface Sessions {
    /**
     * The session kept under [key], or null if none is.
     *
     * @throws IllegalStateException if what is kept could not have been written by [Session.writeTo].
     */
    public fun find(key: Digest): Session?

    /**
     * Keeps a new [session] under [key].
     *
     * @throws IllegalStateException if one is already kept under [key]: two 256-bit secrets do not
     * collide, so a generator that repeats itself is what to look for.
     */
    public fun add(key: Digest, session: Session)

    /**
     * Forgets the session kept under [key], which ends it at once. Forgetting one that is not kept
     * changes nothing.
     */
    public fun forget(key: Digest)

    /**
     * Keeps what [session], the one kept under [key] and confirmed since, says about when its person last
     * proved who they are and by what (ADR-092): the later of the moments, and every factor that proved it.
     *
     * Only that moves. Nothing else [session] tells is read, so a session carrying another device or another
     * account cannot change what is kept under [key]. A moment earlier than the one kept changes nothing,
     * and confirming a session nobody kept changes nothing.
     */
    public fun confirm(key: Digest, session: Session)

    /**
     * Notes that the session under [key] was in use at [at].
     *
     * Last use is kept only to [Session.USE_GRAIN]: a note less than that after the one kept changes
     * nothing, so a person who clicks around does not make the database write on every request. A
     * note earlier than the one kept changes nothing either. Noting a session that is not kept changes
     * nothing.
     */
    public fun saw(key: Digest, at: Instant)

    /**
     * Every session kept for [account], in no particular order. Sessions that are over are among them:
     * what is over is for the caller to judge, under the lifetimes in force.
     */
    public fun ofAccount(account: Uuid): List<Session>

    /**
     * Forgets the session [id] of [account], which ends it at once. Returns whether there was one. A
     * session of another account is not found by it, so whoever asks cannot end anyone else's.
     */
    public fun revoke(account: Uuid, id: SessionId): Boolean

    /**
     * Forgets every session of [account] except [keep], in one statement, so a sign-in that lands
     * between a read and a delete is not lost.
     */
    public fun revokeOthers(account: Uuid, keep: SessionId)

    /**
     * Forgets every session of [account].
     */
    public fun revokeAll(account: Uuid)

    /**
     * Gives the session [id] of [account] the device name [name]. Returns whether there was one.
     */
    public fun rename(account: Uuid, id: SessionId, name: DeviceName): Boolean
}
