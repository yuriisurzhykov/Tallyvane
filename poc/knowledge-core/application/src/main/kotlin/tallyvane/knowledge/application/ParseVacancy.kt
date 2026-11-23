package tallyvane.knowledge.application

import tallyvane.knowledge.domain.Requirement
import tallyvane.knowledge.domain.SkillSlug
import tallyvane.knowledge.domain.Vacancy
import tallyvane.knowledge.domain.port.Diagnostics
import tallyvane.knowledge.domain.port.Skills
import tallyvane.knowledge.domain.port.Vacancies

/**
 * Reads a vacancy into the skills the product knows. The model is shown the whole list and may only choose
 * from it: a requirement whose skill is not in the list is moved to `unmapped` and said to [diagnostics],
 * never turned into a skill.
 */
class ParseVacancy(
    private val call: StructuredCall,
    private val skills: Skills,
    private val vacancies: Vacancies,
    private val diagnostics: Diagnostics,
) {
    suspend fun parse(text: String): VacancyView {
        if (text.isBlank()) throw AnswerNotUsable("The vacancy is empty.")
        val catalog = skills.all()
        val names = catalog.associate { it.slug() to it.name() }
        val listing = catalog.joinToString("\n") { "${it.slug().value}: ${it.name()}" }
        val wire =
            call.run("vacancy", "Vacancy:\n<<<\n${text.trim()}\n>>>", "\n\nSkills (slug: name):\n$listing") {
                Wire.decode<VacancyWire>(it)
            }
        val unmapped = wire.unmapped.filter { it.isNotBlank() }.toMutableList()
        val importance = linkedMapOf<SkillSlug, Int>()
        val must = mutableSetOf<SkillSlug>()
        for (req in wire.reqs) {
            val slug = SkillSlug(req.skill.trim())
            if (slug !in names) {
                diagnostics.unmapped("skill", req.skill)
                unmapped += req.skill
                continue
            }
            importance.merge(slug, req.importance.coerceIn(1, Requirement.MAX_IMPORTANCE), ::maxOf)
            if (req.must) must += slug
        }
        val vacancy =
            Vacancy(
                wire.title.trim(),
                importance.map { (slug, level) -> Requirement(slug, level, slug in must) },
                unmapped.distinct(),
            )
        return VacancyReading.of(vacancy).view(vacancies.save(vacancy), names)
    }
}

/** A vacancy read back out of the domain object: its title, what it asks for and what could not be mapped. */
internal class VacancyReading private constructor() : Vacancy.Record {
    var title = ""
        private set
    var unmapped: List<String> = emptyList()
        private set
    private val asks = mutableListOf<Triple<SkillSlug, Int, Boolean>>()

    override fun vacancy(title: String, unmapped: List<String>) {
        this.title = title
        this.unmapped = unmapped
    }

    override fun requirement(skill: SkillSlug, importance: Int, must: Boolean) {
        asks += Triple(skill, importance, must)
    }

    fun requirements(names: Map<SkillSlug, String>): List<RequirementView> =
        asks.map { (skill, importance, must) -> RequirementView(skill.value, names[skill] ?: skill.value, importance, must) }

    fun view(id: tallyvane.knowledge.domain.VacancyId, names: Map<SkillSlug, String>): VacancyView =
        VacancyView(id.uuid.toString(), title, requirements(names), unmapped)

    companion object {
        fun of(vacancy: Vacancy): VacancyReading = VacancyReading().also { vacancy.writeTo(it) }
    }
}
