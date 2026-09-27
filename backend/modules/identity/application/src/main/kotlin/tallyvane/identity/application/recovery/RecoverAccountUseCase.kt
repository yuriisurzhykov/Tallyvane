package tallyvane.identity.application.recovery

import tallyvane.identity.application.SessionIssuer
import tallyvane.identity.application.SignInOutcome
import tallyvane.identity.application.email.BackupCodes
import tallyvane.identity.application.port.BackupCodeStore
import tallyvane.identity.application.port.CredentialRepository
import tallyvane.identity.application.port.EmailMfaEnrollmentStore
import tallyvane.identity.application.port.PasswordHasher
import tallyvane.identity.application.port.PendingAuthenticationStore
import tallyvane.identity.application.port.RefreshTokenStore
import tallyvane.identity.application.port.SessionStore
import tallyvane.identity.application.port.TotpEnrollmentStore
import tallyvane.identity.application.port.UserRepository
import tallyvane.identity.contract.Principal
import tallyvane.identity.domain.credential.Credential
import tallyvane.identity.domain.credential.PasswordPolicy
import tallyvane.identity.domain.outcome.AuthenticationOutcome
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict
import tallyvane.identity.contract.UserId as ContractUserId

public interface RecoverAccountUseCase : UseCase {
    public suspend fun recover(request: RecoverAccountRequest): SignInOutcome

    public class Recover internal constructor(
        private val users: UserRepository,
        private val credentials: CredentialRepository,
        private val passwords: PasswordHasher,
        private val recoveryCodes: BackupCodes,
        private val recoveryCodeStore: BackupCodeStore,
        private val sessions: SessionStore,
        private val refreshTokens: RefreshTokenStore,
        private val pending: PendingAuthenticationStore,
        private val totp: TotpEnrollmentStore,
        private val emailMfa: EmailMfaEnrollmentStore,
        private val issuer: SessionIssuer,
        private val clock: Clock,
        private val transactions: TransactionRunner,
        private val passwordPolicy: PasswordPolicy = PasswordPolicy.Default,
    ) : RecoverAccountUseCase {
        override suspend fun recover(request: RecoverAccountRequest): SignInOutcome {
            if (!passwordPolicy.accepts(request.newPassword.revealed())) return refused()
            val newCredential = Credential.PasswordRecord(passwords.hash(request.newPassword))
            return transactions.inTransaction {
                val user = users.findByEmail(request.email)?.takeIf {
                    it.disabledAt == null && it.emailVerified
                } ?: return@inTransaction Verdict.Rollback(refused())
                if (!recoveryCodes.consume(user.id, request.recoveryCode)) {
                    return@inTransaction Verdict.Rollback(refused())
                }

                credentials.saveOrReplacePasswordFor(user.id, newCredential)
                sessions.listFor(user.id).forEach { session -> refreshTokens.revokeAllFor(session.id) }
                sessions.revokeAllFor(user.id, clock.now())
                pending.deleteFor(user.id)
                totp.delete(user.id)
                emailMfa.unenroll(user.id)
                credentials.deleteGoogleFor(user.id)
                recoveryCodeStore.replace(user.id, emptyList())

                val principal = Principal.User(ContractUserId(user.id.value))
                Verdict.Commit(SignInOutcome.Issued(issuer.issue(principal, request.device)))
            }
        }

        private fun refused(): SignInOutcome = SignInOutcome.NotIssued(AuthenticationOutcome.InvalidCredential)
    }
}
