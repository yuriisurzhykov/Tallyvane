package tallyvane.identity.application.google

import tallyvane.identity.application.AuthenticationCompleter
import tallyvane.identity.application.SignInOutcome
import tallyvane.identity.application.port.CredentialRepository
import tallyvane.identity.application.port.UserRepository
import tallyvane.identity.domain.credential.Credential
import tallyvane.identity.domain.outcome.AuthenticationOutcome
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
     * A first sign-in for a Google identity this account has never seen creates the account and
     * its [Credential.GoogleRecord] in the same transaction; a returning one finds it by
     * [GoogleIdentity.subject] alone, never by email.
     *
     * If the account's email is already taken by a *different* credential (a password account
     * signing up again through Google, say), this refuses rather than linking the two silently —
     * why: `application/README.md`.
     */
    class Default(
        private val users: UserRepository,
        private val credentials: CredentialRepository,
        private val completer: AuthenticationCompleter,
        private val transactions: TransactionRunner,
        private val ids: IdGenerator,
        private val clock: Clock,
    ) : GoogleSignInCompleter {
        override suspend fun complete(identity: GoogleIdentity, device: DeviceLabel): SignInOutcome {
            val userId = findOrCreateUser(identity)
            return if (userId == null) {
                SignInOutcome.NotIssued(AuthenticationOutcome.InvalidCredential)
            } else {
                completer.complete(userId, device)
            }
        }

        private suspend fun findOrCreateUser(identity: GoogleIdentity): UserId? {
            credentials.findUserIdByGoogleSubject(identity.subject)?.let { return it }
            return transactions.inTransaction {
                val userId = UserId(ids.next())
                val user = User(
                    id = userId,
                    email = identity.email,
                    displayName = null,
                    createdAt = clock.now(),
                    disabledAt = null,
                )
                when (users.insert(user)) {
                    UserRepository.InsertOutcome.EMAIL_TAKEN -> Verdict.Rollback(null)
                    UserRepository.InsertOutcome.INSERTED -> {
                        credentials.save(userId, Credential.GoogleRecord(identity.subject))
                        Verdict.Commit(userId)
                    }
                }
            }
        }
    }
}
