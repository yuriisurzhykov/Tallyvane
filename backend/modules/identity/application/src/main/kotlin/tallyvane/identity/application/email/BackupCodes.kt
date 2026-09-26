package tallyvane.identity.application.email

import tallyvane.identity.application.port.AuthenticationCodes
import tallyvane.identity.application.port.BackupCodeStore
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.Secret

public class BackupCodes(
    private val store: BackupCodeStore,
    private val codes: AuthenticationCodes,
) {
    public suspend fun issue(userId: UserId): List<Secret> {
        val issued = List(10) { codes.backupCode() }
        store.replace(userId, issued.map { codes.hash("backup:${userId.value}", it) })
        return issued
    }

    public suspend fun consume(userId: UserId, code: Secret): Boolean =
        store.consume(userId, codes.hash("backup:${userId.value}", code))

    public suspend fun hasAny(userId: UserId): Boolean = store.hasAny(userId)
}
