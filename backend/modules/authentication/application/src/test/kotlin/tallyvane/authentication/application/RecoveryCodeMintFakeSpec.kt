package tallyvane.authentication.application

import tallyvane.authentication.application.port.RecoveryCodeMint

/**
 * [RecoveryCodeMintFake] held to the suite every [RecoveryCodeMint] must pass.
 */
class RecoveryCodeMintFakeSpec : RecoveryCodeMintConformance() {
    override fun fresh(): RecoveryCodeMint = RecoveryCodeMintFake()
}
