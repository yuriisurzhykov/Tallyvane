package tallyvane.identity.application.google

import tallyvane.identity.application.AuthenticationCompleter
import tallyvane.identity.application.SignInOutcome
import tallyvane.identity.application.port.AuthenticationPolicyStore
import tallyvane.identity.application.port.CredentialRepository
import tallyvane.identity.application.port.UserRepository
import tallyvane.identity.domain.credential.Credential
import tallyvane.identity.domain.outcome.AuthenticationOutcome
import tallyvane.identity.domain.secondfactor.AuthenticationPolicy
import tallyvane.identity.domain.secondfactor.AuthenticationAction
import tallyvane.identity.domain.secondfactor.AuthenticationTokenKind
import tallyvane.identity.domain.secondfactor.PrimaryMethod
import tallyvane.identity.domain.session.DeviceLabel
import tallyvane.identity.domain.user.User
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.IdGenerator
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.Verdict

/**
 * The one sequence both Google sign-in methods need once they have a verified [GoogleIdentity] —
 * find or create the account, then hand the resulting [UserId] to [completer]. Extracted once the
 * Google Identity Services method needed the identical sequence Google OAuth already had, not
 * before: `application/README.md`.
 */
internal interface GoogleSignInCompleter {
    suspend fun complete(identity: GoogleIdentity, device: DeviceLabel): SignInOutcome

    /**
     * One [transactions.inTransaction] covers the whole method — the lookup by subject included,
     * not only the insert — because [users], [credentials] and every port [completer] itself
     * touches open no transaction of their own; whichever use case calls them is the one that
     * must have one open already. Verified for real, not merely argued:
     * `backend/playground/transactions/README.md`'s 2026-09-02 entry.
     *
     * A first sign-in for a Google identity creates its account and [Credential.GoogleRecord] in
     * the same transaction. When the verified Google email already belongs to a local account,
     * its Google slot is claimed atomically and that existing account receives the session. A
     * returning identity is resolved by [GoogleIdentity.subject] first, so an email change does
     * not move the Google credential to another account.
     *
     * The infrastructure verifier only constructs [GoogleIdentity] from a Google ID token whose
     * `email_verified` claim is true. `saveGoogleIfUnclaimed` also refuses to replace an existing
     * Google credential or claim a subject already assigned to another account.
     */
    class Default(
        private val users: UserRepository,
        private val credentials: CredentialRepository,
        private val completer: AuthenticationCompleter,
        private val transactions: TransactionRunner,
        private val ids: IdGenerator,
        private val clock: Clock,
        private val policies: AuthenticationPolicyStore? = null,
    ) : GoogleSignInCompleter {
        override suspend fun complete(identity: GoogleIdentity, device: DeviceLabel): SignInOutcome =
            transactions.inTransaction {
                val policy = policies?.current() ?: AuthenticationPolicy.defaults()
                val googleEnabled = policy.schemesFor(AuthenticationAction.SIGN_IN).any {
                    AuthenticationTokenKind.GOOGLE in it.requiredTokens
                }
                val userId = if (googleEnabled) findOrCreateUser(identity) else null
                val outcome = if (userId == null) {
                    SignInOutcome.NotIssued(AuthenticationOutcome.InvalidCredential)
                } else {
                    completer.complete(userId, device, PrimaryMethod.GOOGLE)
                }
                Verdict.Commit(outcome)
            }

        private suspend fun findOrCreateUser(identity: GoogleIdentity): UserId? {
            credentials.findUserIdByGoogleSubject(identity.subject)?.let { return it }
            users.findByEmail(identity.email)?.let { user ->
                return if (credentials.saveGoogleIfUnclaimed(user.id, identity.subject)) {
                    user.id
                } else {
                    credentials.findUserIdByGoogleSubject(identity.subject)
                }
            }
            val userId = UserId(ids.next())
            val user = User(
                id = userId,
                email = identity.email,
                displayName = null,
                createdAt = clock.now(),
                disabledAt = null,
            )
            return when (users.insert(user)) {
                UserRepository.InsertOutcome.EMAIL_TAKEN -> users.findByEmail(identity.email)?.let { existing ->
                    if (credentials.saveGoogleIfUnclaimed(existing.id, identity.subject)) {
                        existing.id
                    } else {
                        credentials.findUserIdByGoogleSubject(identity.subject)
                    }
                }
                UserRepository.InsertOutcome.INSERTED -> {
                    credentials.save(userId, Credential.GoogleRecord(identity.subject))
                    userId
                }
            }
        }
    }
}
