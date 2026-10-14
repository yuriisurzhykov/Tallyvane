package tallyvane.sessions.application.port

import tallyvane.sessions.application.Change
import tallyvane.sessions.application.KeptLifetimes
import tallyvane.sessions.domain.ClientLifetimes
import tallyvane.sessions.domain.ClientType
import tallyvane.sessions.domain.LifetimeRules
import tallyvane.sessions.domain.LifetimeVersion
import kotlin.time.Instant

/**
 * Where the versions of the session lifetimes are kept, and which of them is in force (ADR-078, ADR-090).
 *
 * A version is never edited: a change is a new version, and a rollback is making an earlier one the one in force
 * again, so the history of how long sessions were allowed to live is never lost. Both are only ever added to what is
 * kept.
 *
 * Runs inside the caller's transaction and blocks on the database, as [Sessions] does.
 */
public interface LifetimeVersions {
    /**
     * The lifetimes in force now, one pair for every kind of client.
     *
     * @throws IllegalStateException a kind of client has no version in force, or what is kept is outside
     * the bounds the code sets.
     */
    public fun active(): LifetimeRules

    /**
     * Every version kept for [client], newest first, with who made each and why, and which is in force.
     */
    public fun history(client: ClientType): KeptLifetimes

    /**
     * Keeps [lifetimes] as the next version of its kind of client, made by [change] at [at]. It is not in force
     * until [activate] makes it so.
     *
     * Two administrators adding at once each get a number of their own.
     */
    public fun add(lifetimes: ClientLifetimes, change: Change, at: Instant): LifetimeVersion

    /**
     * Makes [version] the one in force for its kind of client, from [at], for the reason [change] gives. Activating
     * an earlier version is a rollback; activating the one already in force is kept as an activation too.
     *
     * @throws IllegalStateException if no version is kept that says what [version] says: one that was never kept,
     * or one built to look like a kept version. A version put in force is one that [add] or [history] handed out.
     */
    public fun activate(version: LifetimeVersion, change: Change, at: Instant)
}
