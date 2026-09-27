package tallyvane.identity.application.googleoauth

import tallyvane.identity.application.port.AuthenticationPolicyStore
import tallyvane.identity.application.port.CredentialRepository
import tallyvane.identity.application.port.SecondFactorMethod
import tallyvane.identity.application.port.UserRepository
import tallyvane.identity.application.secondfactor.AuthenticationActionProofRequirement
import tallyvane.identity.domain.secondfactor.AuthenticationAction
import tallyvane.identity.domain.secondfactor.AuthenticationPolicy
import tallyvane.identity.domain.secondfactor.AuthenticationTokenKind
import tallyvane.identity.domain.secondfactor.SecondFactorKind
import tallyvane.identity.domain.session.SessionId
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict

public interface UnlinkGoogleAccountUseCase : UseCase {
    public suspend fun unlink(userId: UserId, sessionId: SessionId, actionProof: String?): Result

    public enum class Result {
        Unlinked,
        InvalidCredential,
        LastSignInMethod,
        NotLinked,
    }

    public class Unlink(
        private val users: UserRepository,
        private val credentials: CredentialRepository,
        private val transactions: TransactionRunner,
        private val actionProofs: AuthenticationActionProofRequirement?,
        private val policies: AuthenticationPolicyStore,
        private val factors: List<SecondFactorMethod>,
        private val emailSignInAvailable: Boolean,
    ) : UnlinkGoogleAccountUseCase {
        override suspend fun unlink(userId: UserId, sessionId: SessionId, actionProof: String?): Result =
            transactions.inTransaction {
                val authorized = actionProofs?.consume(
                    actionProof,
                    userId,
                    sessionId,
                    AuthenticationAction.CHANGE_PRIMARY_CREDENTIAL,
                ) == true
                val user = users.findById(userId)
                val googleRecord = credentials.findGoogleFor(userId)
                val policy = policies.current()
                    ?: return@inTransaction Verdict.Rollback(Result.InvalidCredential)
                when {
                    !authorized -> Verdict.Rollback(Result.InvalidCredential)
                    user == null || user.disabledAt != null || !user.emailVerified ->
                        Verdict.Rollback(Result.InvalidCredential)
                    googleRecord == null -> Verdict.Rollback(Result.NotLinked)
                    !hasSignInPathAfterUnlink(userId, policy) ->
                        Verdict.Rollback(Result.LastSignInMethod)
                    credentials.deleteGoogleFor(userId) -> Verdict.Commit(Result.Unlinked)
                    else -> Verdict.Rollback(Result.NotLinked)
                }
            }

        private suspend fun hasSignInPathAfterUnlink(userId: UserId, policy: AuthenticationPolicy): Boolean {
            val available = buildSet {
                if (credentials.findPasswordFor(userId) != null) add(AuthenticationTokenKind.PASSWORD)
                if (emailSignInAvailable) add(AuthenticationTokenKind.EMAIL_SIGN_IN_CODE)
                factors.filter { it.isEnrolledFor(userId) }.forEach { factor -> add(factor.kind.toToken()) }
            }
            return policy.schemesFor(AuthenticationAction.SIGN_IN).any { it.isSatisfiedBy(available) }
        }

        private fun SecondFactorKind.toToken(): AuthenticationTokenKind = when (this) {
            SecondFactorKind.TOTP -> AuthenticationTokenKind.TOTP
            SecondFactorKind.EMAIL_OTP -> AuthenticationTokenKind.EMAIL_FACTOR_CODE
        }
    }
}
