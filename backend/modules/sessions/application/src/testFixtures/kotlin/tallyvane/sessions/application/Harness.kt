package tallyvane.sessions.application

import tallyvane.authentication.contract.Proof
import tallyvane.identity.contract.AccountId
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.Digests
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.SecretGeneratorFake
import tallyvane.platform.kernel.TransactionRunnerFake
import tallyvane.sessions.domain.Lifetimes
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Everything the session use cases need, as fakes, with a browser's lifetimes in force.
 *
 * A browser's cookie is the [Secret] a use case handed it; [SecretGeneratorFake] numbers them
 * `secret-1`, `secret-2`, ... in the order they are drawn.
 */
class Harness {
    val sessions = SessionsFake()
    val signIns = SignInsStub()
    val clock = TickingClock(Instant.parse("2026-10-02T09:00:00Z"))
    val lifetimes = Lifetimes.forBrowser()
    private val transactions = TransactionRunnerFake()
    private val keys = SessionKeys(
        SecretGeneratorFake(),
        Digests.Hmac(Secret("a-pepper-only-the-tests-use-0123456789"), 1),
    )

    val open: OpenSessionUseCase = OpenSessionUseCase.OpenSession(
        signIns,
        sessions,
        transactions,
        clock,
        keys,
        lifetimes,
    )

    val authenticate: AuthenticateUseCase =
        AuthenticateUseCase.Authenticate(sessions, transactions, clock, keys, lifetimes)

    val signOut: SignOutUseCase = SignOutUseCase.SignOut(sessions, transactions, keys)

    /**
     * A person whose sign-in is complete, as the browser's `__Host-attempt` cookie.
     */
    fun finishedSigningIn(
        account: AccountId = ACCOUNT,
        proofs: Set<Proof> = setOf(Proof.Google),
        attempt: Secret = Secret("attempt-1"),
    ): Secret {
        signIns.complete(attempt, account, proofs, clock.now())
        return attempt
    }

    /**
     * A person who signed in: the secret of the session they were given.
     */
    suspend fun signedIn(account: AccountId = ACCOUNT): Secret = secretOf(open.open(finishedSigningIn(account)))

    /**
     * The secret an issued session gave the browser.
     */
    fun secretOf(opened: Opened): Secret {
        val told = mutableListOf<Secret>()
        (opened as Opened.Issued).writeTo { secret, _ -> told += secret }
        return told.single()
    }

    /**
     * Who a browser holding [session] is, as one line.
     */
    suspend fun who(session: Secret?): String = authenticate.resolve(session).reportTo(
        object : Resolution.Report<String> {
            override fun signedIn(account: AccountId): String = "signed in ${account.value}"

            override fun lapsed(): String = "lapsed"

            override fun anonymous(): String = "anonymous"
        },
    )

    /**
     * A clock the specs move.
     */
    class TickingClock(private var instant: Instant) : Clock {
        override fun now(): Instant = instant

        fun advance(by: Duration) {
            instant += by
        }
    }

    companion object {
        val ACCOUNT = AccountId(Uuid.parse("0199a000-0000-7000-8000-0000000000aa"))
    }
}
