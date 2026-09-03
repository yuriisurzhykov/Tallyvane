package tallyvane.identity.application.password

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.slf4j.LoggerFactory
import tallyvane.identity.application.SignInOutcome
import tallyvane.identity.application.port.LoginAttempts
import tallyvane.identity.application.port.LoginAttemptsFake
import tallyvane.identity.domain.outcome.AuthenticationOutcome
import tallyvane.identity.domain.session.DeviceLabel
import tallyvane.identity.domain.user.Email
import tallyvane.platform.kernel.Secret
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import ch.qos.logback.classic.Logger as LogbackLogger

class RateLimitedSpec :
    StringSpec({
        val request = SignInWithPasswordRequest(Email("person@example.com"), Secret("whatever"), DeviceLabel("Chrome"))
        val notIssuedInvalid = SignInOutcome.NotIssued(AuthenticationOutcome.InvalidCredential)
        val notIssuedDisabled = SignInOutcome.NotIssued(AuthenticationOutcome.AccountDisabled)
        // Reuses production's own key-building function rather than repeating the literal here too
        // — see SignInWithPasswordUseCase.RateLimited's own KDoc for why the function exists.
        val key = SignInWithPasswordUseCase.RateLimited.rateLimitKey(request.email)

        fun rateLimited(origin: FixedOutcomeOrigin, attempts: LoginAttempts, threshold: Int = 5) =
            SignInWithPasswordUseCase.RateLimited(origin.asUseCase, attempts, threshold, 15.minutes)

        "the rate-limit key keeps this module's own prefix, so platform:cache's key-collision guard sees it" {
            key shouldBe "identity:sign-in-password:${request.email.value}"
        }

        "passes an attempt through under the threshold" {
            val origin = FixedOutcomeOrigin(notIssuedDisabled)

            val result = rateLimited(origin, LoginAttemptsFake()).signIn(request)

            result shouldBe notIssuedDisabled
        }

        "refuses without calling the origin once the threshold is reached" {
            val origin = FixedOutcomeOrigin(notIssuedInvalid)
            val attempts = LoginAttemptsFake()
            val limited = rateLimited(origin, attempts, threshold = 3)
            repeat(3) { limited.signIn(request) }

            val result = limited.signIn(request)

            result shouldBe SignInOutcome.NotIssued(AuthenticationOutcome.RateLimited)
            origin.calls shouldBe 3
        }

        "records a failure only when the origin answers InvalidCredential, not on success" {
            val origin = FixedOutcomeOrigin(notIssuedDisabled)
            val attempts = LoginAttemptsFake()

            rateLimited(origin, attempts).signIn(request)

            attempts.failuresWithin(key, 15.minutes) shouldBe 0
        }

        "does not record a failure for AccountDisabled either" {
            val origin = FixedOutcomeOrigin(notIssuedDisabled)
            val attempts = LoginAttemptsFake()

            rateLimited(origin, attempts).signIn(request)

            attempts.failuresWithin(key, 15.minutes) shouldBe 0
        }

        "fails closed when the attempts store itself throws" {
            val origin = FixedOutcomeOrigin(notIssuedDisabled)

            val result = rateLimited(origin, BrokenLoginAttempts()).signIn(request)

            result shouldBe SignInOutcome.NotIssued(AuthenticationOutcome.RateLimited)
            origin.calls shouldBe 0
        }

        "recording a failure that fails to persist does not turn InvalidCredential into an error" {
            val origin = FixedOutcomeOrigin(notIssuedInvalid)
            val attempts = BrokenLoginAttempts(failFailuresWithin = false)

            val result = rateLimited(origin, attempts).signIn(request)

            result shouldBe notIssuedInvalid
        }

        "logs a warning naming the cause when failing closed on the read" {
            val origin = FixedOutcomeOrigin(notIssuedDisabled)

            val events = capturedWarnings { rateLimited(origin, BrokenLoginAttempts()).signIn(request) }

            val event = events.single()
            event.level shouldBe Level.WARN
            event.throwableProxy.shouldNotBeNull()
        }

        "logs a warning naming the cause when failing open on the write" {
            val origin = FixedOutcomeOrigin(notIssuedInvalid)
            val attempts = BrokenLoginAttempts(failFailuresWithin = false)

            val events = capturedWarnings { rateLimited(origin, attempts).signIn(request) }

            val event = events.single()
            event.level shouldBe Level.WARN
            event.throwableProxy.shouldNotBeNull()
        }

        "does not log anything when the attempts store answers normally" {
            val origin = FixedOutcomeOrigin(notIssuedDisabled)

            val events = capturedWarnings { rateLimited(origin, LoginAttemptsFake()).signIn(request) }

            events shouldBe emptyList()
        }
    })

/**
 * Runs [block] with a [ListAppender] attached to [SignInWithPasswordUseCase.RateLimited]'s own
 * logger, and returns whatever it captured — this test's only way to observe the log line
 * `ENGINEERING-PRINCIPLES.md`'s "A recovered failure is logged where its meaning is known" asks
 * for, independent of `RateLimited`'s own return value.
 */
private suspend fun capturedWarnings(block: suspend () -> Unit): List<ILoggingEvent> {
    val appender = ListAppender<ILoggingEvent>()
    val logger = LoggerFactory.getLogger(SignInWithPasswordUseCase.RateLimited::class.java) as LogbackLogger
    appender.start()
    logger.addAppender(appender)
    try {
        block()
    } finally {
        logger.detachAppender(appender)
        appender.stop()
    }
    return appender.list
}

/**
 * Counts calls to a [SignInWithPasswordUseCase] that always answers [outcome] — not itself a
 * named class implementing that interface, so `usecase-has-test` does not ask it for a `Spec` of
 * its own. [asUseCase] is an anonymous object, which Konsist's `classes()` never enumerates.
 */
private class FixedOutcomeOrigin(outcome: SignInOutcome) {
    var calls: Int = 0
        private set

    val asUseCase: SignInWithPasswordUseCase =
        object : SignInWithPasswordUseCase {
            override suspend fun signIn(request: SignInWithPasswordRequest): SignInOutcome {
                calls += 1
                return outcome
            }
        }
}

/**
 * A [LoginAttempts] that always throws — proving [SignInWithPasswordUseCase.RateLimited] fails
 * closed on the read and fails open on the write, without needing a real store to break.
 */
private class BrokenLoginAttempts(private val failFailuresWithin: Boolean = true) : LoginAttempts {
    override suspend fun failuresWithin(key: String, window: Duration): Long =
        if (failFailuresWithin) error("store unavailable") else 0

    override suspend fun recordFailure(key: String, window: Duration) {
        error("store unavailable")
    }
}
