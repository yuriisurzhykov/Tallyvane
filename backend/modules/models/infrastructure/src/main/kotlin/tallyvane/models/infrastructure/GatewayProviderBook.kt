package tallyvane.models.infrastructure

import tallyvane.models.application.Cost
import tallyvane.models.application.ProviderForm
import tallyvane.models.application.ProviderSetting
import tallyvane.models.application.port.ProviderBook
import tallyvane.platform.kernel.Secret
import tallyvane.platform.llm.ProviderDraft
import tallyvane.platform.llm.ProviderKind
import tallyvane.platform.llm.ProviderRecords
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * [ProviderBook] over the model gateway's own records.
 */
internal class GatewayProviderBook(private val records: ProviderRecords) : ProviderBook {
    override fun list(): List<ProviderSetting> = records.list().map {
        ProviderSetting(
            it.id, it.name, wordOf(it.kind), it.baseUrl, it.keyHint, it.fast, it.careful, it.priceIn, it.priceOut,
            it.active,
        )
    }

    override fun save(form: ProviderForm): Boolean = records.save(
        ProviderDraft(
            form.id,
            form.name.trim(),
            kindOf(form.kind),
            form.baseUrl?.trim()?.takeIf { it.isNotEmpty() },
            form.key?.trim()?.takeIf { it.isNotEmpty() }?.let(::Secret),
            form.fast.trim(),
            form.careful.trim(),
            form.priceIn,
            form.priceOut,
        ),
    )

    override fun activate(id: Uuid): Boolean = records.activate(id)

    override fun remove(id: Uuid): Boolean = records.remove(id)

    override fun costs(since: Instant): List<Cost> = records.costs(since).map {
        Cost(it.provider, it.model, it.spec, it.calls, it.remembered, it.tokensIn, it.tokensOut, it.cost)
    }

    private fun wordOf(kind: ProviderKind): String = if (kind == ProviderKind.Anthropic) ANTHROPIC else OPENAI

    private fun kindOf(word: String): ProviderKind =
        if (word == ANTHROPIC) ProviderKind.Anthropic else ProviderKind.OpenAiCompatible

    override fun toString(): String = "GatewayProviderBook"

    private companion object {
        const val ANTHROPIC = "anthropic"
        const val OPENAI = "openai_compatible"
    }
}
