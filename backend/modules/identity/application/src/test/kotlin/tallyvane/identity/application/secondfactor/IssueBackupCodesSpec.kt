package tallyvane.identity.application.secondfactor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.identity.application.email.BackupCodes
import tallyvane.identity.application.port.AuthenticationCodes
import tallyvane.identity.application.port.BackupCodeStore
import tallyvane.identity.application.port.CredentialRepositoryFake
import tallyvane.identity.application.port.PasswordHasherFake
import tallyvane.identity.application.port.UserRepositoryFake
import tallyvane.identity.domain.credential.Credential
import tallyvane.identity.domain.user.Email
import tallyvane.identity.domain.user.User
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunnerFake
import kotlin.time.Instant
import kotlin.uuid.Uuid

class IssueBackupCodesSpec :
    StringSpec({
        "reissuing codes requires a verified current password and invalid input leaves the set unchanged" {
            val userId = UserId(Uuid.parse("00000000-0000-7000-8000-000000000001"))
            val users = UserRepositoryFake()
            users.insert(User(userId, Email("owner@example.test"), null, Instant.parse("2026-01-01T00:00:00Z"), null))
            val hasher = PasswordHasherFake()
            val credentials = CredentialRepositoryFake()
            credentials.save(userId, Credential.PasswordRecord(hasher.hash(Secret("current-password"))))
            val store = CodesStore()
            val issue = IssueBackupCodesUseCase.Issue(
                users,
                credentials,
                hasher,
                BackupCodes(store, TestCodes(), TransactionRunnerFake()),
                TransactionRunnerFake(),
            )

            issue.issue(userId, Secret("wrong-password")) shouldBe null
            store.replaceCount shouldBe 0
            issue.issue(userId, Secret("current-password"))?.size shouldBe 10
            store.replaceCount shouldBe 1
        }
    })

private class CodesStore : BackupCodeStore {
    var replaceCount = 0
    private var values = emptySet<String>()
    override suspend fun replace(userId: UserId, hashes: List<Secret>) {
        replaceCount++
        values = hashes.map { it.revealed() }.toSet()
    }
    override suspend fun consume(userId: UserId, hash: Secret) = values.contains(hash.revealed())
    override suspend fun hasAny(userId: UserId) = values.isNotEmpty()
}

private class TestCodes : AuthenticationCodes {
    private var next = 0
    override fun emailCode() = Secret("123456")
    override fun backupCode() = Secret("code-${++next}")
    override fun hash(context: String, code: Secret) = Secret("$context:${code.revealed()}")
}
