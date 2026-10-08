package tallyvane.sessions.application

import tallyvane.authentication.contract.Proof
import tallyvane.identity.contract.AccountId
import tallyvane.journal.contract.SecurityJournalRecorder
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.Digests
import tallyvane.platform.kernel.IdGeneratorFake
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.SecretGeneratorFake
import tallyvane.platform.kernel.Surface
import tallyvane.platform.kernel.TransactionRunnerFake
import tallyvane.sessions.domain.Freshness
import tallyvane.sessions.domain.SessionId
import tallyvane.sessions.domain.UserAgent
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Everything the session use cases need, as fakes, with a browser's lifetimes in force, a day idle and a
 * week at most, which a spec changes through [lifetimeVersions].
 *
 * A browser's cookie is the [Secret] a use case handed it; [SecretGeneratorFake] numbers them
 * `secret-1`, `secret-2`, ... in the order they are drawn.
 */
class Harness {
    val sessions = SessionsFake()
    val signIns = SignInsStub()
    val admins = AdminsStub()
    val clock = TickingClock(Instant.parse("2026-10-02T09:00:00Z"))
    val lifetimeVersions = LifetimeVersionsFake()

    /**
     * What the journal was told, which a spec reads to see that an act left its record.
     */
    val journal = SecurityJournalRecorder()
    private val transactions = TransactionRunnerFake()
    private val keys = SessionKeys(
        SecretGeneratorFake(),
        Digests.Hmac(Secret("a-pepper-only-the-tests-use-0123456789"), 1),
        IdGeneratorFake(),
    )
    private val recognition = Recognition(sessions, lifetimeVersions, clock, keys)

    val open: OpenSessionUseCase = OpenSessionUseCase.OpenSession(
        signIns,
        admins,
        sessions,
        journal,
        DeviceWords(),
        transactions,
        clock,
        keys,
    )

    val authenticate: AuthenticateUseCase = AuthenticateUseCase.Authenticate(recognition, transactions)

    val signOut: SignOutUseCase = SignOutUseCase.SignOut(sessions, transactions, keys)

    val listDevices: ListDevicesUseCase =
        ListDevicesUseCase.ListDevices(recognition, sessions, lifetimeVersions, transactions, clock)

    val revokeDevice: RevokeDeviceUseCase = RevokeDeviceUseCase.RevokeDevice(recognition, sessions, transactions)

    val renameDevice: RenameDeviceUseCase = RenameDeviceUseCase.RenameDevice(recognition, sessions, transactions)

    val signOutOthers: SignOutOthersUseCase = SignOutOthersUseCase.SignOutOthers(
        recognition,
        sessions,
        journal,
        transactions,
    )

    val confirmStepUp: ConfirmStepUpUseCase =
        ConfirmStepUpUseCase.ConfirmStepUp(recognition, signIns, sessions, transactions, keys)

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
     * An administrator whose sign-in is complete, as the browser's `__Host-attempt` cookie.
     */
    fun finishedSigningInAsAdmin(
        account: AccountId = ACCOUNT,
        proofs: Set<Proof> = setOf(Proof.Google, Proof.Totp),
        attempt: Secret = Secret("admin-attempt-1"),
    ): Secret {
        signIns.completeAdmin(attempt, account, proofs, clock.now())
        return attempt
    }

    /**
     * A person who signed in: the secret of the session they were given.
     */
    suspend fun signedIn(
        account: AccountId = ACCOUNT,
        agent: UserAgent = CHROME_ON_WINDOWS,
        attempt: Secret = Secret("attempt-${attempts++}"),
    ): Secret = secretOf(open.open(finishedSigningIn(account, attempt = attempt), agent, Surface.App))

    /**
     * An administrator who signed in on the administrators' site: the secret of the session they were given.
     */
    suspend fun signedInAsAdmin(
        account: AccountId = ACCOUNT,
        agent: UserAgent = CHROME_ON_WINDOWS,
        attempt: Secret = Secret("admin-attempt-${attempts++}"),
    ): Secret {
        admins.grant(account)
        return secretOf(open.open(finishedSigningInAsAdmin(account, attempt = attempt), agent, Surface.Admin))
    }

    private var attempts = 1

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
    suspend fun who(session: Secret?, surface: Surface = Surface.App): String =
        authenticate.resolve(session, surface).reportTo(
            object : Resolution.Report<String> {
                override fun signedIn(account: AccountId, session: SessionId, freshness: Freshness): String =
                    "signed in ${account.value}"

                override fun lapsed(): String = "lapsed"

                override fun anonymous(): String = "anonymous"
            },
        )

    /**
     * Whether a browser holding [session] proved who it is recently enough for a dangerous act, or null
     * when the session speaks for nobody.
     */
    suspend fun freshnessOf(session: Secret?, surface: Surface = Surface.App): Freshness? =
        authenticate.resolve(session, surface).reportTo(
            object : Resolution.Report<Freshness?> {
                override fun signedIn(account: AccountId, session: SessionId, freshness: Freshness): Freshness =
                    freshness

                override fun lapsed(): Freshness? = null

                override fun anonymous(): Freshness? = null
            },
        )

    /**
     * A person who confirmed in a window of their own: the cookie their finished confirmation is kept under.
     */
    fun finishedConfirming(
        account: AccountId = ACCOUNT,
        proofs: Set<Proof> = setOf(Proof.Google),
        attempt: Secret = Secret("step-up-1"),
    ): Secret {
        signIns.confirm(attempt, account, proofs, clock.now())
        return attempt
    }

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
        val OTHER_ACCOUNT = AccountId(Uuid.parse("0199a000-0000-7000-8000-0000000000bb"))

        val CHROME_ON_WINDOWS = UserAgent(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/130.0.0.0 Safari/537.36",
        )
    }
}
