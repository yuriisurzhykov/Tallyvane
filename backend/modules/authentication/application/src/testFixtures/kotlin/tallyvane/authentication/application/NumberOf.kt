package tallyvane.authentication.application

import tallyvane.authentication.domain.FactorKind
import tallyvane.authentication.domain.PolicyVersion
import tallyvane.authentication.domain.Purpose
import tallyvane.authentication.domain.Step
import kotlin.time.Duration

/**
 * Asks a version which number it has, the only way there is: by listening to it [PolicyVersion.writeTo].
 */
class NumberOf(version: PolicyVersion) {
    private val heard = mutableListOf<Int>().also { numbers ->
        version.writeTo(
            object : PolicyVersion.Record {
                override fun number(number: Int) {
                    numbers += number
                }

                override fun purpose(purpose: Purpose) = Unit

                override fun step(accepts: Set<FactorKind>, necessity: Step.Necessity) = Unit

                override fun limits(attemptLifetime: Duration, maxFailures: Int, firstDelay: Duration) = Unit
            },
        )
    }

    fun number(): Int = heard.single()
}
