package tallyvane.authentication.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.platform.kernel.Digest
import kotlin.time.Instant

private val AT = Instant.parse("2026-10-03T09:00:00Z")

private fun digest(seed: Int) = Digest(byteArrayOf(seed.toByte(), 7, 7), 1)

private fun RecoveryCodes.spentBy(digest: Digest): RecoveryCodes = spend(digest, AT).reportTo(
    object : SpendVerdict.Report<RecoveryCodes> {
        override fun spent(next: RecoveryCodes): RecoveryCodes = next

        override fun unknown(): RecoveryCodes = error("The code should have been spent.")
    },
)

private fun RecoveryCodes.isUnknown(digest: Digest): Boolean = spend(digest, AT).reportTo(
    object : SpendVerdict.Report<Boolean> {
        override fun spent(next: RecoveryCodes): Boolean = false

        override fun unknown(): Boolean = true
    },
)

class RecoveryCodesSpec :
    StringSpec(
        {
            val issued = RecoveryCodes.issue((1..10).map(::digest))

            "a new set has every code unspent" {
                issued.remaining() shouldBe 10
            }

            "spending a code uses it up, and only that one" {
                val after = issued.spentBy(digest(3))

                after.remaining() shouldBe 9
                after.isUnknown(digest(3)) shouldBe true
                after.isUnknown(digest(4)) shouldBe false
            }

            "a code that was never issued is not spent" {
                issued.isUnknown(digest(99)) shouldBe true
                issued.remaining() shouldBe 10
            }

            "a digest made with another pepper version is another code" {
                issued.isUnknown(Digest(byteArrayOf(3, 7, 7), 2)) shouldBe true
            }

            "a set of none, or with a code twice, is refused" {
                shouldThrow<IllegalArgumentException> { RecoveryCodes.issue(emptyList()) }
                shouldThrow<IllegalArgumentException> { RecoveryCodes.issue(listOf(digest(1), digest(1))) }
            }

            "what it tells comes back as the same set, spent codes included" {
                val after = issued.spentBy(digest(2))

                val restored = RecoveryCodes.restore { record ->
                    after.writeTo { digest, spentAt -> record.code(digest, spentAt) }
                }

                restored.remaining() shouldBe 9
                restored.isUnknown(digest(2)) shouldBe true
            }

            "a replay of no code, or of one code twice, is refused" {
                shouldThrow<IllegalStateException> { RecoveryCodes.restore { } }
                shouldThrow<IllegalStateException> {
                    RecoveryCodes.restore { record ->
                        record.code(digest(1), null)
                        record.code(digest(1), null)
                    }
                }
            }

            "printing one says how many are left and nothing about the codes" {
                issued.toString() shouldBe "RecoveryCodes(remaining=10 of 10)"
            }
        },
    )
