package tallyvane.platform.llm

import kotlin.uuid.Uuid

/**
 * A [Spending] in a map (ADR-044).
 */
class SpendingFake : Spending {
    private val spent = mutableMapOf<Pair<Uuid, String>, Long>()

    override suspend fun spentOn(account: Uuid, day: String): Long = spent[account to day] ?: 0

    override suspend fun add(account: Uuid, day: String, spec: String, tokens: TokenCount) {
        spent[account to day] = spentOn(account, day) + tokens.total()
    }
}
