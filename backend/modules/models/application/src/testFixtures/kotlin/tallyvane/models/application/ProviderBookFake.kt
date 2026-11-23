package tallyvane.models.application

import tallyvane.models.application.port.ProviderBook
import kotlin.time.Instant
import kotlin.uuid.Uuid

class ProviderBookFake : ProviderBook {
    private val kept = linkedMapOf<Uuid, ProviderSetting>()

    override fun list(): List<ProviderSetting> = kept.values.sortedBy { it.name }

    override fun save(form: ProviderForm): Boolean {
        if (kept.values.any { it.name == form.name && it.id != form.id }) return false
        val old = kept[form.id]
        val hint = form.key?.takeIf { it.isNotBlank() }?.let { "…" + it.takeLast(HINT) } ?: old?.keyHint.orEmpty()
        kept[form.id] = ProviderSetting(
            form.id, form.name, form.kind, form.baseUrl, hint, form.fast, form.careful, form.priceIn, form.priceOut,
            old?.active ?: false,
        )
        return true
    }

    override fun activate(id: Uuid): Boolean {
        if (id !in kept) return false
        kept.replaceAll { key, it -> it.with(key == id) }
        return true
    }

    override fun remove(id: Uuid): Boolean = kept.remove(id) != null

    override fun costs(since: Instant): List<Cost> = emptyList()

    private fun ProviderSetting.with(on: Boolean) =
        ProviderSetting(id, name, kind, baseUrl, keyHint, fast, careful, priceIn, priceOut, on)

    override fun toString(): String = "ProviderBookFake(${kept.size})"

    private companion object {
        const val HINT = 4
    }
}
