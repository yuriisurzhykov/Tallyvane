package tallyvane.authentication.application.port

import tallyvane.authentication.domain.Enrollment

/**
 * How many accounts have set up each combination of second factors (ADR-098).
 *
 * What a preview of a policy needs, so that "how many people would this keep from signing in" is one query and
 * not one per account. The factors live in this module's own tables, so it knows the accounts that have set
 * something up and cannot know the ones that have not; the total is `identity`'s to say.
 *
 * Runs inside the caller's transaction and blocks on the database, as [PolicyVersions] does.
 */
public interface EnrollmentCensus {
    /**
     * The number of accounts for each combination of factors that at least one account has set up. A combination
     * nobody has is absent, and so is the empty one: an account with nothing set up is not counted here.
     */
    public fun enrolled(): Map<Enrollment, Int>
}
