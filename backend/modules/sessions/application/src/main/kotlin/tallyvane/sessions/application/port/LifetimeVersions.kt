package tallyvane.sessions.application.port

import tallyvane.sessions.domain.LifetimeRules

/**
 * Where the versions of the session lifetimes are kept, and which of them is in force (ADR-078, ADR-090).
 *
 * A version is never edited: a change is a new version, and a rollback is making an earlier one the one
 * in force again, so the history of how long sessions were allowed to live is never lost. This port only
 * reads; making and activating versions arrives with the admin screen (slice 7).
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
}
