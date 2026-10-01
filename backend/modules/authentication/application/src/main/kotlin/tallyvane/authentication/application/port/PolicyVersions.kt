package tallyvane.authentication.application.port

import tallyvane.authentication.domain.PolicyVersion
import tallyvane.authentication.domain.Purpose
import tallyvane.authentication.domain.SignInPolicy
import kotlin.time.Instant

/**
 * Where the versions of each purpose's policy are kept, and which of them is in force (ADR-078).
 *
 * A version is never edited: a change is a new version, and a rollback is making an earlier one the
 * one in force again. Both are only ever added to what is kept, so the history of what the system
 * demanded, and since when, is never lost.
 *
 * Every method runs inside the caller's transaction and blocks on the database, as [Attempts] does.
 *
 * Limits are kept to the millisecond, as the database keeps them; an administrator cannot mean a
 * finer pause than that.
 */
public interface PolicyVersions {
    /**
     * The version in force for [purpose], or null if none was ever made so.
     */
    public fun active(purpose: Purpose): PolicyVersion?

    /**
     * Keeps [policy] as the next version of its purpose, numbered after the latest one kept, at
     * [at]. It is not in force until [activate] makes it so.
     *
     * Two administrators adding at once each get a number of their own.
     */
    public fun add(policy: SignInPolicy, at: Instant): PolicyVersion

    /**
     * Makes [version] the one in force for its purpose, from [at]. Activating an earlier version is
     * a rollback; activating the one already in force is kept as an activation too.
     *
     * @throws IllegalStateException if no version is kept that says what [version] says: one that
     * was never kept, or one built to look like a kept version. A version put in force is one that
     * [add] or [active] handed out.
     */
    public fun activate(version: PolicyVersion, at: Instant)
}
