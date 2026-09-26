package tallyvane.identity.application.secondfactor

import tallyvane.identity.application.port.BackupCodeStore
import tallyvane.identity.application.port.EmailMfaEnrollmentStore
import tallyvane.identity.application.port.SessionStore
import tallyvane.identity.application.port.TotpEnrollmentStore
import tallyvane.identity.domain.secondfactor.SecondFactorKind
import tallyvane.identity.domain.session.SessionId
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict
import kotlin.time.Duration.Companion.minutes

public interface ReadSecondFactorStatusUseCase : UseCase {
    public suspend fun read(userId: UserId, sessionId: SessionId): Status

    public data class Status(public val enrolled: Set<SecondFactorKind>, public val recentlyAuthenticated: Boolean)

    public class Read(
        private val sessions: SessionStore,
        private val totp: TotpEnrollmentStore,
        private val emailMfa: EmailMfaEnrollmentStore,
        private val backupCodes: BackupCodeStore,
        private val clock: Clock,
        private val transactions: TransactionRunner,
    ) : ReadSecondFactorStatusUseCase {
        override suspend fun read(userId: UserId, sessionId: SessionId): Status = transactions.inTransaction {
            val now = clock.now()
            val session = sessions.find(sessionId)?.takeIf { it.userId == userId && it.revokedAt == null }
            val enrolled = buildSet {
                if (totp.find(userId)?.active == true) add(SecondFactorKind.TOTP)
                if (emailMfa.isEnrolled(userId)) add(SecondFactorKind.EMAIL_OTP)
                if (backupCodes.hasAny(userId)) add(SecondFactorKind.BACKUP_CODE)
            }
            val authenticatedAt = session?.reauthenticatedAt
            Verdict.Commit(
                Status(
                    enrolled,
                    authenticatedAt != null && authenticatedAt <= now && now - authenticatedAt <= REAUTHENTICATION_WINDOW,
                ),
            )
        }

        private companion object {
            val REAUTHENTICATION_WINDOW = 5.minutes
        }
    }
}
