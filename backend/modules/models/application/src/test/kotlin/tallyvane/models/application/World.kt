package tallyvane.models.application

import tallyvane.identity.contract.AccountId
import tallyvane.identity.contract.Admins
import tallyvane.platform.kernel.ClockFake
import tallyvane.platform.kernel.TransactionRunnerFake
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal val ADMIN = AccountId(Uuid.parse("00000000-0000-7000-8000-0000000000a1"))
internal val PERSON = AccountId(Uuid.parse("00000000-0000-7000-8000-0000000000a2"))

/**
 * A provider book and an administration over it, where only [ADMIN] is an administrator.
 */
internal class World {
    val book = ProviderBookFake()
    val administration = Administration(
        book,
        object : Admins {
            override fun isAdmin(account: AccountId): Boolean = account == ADMIN

            override fun all(): List<AccountId> = listOf(ADMIN)
        },
        TransactionRunnerFake(),
        ClockFake(Instant.parse("2026-11-13T10:00:00Z")),
    )

    fun form(id: Uuid, key: String? = "sk-1", kind: String = "openai_compatible", url: String? = "https://x.test/v1") =
        ProviderForm(id, "Provider $id", kind, url, key, "small", "big", 0.15, 0.6)

    override fun toString(): String = "World"
}
