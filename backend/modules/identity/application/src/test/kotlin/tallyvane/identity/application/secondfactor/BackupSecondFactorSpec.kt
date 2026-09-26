package tallyvane.identity.application.secondfactor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.identity.application.email.BackupCodes
import tallyvane.identity.application.port.AuthenticationCodes
import tallyvane.identity.application.port.BackupCodeStore
import tallyvane.identity.application.port.SecondFactorMethod
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunnerFake
import kotlin.uuid.Uuid

class BackupSecondFactorSpec :
    StringSpec({
        "backup code is enrolled only while codes remain and each code can be used once" {
            val store = RecoveryStore()
            val codes = FixedCodes()
            val service = BackupCodes(store, codes)
            val method = SecondFactorMethod.Backup(service)
            val user = UserId(Uuid.parse("00000000-0000-7000-8000-000000000001"))

            method.isEnrolledFor(user) shouldBe false
            service.issue(user).size shouldBe 10
            method.isEnrolledFor(user) shouldBe true
            method.verify(user, SecondFactorProof("recovery-1")) shouldBe true
            method.verify(user, SecondFactorProof("recovery-1")) shouldBe false
            method.isEnrolledFor(user) shouldBe true
            EnrollSecondFactorUseCase.Enroll(
                SecondFactorMethodRegistry.Default(listOf(method)),
                TransactionRunnerFake(),
            )
                .enroll(
                    EnrollSecondFactorRequest(
                        user,
                        tallyvane.identity.domain.secondfactor.SecondFactorKind.BACKUP_CODE,
                    ),
                ) shouldBe
                null
        }
    })

private class RecoveryStore : BackupCodeStore {
    private val stored = mutableMapOf<UserId, MutableSet<String>>()
    override suspend fun replace(userId: UserId, hashes: List<Secret>) {
        stored[userId] = hashes.mapTo(mutableSetOf()) { it.revealed() }
    }
    override suspend fun consume(userId: UserId, hash: Secret): Boolean =
        stored[userId]?.remove(hash.revealed()) == true
    override suspend fun hasAny(userId: UserId): Boolean = stored[userId]?.isNotEmpty() == true
}

private class FixedCodes : AuthenticationCodes {
    private var sequence = 0
    override fun emailCode() = Secret("123456")
    override fun backupCode() = Secret("recovery-${++sequence}")
    override fun hash(context: String, code: Secret) = Secret("$context:${code.revealed()}")
}
