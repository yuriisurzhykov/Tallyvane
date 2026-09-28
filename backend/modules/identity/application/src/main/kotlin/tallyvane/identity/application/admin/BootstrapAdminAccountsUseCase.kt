package tallyvane.identity.application.admin

import tallyvane.identity.application.port.CredentialRepository
import tallyvane.identity.application.port.EmailMfaEnrollmentStore
import tallyvane.identity.application.port.TotpEnrollmentStore
import tallyvane.identity.application.port.UserRepository
import tallyvane.identity.domain.user.Email
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.IdGenerator
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict

/**
 * Creates missing administrator identities from verified, enabled accounts named by the bootstrap
 * allowlist. Account IDs and every copied credential/factor row are independent after this one-time
 * operation; later changes in either realm do not propagate to the other.
 */
public interface BootstrapAdminAccountsUseCase : UseCase {
    /**
     * Copies any eligible allowlisted accounts that do not already have an administrator record.
     */
    public suspend fun provisionConfiguredAdmins(): Int

    public class Copy(
        private val users: UserRepository,
        private val userCredentials: CredentialRepository,
        private val userTotp: TotpEnrollmentStore,
        private val userEmailMfa: EmailMfaEnrollmentStore,
        private val admins: UserRepository,
        private val adminCredentials: CredentialRepository,
        private val adminTotp: TotpEnrollmentStore,
        private val adminEmailMfa: EmailMfaEnrollmentStore,
        adminEmails: Set<String>,
        private val ids: IdGenerator,
        private val clock: Clock,
        private val transactions: TransactionRunner,
    ) : BootstrapAdminAccountsUseCase {
        private val configuredEmails = adminEmails.map { it.trim() }.filter(String::isNotEmpty).distinct()

        override suspend fun provisionConfiguredAdmins(): Int = transactions.inTransaction {
            var provisioned = 0
            for (configuredEmail in configuredEmails) {
                val email = runCatching { Email(configuredEmail) }.getOrNull()
                if (email != null && provision(email)) provisioned += 1
            }
            Verdict.Commit(provisioned)
        }

        private suspend fun provision(email: Email): Boolean = users.findByEmail(email)
            ?.takeIf { it.disabledAt == null && it.emailVerified }
            ?.takeIf { admins.findByEmail(email) == null }
            ?.let { source ->
                val adminId = UserId(ids.next())
                val admin = source.copy(id = adminId, createdAt = clock.now())
                if (admins.insert(admin) != UserRepository.InsertOutcome.INSERTED) {
                    false
                } else {
                    copyCredentials(source.id, adminId)
                    copyFactors(source.id, adminId)
                    true
                }
            } ?: false

        private suspend fun copyCredentials(sourceId: UserId, adminId: UserId) {
            userCredentials.findPasswordFor(sourceId)?.let { adminCredentials.save(adminId, it) }
            userCredentials.findGoogleFor(sourceId)?.let { credential ->
                adminCredentials.saveGoogleIfUnclaimed(adminId, credential.subject)
            }
        }

        private suspend fun copyFactors(sourceId: UserId, adminId: UserId) {
            userTotp.find(sourceId)?.let { enrollment -> adminTotp.save(enrollment.copy(userId = adminId)) }
            if (userEmailMfa.isEnrolled(sourceId)) adminEmailMfa.enroll(adminId)
        }
    }
}
