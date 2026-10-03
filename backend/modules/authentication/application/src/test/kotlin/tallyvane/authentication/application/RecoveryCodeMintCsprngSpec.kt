package tallyvane.authentication.application

import tallyvane.authentication.application.port.RecoveryCodeMint

/**
 * [RecoveryCodeMintCsprng] held to the suite every [RecoveryCodeMint] must pass.
 */
class RecoveryCodeMintCsprngSpec : RecoveryCodeMintConformance() {
    override fun fresh(): RecoveryCodeMint = RecoveryCodeMint.Csprng()
}
