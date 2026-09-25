package tallyvane.identity.application.password

import tallyvane.identity.application.google.GoogleIdentity
import tallyvane.identity.application.port.CredentialRepository
import tallyvane.identity.application.port.GoogleOAuthGateway
import tallyvane.identity.application.port.PasswordHasher
import tallyvane.identity.application.port.SessionStore
import tallyvane.identity.application.port.UserRepository
import tallyvane.identity.domain.session.SessionId
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict

public interface ReauthenticateUseCase : UseCase {
    public suspend fun password(userId: UserId, sessionId: SessionId, password: Secret): Outcome

    public suspend fun google(
        userId: UserId,
        sessionId: SessionId,
        code: String,
        codeVerifier: String,
        redirectUri: String,
    ): Outcome

    public enum class Outcome { REAUTHENTICATED, INVALID_CREDENTIAL, PROVIDER_UNAVAILABLE }

    public class Reauthenticate(
        private val users: UserRepository,
        private val credentials: CredentialRepository,
        private val passwords: PasswordHasher,
        private val google: GoogleOAuthGateway?,
        private val sessions: SessionStore,
        private val clock: Clock,
        private val transactions: TransactionRunner,
    ) : ReauthenticateUseCase {
        override suspend fun password(userId: UserId, sessionId: SessionId, password: Secret): Outcome =
            transactions.inTransaction {
                val user = users.findById(userId)
                val passwordRecord = credentials.findPasswordFor(userId)
                val valid = user != null &&
                    user.disabledAt == null &&
                    user.emailVerified &&
                    passwordRecord?.let { passwords.verify(password, it.hash) } == true
                if (!valid || !sessions.recordReauthentication(sessionId, userId, clock.now())) {
                    Verdict.Rollback(Outcome.INVALID_CREDENTIAL)
                } else {
                    Verdict.Commit(Outcome.REAUTHENTICATED)
                }
            }

        override suspend fun google(
            userId: UserId,
            sessionId: SessionId,
            code: String,
            codeVerifier: String,
            redirectUri: String,
        ): Outcome {
            val gateway = google ?: return Outcome.PROVIDER_UNAVAILABLE
            val identity = gateway.exchangeCode(code, codeVerifier, redirectUri) ?: return Outcome.INVALID_CREDENTIAL
            return recordGoogleProof(userId, sessionId, identity)
        }

        private suspend fun recordGoogleProof(
            userId: UserId,
            sessionId: SessionId,
            identity: GoogleIdentity,
        ): Outcome = transactions.inTransaction {
            val user = users.findById(userId)
            val googleRecord = credentials.findGoogleFor(userId)
            val valid = user != null &&
                user.disabledAt == null &&
                user.emailVerified &&
                googleRecord?.subject == identity.subject
            if (!valid || !sessions.recordReauthentication(sessionId, userId, clock.now())) {
                Verdict.Rollback(Outcome.INVALID_CREDENTIAL)
            } else {
                Verdict.Commit(Outcome.REAUTHENTICATED)
            }
        }
    }
}
