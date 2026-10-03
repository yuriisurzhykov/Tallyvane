package tallyvane.authentication.application

import tallyvane.authentication.application.port.RecoveryCodeMint
import tallyvane.platform.kernel.Secret

/**
 * A [RecoveryCodeMint] whose batches are `BATCH1-CODE1` … `BATCH1-CODE10`, then `BATCH2-CODE1` …, so a test
 * can name the code it expects to be shown.
 */
class RecoveryCodeMintFake : RecoveryCodeMint {
    private var batch = 0

    override fun mint(): List<Secret> {
        batch += 1
        return (1..CODES).map { Secret("BATCH$batch-CODE$it") }
    }

    override fun toString(): String = "RecoveryCodeMintFake"

    private companion object {
        const val CODES = 10
    }
}
