package tallyvane.identity.application.secondfactor

import tallyvane.identity.application.email.EmailChallenges
import tallyvane.identity.application.port.CredentialRepository
import tallyvane.identity.application.port.GoogleOAuthGateway
import tallyvane.identity.application.port.PasswordHasher
import tallyvane.identity.domain.email.EmailChallengePurpose
import tallyvane.identity.domain.secondfactor.AuthenticationTokenKind
import tallyvane.identity.domain.secondfactor.SecondFactorKind
import tallyvane.identity.domain.user.Email
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.Verdict

internal class ActionTokenVerifier(
    private val credentials: CredentialRepository,
    private val passwords: PasswordHasher,
    private val google: GoogleOAuthGateway?,
    private val emailChallenges: EmailChallenges?,
    private val factors: SecondFactorMethodRegistry,
    private val transactions: TransactionRunner,
) {
    suspend fun verify(
        request: AuthorizeAuthenticationActionUseCase.Request,
        email: Email,
        token: AuthorizeAuthenticationActionUseCase.PresentedToken,
        policyVersion: Long,
    ): Boolean = when (token.kind) {
        AuthenticationTokenKind.PASSWORD -> verifyPassword(request.userId, token)
        AuthenticationTokenKind.GOOGLE -> verifyGoogle(request.userId, token)
        AuthenticationTokenKind.EMAIL_SIGN_IN_CODE -> verifyEmailCode(request, email, token, policyVersion)
        AuthenticationTokenKind.TOTP,
        AuthenticationTokenKind.EMAIL_FACTOR_CODE,
        -> verifySecondFactor(request, token, policyVersion)
    }

    private suspend fun verifyPassword(
        userId: UserId,
        token: AuthorizeAuthenticationActionUseCase.PresentedToken,
    ): Boolean = transactions.inTransaction { Verdict.Commit(credentials.findPasswordFor(userId)) }
        ?.let { passwords.verify(Secret(token.value), it.hash) } == true

    private suspend fun verifyGoogle(
        userId: UserId,
        token: AuthorizeAuthenticationActionUseCase.PresentedToken,
    ): Boolean {
        val verifier = token.codeVerifier
        val redirect = token.redirectUri
        val identity = if (verifier != null && redirect != null) {
            google?.exchangeCode(token.value, verifier, redirect)
        } else {
            null
        }
        return identity?.let { verified ->
            transactions.inTransaction {
                Verdict.Commit(credentials.findGoogleFor(userId)?.subject == verified.subject)
            }
        } == true
    }

    private suspend fun verifyEmailCode(
        request: AuthorizeAuthenticationActionUseCase.Request,
        email: Email,
        token: AuthorizeAuthenticationActionUseCase.PresentedToken,
        policyVersion: Long,
    ): Boolean = token.challengeId?.let { challengeId ->
        emailChallenges?.verifyInCurrentTransaction(
            challengeId,
            email,
            EmailChallengePurpose.EMAIL_LOGIN,
            Secret(token.value),
            "action-proof:${request.action}:${request.sessionId.value}:$policyVersion",
        )
    } == true

    private suspend fun verifySecondFactor(
        request: AuthorizeAuthenticationActionUseCase.Request,
        token: AuthorizeAuthenticationActionUseCase.PresentedToken,
        policyVersion: Long,
    ): Boolean {
        val kind = token.kind.toFactorKind()
        return factors.find(kind)?.verify(
            request.userId,
            SecondFactorProof(
                code = token.value,
                challengeId = token.challengeId,
                binding = if (kind == SecondFactorKind.EMAIL_OTP) {
                    "action-proof:${request.action}:${request.sessionId.value}:$policyVersion"
                } else {
                    null
                },
            ),
        ) == true
    }

    private fun AuthenticationTokenKind.toFactorKind(): SecondFactorKind = when (this) {
        AuthenticationTokenKind.TOTP -> SecondFactorKind.TOTP
        AuthenticationTokenKind.EMAIL_FACTOR_CODE -> SecondFactorKind.EMAIL_OTP
        else -> error("$this is not a second-factor token")
    }
}
