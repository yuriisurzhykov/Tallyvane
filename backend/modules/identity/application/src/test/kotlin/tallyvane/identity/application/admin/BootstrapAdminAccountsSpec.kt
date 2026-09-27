package tallyvane.identity.application.admin

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.identity.application.port.CredentialRepositoryFake
import tallyvane.identity.application.port.EmailMfaEnrollmentStore
import tallyvane.identity.application.port.TotpEnrollmentStoreFake
import tallyvane.identity.application.port.UserRepositoryFake
import tallyvane.identity.domain.credential.Credential
import tallyvane.identity.domain.credential.GoogleSubject
import tallyvane.identity.domain.credential.PasswordHash
import tallyvane.identity.domain.secondfactor.EncryptedSecret
import tallyvane.identity.domain.secondfactor.totp.TotpEnrollment
import tallyvane.identity.domain.user.Email
import tallyvane.identity.domain.user.User
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.ClockFake
import tallyvane.platform.kernel.IdGeneratorFake
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunnerFake
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

class CopySpec :
    StringSpec({
        "copies a verified allowlisted identity and its sign-in and factor records once" {
            val now = Instant.parse("2026-01-01T00:00:00Z")
            val sourceId = UserId(Uuid.parse("00000000-0000-7000-8000-000000000101"))
            val users = UserRepositoryFake()
            val admins = UserRepositoryFake()
            val userCredentials = CredentialRepositoryFake()
            val adminCredentials = CredentialRepositoryFake()
            val userTotp = TotpEnrollmentStoreFake()
            val adminTotp = TotpEnrollmentStoreFake()
            val userEmailMfa = MemoryEmailMfaEnrollmentStore()
            val adminEmailMfa = MemoryEmailMfaEnrollmentStore()
            val email = Email("admin@example.test")
            users.insert(User(sourceId, email, "Admin", now, null, emailVerified = true))
            userCredentials.save(sourceId, Credential.PasswordRecord(PasswordHash(Secret("password-hash"))))
            userCredentials.saveGoogleIfUnclaimed(sourceId, GoogleSubject("google-subject"))
            userTotp.save(TotpEnrollment(sourceId, EncryptedSecret("encrypted-seed"), true, now))
            userEmailMfa.enroll(sourceId)
            val copy = BootstrapAdminAccountsUseCase.Copy(
                users,
                userCredentials,
                userTotp,
                userEmailMfa,
                admins,
                adminCredentials,
                adminTotp,
                adminEmailMfa,
                setOf("admin@example.test"),
                IdGeneratorFake(),
                ClockFake(now + 1.seconds),
                TransactionRunnerFake(),
            )

            copy.provisionConfiguredAdmins() shouldBe 1
            copy.provisionConfiguredAdmins() shouldBe 0
            val admin = requireNotNull(admins.findByEmail(email))
            admin.id shouldBe UserId(Uuid.parse("00000000-0000-7000-8000-000000000001"))
            admin.createdAt shouldBe now + 1.seconds
            adminCredentials.findPasswordFor(admin.id) shouldBe
                Credential.PasswordRecord(PasswordHash(Secret("password-hash")))
            adminCredentials.findGoogleFor(admin.id) shouldBe Credential.GoogleRecord(GoogleSubject("google-subject"))
            adminTotp.find(admin.id) shouldBe TotpEnrollment(admin.id, EncryptedSecret("encrypted-seed"), true, now)
            adminEmailMfa.isEnrolled(admin.id) shouldBe true
        }

        "skips unverified, disabled, malformed, and non-allowlisted accounts" {
            val now = Instant.parse("2026-01-01T00:00:00Z")
            val users = UserRepositoryFake()
            val admins = UserRepositoryFake()
            users.insert(User(userId(1), Email("unverified@example.test"), null, now, null, emailVerified = false))
            users.insert(User(userId(2), Email("disabled@example.test"), null, now, now, emailVerified = true))
            users.insert(User(userId(3), Email("other@example.test"), null, now, null, emailVerified = true))
            val emptyCredentials = CredentialRepositoryFake()
            val emptyTotp = TotpEnrollmentStoreFake()
            val noEmailMfa = MemoryEmailMfaEnrollmentStore()
            val copy = BootstrapAdminAccountsUseCase.Copy(
                users,
                emptyCredentials,
                emptyTotp,
                noEmailMfa,
                admins,
                CredentialRepositoryFake(),
                TotpEnrollmentStoreFake(),
                MemoryEmailMfaEnrollmentStore(),
                setOf("unverified@example.test", "disabled@example.test", "invalid-email", " "),
                IdGeneratorFake(),
                ClockFake(now),
                TransactionRunnerFake(),
            )

            copy.provisionConfiguredAdmins() shouldBe 0
            admins.findByEmail(Email("unverified@example.test")) shouldBe null
            admins.findByEmail(Email("disabled@example.test")) shouldBe null
        }
    })

private class MemoryEmailMfaEnrollmentStore : EmailMfaEnrollmentStore {
    private val enrolled = mutableSetOf<UserId>()

    override suspend fun enroll(userId: UserId) {
        enrolled += userId
    }

    override suspend fun unenroll(userId: UserId) {
        enrolled -= userId
    }

    override suspend fun isEnrolled(userId: UserId): Boolean = userId in enrolled
}

private fun userId(number: Int) = UserId(Uuid.parse("00000000-0000-7000-8000-%012d".format(number)))
