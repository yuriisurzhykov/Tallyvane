package tallyvane.authentication.application

import tallyvane.authentication.application.port.Attempts
import tallyvane.authentication.contract.SignIns
import tallyvane.authentication.domain.Purpose
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.Digests
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.SecretGeneratorFake
import tallyvane.platform.kernel.TransactionRunnerFake
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Everything the sign-in use cases need, as fakes, with the policies of ADR-078 in force.
 *
 * A browser's cookie is the [Secret] a use case handed it; [SecretGeneratorFake] numbers them
 * `secret-1`, `secret-2`, ... in the order they are drawn, which the specs read off the outcomes
 * rather than assume.
 */
class Harness {
    val store = SignInsFake()
    val google = GoogleFake()
    val accounts = AccountsFake()
    val clock = TickingClock(Instant.parse("2026-10-02T09:00:00Z"))
    private val transactions = TransactionRunnerFake()
    private val versions = PolicyVersionsFake().also { versions ->
        listOf(Purpose.Login, Purpose.Registration, Purpose.StepUp).forEach { purpose ->
            versions.activate(versions.add(CheckedPolicy(purpose).policy(), clock.now()), clock.now())
        }
    }
    private val policies = ActivePolicies(versions)
    private val keys = SignInKeys(
        SecretGeneratorFake(),
        Digests.Hmac(Secret("a-pepper-only-the-tests-use-0123456789"), 1),
    )

    val begin: BeginSignInUseCase = BeginSignInUseCase.BeginSignIn(trips(), google, clock, keys)

    val beginStepUp: BeginStepUpUseCase = BeginStepUpUseCase.BeginStepUp(trips(), google, clock, keys)

    val continueWith: ContinueWithGoogleUseCase =
        ContinueWithGoogleUseCase.ContinueWithGoogle(trips(), google, policies, clock, keys)

    val show: ShowRegistrationUseCase =
        ShowRegistrationUseCase.ShowRegistration(store, store, policies, transactions, clock, keys)

    val register: RegisterUseCase =
        RegisterUseCase.Register(store, store, policies, accounts, transactions, clock, keys)

    val redemptions: SignIns = Redemptions(store, policies, accounts, clock, keys)

    /**
     * Completed sign-ins as `sessions` takes them, over [attempts] in place of the store, for a case about
     * what a port does that the fake never does.
     */
    fun redemptionsOver(attempts: Attempts): SignIns = Redemptions(attempts, policies, accounts, clock, keys)

    private fun trips() = GoogleTrips(store, store, store, accounts, transactions)

    /**
     * Presses "Sign in with Google": the cookie the browser keeps, and the state Google was sent.
     */
    suspend fun pressSignIn(): Pressed {
        val told = mutableListOf<Pair<Secret, String>>()
        begin.begin().writeTo { attempt, address -> told += attempt to address }
        val (attempt, address) = told.single()
        return Pressed(attempt, address.substringAfter("state="))
    }

    /**
     * Starts confirming a dangerous act: the cookie the browser keeps, and the state Google was sent.
     */
    suspend fun pressStepUp(): Pressed {
        val told = mutableListOf<Pair<Secret, String>>()
        beginStepUp.begin().writeTo { attempt, address -> told += attempt to address }
        val (attempt, address) = told.single()
        return Pressed(attempt, address.substringAfter("state="))
    }

    /**
     * Comes back from Google with a code Google arranged for [pressed] to be worth [answer].
     */
    suspend fun returnWith(pressed: Pressed, answer: GoogleAnswer, cookie: Secret? = pressed.attempt): GoogleReturn =
        continueWith.continueWith(cookie, GoogleReply.Granted(google.arrange(pressed.state, answer), pressed.state))

    /**
     * What a return told, as one line.
     */
    fun line(outcome: GoogleReturn): String = outcome.reportTo(
        object : GoogleReturn.Report<String> {
            override fun verified(): String = "verified"

            override fun steppedUp(): String = "stepped up"

            override fun registering(attempt: Secret): String = "registering ${attempt.revealed()}"

            override fun turnedBack(reason: TurnBack): String = "turned back: $reason"
        },
    )

    /**
     * A sign-in or a confirmation pressed: the secret in the cookie and the `state` in the address.
     */
    class Pressed(val attempt: Secret, val state: String)

    /**
     * A clock the specs move.
     */
    class TickingClock(private var instant: Instant) : Clock {
        override fun now(): Instant = instant

        fun passes(time: Duration) {
            instant += time
        }
    }
}
