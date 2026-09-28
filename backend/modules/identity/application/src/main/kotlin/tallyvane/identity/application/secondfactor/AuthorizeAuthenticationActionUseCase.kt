package tallyvane.identity.application.secondfactor

import tallyvane.identity.application.email.EmailChallenges
import tallyvane.identity.application.port.AuthenticationActionProofStore
import tallyvane.identity.application.port.AuthenticationPolicyStore
import tallyvane.identity.application.port.CredentialRepository
import tallyvane.identity.application.port.GoogleOAuthGateway
import tallyvane.identity.application.port.PasswordHasher
import tallyvane.identity.application.port.SessionStore
import tallyvane.identity.application.port.TokenFactory
import tallyvane.identity.application.port.TokenHasher
import tallyvane.identity.application.port.UserRepository
import tallyvane.identity.domain.email.EmailChallengePurpose
import tallyvane.identity.domain.secondfactor.AuthenticationAction
import tallyvane.identity.domain.secondfactor.AuthenticationActionProof
import tallyvane.identity.domain.secondfactor.AuthenticationPolicy
import tallyvane.identity.domain.secondfactor.AuthenticationScheme
import tallyvane.identity.domain.secondfactor.AuthenticationTokenKind
import tallyvane.identity.domain.secondfactor.SecondFactorKind
import tallyvane.identity.domain.session.SessionId
import tallyvane.identity.domain.token.TokenKind
import tallyvane.identity.domain.token.TokenValue
import tallyvane.identity.domain.user.Email
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

/**
 * Verifies the highest available scheme for a protected account action and grants one-use proof.
 */
public interface AuthorizeAuthenticationActionUseCase : UseCase {
    public suspend fun authorize(request: Request): Result

    public data class PresentedToken(
        public val kind: AuthenticationTokenKind,
        public val value: String,
        public val challengeId: Uuid? = null,
        public val codeVerifier: String? = null,
        public val redirectUri: String? = null,
    )

    public data class Request(
        public val userId: UserId,
        public val sessionId: SessionId,
        public val action: AuthenticationAction,
        public val tokens: List<PresentedToken>,
    )

    public sealed interface Result {
        public data class Authorized(
            public val proof: TokenValue,
            public val schemeId: String,
            public val assuranceRank: Int,
            public val expiresAt: kotlin.time.Instant,
        ) : Result
        public data object Refused : Result
    }

    public class Authorize internal constructor(
        private val users: UserRepository,
        private val credentials: CredentialRepository,
        private val passwords: PasswordHasher,
        private val google: GoogleOAuthGateway?,
        private val emailChallenges: EmailChallenges?,
        private val factors: SecondFactorMethodRegistry,
        private val policies: AuthenticationPolicyStore,
        private val proofs: AuthenticationActionProofStore,
        private val sessions: SessionStore,
        private val tokenFactory: TokenFactory,
        private val tokenHasher: TokenHasher,
        private val clock: Clock,
        private val transactions: TransactionRunner,
        private val ttl: Duration = 3.minutes,
    ) : AuthorizeAuthenticationActionUseCase,
        ReadAuthenticationActionSchemesUseCase,
        RequestAuthenticationActionEmailCodeUseCase {
        private val tokenVerifier =
            ActionTokenVerifier(credentials, passwords, google, emailChallenges, factors, transactions)

        override suspend fun read(
            userId: UserId,
            sessionId: SessionId,
            action: AuthenticationAction,
        ): List<AuthenticationScheme> = transactions.inTransaction {
            if (action == AuthenticationAction.SIGN_IN) return@inTransaction Verdict.Commit(emptyList())
            val session = sessions.find(sessionId)
            if (session?.userId != userId || session.revokedAt != null) return@inTransaction Verdict.Commit(emptyList())
            users.findById(userId)?.takeIf { it.disabledAt == null && it.emailVerified }
                ?: return@inTransaction Verdict.Commit(emptyList())
            val policy = policies.current() ?: return@inTransaction Verdict.Commit(emptyList())
            Verdict.Commit(policy.strongest(action, availableTokens(userId, action, policy)))
        }

        override suspend fun request(
            userId: UserId,
            sessionId: SessionId,
            action: AuthenticationAction,
            kind: AuthenticationTokenKind,
        ): Uuid? {
            val target = if (
                action == AuthenticationAction.SIGN_IN ||
                kind !in setOf(AuthenticationTokenKind.EMAIL_SIGN_IN_CODE, AuthenticationTokenKind.EMAIL_FACTOR_CODE)
            ) {
                null
            } else {
                transactions.inTransaction {
                    val user = users.findById(userId)?.takeIf { it.disabledAt == null && it.emailVerified }
                        ?: return@inTransaction Verdict.Rollback(null)
                    val session = sessions.find(sessionId)?.takeIf { it.userId == userId && it.revokedAt == null }
                        ?: return@inTransaction Verdict.Rollback(null)
                    val policy = policies.current() ?: return@inTransaction Verdict.Rollback(null)
                    val available = availableTokens(userId, action, policy)
                    if (policy.strongest(action, available).none { kind in it.requiredTokens }) {
                        return@inTransaction Verdict.Rollback(null)
                    }
                    Verdict.Commit(user.email to "action-proof:$action:${session.id.value}:${policy.version}")
                }
            }
            return target?.let { (email, binding) ->
                val purpose = if (kind == AuthenticationTokenKind.EMAIL_SIGN_IN_CODE) {
                    EmailChallengePurpose.EMAIL_LOGIN
                } else {
                    EmailChallengePurpose.MFA
                }
                emailChallenges?.issue(email, purpose, binding)?.id
            }
        }

        override suspend fun authorize(request: Request): Result {
            val presented = request.tokens.associateBy { it.kind }
            val result = initialContext(request, presented)?.let { initial ->
                if (verifyPrimaryTokens(request, presented, initial)) {
                    finishAuthorization(request, presented, initial)
                } else {
                    Result.Refused
                }
            } ?: Result.Refused
            return result
        }

        private suspend fun initialContext(
            request: Request,
            presented: Map<AuthenticationTokenKind, PresentedToken>,
        ): InitialContext? {
            if (request.action == AuthenticationAction.SIGN_IN ||
                request.tokens.map { it.kind }.toSet().size != request.tokens.size
            ) {
                return null
            }
            return transactions.inTransaction {
                val session = sessions.find(request.sessionId)
                if (session?.userId != request.userId || session.revokedAt != null) {
                    return@inTransaction Verdict.Rollback(null)
                }
                val user = users.findById(request.userId)?.takeIf { it.disabledAt == null && it.emailVerified }
                    ?: return@inTransaction Verdict.Rollback(null)
                val policy = policies.current() ?: return@inTransaction Verdict.Rollback(null)
                val available = availableTokens(request.userId, request.action, policy)
                val scheme = policy.strongest(request.action, available)
                    .firstOrNull { candidate -> candidate.requiredTokens.all(presented::containsKey) }
                    ?: return@inTransaction Verdict.Rollback(null)
                Verdict.Commit(InitialContext(user.email, policy, scheme))
            }
        }

        private suspend fun verifyPrimaryTokens(
            request: Request,
            presented: Map<AuthenticationTokenKind, PresentedToken>,
            initial: InitialContext,
        ): Boolean {
            var verified = true
            for (kind in listOf(AuthenticationTokenKind.PASSWORD, AuthenticationTokenKind.GOOGLE)) {
                if (verified && kind in initial.scheme.requiredTokens) {
                    verified =
                        tokenVerifier.verify(request, initial.email, presented.getValue(kind), initial.policy.version)
                }
            }
            return verified
        }

        private suspend fun finishAuthorization(
            request: Request,
            presented: Map<AuthenticationTokenKind, PresentedToken>,
            initial: InitialContext,
        ): Result = transactions.inTransaction {
            val active = sessions.find(request.sessionId)
                ?.let { it.userId == request.userId && it.revokedAt == null } == true
            val currentUser = users.findById(request.userId)?.takeIf { it.disabledAt == null && it.emailVerified }
            val currentPolicy = policies.current()
            if (currentPolicy == null) return@inTransaction Verdict.Rollback(Result.Refused)
            val stillStrongest = currentPolicy.let { policy ->
                policy.version == initial.policy.version &&
                    initial.scheme in policy.strongest(
                        request.action,
                        availableTokens(request.userId, request.action, policy),
                    )
            }
            if (!active || currentUser == null || !stillStrongest) {
                return@inTransaction Verdict.Rollback(Result.Refused)
            }

            if (!verifyFactorTokens(request, presented, initial.scheme, currentUser.email, currentPolicy.version)) {
                // Keep challenge attempt counters while no unrelated recovery code has been consumed.
                return@inTransaction Verdict.Commit(Result.Refused)
            }

            val rawProof = tokenFactory.mint(TokenKind.ACTION_PROOF)
            val expiresAt = clock.now() + ttl
            val record = AuthenticationActionProof(
                token = tokenHasher.hash(rawProof),
                userId = request.userId,
                sessionId = request.sessionId,
                action = request.action,
                policyVersion = currentPolicy.version,
                schemeId = initial.scheme.id,
                assuranceRank = initial.scheme.assuranceRank,
                expiresAt = expiresAt,
            )
            proofs.save(record)
            Verdict.Commit(
                Result.Authorized(rawProof, initial.scheme.id, initial.scheme.assuranceRank, expiresAt),
            )
        }

        private suspend fun verifyFactorTokens(
            request: Request,
            presented: Map<AuthenticationTokenKind, PresentedToken>,
            scheme: AuthenticationScheme,
            email: Email,
            policyVersion: Long,
        ): Boolean {
            var verified = true
            for (kind in listOf(
                AuthenticationTokenKind.TOTP,
                AuthenticationTokenKind.EMAIL_SIGN_IN_CODE,
                AuthenticationTokenKind.EMAIL_FACTOR_CODE,
            )) {
                if (verified && kind in scheme.requiredTokens) {
                    verified = tokenVerifier.verify(request, email, presented.getValue(kind), policyVersion)
                }
            }
            return verified
        }

        private suspend fun availableTokens(
            userId: UserId,
            action: AuthenticationAction,
            policy: AuthenticationPolicy,
        ): Set<AuthenticationTokenKind> = buildSet {
            if (credentials.findPasswordFor(userId) != null) add(AuthenticationTokenKind.PASSWORD)
            if (google != null && credentials.findGoogleFor(userId) != null) add(AuthenticationTokenKind.GOOGLE)
            val emailCodeSignInEnabled = policy.schemesFor(AuthenticationAction.SIGN_IN).any {
                AuthenticationTokenKind.EMAIL_SIGN_IN_CODE in it.requiredTokens
            }
            if (emailChallenges != null &&
                emailCodeSignInEnabled &&
                policy.schemesFor(action).any { AuthenticationTokenKind.EMAIL_SIGN_IN_CODE in it.requiredTokens }
            ) {
                add(AuthenticationTokenKind.EMAIL_SIGN_IN_CODE)
            }
            factors.enrolledFor(userId).forEach { factor -> add(factor.toToken()) }
        }

        private data class InitialContext(
            val email: Email,
            val policy: AuthenticationPolicy,
            val scheme: AuthenticationScheme,
        )

        private fun SecondFactorKind.toToken(): AuthenticationTokenKind = when (this) {
            SecondFactorKind.TOTP -> AuthenticationTokenKind.TOTP
            SecondFactorKind.EMAIL_OTP -> AuthenticationTokenKind.EMAIL_FACTOR_CODE
        }
    }
}
