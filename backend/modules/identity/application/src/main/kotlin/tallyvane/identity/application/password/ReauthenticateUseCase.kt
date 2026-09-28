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
    public suspend fun reauthenticate(request: Request): Outcome

    public sealed interface Request {
        public val userId: UserId
        public val sessionId: SessionId

        public data class Password(override val userId: UserId, override val sessionId: SessionId, val value: Secret) :
            Request

        public data class Google(
            override val userId: UserId,
            override val sessionId: SessionId,
            val code: String,
            val codeVerifier: String,
            val redirectUri: String,
        ) : Request
    }

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
        override suspend fun reauthenticate(request: Request): Outcome = when (request) {
            is Request.Password -> password(request)
            is Request.Google -> google(request)
        }

        private suspend fun password(request: Request.Password): Outcome = transactions.inTransaction {
            val user = users.findById(request.userId)
            val passwordRecord = credentials.findPasswordFor(request.userId)
            val valid = user != null &&
                user.disabledAt == null &&
                user.emailVerified &&
                passwordRecord?.let { passwords.verify(request.value, it.hash) } == true
            if (!valid || !sessions.recordReauthentication(request.sessionId, request.userId, clock.now())) {
                Verdict.Rollback(Outcome.INVALID_CREDENTIAL)
            } else {
                Verdict.Commit(Outcome.REAUTHENTICATED)
            }
        }

        private suspend fun google(request: Request.Google): Outcome = google?.let { gateway ->
            gateway.exchangeCode(request.code, request.codeVerifier, request.redirectUri)
                ?.let { identity -> recordGoogleProof(request.userId, request.sessionId, identity) }
                ?: Outcome.INVALID_CREDENTIAL
        } ?: Outcome.PROVIDER_UNAVAILABLE

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
