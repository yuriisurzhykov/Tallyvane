package tallyvane.identity.application.recovery

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import tallyvane.identity.application.SessionIssuer
import tallyvane.identity.application.SignInOutcome
import tallyvane.identity.application.email.BackupCodes
import tallyvane.identity.application.port.AuthenticationCodes
import tallyvane.identity.application.port.BackupCodeStore
import tallyvane.identity.application.port.CredentialRepositoryFake
import tallyvane.identity.application.port.EmailMfaEnrollmentStore
import tallyvane.identity.application.port.PasswordHasherFake
import tallyvane.identity.application.port.PendingAuthenticationStoreFake
import tallyvane.identity.application.port.RefreshTokenStoreFake
import tallyvane.identity.application.port.SessionStoreFake
import tallyvane.identity.application.port.TokenFactoryFake
import tallyvane.identity.application.port.TokenHasherFake
import tallyvane.identity.application.port.TotpEnrollmentStore
import tallyvane.identity.application.port.UserRepositoryFake
import tallyvane.identity.domain.credential.Credential
import tallyvane.identity.domain.credential.GoogleSubject
import tallyvane.identity.domain.outcome.AuthenticationOutcome
import tallyvane.identity.domain.secondfactor.PendingAuthentication
import tallyvane.identity.domain.secondfactor.PendingAuthenticationId
import tallyvane.identity.domain.secondfactor.SecondFactorKind
import tallyvane.identity.domain.session.DeviceLabel
import tallyvane.identity.domain.user.Email
import tallyvane.identity.domain.user.User
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.ClockFake
import tallyvane.platform.kernel.IdGeneratorFake
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunnerFake
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

class RecoverSpec :
    StringSpec({
        "valid recovery resets credentials and factors, revokes old access, and consumes the old code set" {
            val fixture = RecoveryFixture()
            fixture.install()
            val previousSession = fixture.issuer.issue(
                tallyvane.identity.contract.Principal.User(tallyvane.identity.contract.UserId(fixture.userId.value)),
                DeviceLabel("Old browser"),
            )
            val previousRefreshHash = fixture.tokenHasher.hash(previousSession.tokens.refresh)
            fixture.pending.save(fixture.pendingAuthentication())
            fixture.totp.enrolled += fixture.userId
            fixture.emailMfa.enrolled += fixture.userId
            fixture.backupCodeStore.replace(fixture.userId, listOf(fixture.codes.hash(fixture.context, VALID_CODE)))

            val result = fixture.subject.recover(fixture.request())

            val recovered = result.shouldBeInstanceOf<SignInOutcome.Issued>()
            recovered.session.session.userId shouldBe fixture.userId
            recovered.session.session.device shouldBe DeviceLabel("New recovery device")
            fixture.sessions.find(previousSession.session.id)?.revokedAt shouldBe NOW
            fixture.refreshTokens.stateOf(previousRefreshHash)?.used shouldBe true
            fixture.pending.saved shouldBe emptyMap()
            fixture.totp.enrolled shouldBe emptySet()
            fixture.emailMfa.enrolled shouldBe emptySet()
            fixture.credentials.findGoogleFor(fixture.userId).shouldBeNull()
            fixture.credentials.findPasswordFor(fixture.userId) shouldBe
                Credential.PasswordRecord(fixture.passwords.hash(NEW_PASSWORD))
            fixture.backupCodeStore.hasAny(fixture.userId) shouldBe false
        }

        "an invalid recovery code does not consume codes or change account security" {
            val fixture = RecoveryFixture()
            fixture.install()
            val previousSession = fixture.issuer.issue(
                tallyvane.identity.contract.Principal.User(tallyvane.identity.contract.UserId(fixture.userId.value)),
                DeviceLabel("Old browser"),
            )
            val previousPassword = fixture.passwords.hash(Secret("Original-account-password"))
            fixture.credentials.saveOrReplacePasswordFor(
                fixture.userId,
                Credential.PasswordRecord(previousPassword),
            )
            fixture.credentials.save(fixture.userId, Credential.GoogleRecord(GoogleSubject("google-subject")))
            fixture.pending.save(fixture.pendingAuthentication())
            fixture.totp.enrolled += fixture.userId
            fixture.emailMfa.enrolled += fixture.userId
            fixture.backupCodeStore.replace(fixture.userId, listOf(fixture.codes.hash(fixture.context, VALID_CODE)))

            val result = fixture.subject.recover(fixture.request(Secret("wrong-code")))

            result.shouldBeInstanceOf<SignInOutcome.NotIssued>().reason shouldBe AuthenticationOutcome.InvalidCredential
            fixture.sessions.find(previousSession.session.id)?.revokedAt.shouldBeNull()
            fixture.credentials.findPasswordFor(fixture.userId) shouldBe Credential.PasswordRecord(previousPassword)
            fixture.credentials.findGoogleFor(fixture.userId) shouldBe
                Credential.GoogleRecord(GoogleSubject("google-subject"))
            fixture.pending.saved.size shouldBe 1
            fixture.totp.enrolled shouldBe setOf(fixture.userId)
            fixture.emailMfa.enrolled shouldBe setOf(fixture.userId)
            fixture.backupCodeStore.hasAny(fixture.userId) shouldBe true
        }
    })

private class RecoveryFixture {
    val userId = UserId(Uuid.parse("00000000-0000-7000-8000-000000000001"))
    private val email = Email("person@example.test")
    val context = "backup:${userId.value}"
    val codes = RecoveryTestCodes()
    val backupCodeStore = RecoveryCodeStoreFake()
    val backupCodes = BackupCodes(backupCodeStore, codes)
    val users = UserRepositoryFake()
    val credentials = CredentialRepositoryFake()
    val passwords = PasswordHasherFake()
    val sessions = SessionStoreFake()
    val refreshTokens = RefreshTokenStoreFake()
    val pending = PendingAuthenticationStoreFake()
    val totp = RecoveryTotpStore()
    val emailMfa = RecoveryEmailMfaStore()
    val tokenHasher = TokenHasherFake()
    private val clock = ClockFake(NOW)
    val issuer = SessionIssuer.Default(
        sessions,
        refreshTokens,
        TokenFactoryFake(),
        tokenHasher,
        clock,
        IdGeneratorFake(),
        15.minutes,
        30.days,
    )
    val subject = RecoverAccountUseCase.Recover(
        users,
        credentials,
        passwords,
        backupCodes,
        backupCodeStore,
        sessions,
        refreshTokens,
        pending,
        totp,
        emailMfa,
        issuer,
        clock,
        TransactionRunnerFake(),
    )

    suspend fun install() {
        users.insert(User(userId, email, null, NOW, null, emailVerified = true))
        credentials.saveOrReplacePasswordFor(
            userId,
            Credential.PasswordRecord(passwords.hash(Secret("Original-account-password"))),
        )
        credentials.save(userId, Credential.GoogleRecord(GoogleSubject("google-subject")))
    }

    fun request(code: Secret = VALID_CODE) = RecoverAccountRequest(
        email,
        code,
        NEW_PASSWORD,
        DeviceLabel("New recovery device"),
    )

    fun pendingAuthentication() = PendingAuthentication(
        PendingAuthenticationId(Uuid.parse("00000000-0000-7000-8000-000000000002")),
        userId,
        DeviceLabel("Old pending browser"),
        SecondFactorKind.TOTP,
        setOf(SecondFactorKind.TOTP),
        NOW,
        NOW + 5.minutes,
    )
}

private class RecoveryCodeStoreFake : BackupCodeStore {
    private val hashes = mutableMapOf<UserId, MutableSet<Secret>>()

    override suspend fun replace(userId: UserId, hashes: List<Secret>) {
        this.hashes[userId] = hashes.toMutableSet()
    }

    override suspend fun consume(userId: UserId, hash: Secret): Boolean = hashes[userId]?.remove(hash) == true

    override suspend fun hasAny(userId: UserId): Boolean = !hashes[userId].isNullOrEmpty()
}

private class RecoveryTestCodes : AuthenticationCodes {
    override fun emailCode() = Secret("123456")
    override fun backupCode() = VALID_CODE
    override fun hash(context: String, code: Secret) = Secret("$context:${code.revealed()}")
}

private class RecoveryTotpStore : TotpEnrollmentStore {
    val enrolled = mutableSetOf<UserId>()
    override suspend fun save(enrollment: tallyvane.identity.domain.secondfactor.totp.TotpEnrollment) = Unit
    override suspend fun find(userId: UserId): tallyvane.identity.domain.secondfactor.totp.TotpEnrollment? = null
    override suspend fun delete(userId: UserId) {
        enrolled.remove(userId)
    }
}

private class RecoveryEmailMfaStore : EmailMfaEnrollmentStore {
    val enrolled = mutableSetOf<UserId>()
    override suspend fun enroll(userId: UserId) {
        enrolled += userId
    }
    override suspend fun unenroll(userId: UserId) {
        enrolled.remove(userId)
    }
    override suspend fun isEnrolled(userId: UserId): Boolean = userId in enrolled
}

private val NOW = Instant.parse("2026-01-01T00:00:00Z")
private val VALID_CODE = Secret("valid-recovery-code")
private val NEW_PASSWORD = Secret("A-new-password-long-enough")
