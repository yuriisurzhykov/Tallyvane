package tallyvane.identity.application.port

import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.Secret

/** Caller-owned transaction. Replacement must serialize with concurrent replacement and consumption. */
public interface BackupCodeStore {
    public suspend fun replace(userId: UserId, hashes: List<Secret>)

    public suspend fun consume(userId: UserId, hash: Secret): Boolean
}
