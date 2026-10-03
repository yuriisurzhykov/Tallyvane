package tallyvane.authentication.application

import tallyvane.authentication.application.port.Attempts
import tallyvane.authentication.contract.SignIns
import tallyvane.authentication.domain.FactorKind
import tallyvane.authentication.domain.Progress
import tallyvane.authentication.domain.Purpose
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.Digests
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.SecretGeneratorFake
import tallyvane.platform.kernel.TransactionRunnerFake
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
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
    val secondFactors = SecondFactorsFake()
    private val accountFailures = AccountFailuresFake()
    private val policies = ActivePolicies(versions, AttemptOwners(accounts), Enrollments(secondFactors, secondFactors))
    private val keys = SignInKeys(
        SecretGeneratorFake(),
        Digests.Hmac(Secret("a-pepper-only-the-tests-use-0123456789"), 1),
    )

    private val departures = Departures(trips(), google, clock, keys)

    val begin: BeginSignInUseCase = BeginSignInUseCase.BeginSignIn(departures)

    val beginStepUp: BeginStepUpUseCase = BeginStepUpUseCase.BeginStepUp(departures)

    val continueWith: ContinueWithGoogleUseCase =
        ContinueWithGoogleUseCase.ContinueWithGoogle(trips(), google, policies, clock, keys)

    val show: ShowRegistrationUseCase =
        ShowRegistrationUseCase.ShowRegistration(store, store, policies, transactions, clock, keys)

    val register: RegisterUseCase =
        RegisterUseCase.Register(store, store, policies, accounts, transactions, clock, keys)

    val redemptions: SignIns = Redemptions(store, policies, accounts, clock, keys)

    private val digests = Digests.Hmac(Secret("a-pepper-only-the-tests-use-0123456789"), 1)
    private val words = RecoveryCodeWords()
    private val mint = RecoveryCodeMintFake()

    val showSignIn: ShowSignInUseCase = ShowSignInUseCase.ShowSignIn(store, policies, transactions, clock, keys)

    val verify: VerifySecondFactorUseCase = VerifySecondFactorUseCase.VerifySecondFactor(
        attempts = store,
        policies = policies,
        owners = AttemptOwners(accounts),
        totp = secondFactors,
        codes = secondFactors,
        failures = accountFailures,
        words = words,
        digests = digests,
        transactions = transactions,
        clock = clock,
        keys = keys,
    )

    val beginTotp: BeginTotpUseCase =
        BeginTotpUseCase.BeginTotp(secondFactors, SeedSourceFake(), "Tallyvane", transactions)

    val confirmTotp: ConfirmTotpUseCase =
        ConfirmTotpUseCase.ConfirmTotp(secondFactors, secondFactors, mint, words, digests, transactions, clock)

    val disableTotp: DisableTotpUseCase = DisableTotpUseCase.DisableTotp(secondFactors, transactions)

    val regenerateRecoveryCodes: RegenerateRecoveryCodesUseCase = RegenerateRecoveryCodesUseCase
        .RegenerateRecoveryCodes(secondFactors, secondFactors, mint, words, digests, transactions)

    val showSecondFactor: ShowSecondFactorUseCase =
        ShowSecondFactorUseCase.ShowSecondFactor(secondFactors, secondFactors, transactions)

    /**
     * Completed sign-ins as `sessions` takes them, over [attempts] in place of the store, for a case about
     * what a port does that the fake never does.
     */
    fun redemptionsOver(attempts: Attempts): SignIns = Redemptions(attempts, policies, accounts, clock, keys)

    private companion object {
        val STEP = 31.seconds
    }

    /**
     * Answering second steps over [attempts] in place of the store, for a case about what a port does that
     * the fake never does.
     */
    fun verifyOver(attempts: Attempts): VerifySecondFactorUseCase = VerifySecondFactorUseCase.VerifySecondFactor(
        attempts = attempts,
        policies = policies,
        owners = AttemptOwners(accounts),
        totp = secondFactors,
        codes = secondFactors,
        failures = accountFailures,
        words = words,
        digests = digests,
        transactions = transactions,
        clock = clock,
        keys = keys,
    )

    /**
     * How the transactions so far ended, which a spec reads to see that a refusal still committed.
     */
    fun endings(): List<TransactionRunnerFake.Ending> = transactions.endings

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
     * Turns TOTP on for the account of [subject], who must have one, as a person does: begins, copies the key
     * into an app, types its first code and writes the recovery codes down. The clock is moved past the step
     * of that first code, so the next code the app shows is one that can sign in.
     */
    suspend fun enableTotp(subject: String): TotpSetUp {
        val account = checkNotNull(accounts.withGoogle(subject)) { "$subject has no account." }
        val keys = mutableListOf<String>()
        (beginTotp.begin(account) as TotpBegun.Started).writeTo { key, _ -> keys += key.revealed() }
        val app = AuthenticatorApp(keys.single())
        val shown = mutableListOf<String>()
        (confirmTotp.confirm(account, app.codeAt(clock.now())) as TotpConfirmed.Confirmed)
            .writeTo { codes -> shown += codes.map { it.revealed() } }
        clock.passes(STEP)
        return TotpSetUp(app, shown)
    }

    /**
     * Presses "Sign in with Google" and comes back as [subject], whom Google vouches for: the cookie the
     * browser keeps once the first step is done.
     */
    suspend fun signedInWithGoogle(subject: String): Secret {
        val pressed = pressSignIn()
        returnWith(pressed, GoogleAnswer.Vouched(subject, GoogleProfile("Ann Example", "ann@example.com")))
        return pressed.attempt
    }

    /**
     * Where the sign-in under [attempt] stands, as one word and what goes with it.
     */
    suspend fun standing(attempt: Secret): String = (showSignIn.show(attempt) as SignInShown.Shown).reportTo(
        object : Progress.Report<String> {
            override fun complete(factors: Set<FactorKind>, authenticatedAt: Instant, subject: String): String =
                "complete $factors"

            override fun restricted(
                factors: Set<FactorKind>,
                authenticatedAt: Instant,
                subject: String,
                toSetUp: Set<FactorKind>,
            ): String = "restricted $toSetUp"

            override fun awaiting(accepted: Set<FactorKind>): String = "awaiting ${accepted.sortedBy { it.name }}"

            override fun paused(accepted: Set<FactorKind>, until: Instant): String = "paused until $until"

            override fun exhausted(): String = "exhausted"

            override fun expired(): String = "expired"
        },
    )

    /**
     * What a person with an authenticator app has to show for having turned TOTP on.
     */
    class TotpSetUp(val app: AuthenticatorApp, val recoveryCodes: List<String>)

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
