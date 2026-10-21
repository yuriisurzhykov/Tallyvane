package tallyvane.knowledge.application

import tallyvane.knowledge.domain.ActionId
import tallyvane.knowledge.domain.Bullet
import tallyvane.knowledge.domain.Portfolio
import tallyvane.knowledge.domain.RolePeriod
import tallyvane.knowledge.domain.SkillSlug
import tallyvane.knowledge.domain.VacancyId
import tallyvane.knowledge.domain.ActionEntry
import tallyvane.knowledge.domain.port.KnowledgeGraph
import tallyvane.knowledge.domain.port.Skills
import tallyvane.knowledge.domain.port.Vacancies
import java.time.LocalDate

/**
 * The résumé for a vacancy, assembled from a template and the stored bullets. No model is involved, so
 * there is nothing in it that was not already checked when its project was closed. Every bullet comes with
 * the words of the person that it was made from.
 */
class BuildResume(
    private val graph: KnowledgeGraph,
    private val vacancies: Vacancies,
    private val skills: Skills,
    private val today: () -> LocalDate,
    private val candidateName: String = "Candidate Name",
) {
    suspend fun build(id: VacancyId): ResumeView {
        val vacancy = vacancies.find(id)
        val portfolio = graph.portfolio()
        val names = skills.all().associate { it.slug() to it.name() }
        val layout = Layout()
        vacancy.rankedIn(portfolio, today(), onlyWithBullets = true, report = layout)
        val strong = linkedMapOf<SkillSlug, Double>()
        portfolio.strongSkillsTo(ActionEntry.StrongSkills { skill, weight -> strong[skill] = weight })
        val title = VacancyReading.of(vacancy).title

        val views = mutableListOf<BulletView>()
        val markdown =
            buildString {
                appendLine("# $candidateName")
                if (title.isNotBlank()) appendLine("**Target:** $title")
                appendLine()
                appendLine("## Experience")
                for (role in layout.roles.filter { r -> r.projects.any { it.bullets.isNotEmpty() } }) {
                    appendLine()
                    appendLine("### ${role.company}${role.title?.let { " — $it" } ?: ""}${role.period.takeIf { it.isNotEmpty() }?.let { " ($it)" } ?: ""}")
                    for (bullet in role.projects.flatMap { it.bullets }) {
                        val reading = BulletReading.of(bullet)
                        appendLine("- ${reading.text}")
                        views += BulletView(reading.text, graph.quotesOfActions(reading.covers))
                    }
                }
                val skillNames =
                    strong.entries
                        .sortedWith(compareByDescending<Map.Entry<SkillSlug, Double>> { vacancy.asksFor(it.key) }.thenByDescending { it.value })
                        .map { names[it.key] ?: it.key.value }
                if (skillNames.isNotEmpty()) {
                    appendLine()
                    appendLine("## Skills")
                    appendLine(skillNames.joinToString(", "))
                }
            }.trimEnd()
        return ResumeView(markdown, views)
    }

    private class Layout : Portfolio.Report {
        class Project(val bullets: MutableList<Bullet> = mutableListOf())

        class Role(val company: String, val title: String?, val period: String) {
            val projects = mutableListOf<Project>()
        }

        val roles = mutableListOf<Role>()

        override fun role(company: String, title: String?, period: RolePeriod, score: Double) {
            roles += Role(company, title, period.label())
        }

        override fun project(title: String?, score: Double) {
            roles.last().projects += Project()
        }

        override fun bullet(bullet: Bullet, score: Double) {
            roles.last().projects.last().bullets += bullet
        }
    }

    private class BulletReading private constructor() : Bullet.Record {
        var text = ""
            private set
        var covers: List<ActionId> = emptyList()
            private set

        override fun bullet(text: String, covers: List<ActionId>) {
            this.text = text
            this.covers = covers
        }

        companion object {
            fun of(bullet: Bullet): BulletReading = BulletReading().also { bullet.writeTo(it) }
        }
    }
}
