package tallyvane.authentication.application

import tallyvane.authentication.domain.FactorKind
import tallyvane.authentication.domain.PolicyVersion
import tallyvane.authentication.domain.Purpose
import tallyvane.authentication.domain.Step
import kotlin.time.Duration

/**
 * Hears the number a version tells first, and nothing else, for the code that must name a version without
 * reaching into it.
 */
internal class Numbered : PolicyVersion.Record {
    private val heard = mutableListOf<Int>()

    fun of(version: PolicyVersion): Int {
        heard.clear()
        version.writeTo(this)
        return heard.single()
    }

    override fun number(number: Int) {
        heard += number
    }

    override fun purpose(purpose: Purpose) = Unit

    override fun step(accepts: Set<FactorKind>, necessity: Step.Necessity) = Unit

    override fun limits(attemptLifetime: Duration, maxFailures: Int, firstDelay: Duration) = Unit
}
