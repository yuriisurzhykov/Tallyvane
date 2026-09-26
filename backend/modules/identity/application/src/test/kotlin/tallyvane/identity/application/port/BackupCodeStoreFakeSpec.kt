package tallyvane.identity.application.port

import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunnerFake

class BackupCodeStoreFakeSpec : BackupCodeStoreConformance() {
    override fun fresh(): BackupCodeStoreConformance.Subject = SubjectImpl()

    private class SubjectImpl : BackupCodeStoreConformance.Subject {
        override val transactions = TransactionRunnerFake()
        override val store = MemoryBackupCodeStore()
    }

    private class MemoryBackupCodeStore : BackupCodeStore {
        private val values = mutableMapOf<UserId, MutableSet<String>>()
        override suspend fun replace(userId: UserId, hashes: List<Secret>) {
            values[userId] = hashes.mapTo(mutableSetOf()) { it.revealed() }
        }
        override suspend fun consume(userId: UserId, hash: Secret): Boolean =
            values[userId]?.remove(hash.revealed()) == true
        override suspend fun hasAny(userId: UserId): Boolean = values[userId]?.isNotEmpty() == true
    }
}
