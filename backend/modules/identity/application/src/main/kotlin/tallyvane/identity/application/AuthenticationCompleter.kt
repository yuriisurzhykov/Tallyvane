package tallyvane.identity.application

import tallyvane.identity.application.port.AuthenticationPolicyStore
import tallyvane.identity.application.port.PendingAuthenticationStore
import tallyvane.identity.application.secondfactor.SecondFactorMethodRegistry
import tallyvane.identity.contract.Principal
import tallyvane.identity.domain.outcome.AuthenticationOutcome
import tallyvane.identity.domain.secondfactor.AuthenticationAction
import tallyvane.identity.domain.secondfactor.AuthenticationPolicy
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
            val enrolled = registry.enrolledFor(userId)
            val primaryToken = primaryMethod.toAuthenticationToken()
            val availableTokens = enrolled.mapTo(mutableSetOf()) { it.toAuthenticationToken() }.apply {
                add(primaryToken)
            }
            val signInSchemes = policy.schemesFor(AuthenticationAction.SIGN_IN).filter { primaryToken in it.requiredTokens }
            val candidates = if (enrolled.isEmpty()) {
                policy.strongest(AuthenticationAction.SIGN_IN, availableTokens, primaryToken)
            } else {
                val factorSchemes = signInSchemes.filter { scheme ->
                    scheme.requiredTokens.any(AuthenticationTokenKind::isSecondFactor) &&
                        scheme.isSatisfiedBy(availableTokens)
                }
                val maximumRank = factorSchemes.maxOfOrNull { it.assuranceRank }
                if (maximumRank == null) emptyList() else factorSchemes.filter { it.assuranceRank == maximumRank }
            }
            if (candidates.isEmpty()) return invalidCredential()

            val requiredFactors = candidates.flatMapTo(mutableSetOf()) { scheme ->
                scheme.requiredTokens.filter(AuthenticationTokenKind::isSecondFactor).map { it.toSecondFactorKind() }
            }
            return if (requiredFactors.isEmpty()) {
                issueSession(userId, device)
            } else {
                SignInOutcome.NotIssued(requireSecondFactor(userId, device, requiredFactors, policy.version))
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
            enrolled: Set<SecondFactorKind>,
            policyVersion: Long,
        ): AuthenticationOutcome.RequiresSecondFactor {
            val now = clock.now()
            val pending = PendingAuthentication(
                id = PendingAuthenticationId(ids.next()),
                userId = userId,
                device = device,
                availableMethods = enrolled,
                createdAt = now,
                expiresAt = now + pendingAuthenticationTtl,
                policyVersion = policyVersion,
            )
            pendingAuthentications.save(pending)
            return AuthenticationOutcome.RequiresSecondFactor(pending.id, pending.availableMethods)
        }

        private fun PrimaryMethod.toAuthenticationToken(): AuthenticationTokenKind = when (this) {
            PrimaryMethod.PASSWORD -> AuthenticationTokenKind.PASSWORD
            PrimaryMethod.GOOGLE -> AuthenticationTokenKind.GOOGLE
            PrimaryMethod.EMAIL_CODE -> AuthenticationTokenKind.EMAIL_SIGN_IN_CODE
        }

        private fun SecondFactorKind.toAuthenticationToken(): AuthenticationTokenKind = when (this) {
            SecondFactorKind.TOTP -> AuthenticationTokenKind.TOTP
            SecondFactorKind.EMAIL_OTP -> AuthenticationTokenKind.EMAIL_FACTOR_CODE
            SecondFactorKind.BACKUP_CODE -> AuthenticationTokenKind.BACKUP_CODE
        }

        private fun AuthenticationTokenKind.toSecondFactorKind(): SecondFactorKind = when (this) {
            AuthenticationTokenKind.TOTP -> SecondFactorKind.TOTP
            AuthenticationTokenKind.EMAIL_FACTOR_CODE -> SecondFactorKind.EMAIL_OTP
            AuthenticationTokenKind.BACKUP_CODE -> SecondFactorKind.BACKUP_CODE
            else -> error("$this is not a second-factor token")
        }
    }
}
