package tallyvane.authentication.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.authentication.application.port.RecoveryCodeMint

/**
 * The behaviour every [RecoveryCodeMint] must show, whatever makes the codes.
 */
abstract class RecoveryCodeMintConformance : StringSpec() {
    protected abstract fun fresh(): RecoveryCodeMint

    init {
        "mints ten codes, each two groups of five letters and digits without look-alikes" {
            val codes = fresh().mint().map { it.revealed() }

            codes.size shouldBe 10
            codes.forEach { code ->
                code.length shouldBe 11
                code[5] shouldBe '-'
                code.replace("-", "").all { it in RecoveryCodeMint.Csprng.ALPHABET } shouldBe true
            }
        }

        "mints no two alike, in a batch or between batches" {
            val mint = fresh()

            val codes = (mint.mint() + mint.mint() + mint.mint()).map { it.revealed() }

            codes.toSet().size shouldBe codes.size
        }
    }
}
