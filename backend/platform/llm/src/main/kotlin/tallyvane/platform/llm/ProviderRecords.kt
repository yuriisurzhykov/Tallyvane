package tallyvane.platform.llm

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import tallyvane.platform.kernel.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * The providers and the call log as an administrator manages them. Every call runs inside a transaction the
 * caller opened.
 */
public class ProviderRecords(private val cipher: KeyCipher, private val clock: Clock) {
    public fun list(): List<ProviderEntry> = ModelProvidersTable.selectAll()
        .orderBy(ModelProvidersTable.name)
        .map(::entryOf)

    /**
     * Saves [draft]. Returns false when a new provider comes without a key, or another provider has its name.
     */
    public fun save(draft: ProviderDraft): Boolean {
        val exists = ModelProvidersTable.selectAll().where { ModelProvidersTable.id eq draft.id }.any()
        val taken = ModelProvidersTable.selectAll()
            .where { (ModelProvidersTable.name eq draft.name) and (ModelProvidersTable.id neq draft.id) }
            .any()
        val key = draft.key
        return when {
            taken || (!exists && key == null) -> false
            exists -> {
                ModelProvidersTable.update({ ModelProvidersTable.id eq draft.id }) { row ->
                    columns(row, draft)
                    key?.let {
                        row[sealedKey] = cipher.seal(it)
                        row[keyHint] = hintOf(it.revealed())
                    }
                }
                true
            }
            else -> {
                ModelProvidersTable.insert { row ->
                    row[id] = draft.id
                    row[active] = false
                    columns(row, draft)
                    row[sealedKey] = cipher.seal(checkNotNull(key))
                    row[keyHint] = hintOf(key.revealed())
                }
                true
            }
        }
    }

    /**
     * Makes [id] the one provider in use. False when there is no such provider.
     */
    public fun activate(id: Uuid): Boolean {
        val known = ModelProvidersTable.selectAll().where { ModelProvidersTable.id eq id }.any()
        if (known) {
            ModelProvidersTable.update { it[active] = false }
            ModelProvidersTable.update({ ModelProvidersTable.id eq id }) { it[active] = true }
        }
        return known
    }

    public fun remove(id: Uuid): Boolean = ModelProvidersTable.deleteWhere { ModelProvidersTable.id eq id } > 0

    /**
     * What each provider, model and spec cost since [since], the most expensive first.
     */
    public fun costs(since: Instant): List<CostLine> {
        val t = ModelCallsTable
        val calls = t.id.count()
        val input = t.tokensIn.sum()
        val output = t.tokensOut.sum()
        val cost = t.cost.sum()
        val names = ModelProvidersTable.selectAll().associate {
            it[ModelProvidersTable.id] to
                it[ModelProvidersTable.name]
        }
        val all = t.select(t.providerId, t.model, t.spec, calls, input, output, cost)
            .where { t.at greaterEq since }
            .groupBy(t.providerId, t.model, t.spec)
            .orderBy(cost, SortOrder.DESC)
            .toList()
        val remembered = t.select(t.providerId, t.model, t.spec, calls)
            .where { (t.at greaterEq since) and (t.remembered eq true) }
            .groupBy(t.providerId, t.model, t.spec)
            .associate { Triple(it[t.providerId], it[t.model], it[t.spec]) to it[calls] }
        return all.map {
            CostLine(
                names[it[t.providerId]] ?: "removed",
                it[t.model],
                it[t.spec],
                it[calls],
                remembered[Triple(it[t.providerId], it[t.model], it[t.spec])] ?: 0,
                it[input] ?: 0,
                it[output] ?: 0,
                it[cost] ?: 0.0,
            )
        }
    }

    internal fun activeProvider(): Provider? = ModelProvidersTable.selectAll()
        .where { ModelProvidersTable.active eq true }
        .singleOrNull()
        ?.let {
            Provider(
                it[ModelProvidersTable.id],
                ProviderKind.valueOf(it[ModelProvidersTable.kind]),
                it[ModelProvidersTable.baseUrl],
                cipher.open(it[ModelProvidersTable.sealedKey]),
                it[ModelProvidersTable.fast],
                it[ModelProvidersTable.careful],
                it[ModelProvidersTable.priceIn],
                it[ModelProvidersTable.priceOut],
            )
        }

    private fun ModelProvidersTable.columns(
        row: org.jetbrains.exposed.v1.core.statements.UpdateBuilder<*>,
        draft: ProviderDraft,
    ) {
        row[name] = draft.name
        row[kind] = draft.kind.name
        row[baseUrl] = draft.baseUrl
        row[fast] = draft.fast
        row[careful] = draft.careful
        row[priceIn] = draft.priceIn
        row[priceOut] = draft.priceOut
        row[updatedAt] = clock.now()
    }

    private fun entryOf(row: ResultRow): ProviderEntry = ProviderEntry(
        row[ModelProvidersTable.id],
        row[ModelProvidersTable.name],
        ProviderKind.valueOf(row[ModelProvidersTable.kind]),
        row[ModelProvidersTable.baseUrl],
        row[ModelProvidersTable.keyHint],
        row[ModelProvidersTable.fast],
        row[ModelProvidersTable.careful],
        row[ModelProvidersTable.priceIn],
        row[ModelProvidersTable.priceOut],
        row[ModelProvidersTable.active],
    )

    private fun hintOf(key: String): String = "…" + key.takeLast(HINT)

    override fun toString(): String = "ProviderRecords"

    private companion object {
        const val HINT = 4
    }
}
