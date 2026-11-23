package tallyvane.knowledge.application

import tallyvane.knowledge.domain.GapReport
import tallyvane.knowledge.domain.Portfolio
import tallyvane.knowledge.domain.Requirement
import tallyvane.knowledge.domain.RolePeriod
import tallyvane.knowledge.domain.SkillSlug
import tallyvane.knowledge.domain.VacancyId
import tallyvane.knowledge.domain.port.KnowledgeGraph
import tallyvane.knowledge.domain.port.Skills
import tallyvane.knowledge.domain.port.Vacancies
import java.time.LocalDate

/** How well the person's actions answer a vacancy: scores by role and project, and the gaps. No model is involved. */
class MatchAndScore(
    private val graph: KnowledgeGraph,
    private val vacancies: Vacancies,
    private val skills: Skills,
    private val today: () -> LocalDate,
) {
    suspend fun match(id: VacancyId): MatchView {
        val vacancy = vacancies.find(id)
        val portfolio = graph.portfolio()
        val names = skills.all().associate { it.slug() to it.name() }
        val roles = RoleScores()
        vacancy.rankedIn(portfolio, today(), onlyWithBullets = false, report = roles)
        val gaps = Gaps(names)
        vacancy.gapsIn(portfolio).writeTo(gaps)
        return MatchView(roles.views(), gaps.views(), VacancyReading.of(vacancy).unmapped)
    }

    private class RoleScores : Portfolio.Report {
        private class Role(val company: String, val title: String?, val period: String, val score: Double) {
            val projects = mutableListOf<ProjectScoreView>()
        }

        private val roles = mutableListOf<Role>()

        override fun role(company: String, title: String?, period: RolePeriod, score: Double) {
            roles += Role(company, title, period.label(), score)
        }

        override fun project(title: String?, score: Double) {
            roles.last().projects += ProjectScoreView(title, score)
        }

        override fun bullet(bullet: tallyvane.knowledge.domain.Bullet, score: Double) = Unit

        fun views(): List<RoleScoreView> = roles.map { RoleScoreView(it.company, it.title, it.period, it.score, it.projects) }
    }

    private class Gaps(private val names: Map<SkillSlug, String>) : GapReport.Record {
        private val found = mutableListOf<GapView>()

        override fun gap(requirement: Requirement, bestWeight: Double, missing: Boolean) {
            requirement.writeTo(
                object : Requirement.Record {
                    override fun requirement(skill: SkillSlug, importance: Int, must: Boolean) {
                        found += GapView(skill.value, names[skill] ?: skill.value, must, importance, bestWeight, missing)
                    }
                },
            )
        }

        fun views(): List<GapView> = found
    }
}
