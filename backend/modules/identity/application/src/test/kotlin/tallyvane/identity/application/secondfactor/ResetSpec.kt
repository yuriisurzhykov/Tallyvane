package tallyvane.identity.application.secondfactor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.identity.application.port.AuthenticationPolicyAuditStore
import tallyvane.identity.application.port.AuthenticationPolicyStore
import tallyvane.identity.application.port.BackupCodeStore
import tallyvane.identity.application.port.EmailMfaEnrollmentStore
import tallyvane.identity.application.port.PendingAuthenticationStoreFake
import tallyvane.identity.application.port.RefreshTokenStoreFake
import tallyvane.identity.application.port.SessionStoreFake
import tallyvane.identity.application.port.TotpEnrollmentStoreFake
import tallyvane.identity.application.port.UserRepositoryFake
import tallyvane.identity.domain.secondfactor.AuthenticationPolicy
import tallyvane.identity.domain.secondfactor.EncryptedSecret
import tallyvane.identity.domain.secondfactor.PendingAuthentication
import tallyvane.identity.domain.secondfactor.PendingAuthenticationId
import tallyvane.identity.domain.secondfactor.SecondFactorKind
import tallyvane.identity.domain.secondfactor.totp.TotpEnrollment
import tallyvane.identity.domain.session.DeviceLabel
import tallyvane.identity.domain.session.Session
import tallyvane.identity.domain.session.SessionId
import tallyvane.identity.domain.token.HashedToken
import tallyvane.identity.domain.token.TokenFamilyId
import tallyvane.identity.domain.user.Email
import tallyvane.identity.domain.user.User
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.ClockFake
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunnerFake
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.uuid.Uuid

class ResetSpec :
    StringSpec({
        "requires an explicit confirmation before it mutates account security" {
            val now = Instant.parse("2026-01-01T00:00:00Z")
            val actorId = UserId(Uuid.random())
            val targetId = UserId(Uuid.random())
            val users = UserRepositoryFake()
            users.insert(User(actorId, Email("admin@example.test"), null, now, null, emailVerified = true))
            users.insert(User(targetId, Email("person@example.test"), null, now, null, emailVerified = true))
            val pending = PendingAuthenticationStoreFake()
            val audit = ResetAudit()
            val useCase = ResetAccountMfaUseCase.Reset(
                users = users,
                sessions = SessionStoreFake(),
                refreshTokens = RefreshTokenStoreFake(),
                pending = pending,
                totp = TotpEnrollmentStoreFake(),
                emailMfa = ResetEmailMfa(),
                backupCodes = ResetBackupCodes(),
                policies = ResetPolicyStore(),
                audit = audit,
                adminEmails = setOf("admin@example.test"),
                clock = ClockFake(now),
                transactions = TransactionRunnerFake(),
            )

            useCase.reset(ResetAccountMfaUseCase.Request(actorId, Email("person@example.test"), false)) shouldBe
                ResetAccountMfaUseCase.Outcome.CONFIRMATION_REQUIRED
            audit.events shouldBe emptyList()
        }

        "resets enrolled methods and revokes sessions and sign-in challenges" {
            val now = Instant.parse("2026-01-01T00:00:00Z")
            val actorId = UserId(Uuid.random())
            val targetId = UserId(Uuid.random())
            val users = UserRepositoryFake()
            users.insert(User(actorId, Email("admin@example.test"), null, now, null, emailVerified = true))
            users.insert(User(targetId, Email("person@example.test"), null, now, null, emailVerified = true))
            val sessions = SessionStoreFake()
            val sessionId = SessionId(Uuid.random())
            val familyId = TokenFamilyId(Uuid.random())
            val hash = HashedToken(Secret("refresh-hash"), 1)
            sessions.save(Session(sessionId, targetId, DeviceLabel("Browser"), familyId, now, now, null))
            val refresh = RefreshTokenStoreFake()
            refresh.issueFirst(sessionId, familyId, hash, now + 30.days, now)
            val pending = PendingAuthenticationStoreFake()
            val pendingId = PendingAuthenticationId(Uuid.random())
            pending.save(
                PendingAuthentication(
                    pendingId,
                    targetId,
                    DeviceLabel("Browser"),
                    setOf(SecondFactorKind.TOTP),
                    now,
                    now + 5.days,
                ),
            )
            val totp = TotpEnrollmentStoreFake().also {
                it.save(TotpEnrollment(targetId, EncryptedSecret("encrypted"), true, now))
            }
            val emailMfa = ResetEmailMfa().also { it.enroll(targetId) }
            val backup = ResetBackupCodes()
            backup.replace(targetId, listOf(Secret("hash")))
            val audit = ResetAudit()
            val useCase = ResetAccountMfaUseCase.Reset(
                users, sessions, refresh, pending, totp, emailMfa, backup, ResetPolicyStore(), audit,
                setOf("admin@example.test"), ClockFake(now), TransactionRunnerFake(),
            )

            useCase.reset(ResetAccountMfaUseCase.Request(actorId, Email("person@example.test"), true)) shouldBe
                ResetAccountMfaUseCase.Outcome.RESET
            sessions.find(sessionId)?.revokedAt shouldBe now
            refresh.stateOf(hash)?.used shouldBe true
            pending.find(pendingId) shouldBe null
            totp.find(targetId) shouldBe null
            emailMfa.isEnrolled(targetId) shouldBe false
            backup.hasAny(targetId) shouldBe false
            audit.events shouldBe listOf("MFA_RESET:${targetId.value}")
        }
    })

private class ResetEmailMfa : EmailMfaEnrollmentStore {
    private val enrolled = mutableSetOf<UserId>()
    override suspend fun enroll(userId: UserId) {
        enrolled += userId
    }
    override suspend fun unenroll(userId: UserId) {
        enrolled -= userId
    }
    override suspend fun isEnrolled(userId: UserId) = userId in enrolled
}

private class ResetBackupCodes : BackupCodeStore {
    private val hashes = mutableMapOf<UserId, List<Secret>>()
    override suspend fun replace(userId: UserId, hashes: List<Secret>) {
        this.hashes[userId] = hashes
    }
    override suspend fun consume(userId: UserId, hash: Secret) = false
    override suspend fun hasAny(userId: UserId) = hashes[userId].orEmpty().isNotEmpty()
}

private class ResetPolicyStore : AuthenticationPolicyStore {
    override suspend fun current() = AuthenticationPolicy.defaults()
    override suspend fun replace(expectedVersion: Long, policy: AuthenticationPolicy) = true
}

private class ResetAudit : AuthenticationPolicyAuditStore {
    val events = mutableListOf<String>()
    override suspend fun record(actor: UserId, action: String, policyVersion: Long, occurredAt: Instant) {
        events += action
    }
}
