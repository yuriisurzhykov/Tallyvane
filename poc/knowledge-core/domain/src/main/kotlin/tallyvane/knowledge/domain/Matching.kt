package tallyvane.knowledge.domain

import java.time.LocalDate

/** One thing a vacancy asks for: a skill, how much it matters from 1 to 3, and whether it is a must. */
class Requirement(private val skill: SkillSlug, private val importance: Int, private val must: Boolean) {
    init {
        require(importance in 1..MAX_IMPORTANCE) { "Importance is 1 to $MAX_IMPORTANCE, not $importance." }
    }

    fun isMust(): Boolean = must

    fun isFor(other: SkillSlug): Boolean = skill == other

    /** What an action tagged with [weights] is worth towards this requirement. */
    fun worthOf(weights: Map<SkillSlug, Double>): Double = weightIn(weights) * importance

    /** How strongly [weights] say the skill is there, 0 if they do not mention it. */
    fun weightIn(weights: Map<SkillSlug, Double>): Double = weights[skill] ?: 0.0

    fun writeTo(record: Record) = record.requirement(skill, importance, must)

    interface Record {
        fun requirement(skill: SkillSlug, importance: Int, must: Boolean)
    }

    companion object {
        const val MAX_IMPORTANCE = 3
    }
}

/** What a vacancy asks for, and the asks that matched none of the skills the product knows. */
class Vacancy(
    private val title: String,
    private val requirements: List<Requirement>,
    private val unmapped: List<String>,
) {
    fun rankedIn(portfolio: Portfolio, today: LocalDate, onlyWithBullets: Boolean, report: Portfolio.Report) =
        portfolio.rankTo(report, requirements, today, onlyWithBullets)

    fun gapsIn(portfolio: Portfolio): GapReport = portfolio.gaps(requirements)

    /** Whether the vacancy asks for [skill]. */
    fun asksFor(skill: SkillSlug): Boolean = requirements.any { it.isFor(skill) }

    fun writeTo(record: Record) {
        record.vacancy(title, unmapped)
        requirements.forEach { it.writeTo(record) }
    }

    interface Record : Requirement.Record {
        fun vacancy(title: String, unmapped: List<String>)
    }
}

/** For each thing a vacancy asks for, how well the person's actions answer it. */
class GapReport(private val entries: List<Entry>) {
    class Entry(val requirement: Requirement, val bestWeight: Double, val missing: Boolean)

    fun writeTo(record: Record) =
        entries.forEach { record.gap(it.requirement, it.bestWeight, it.missing) }

    interface Record {
        fun gap(requirement: Requirement, bestWeight: Double, missing: Boolean)
    }
}

/** One action as scoring sees it: how much of which skill it shows, and whether a number came of it. */
class ActionEntry(
    private val id: ActionId,
    private val weights: Map<SkillSlug, Double>,
    private val metricLinked: Boolean,
) {
    /** The sum over the requirements of weight x importance, x the role's [recency], and a fifth more with a metric. */
    fun scoreFor(requirements: List<Requirement>, recency: Double): Double {
        val base = requirements.sumOf { it.worthOf(weights) } * recency
        return if (metricLinked) base * METRIC_BONUS else base
    }

    fun bestWeightFor(requirement: Requirement): Double = requirement.weightIn(weights)

    fun isOneOf(ids: List<ActionId>): Boolean = id in ids

    fun strongSkillsTo(record: StrongSkills, minimum: Double) =
        weights.filterValues { it >= minimum }.forEach { (skill, weight) -> record.skill(skill, weight) }

    fun interface StrongSkills {
        fun skill(skill: SkillSlug, weight: Double)
    }

    companion object {
        const val METRIC_BONUS = 1.2
    }
}

/** A project's actions and the bullets written for them once it was closed. */
class ProjectEntry(
    private val title: String?,
    private val bullets: List<Bullet>,
    private val actions: List<ActionEntry>,
) {
    fun hasBullets(): Boolean = bullets.isNotEmpty()

    fun scoreFor(requirements: List<Requirement>, recency: Double): Double =
        actions.sumOf { it.scoreFor(requirements, recency) }

    fun writeTo(report: Portfolio.Report, requirements: List<Requirement>, recency: Double) {
        report.project(title, scoreFor(requirements, recency))
        bullets
            .map { bullet -> bullet to scoreOf(bullet, requirements, recency) }
            .sortedByDescending { it.second }
            .forEach { (bullet, score) -> report.bullet(bullet, score) }
    }

    fun bestWeightFor(requirement: Requirement): Double = actions.maxOfOrNull { it.bestWeightFor(requirement) } ?: 0.0

    fun strongSkillsTo(record: ActionEntry.StrongSkills, minimum: Double) =
        actions.forEach { it.strongSkillsTo(record, minimum) }

    private fun scoreOf(bullet: Bullet, requirements: List<Requirement>, recency: Double): Double {
        var covered = emptyList<ActionId>()
        bullet.writeTo(
            object : Bullet.Record {
                override fun bullet(text: String, covers: List<ActionId>) {
                    covered = covers
                }
            },
        )
        return actions.filter { it.isOneOf(covered) }.sumOf { it.scoreFor(requirements, recency) }
    }
}

/** A role, when it was held, and its projects. */
class RoleEntry(
    private val company: String,
    private val title: String?,
    private val period: RolePeriod,
    private val projects: List<ProjectEntry>,
) {
    fun recencyRank(): Int = period.recencyRank()

    fun writeTo(
        report: Portfolio.Report,
        requirements: List<Requirement>,
        today: LocalDate,
        onlyWithBullets: Boolean,
    ) {
        val recency = period.recencyOn(today)
        val shown =
            projects
                .filter { !onlyWithBullets || it.hasBullets() }
                .sortedByDescending { it.scoreFor(requirements, recency) }
                .take(Portfolio.PROJECTS_PER_ROLE)
        report.role(company, title, period, shown.sumOf { it.scoreFor(requirements, recency) })
        shown.forEach { it.writeTo(report, requirements, recency) }
    }

    fun bestWeightFor(requirement: Requirement): Double = projects.maxOfOrNull { it.bestWeightFor(requirement) } ?: 0.0

    fun strongSkillsTo(record: ActionEntry.StrongSkills, minimum: Double) =
        projects.forEach { it.strongSkillsTo(record, minimum) }
}

/** Everything a person has done, as the ranking against a vacancy reads it. */
class Portfolio(private val roles: List<RoleEntry>) {
    interface Report {
        fun role(company: String, title: String?, period: RolePeriod, score: Double)

        fun project(title: String?, score: Double)

        fun bullet(bullet: Bullet, score: Double)
    }

    /**
     * Roles, most recent first; in each, the best [PROJECTS_PER_ROLE] projects by score; in each, its bullets
     * by the score of the actions they speak for.
     */
    fun rankTo(report: Report, requirements: List<Requirement>, today: LocalDate, onlyWithBullets: Boolean) =
        roles.sortedByDescending { it.recencyRank() }.forEach { it.writeTo(report, requirements, today, onlyWithBullets) }

    /** For each requirement, the best any action does against it; a must below [MISSING_BELOW] is missing. */
    fun gaps(requirements: List<Requirement>): GapReport =
        GapReport(
            requirements.map { requirement ->
                val best = roles.maxOfOrNull { it.bestWeightFor(requirement) } ?: 0.0
                GapReport.Entry(requirement, best, requirement.isMust() && best < MISSING_BELOW)
            },
        )

    /** The skills some action shows at [minimum] weight or more, each with the highest weight it was shown at. */
    fun strongSkillsTo(record: ActionEntry.StrongSkills, minimum: Double = STRONG_FROM) {
        val best = mutableMapOf<SkillSlug, Double>()
        roles.forEach { role ->
            role.strongSkillsTo({ skill, weight -> best.merge(skill, weight, ::maxOf) }, minimum)
        }
        best.forEach { (skill, weight) -> record.skill(skill, weight) }
    }

    companion object {
        const val PROJECTS_PER_ROLE = 3
        const val MISSING_BELOW = 0.5
        const val STRONG_FROM = 0.6
    }
}
