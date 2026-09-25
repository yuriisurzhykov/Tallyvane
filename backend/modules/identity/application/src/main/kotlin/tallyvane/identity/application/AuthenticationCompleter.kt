package tallyvane.identity.application

import tallyvane.identity.application.port.AuthenticationPolicyStore
import tallyvane.identity.application.port.PendingAuthenticationStore
import tallyvane.identity.application.secondfactor.SecondFactorMethodRegistry
import tallyvane.identity.contract.Principal
import tallyvane.identity.domain.outcome.AuthenticationOutcome
import tallyvane.identity.domain.secondfactor.AuthenticationPolicy
import tallyvane.identity.domain.secondfactor.PendingAuthentication
import tallyvane.identity.domain.secondfactor.PendingAuthenticationId
import tallyvane.identity.domain.secondfactor.PrimaryMethod
import tallyvane.identity.domain.secondfactor.SecondFactorKind
import tallyvane.identity.domain.session.DeviceLabel
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.IdGenerator
import kotlin.time.Duration
import tallyvane.identity.contract.UserId as ContractUserId

/**
 * What every primary credential check does once it has a [UserId] that checked out — issue a
 * session directly, or, if [registry] finds at least one second factor enrolled, create a
 * [PendingAuthentication] instead. Extracted once a second real caller
 * ([tallyvane.identity.application.google.GoogleSignInCompleter]) needed the identical two lines
 * [tallyvane.identity.application.password.SignInWithPasswordUseCase.SignIn] already had — the
 * same "second real implementation, not predicted" rule `GoogleSignInCompleter` was extracted
 * under: `application/README.md`.
 */
internal interface AuthenticationCompleter {
    suspend fun complete(
        userId: UserId,
        device: DeviceLabel,
        primaryMethod: PrimaryMethod = PrimaryMethod.PASSWORD,
    ): SignInOutcome

    class Default(
        private val registry: SecondFactorMethodRegistry,
        private val pendingAuthentications: PendingAuthenticationStore,
        private val sessions: SessionIssuer,
        private val ids: IdGenerator,
        private val clock: Clock,
        private val pendingAuthenticationTtl: Duration,
        private val policies: AuthenticationPolicyStore? = null,
    ) : AuthenticationCompleter {
        override suspend fun complete(
            userId: UserId,
            device: DeviceLabel,
            primaryMethod: PrimaryMethod,
        ): SignInOutcome {
            val policy = policies?.current() ?: AuthenticationPolicy.defaults()
            val rule = policy.rule(primaryMethod)
            val enrolled = registry.enrolledFor(userId)
            return when {
                !rule.enabled -> invalidCredential()
                rule.requirement == tallyvane.identity.domain.secondfactor.MfaRequirement.DISABLED ->
                    issueSession(userId, device)
                else -> evaluateFactors(userId, device, primaryMethod, policy, rule, enrolled)
            }
        }

        private suspend fun evaluateFactors(
            userId: UserId,
            device: DeviceLabel,
            primaryMethod: PrimaryMethod,
            policy: AuthenticationPolicy,
            rule: tallyvane.identity.domain.secondfactor.AuthenticationRule,
            enrolled: Set<SecondFactorKind>,
        ): SignInOutcome {
            val permitted = rule.available(enrolled, policy.advancedAcknowledged)
            return if (permitted.isEmpty()) {
                enrollmentOrSession(userId, device, primaryMethod, rule, enrolled, policy)
            } else {
                SignInOutcome.NotIssued(requireSecondFactor(userId, device, permitted))
            }
        }

        private suspend fun enrollmentOrSession(
            userId: UserId,
            device: DeviceLabel,
            primaryMethod: PrimaryMethod,
            rule: tallyvane.identity.domain.secondfactor.AuthenticationRule,
            enrolled: Set<SecondFactorKind>,
            policy: AuthenticationPolicy,
        ): SignInOutcome {
            val required = rule.allowedMethods.intersect(registry.enrollmentKinds())
            return when {
                requiresEnrollment(rule.requirement, enrolled) && required.isEmpty() -> invalidCredential()
                requiresEnrollment(rule.requirement, enrolled) -> SignInOutcome.NotIssued(
                    requireEnrollment(userId, device, primaryMethod, required, policy.version),
                )
                else -> issueSession(userId, device)
            }
        }

        private fun requiresEnrollment(
            requirement: tallyvane.identity.domain.secondfactor.MfaRequirement,
            enrolled: Set<SecondFactorKind>,
        ): Boolean = requirement == tallyvane.identity.domain.secondfactor.MfaRequirement.REQUIRED ||
            requirement == tallyvane.identity.domain.secondfactor.MfaRequirement.IF_ENROLLED &&
            enrolled.isNotEmpty()

        private fun invalidCredential() = SignInOutcome.NotIssued(AuthenticationOutcome.InvalidCredential)

        private suspend fun issueSession(userId: UserId, device: DeviceLabel): SignInOutcome.Issued {
            val principal = Principal.User(ContractUserId(userId.value))
            return SignInOutcome.Issued(sessions.issue(principal, device))
        }

        private suspend fun requireEnrollment(
            userId: UserId,
            device: DeviceLabel,
            primaryMethod: PrimaryMethod,
            required: Set<SecondFactorKind>,
            policyVersion: Long,
        ): AuthenticationOutcome.RequiresEnrollment {
            val now = clock.now()
            val pending = PendingAuthentication(
                PendingAuthenticationId(ids.next()), userId, device, required, now,
                now + pendingAuthenticationTtl, requiresEnrollment = true,
                primaryMethod = primaryMethod, policyVersion = policyVersion,
            )
            pendingAuthentications.save(pending)
            return AuthenticationOutcome.RequiresEnrollment(pending.id, primaryMethod, required)
        }

        private suspend fun requireSecondFactor(
            userId: UserId,
            device: DeviceLabel,
            enrolled: Set<SecondFactorKind>,
        ): AuthenticationOutcome.RequiresSecondFactor {
            val now = clock.now()
            val pending = PendingAuthentication(
                id = PendingAuthenticationId(ids.next()),
                userId = userId,
                device = device,
                availableMethods = enrolled,
                createdAt = now,
                expiresAt = now + pendingAuthenticationTtl,
            )
            pendingAuthentications.save(pending)
            return AuthenticationOutcome.RequiresSecondFactor(pending.id, pending.availableMethods)
        }
    }
}
