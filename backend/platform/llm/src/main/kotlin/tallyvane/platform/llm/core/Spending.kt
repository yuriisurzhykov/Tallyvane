package tallyvane.platform.llm.core

import tallyvane.platform.llm.TokenCount
import kotlin.uuid.Uuid

/**
 * The ledger of what each account's calls cost, a day at a time. It is also what a person reads to see what
 * was sent and what came back, per spec.
 *
 * Each call is a transaction of its own.
 */
public interface Spending {
    /**
     * The tokens [account] spent on [day], written `2026-11-13`.
     */
    public suspend fun spentOn(account: Uuid, day: String): Long

    /**
     * Adds one call of [spec] to what [account] spent on [day].
     */
    public suspend fun add(account: Uuid, day: String, spec: String, tokens: TokenCount)
}
