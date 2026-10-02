package tallyvane.authentication.domain

import kotlin.time.Instant

/**
 * Where a sign-in stands, as [SignInPolicy.progressOf] sees it.
 *
 * Closed on purpose: every screen of the sign-in flow is one of these cases. Their contents are
 * private, and the only way to read them is [reportTo]: the reader implements [Report], one method
 * per case, so forgetting a case does not compile and nobody can reach into a case for a field it
 * did not offer.
 *
 * Only [SignInPolicy] builds one: the constructors are `internal`, so no code outside this module
 * can produce a [Complete] that no policy granted.
 *
 * [Exhausted] and [Expired] carry nothing, but are classes rather than `object`s: the architecture
 * rules keep functions off objects, and [reportTo] is one. Every instance of either equals every
 * other, so they behave as the single value they are.
 */
public sealed interface Progress {
    /**
     * Tells [report] which case this is, with what that case carries.
     */
    public fun <T> reportTo(report: Report<T>): T

    /**
     * Everything the policy asks for is verified. Only an attempt in this state can be redeemed for
     * a session, and that redemption happens in `sessions`, not here (ADR-076).
     *
     * @param factors The kinds verified, which the session records as `amr` (RFC 8176).
     * @param authenticatedAt When the last factor was verified; freshness for dangerous actions
     * counts from here.
     * @param subject Whose account the identifying factor proved, as its provider names them.
     */
    @ConsistentCopyVisibility
    public data class Complete internal constructor(
        private val factors: Set<FactorKind>,
        private val authenticatedAt: Instant,
        private val subject: String,
    ) : Progress {
        override fun <T> reportTo(report: Report<T>): T = report.complete(factors, authenticatedAt, subject)

        override fun toString(): String = "Complete(factors=$factors, authenticatedAt=$authenticatedAt)"
    }

    /**
     * Everything reachable is verified, but the policy demands a factor the account has not set up.
     * The person is signed in only to set it up (ADR-078), instead of being locked out.
     *
     * @param factors The kinds verified, as in [Complete].
     * @param authenticatedAt When the last factor was verified, as in [Complete].
     * @param subject Whose account it is, as in [Complete].
     * @param toSetUp The kinds of the first step the account cannot reach yet; setting up any one
     * of them reaches it. A later unreachable step, if a policy ever has one, is reported at the
     * next sign-in.
     */
    @ConsistentCopyVisibility
    public data class Restricted internal constructor(
        private val factors: Set<FactorKind>,
        private val authenticatedAt: Instant,
        private val subject: String,
        private val toSetUp: Set<FactorKind>,
    ) : Progress {
        override fun <T> reportTo(report: Report<T>): T = report.restricted(factors, authenticatedAt, subject, toSetUp)

        override fun toString(): String =
            "Restricted(factors=$factors, authenticatedAt=$authenticatedAt, toSetUp=$toSetUp)"
    }

    /**
     * The next factor is wanted now, as any one of [accepted].
     */
    @ConsistentCopyVisibility
    public data class Awaiting internal constructor(private val accepted: Set<FactorKind>) : Progress {
        override fun <T> reportTo(report: Report<T>): T = report.awaiting(accepted)
    }

    /**
     * The next factor is wanted, but not before [until]: the delay after wrong answers (ADR-082).
     */
    @ConsistentCopyVisibility
    public data class Paused internal constructor(private val accepted: Set<FactorKind>, private val until: Instant) :
        Progress {
        override fun <T> reportTo(report: Report<T>): T = report.paused(accepted, until)
    }

    /**
     * Too many wrong answers. The attempt is over; the person starts a new sign-in.
     */
    public class Exhausted internal constructor() : Progress {
        override fun <T> reportTo(report: Report<T>): T = report.exhausted()

        override fun equals(other: Any?): Boolean = other is Exhausted

        override fun hashCode(): Int = Exhausted::class.hashCode()

        override fun toString(): String = "Exhausted"
    }

    /**
     * The attempt outlived its lifetime. The person starts a new sign-in.
     */
    public class Expired internal constructor() : Progress {
        override fun <T> reportTo(report: Report<T>): T = report.expired()

        override fun equals(other: Any?): Boolean = other is Expired

        override fun hashCode(): Int = Expired::class.hashCode()

        override fun toString(): String = "Expired"
    }

    /**
     * A reader of [Progress]: the sign-in screen, the code that redeems a complete attempt, a test.
     *
     * Each method receives exactly what its case means and nothing else, so a reader cannot ask a
     * paused attempt for its authentication time or an exhausted one for its factors.
     */
    public interface Report<out T> {
        public fun complete(factors: Set<FactorKind>, authenticatedAt: Instant, subject: String): T

        public fun restricted(
            factors: Set<FactorKind>,
            authenticatedAt: Instant,
            subject: String,
            toSetUp: Set<FactorKind>,
        ): T

        public fun awaiting(accepted: Set<FactorKind>): T

        public fun paused(accepted: Set<FactorKind>, until: Instant): T

        public fun exhausted(): T

        public fun expired(): T
    }
}
