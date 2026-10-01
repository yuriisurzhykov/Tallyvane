package tallyvane.authentication.application

import tallyvane.authentication.domain.FactorKind
import tallyvane.authentication.domain.PolicyVersion
import tallyvane.authentication.domain.Purpose
import tallyvane.authentication.domain.SignInPolicy
import tallyvane.authentication.domain.Step
import kotlin.time.Duration

/**
 * Asks a policy or a version which purpose it is for, the only way there is: by listening to it
 * [PolicyVersion.writeTo] / `SignInPolicy.writeTo`, since neither gives out a field.
 */
class PurposeOf private constructor(private val listener: Listener) {
    constructor(version: PolicyVersion) : this(Listener().also { version.writeTo(it) })

    constructor(policy: SignInPolicy) : this(Listener().also { policy.writeTo(it) })

    fun purpose(): Purpose = listener.purpose()

    private class Listener : PolicyVersion.Record {
        private val heard = mutableListOf<Purpose>()

        override fun number(number: Int) = Unit

        override fun purpose(purpose: Purpose) {
            heard += purpose
        }

        override fun step(accepts: Set<FactorKind>, necessity: Step.Necessity) = Unit

        override fun limits(attemptLifetime: Duration, maxFailures: Int, firstDelay: Duration) = Unit

        fun purpose(): Purpose = heard.single()
    }
}
