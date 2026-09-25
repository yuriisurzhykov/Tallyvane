package tallyvane.identity.application.googleoauth

import tallyvane.identity.application.port.CredentialRepository
import tallyvane.identity.application.port.GoogleOAuthGateway
import tallyvane.identity.application.port.UserRepository
import tallyvane.identity.domain.user.UserId
import tallyvane.identity.domain.session.SessionId
import tallyvane.identity.domain.secondfactor.AuthenticationAction
import tallyvane.identity.application.secondfactor.AuthenticationActionProofRequirement
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict

public interface LinkGoogleAccountUseCase : UseCase {
    public suspend fun link(request: Request): Result

    public data class Request(
        public val userId: UserId,
        public val sessionId: SessionId,
        public val actionProof: String,
        public val code: String,
        public val codeVerifier: String,
        public val redirectUri: String,
    )

    public enum class Result {
        Linked,
        InvalidCredential,
        AlreadyLinked,
        SubjectAlreadyLinked,
    }

    public class Link(
        private val gateway: GoogleOAuthGateway,
        private val users: UserRepository,
        private val credentials: CredentialRepository,
        private val transactions: TransactionRunner,
        private val actionProofs: AuthenticationActionProofRequirement?,
    ) : LinkGoogleAccountUseCase {
        override suspend fun link(request: Request): Result {
            val identity = gateway.exchangeCode(request.code, request.codeVerifier, request.redirectUri)
                ?: return Result.InvalidCredential
            return transactions.inTransaction {
                val authorized = actionProofs?.consume(
                    request.actionProof, request.userId, request.sessionId,
                    AuthenticationAction.CHANGE_PRIMARY_CREDENTIAL,
                ) == true
                val user = users.findById(request.userId)
                when {
                    !authorized -> Verdict.Rollback(Result.InvalidCredential)
                    user == null || user.disabledAt != null || !user.emailVerified ->
                        Verdict.Rollback(Result.InvalidCredential)
                    credentials.findGoogleFor(request.userId) != null -> Verdict.Rollback(Result.AlreadyLinked)
                    credentials.findUserIdByGoogleSubject(identity.subject) != null ->
                        Verdict.Rollback(Result.SubjectAlreadyLinked)
                    else -> {
                        if (credentials.saveGoogleIfUnclaimed(request.userId, identity.subject)) {
                            Verdict.Commit(Result.Linked)
                        } else {
                            val owner = credentials.findUserIdByGoogleSubject(identity.subject)
                            Verdict.Rollback(if (owner == null) Result.AlreadyLinked else Result.SubjectAlreadyLinked)
                        }
                    }
                }
            }
        }
    }
}
