package tallyvane.authentication.application

import tallyvane.authentication.application.port.Attempts
import tallyvane.authentication.application.port.GoogleProfiles
import tallyvane.authentication.domain.FactorKind
import tallyvane.authentication.domain.Progress
import tallyvane.authentication.domain.Purpose
import tallyvane.platform.kernel.Digest
import kotlin.time.Instant

/**
 * Finds the registration a browser's secret belongs to, if it is still one a person may finish.
 *
 * Runs inside the caller's transaction, as the ports it reads do.
 */
internal class Registrations(
    private val attempts: Attempts,
    private val profiles: GoogleProfiles,
    private val policies: ActivePolicies,
) {
    /**
     * The registration kept under [key] whose policy is satisfied at [now], or null when there is
     * none: nothing under the key, an attempt of another purpose, one that expired, or one Google has
     * not identified.
     */
    fun waitingUnder(key: Digest, now: Instant): Pending? {
        val attempt = attempts.find(key)?.takeIf { it.isFor(Purpose.Registration) }
        val profile = profiles.of(key)
        return if (attempt == null || profile == null) {
            null
        } else {
            policies.progressOf(attempt, Purpose.Registration, now).reportTo(Identifying(profile))
        }
    }

    /**
     * Only a complete registration policy lets the form through; every other case is no form.
     */
    private class Identifying(private val profile: GoogleProfile) : Progress.Report<Pending?> {
        override fun complete(factors: Set<FactorKind>, authenticatedAt: Instant, subject: String): Pending =
            Pending(subject, profile)

        override fun restricted(
            factors: Set<FactorKind>,
            authenticatedAt: Instant,
            subject: String,
            toSetUp: Set<FactorKind>,
        ): Pending? = null

        override fun awaiting(accepted: Set<FactorKind>): Pending? = null

        override fun paused(accepted: Set<FactorKind>, until: Instant): Pending? = null

        override fun exhausted(): Pending? = null

        override fun expired(): Pending? = null
    }
}
