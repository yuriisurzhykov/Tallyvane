package tallyvane.knowledge.application

import tallyvane.knowledge.domain.Action
import tallyvane.knowledge.domain.Embedding
import tallyvane.knowledge.domain.Skill
import tallyvane.knowledge.domain.SkillMatch
import tallyvane.knowledge.domain.SkillSlug
import tallyvane.knowledge.domain.SkillTags
import tallyvane.knowledge.domain.Tag
import tallyvane.knowledge.domain.Technology
import tallyvane.knowledge.domain.port.Diagnostics
import tallyvane.knowledge.domain.port.Skills

/**
 * Which skills an action is evidence of. Locally first: the skills of its technologies, and the nearest in
 * meaning. Only when that finds nothing is the model asked, and then only to pick from the nearest candidates;
 * a slug it makes up is not a skill and is said to [diagnostics].
 */
class Tagger(
    private val skills: Skills,
    private val call: StructuredCall,
    private val tags: SkillTags,
    private val diagnostics: Diagnostics,
    private val candidates: Int = CANDIDATES,
    private val fallbackLimit: Int = FALLBACK_LIMIT,
) {
    suspend fun tag(action: Action, technologies: List<Technology>, embedding: Embedding): List<Tag> {
        val catalog = skills.all()
        val nearest = skills.nearest(embedding, candidates)
        val local = tags.pick(nearest, catalog, technologies)
        if (local.isNotEmpty()) return local
        return fromModel(action, nearest, catalog)
    }

    private suspend fun fromModel(
        action: Action,
        nearest: List<SkillMatch>,
        catalog: List<Skill>,
    ): List<Tag> {
        val known = catalog.associate { it.slug() to it.name() }
        val offered = nearest.map { it.slug() }.filter { it in known }
        if (offered.isEmpty()) return emptyList()
        val suffix =
            "Action: ${action.embeddingText()}\nCandidates (slug: name):\n" +
                offered.joinToString("\n") { "${it.value}: ${known.getValue(it)}" }
        val wire = call.run("tags", suffix) { Wire.decode<TagsWire>(it) }
        return wire.skills
            .map { SkillSlug(it) }
            .filter { slug ->
                (slug in offered).also { if (!it) diagnostics.unmapped("skill", slug.value) }
            }
            .distinct()
            .take(fallbackLimit)
            .map { slug -> catalog.first { it.slug() == slug }.tagWith(FALLBACK_WEIGHT) }
    }

    companion object {
        const val CANDIDATES = 30
        const val FALLBACK_LIMIT = 3

        /** A skill the model picked, not one the meaning of the words pointed to: enough to count, not to outrank. */
        const val FALLBACK_WEIGHT = 0.55
    }
}
