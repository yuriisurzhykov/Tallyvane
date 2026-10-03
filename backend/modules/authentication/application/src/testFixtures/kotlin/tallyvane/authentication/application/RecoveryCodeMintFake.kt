package tallyvane.authentication.application

import tallyvane.authentication.application.port.RecoveryCodeMint
import tallyvane.platform.kernel.Secret

/**
 * A [RecoveryCodeMint] that counts: the first code it ever mints is `AAAAA-AAAAA`, the next `AAAAA-AAAAB`,
 * and so on in the alphabet real codes are written in, so no two are alike, in a batch or between batches,
 * and a test knows what shape it is shown.
 */
class RecoveryCodeMintFake : RecoveryCodeMint {
    private var minted = 0

    override fun mint(): List<Secret> = List(CODES) { Secret(written(minted++)) }

    override fun toString(): String = "RecoveryCodeMintFake($minted)"

    private fun written(number: Int): String {
        var rest = number
        val characters = CharArray(LENGTH)
        for (position in LENGTH - 1 downTo 0) {
            characters[position] = RecoveryCodeMint.Csprng.ALPHABET[rest % BASE]
            rest /= BASE
        }
        return characters.concatToString().chunked(LENGTH / 2).joinToString("-")
    }

    private companion object {
        const val CODES = 10
        const val LENGTH = 10
        const val BASE = 32
    }
}
