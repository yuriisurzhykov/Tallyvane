package tallyvane.authentication.application

import tallyvane.authentication.application.port.EnrollmentCensus
import tallyvane.authentication.domain.Enrollment
import tallyvane.authentication.domain.FactorKind

/**
 * [EnrollmentCensus] counted from what a [SecondFactorsFake] holds, for tests of the code that uses the port
 * (ADR-044).
 *
 * [EnrollmentCensusFakeSpec] holds it to the same suite as the adapter over Postgres.
 */
class EnrollmentCensusFake(private val secondFactors: SecondFactorsFake) : EnrollmentCensus {
    override fun enrolled(): Map<Enrollment, Int> = secondFactors.accounts()
        .map { Enrollment.of(secondFactors.find(it), secondFactors.of(it)) }
        .filter { enrollment -> FactorKind.entries.any(enrollment::includes) }
        .groupingBy { it }
        .eachCount()

    override fun toString(): String = "EnrollmentCensusFake($secondFactors)"
}
