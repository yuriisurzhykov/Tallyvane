package tallyvane.identity.application.secondfactor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.identity.application.email.BackupCodes
import tallyvane.identity.application.port.AuthenticationCodes
import tallyvane.identity.application.port.BackupCodeStore
import tallyvane.identity.application.port.VALID_ACTION_PROOF
import tallyvane.identity.application.port.acceptingActionProofRequirement
import tallyvane.identity.domain.session.SessionId
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunnerFake
import kotlin.time.Instant
import kotlin.uuid.Uuid

class IssueBackupCodesSpec :
    StringSpec({
        "reissuing codes requires an action proof and invalid proof leaves the set unchanged" {
            val userId = UserId(Uuid.parse("00000000-0000-7000-8000-000000000001"))
            val sessionId = SessionId(Uuid.parse("00000000-0000-7000-8000-000000000002"))
            val now = Instant.parse("2026-01-01T00:00:00Z")
            val store = CodesStore()
            val issue = IssueBackupCodesUseCase.Issue(
                BackupCodes(store, TestCodes()),
                TransactionRunnerFake(),
                acceptingActionProofRequirement(userId, sessionId, now),
            )

            issue.issue(userId, sessionId, null) shouldBe null
            store.replaceCount shouldBe 0
            issue.issue(userId, sessionId, VALID_ACTION_PROOF)?.size shouldBe 10
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
