package tallyvane.identity.application

import tallyvane.identity.application.port.PendingAuthenticationStore
import tallyvane.identity.application.secondfactor.SecondFactorMethodRegistry
import tallyvane.identity.contract.Principal
import tallyvane.identity.domain.outcome.AuthenticationOutcome
import tallyvane.identity.domain.secondfactor.PendingAuthentication
import tallyvane.identity.domain.secondfactor.PendingAuthenticationId
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
    suspend fun complete(userId: UserId, device: DeviceLabel): SignInOutcome

    class Default(
        private val registry: SecondFactorMethodRegistry,
        private val pendingAuthentications: PendingAuthenticationStore,
        private val sessions: SessionIssuer,
        private val ids: IdGenerator,
        private val clock: Clock,
        private val pendingAuthenticationTtl: Duration,
    ) : AuthenticationCompleter {
        override suspend fun complete(userId: UserId, device: DeviceLabel): SignInOutcome {
            val enrolled = registry.enrolledFor(userId)
            return if (enrolled.isEmpty()) {
                val principal = Principal.User(ContractUserId(userId.value))
                SignInOutcome.Issued(sessions.issue(principal, device))
            } else {
                SignInOutcome.NotIssued(requireSecondFactor(userId, device, enrolled))
            }
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
