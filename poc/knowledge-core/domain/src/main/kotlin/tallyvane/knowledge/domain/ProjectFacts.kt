package tallyvane.knowledge.domain

/** What is known of a role: its title and when it was held. */
class RoleFacts(private val title: String?, private val period: RolePeriod) {
    fun lacksTitle(): Boolean = title == null

    fun lacksDates(): Boolean = period.lacksStart() || period.lacksEndOrCurrent()

    fun writeTo(record: Record) = record.role(title, period)

    interface Record {
        fun role(title: String?, period: RolePeriod)
    }
}

/** What is known of a project: its title and the problem it solved. */
class ProjectDetails(private val title: String?, private val problem: String?) {
    fun lacksTitle(): Boolean = title == null

    fun lacksProblem(): Boolean = problem == null

    fun nameOr(fallback: String): String = title ?: fallback

    fun writeTo(record: Record) = record.project(title, problem)

    interface Record {
        fun project(title: String?, problem: String?)
    }
}

/** An action together with the technologies it used. */
class ActionFacts(
    private val id: ActionId,
    private val action: Action,
    private val technologies: List<Technology>,
) {
    fun isOfKind(kind: ActionKind): Boolean = action.isOfKind(kind)

    fun isOneOf(ids: List<ActionId>): Boolean = id in ids

    fun usesAnyTechnology(): Boolean = technologies.isNotEmpty()

    fun firstTechnologyOr(fallback: String): String = technologies.firstOrNull()?.toString() ?: fallback

    fun describesWhat(): String = action.describesWhat()

    fun usesTechnologyNamed(name: String): Boolean = technologies.any { it.isNamed(name) }

    fun findingStates(figure: Double): Boolean = action.findingStates(figure)

    /** Lower is more substantial: what was made or improved before what was looked into or talked about. */
    fun priority(): Int = PRIORITY.indexOf(kindOf()).let { if (it < 0) PRIORITY.size else it }

    private fun kindOf(): ActionKind = ActionKind.entries.first { action.isOfKind(it) }

    fun asBullet(result: OutcomeFacts?): Bullet {
        val using = if (technologies.isEmpty()) "" else " using " + technologies.joinToString(", ")
        val ending = result?.let { ", " + it.phrase() } ?: ""
        return Bullet("${action.beginsSentence()} ${action.describesWhat()}$using$ending.", listOf(id))
    }

    fun writeTo(record: Record) {
        action.writeTo(
            object : Action.Record {
                override fun action(kind: ActionKind, verb: String, what: String, finding: String?, quote: Quote) =
                    record.action(id, kind, verb, what, finding, technologies)
            },
        )
    }

    private companion object {
        val PRIORITY =
            listOf(
                ActionKind.IMPROVED, ActionKind.BUILT, ActionKind.DESIGNED, ActionKind.LED, ActionKind.DECIDED,
                ActionKind.MENTORED, ActionKind.COMMUNICATED, ActionKind.INVESTIGATED,
            )
    }

    interface Record {
        fun action(
            id: ActionId,
            kind: ActionKind,
            verb: String,
            what: String,
            finding: String?,
            technologies: List<Technology>,
        )
    }
}

/** An outcome together with the actions the person said produced it; it may be linked to none. */
class OutcomeFacts(
    private val outcome: Outcome,
    private val producedBy: List<ActionId>,
) {
    fun hasMetric(): Boolean = outcome.hasMetric()

    fun statesFigure(figure: Double): Boolean = outcome.statesFigure(figure)

    fun isProducedBy(fact: ActionFacts): Boolean = fact.isOneOf(producedBy)

    fun phrase(): String = outcome.phrase()

    fun writeTo(record: Outcome.Record) = outcome.writeTo(record)
}

/**
 * Everything known about one project at the moment: its company and role, what it was, what was done,
 * with whom, and what came of it. The questions asked of the person come from what is missing here; the
 * bullets of the résumé are written from what is here and from nothing else.
 */
class ProjectFacts(
    private val company: String,
    private val role: RoleFacts,
    private val project: ProjectDetails,
    private val actions: List<ActionFacts>,
    private val collaborators: List<Collaborator>,
    private val outcomes: List<OutcomeFacts>,
) {
    fun lacksRoleTitle(): Boolean = role.lacksTitle()

    fun lacksRoleDates(): Boolean = role.lacksDates()

    fun lacksProjectTitle(): Boolean = project.lacksTitle()

    fun lacksProblem(): Boolean = project.lacksProblem()

    fun hasDecision(): Boolean = actions.any { it.isOfKind(ActionKind.DECIDED) }

    fun hasCollaborators(): Boolean = collaborators.isNotEmpty()

    fun hasMetric(): Boolean = outcomes.any { it.hasMetric() }

    fun hasActions(): Boolean = actions.isNotEmpty()

    /** [template] with `{company}`, `{project}` and `{approach}` filled in from what is known. */
    fun fill(template: String): String =
        template
            .replace("{company}", company)
            .replace("{project}", project.nameOr("this project"))
            .replace("{approach}", approach())

    /** The technology the project turned on, or else what the design was, or else the plain words. */
    private fun approach(): String =
        actions.firstOrNull { it.usesAnyTechnology() }?.firstTechnologyOr("this approach")
            ?: actions.firstOrNull { it.isOfKind(ActionKind.DESIGNED) }?.describesWhat()
            ?: "this approach"

    /** The names a bullet may use: the company, the technologies of the project, the roles it worked with. */
    fun allowsName(word: String): Boolean =
        company.equals(word, ignoreCase = true) ||
            collaborators.any { it.isInRole(word) } ||
            actions.any { fact -> fact.usesTechnologyNamed(word) }

    /** Whether the person said [figure] about this project, as a result or as something they found out. */
    fun statesFigure(figure: Double): Boolean =
        outcomes.any { it.statesFigure(figure) } || actions.any { it.findingStates(figure) }

    /**
     * What a bullet can say with no model: up to two of the actions, the most substantial first, each as
     * "Verb what using Tech". A result goes only onto an action the person said produced it, and onto the
     * first such action only, so no action is credited with a result that was not its own.
     */
    fun fallbackBullets(): List<Bullet> {
        val used = mutableSetOf<OutcomeFacts>()
        return actions
            .sortedBy { it.priority() }
            .take(FALLBACK_BULLETS)
            .map { fact ->
                val result = resultOf(fact, used)
                result?.let(used::add)
                fact.asBullet(result)
            }
    }

    private fun resultOf(fact: ActionFacts, used: Set<OutcomeFacts>): OutcomeFacts? {
        val mine = outcomes.filter { it.isProducedBy(fact) && it !in used }
        return mine.firstOrNull { it.hasMetric() } ?: mine.firstOrNull()
    }

    fun writeTo(record: Record) {
        record.context(company, role, project)
        actions.forEach { it.writeTo(record) }
        collaborators.forEach { it.writeTo(record) }
        outcomes.forEach { it.writeTo(record) }
    }

    private companion object {
        const val FALLBACK_BULLETS = 2
    }

    interface Record :
        ActionFacts.Record,
        Collaborator.Record,
        Outcome.Record {
        fun context(company: String, role: RoleFacts, project: ProjectDetails)
    }
}
