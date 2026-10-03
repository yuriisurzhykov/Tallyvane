package tallyvane.authentication.application

import tallyvane.authentication.domain.RecoveryCodes
import tallyvane.authentication.domain.TotpEnrollment
import tallyvane.identity.contract.AccountId
import tallyvane.platform.kernel.Digest
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * What the suites of the second-factor ports share.
 */
internal val ANN = AccountId(Uuid.parse("00000000-0000-7000-8000-00000000000a"))
internal val BOB = AccountId(Uuid.parse("00000000-0000-7000-8000-00000000000b"))
internal val ENTROPY = "12345678901234567890".toByteArray(Charsets.US_ASCII)
internal val AT = Instant.parse("2026-10-03T09:00:00Z")

internal fun TotpEnrollment.told(): List<String> {
    val told = mutableListOf<String>()
    writeTo { seed, standing, last -> told += listOf(seed.revealed(), standing.name, last.toString()) }
    return told
}

internal fun RecoveryCodes.told(): List<String> {
    val told = mutableListOf<String>()
    writeTo { digest, spentAt ->
        val bytes = mutableListOf<Byte>()
        digest.writeTo { raw, version -> bytes += raw.toList() + version.toByte() }
        told += "${bytes.joinToString(",")} spent=$spentAt"
    }
    return told
}

internal fun digest(seed: Int) = Digest(byteArrayOf(seed.toByte(), 9), 1)

internal fun activeAt(step: Long): TotpEnrollment = TotpEnrollment.restore { record ->
    TotpEnrollment.begin(ENTROPY).writeTo { seed, _, _ -> record.kept(seed, TotpEnrollment.Standing.Active, step) }
}
