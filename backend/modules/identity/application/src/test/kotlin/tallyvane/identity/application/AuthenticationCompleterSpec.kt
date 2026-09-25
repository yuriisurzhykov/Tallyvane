package tallyvane.identity.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import tallyvane.identity.application.port.AuthenticationPolicyStore
import tallyvane.identity.application.port.PendingAuthenticationStoreFake
import tallyvane.identity.application.port.RefreshTokenStoreFake
import tallyvane.identity.application.port.SecondFactorMethodFake
import tallyvane.identity.application.port.SessionStoreFake
import tallyvane.identity.application.port.TokenFactoryFake
import tallyvane.identity.application.port.TokenHasherFake
import tallyvane.identity.application.secondfactor.SecondFactorMethodRegistry
import tallyvane.identity.domain.outcome.AuthenticationOutcome
import tallyvane.identity.domain.secondfactor.AuthenticationPolicy
import tallyvane.identity.domain.secondfactor.AuthenticationRule
import tallyvane.identity.domain.secondfactor.MfaRequirement
import tallyvane.identity.domain.secondfactor.PrimaryMethod
import tallyvane.identity.domain.secondfactor.SecondFactorKind
import tallyvane.identity.domain.session.DeviceLabel
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.ClockFake
import tallyvane.platform.kernel.IdGeneratorFake
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

class AuthenticationCompleterSpec :
    StringSpec({
        val userId = UserId(Uuid.parse("00000000-0000-7000-8000-000000000001"))
        val device = DeviceLabel("Chrome on MacBook")
        val now = Instant.parse("2026-01-01T00:00:00Z")

        fun completer(
            methods: List<SecondFactorMethodFake> = emptyList(),
            pending: PendingAuthenticationStoreFake = PendingAuthenticationStoreFake(),
            policy: AuthenticationPolicy? = null,
        ) = AuthenticationCompleter.Default(
            registry = SecondFactorMethodRegistry.Default(methods),
            pendingAuthentications = pending,
            sessions = SessionIssuer.Default(
                sessions = SessionStoreFake(),
                refreshTokens = RefreshTokenStoreFake(),
                tokenFactory = TokenFactoryFake(),
                tokenHasher = TokenHasherFake(),
                clock = ClockFake(now),
                ids = IdGeneratorFake(),
                accessTokenTtl = 15.minutes,
                refreshTokenIdleTtl = 30.days,
            ),
            ids = IdGeneratorFake(),
            clock = ClockFake(now),
            pendingAuthenticationTtl = 5.minutes,
            policies = policy?.let { PolicyStore(it) },
        )

        "a user with no second factor enrolled is issued a session directly" {
            val result = completer().complete(userId, device)

            val issued = result.shouldBeInstanceOf<SignInOutcome.Issued>()
            issued.session.session.userId shouldBe userId
        }

        "a user with a second factor enrolled is not issued a session — a pending authentication is created instead" {
            val totp = SecondFactorMethodFake(SecondFactorKind.TOTP).also { it.enroll(userId) }
            val pending = PendingAuthenticationStoreFake()

            val result = completer(listOf(totp), pending).complete(userId, device)

            val outcome = result.shouldBeInstanceOf<SignInOutcome.NotIssued>()
            val reason = outcome.reason.shouldBeInstanceOf<AuthenticationOutcome.RequiresSecondFactor>()
            reason.availableMethods shouldBe setOf(SecondFactorKind.TOTP)
            pending.saved[reason.pendingId]?.userId shouldBe userId
        }

        "the pending authentication carries the same device the primary sign-in presented" {
            val totp = SecondFactorMethodFake(SecondFactorKind.TOTP).also { it.enroll(userId) }
            val pending = PendingAuthenticationStoreFake()

            val result = completer(listOf(totp), pending).complete(userId, device)

            val reason =
                (result as SignInOutcome.NotIssued).reason as AuthenticationOutcome.RequiresSecondFactor
            pending.saved[reason.pendingId]?.device shouldBe device
        }

        "a disabled primary method does not issue a session" {
            val policy = AuthenticationPolicy.defaults().withRule(
                AuthenticationRule(
                    PrimaryMethod.PASSWORD,
                    false,
                    MfaRequirement.IF_ENROLLED,
                    setOf(SecondFactorKind.TOTP),
                ),
            )

            val result = completer(policy = policy).complete(userId, device)

            (result as SignInOutcome.NotIssued).reason shouldBe AuthenticationOutcome.InvalidCredential
        }

        "required MFA creates a policy-versioned enrollment challenge without issuing a session" {
            val policy = AuthenticationPolicy.defaults(4).withRule(
                AuthenticationRule(PrimaryMethod.PASSWORD, true, MfaRequirement.REQUIRED, setOf(SecondFactorKind.TOTP)),
            )
            val pending = PendingAuthenticationStoreFake()

            val totp = SecondFactorMethodFake(SecondFactorKind.TOTP)
            val result = completer(listOf(totp), pending, policy).complete(userId, device)

            val reason = (result as SignInOutcome.NotIssued).reason
                .shouldBeInstanceOf<AuthenticationOutcome.RequiresEnrollment>()
            val saved = pending.saved[reason.pendingId]!!
            saved.requiresEnrollment shouldBe true
            saved.policyVersion shouldBe 4
            saved.availableMethods shouldBe setOf(SecondFactorKind.TOTP)
        }
    })

private class PolicyStore(private var value: AuthenticationPolicy) : AuthenticationPolicyStore {
    override suspend fun current(): AuthenticationPolicy = value
    override suspend fun replace(expectedVersion: Long, policy: AuthenticationPolicy): Boolean {
        if (value.version != expectedVersion) return false
        value = policy
        return true
    }
}

private fun AuthenticationPolicy.withRule(rule: AuthenticationRule) = AuthenticationPolicy(
    version,
    rules.values.filterNot { it.primary == rule.primary } + rule,
    advancedAcknowledged,
)
