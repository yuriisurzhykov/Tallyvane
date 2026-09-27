package tallyvane.identity.application

import tallyvane.identity.application.port.AuthenticationPolicyStore
import tallyvane.identity.application.port.PendingAuthenticationStore
import tallyvane.identity.application.secondfactor.SecondFactorMethodRegistry
import tallyvane.identity.contract.Principal
import tallyvane.identity.domain.outcome.AuthenticationOutcome
import tallyvane.identity.domain.secondfactor.AuthenticationAction
import tallyvane.identity.domain.secondfactor.AuthenticationTokenKind
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
 * What every primary credential check does once it has a [UserId] that checked out — evaluate the
 * persisted policy against the presented primary proof and the user's enrolled factors, then
 * issue a session, create a [PendingAuthentication], or refuse the sign-in. Extracted once a second real caller
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
        private val policies: AuthenticationPolicyStore,
    ) : AuthenticationCompleter {
        override suspend fun complete(
            userId: UserId,
            device: DeviceLabel,
            primaryMethod: PrimaryMethod,
        ): SignInOutcome {
            val policy = policies.current() ?: return invalidCredential()
            val enrolled = registry.enrolledFor(userId)
            val primaryToken = primaryMethod.toAuthenticationToken()
            val availableTokens = enrolled.mapTo(mutableSetOf()) { it.toAuthenticationToken() }.apply {
                add(primaryToken)
            }
            val candidates = policy.schemesFor(AuthenticationAction.SIGN_IN).filter { scheme ->
                primaryToken in scheme.requiredTokens && scheme.isSatisfiedBy(availableTokens)
            }
            if (candidates.isEmpty()) return invalidCredential()

            val maximumRank = candidates.maxOf { it.assuranceRank }
            val recommendedScheme = candidates.filter { it.assuranceRank == maximumRank }.minBy { it.id }
            val recommendedFactor = recommendedScheme.requiredTokens
                .singleOrNull(AuthenticationTokenKind::isSecondFactor)
            val availableFactors = candidates.flatMapTo(linkedSetOf()) { scheme ->
                scheme.requiredTokens.filter(AuthenticationTokenKind::isSecondFactor).map { it.toSecondFactorKind() }
            }
            return if (recommendedFactor == null) {
                issueSession(userId, device)
            } else {
                SignInOutcome.NotIssued(
                    requireSecondFactor(
                        userId,
                        device,
                        recommendedFactor.toSecondFactorKind(),
                        availableFactors,
                        policy.version,
                    ),
                )
            }
        }

        private fun invalidCredential() = SignInOutcome.NotIssued(AuthenticationOutcome.InvalidCredential)

        private suspend fun issueSession(userId: UserId, device: DeviceLabel): SignInOutcome.Issued {
            val principal = Principal.User(ContractUserId(userId.value))
            return SignInOutcome.Issued(sessions.issue(principal, device))
        }

        private suspend fun requireSecondFactor(
            userId: UserId,
            device: DeviceLabel,
            recommended: SecondFactorKind,
            available: Set<SecondFactorKind>,
            policyVersion: Long,
        ): AuthenticationOutcome.RequiresSecondFactor {
            val now = clock.now()
            val pending = PendingAuthentication(
                id = PendingAuthenticationId(ids.next()),
                userId = userId,
                device = device,
                recommendedMethod = recommended,
                availableMethods = available,
                createdAt = now,
                expiresAt = now + pendingAuthenticationTtl,
                policyVersion = policyVersion,
            )
            pendingAuthentications.save(pending)
            return AuthenticationOutcome.RequiresSecondFactor(
                pending.id,
                pending.recommendedMethod,
                pending.availableMethods,
            )
        }

        private fun PrimaryMethod.toAuthenticationToken(): AuthenticationTokenKind = when (this) {
            PrimaryMethod.PASSWORD -> AuthenticationTokenKind.PASSWORD
            PrimaryMethod.GOOGLE -> AuthenticationTokenKind.GOOGLE
            PrimaryMethod.EMAIL_CODE -> AuthenticationTokenKind.EMAIL_SIGN_IN_CODE
        }

        private fun SecondFactorKind.toAuthenticationToken(): AuthenticationTokenKind = when (this) {
            SecondFactorKind.TOTP -> AuthenticationTokenKind.TOTP
            SecondFactorKind.EMAIL_OTP -> AuthenticationTokenKind.EMAIL_FACTOR_CODE
        }

        private fun AuthenticationTokenKind.toSecondFactorKind(): SecondFactorKind = when (this) {
            AuthenticationTokenKind.TOTP -> SecondFactorKind.TOTP
            AuthenticationTokenKind.EMAIL_FACTOR_CODE -> SecondFactorKind.EMAIL_OTP
            else -> error("$this is not a second-factor token")
        }
    }
}
