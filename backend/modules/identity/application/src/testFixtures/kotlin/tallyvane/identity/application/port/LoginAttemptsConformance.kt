package tallyvane.identity.application.port

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.minutes

/**
 * The behaviour every [LoginAttempts] must show, whatever actually counts. Written once and
 * inherited by each implementation's spec (ADR-046): the fake here, and
 * [tallyvane.identity.infrastructure.LoginAttemptsOverCounter] in `identity:infrastructure`.
 */
public abstract class LoginAttemptsConformance : StringSpec() {
    protected abstract fun fresh(): LoginAttempts

    init {
        "a key never recorded against has zero failures" {
            fresh().failuresWithin("identity:conformance:a@example.com", 15.minutes) shouldBe 0
        }

        "recording a failure raises the count by one" {
            val attempts = fresh()
            attempts.recordFailure("identity:conformance:a@example.com", 15.minutes)

            attempts.failuresWithin("identity:conformance:a@example.com", 15.minutes) shouldBe 1
        }

        "two keys are counted independently" {
            val attempts = fresh()
            attempts.recordFailure("identity:conformance:a@example.com", 15.minutes)
            attempts.recordFailure("identity:conformance:a@example.com", 15.minutes)

            attempts.failuresWithin("identity:conformance:b@example.com", 15.minutes) shouldBe 0
        }
    }
}
