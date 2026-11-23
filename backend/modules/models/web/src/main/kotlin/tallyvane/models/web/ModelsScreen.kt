package tallyvane.models.web

import tallyvane.models.application.ModelsOutcome

/**
 * The body every route here answers with: the screen as it is after the change.
 */
internal class ModelsScreen {
    fun of(shown: ModelsOutcome.Shown): ModelsShown = ModelsShown(
        shown.providers.map {
            ProviderShown(
                it.id.toString(), it.name, it.kind, it.baseUrl, it.keyHint, it.fast, it.careful, it.priceIn,
                it.priceOut, it.active,
            )
        },
        shown.costs.map {
            CostShown(it.provider, it.model, it.spec, it.calls, it.remembered, it.tokensIn, it.tokensOut, it.dollars)
        },
    )

    override fun toString(): String = "ModelsScreen"
}
