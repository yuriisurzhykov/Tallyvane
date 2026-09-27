package tallyvane.identity.application.secondfactor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import tallyvane.identity.application.port.LoginAttempts
import tallyvane.identity.application.port.LoginAttemptsFake
import tallyvane.identity.domain.outcome.SecondFactorOutcome
import tallyvane.identity.domain.secondfactor.PendingAuthenticationId
import tallyvane.identity.domain.secondfactor.SecondFactorKind
import tallyvane.platform.observability.log.LogRecord
import tallyvane.platform.observability.log.Logger
import tallyvane.platform.observability.log.Severity
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

class RateLimitedSpec :
    StringSpec({
        val pendingId = PendingAuthenticationId(Uuid.parse("00000000-0000-7000-8000-000000000001"))
        val request = VerifySecondFactorRequest(pendingId, SecondFactorKind.TOTP, "wrong")
        val notCompletedWrongCode = VerifySecondFactorOutcome.NotCompleted(SecondFactorOutcome.WrongCode)
        val notCompletedExpired = VerifySecondFactorOutcome.NotCompleted(SecondFactorOutcome.Expired)
        // Reuses production's own key-building function rather than repeating the literal here too
        // — see VerifySecondFactorUseCase.RateLimited's own KDoc for why the function exists.
        val key = VerifySecondFactorUseCase.RateLimited.rateLimitKey(pendingId)

        fun rateLimited(
            origin: FixedOutcomeOrigin,
            attempts: LoginAttempts,
            threshold: Int = 5,
            logger: Logger = Logger {},
        ) = VerifySecondFactorUseCase.RateLimited(origin.asUseCase, attempts, threshold, 15.minutes, logger)

        "the rate-limit key keeps this module's own prefix, so platform:cache's key-collision guard sees it" {
            key shouldBe "identity:verify-second-factor:${pendingId.value}"
        }

        "passes an attempt through under the threshold" {
            val origin = FixedOutcomeOrigin(notCompletedExpired)

            val result = rateLimited(origin, LoginAttemptsFake()).verify(request)

            result shouldBe notCompletedExpired
        }

        "refuses without calling the origin once the threshold is reached" {
            val origin = FixedOutcomeOrigin(notCompletedWrongCode)
            val attempts = LoginAttemptsFake()
            val limited = rateLimited(origin, attempts, threshold = 3)
            repeat(3) { limited.verify(request) }

            val result = limited.verify(request)

            result shouldBe VerifySecondFactorOutcome.NotCompleted(SecondFactorOutcome.RateLimited)
            origin.calls shouldBe 3
        }

        "records a failure only when the origin answers WrongCode, not on a different outcome" {
            val origin = FixedOutcomeOrigin(notCompletedExpired)
            val attempts = LoginAttemptsFake()

            rateLimited(origin, attempts).verify(request)

            attempts.failuresWithin(key, 15.minutes) shouldBe 0
        }

        "fails closed when the attempts store itself throws" {
            val origin = FixedOutcomeOrigin(notCompletedExpired)

            val result = rateLimited(origin, BrokenLoginAttempts()).verify(request)

            result shouldBe VerifySecondFactorOutcome.NotCompleted(SecondFactorOutcome.RateLimited)
            origin.calls shouldBe 0
        }

        "recording a failure that fails to persist does not turn WrongCode into an error" {
            val origin = FixedOutcomeOrigin(notCompletedWrongCode)
            val attempts = BrokenLoginAttempts(failFailuresWithin = false)

            val result = rateLimited(origin, attempts).verify(request)

            result shouldBe notCompletedWrongCode
        }

        "logs a warning naming the cause when failing closed on the read" {
            val origin = FixedOutcomeOrigin(notCompletedExpired)

            val events =
                capturedWarnings { logger ->
                    rateLimited(origin, BrokenLoginAttempts(), logger = logger).verify(request)
                }

            val event = events.single()
            event.severity shouldBe Severity.WARN
            event.cause.shouldNotBeNull().message shouldBe "store unavailable"
        }

        "logs a warning naming the cause when failing open on the write" {
            val origin = FixedOutcomeOrigin(notCompletedWrongCode)
            val attempts = BrokenLoginAttempts(failFailuresWithin = false)

            val events = capturedWarnings { logger -> rateLimited(origin, attempts, logger = logger).verify(request) }

            val event = events.single()
            event.severity shouldBe Severity.WARN
            event.cause.shouldNotBeNull().message shouldBe "store unavailable"
        }

        "does not log anything when the attempts store answers normally" {
            val origin = FixedOutcomeOrigin(notCompletedExpired)

            val events =
                capturedWarnings { logger -> rateLimited(origin, LoginAttemptsFake(), logger = logger).verify(request) }

            events shouldBe emptyList()
        }
    })

/**
 * Runs [block] with a [ListAppender] attached to [VerifySecondFactorUseCase.RateLimited]'s own
 * logger, and returns whatever it captured — this test's only way to observe the log line
 * `ENGINEERING-PRINCIPLES.md`'s "A recovered failure is logged where its meaning is known" asks
 * for, independent of `RateLimited`'s own return value.
 */
private suspend fun capturedWarnings(block: suspend (Logger) -> Unit): List<LogRecord> {
    val records = mutableListOf<LogRecord>()
    block(Logger(records::add))
    return records
}

/**
 * Counts calls to a [VerifySecondFactorUseCase] that always answers [outcome] — not itself a named
 * class implementing that interface, so `usecase-has-test` does not ask it for a `Spec` of its
 * own. [asUseCase] is an anonymous object, which Konsist's `classes()` never enumerates.
 */
private class FixedOutcomeOrigin(outcome: VerifySecondFactorOutcome) {
    var calls: Int = 0
        private set

    val asUseCase: VerifySecondFactorUseCase =
        object : VerifySecondFactorUseCase {
            override suspend fun verify(request: VerifySecondFactorRequest): VerifySecondFactorOutcome {
                calls += 1
                return outcome
            }
        }
}

/**
 * A [LoginAttempts] that always throws — proving [VerifySecondFactorUseCase.RateLimited] fails
 * closed on the read and fails open on the write, without needing a real store to break.
 */
private class BrokenLoginAttempts(private val failFailuresWithin: Boolean = true) : LoginAttempts {
    override suspend fun failuresWithin(key: String, window: Duration): Long =
        if (failFailuresWithin) error("store unavailable") else 0

    override suspend fun recordFailure(key: String, window: Duration) {
        error("store unavailable")
    }
}
