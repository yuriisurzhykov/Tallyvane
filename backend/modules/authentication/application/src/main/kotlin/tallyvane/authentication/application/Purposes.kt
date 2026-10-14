package tallyvane.authentication.application

import tallyvane.authentication.domain.FactorKind
import tallyvane.authentication.domain.PolicyVersion
import tallyvane.authentication.domain.Purpose
import tallyvane.authentication.domain.Step
import kotlin.time.Duration

/**
 * Hears the purpose a version tells, and nothing else, for the code that must name what a version is for without
 * reaching into it. One instance is used for one version.
 */
internal class Purposes : PolicyVersion.Record {
    private val heard = mutableListOf<Purpose>()

    fun of(version: PolicyVersion): Purpose {
        heard.clear()
        version.writeTo(this)
        return heard.single()
    }

    override fun number(number: Int) = Unit

    override fun purpose(purpose: Purpose) {
        heard += purpose
    }

    override fun step(accepts: Set<FactorKind>, necessity: Step.Necessity) = Unit

    override fun limits(attemptLifetime: Duration, maxFailures: Int, firstDelay: Duration) = Unit
}
