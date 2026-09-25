package tallyvane.identity.application.secondfactor

import tallyvane.identity.application.port.AuthenticationPolicyAuditStore
import tallyvane.identity.application.port.AuthenticationPolicyStore
import tallyvane.identity.application.port.UserRepositoryFake
import tallyvane.identity.domain.secondfactor.AuthenticationPolicy
import tallyvane.identity.domain.secondfactor.AuthenticationRule
import tallyvane.identity.domain.user.Email
import tallyvane.identity.domain.user.User
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.ClockFake
import tallyvane.platform.kernel.TransactionRunnerFake
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class PolicyFixture {
    val admin = UserId(Uuid.parse("00000000-0000-7000-8000-000000000001"))
    val outsider = UserId(Uuid.parse("00000000-0000-7000-8000-000000000002"))
    var currentPolicy = AuthenticationPolicy.defaults()
    val auditRecords = mutableListOf<String>()
    private val users = UserRepositoryFake()
    private val policyStore = object : AuthenticationPolicyStore {
        override suspend fun current() = currentPolicy
        override suspend fun replace(expectedVersion: Long, policy: AuthenticationPolicy): Boolean {
            if (currentPolicy.version != expectedVersion) return false
            currentPolicy = policy
            return true
        }
    }
    private val auditStore = object : AuthenticationPolicyAuditStore {
        override suspend fun record(actor: UserId, action: String, policyVersion: Long, occurredAt: Instant) {
            auditRecords += action
        }
    }
    private val administration = AuthenticationPolicyAdministration(
        users,
        policyStore,
        auditStore,
        setOf("admin@example.test"),
        ClockFake(Instant.parse("2026-01-01T00:00:00Z")),
        TransactionRunnerFake(),
    )
    val read = ReadAuthenticationPolicyUseCase.Read(administration)
    val update = UpdateAuthenticationPolicyUseCase.Update(administration)

    suspend fun addUser(id: UserId, email: String, verified: Boolean) {
        users.insert(
            User(id, Email(email), null, Instant.parse("2025-01-01T00:00:00Z"), null, emailVerified = verified),
        )
    }

    fun rulesRequiringPasswordMfa(): List<AuthenticationRule> = currentPolicy.rules.values.map { rule ->
        if (rule.primary.name ==
            "PASSWORD"
        ) {
            rule.copy(
                allowedMethods =
                rule.allowedMethods - tallyvane.identity.domain.secondfactor.SecondFactorKind.EMAIL_OTP,
            )
        } else {
            rule
        }
    }
}
