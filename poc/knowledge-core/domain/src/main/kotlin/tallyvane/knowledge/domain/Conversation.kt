package tallyvane.knowledge.domain

enum class Scope { ROLE, PROJECT, CONVERSATION }

/** Where the new things in an answer go. The question asked decides this, not the model. */
enum class Placement { SAME_PROJECT, NEW_PROJECT_IN_ROLE, NEW_ROLE }

/** Where the interview is: working on a project, or having just finished one, or a role, or everything. */
enum class Stage { PROJECT, AFTER_PROJECT, AFTER_ROLE, DONE }

/** What the interview is about right now: a company, a role there, a project in it, and how far along it is. */
class Cursor private constructor(
    private val company: CompanyId?,
    private val role: RoleId?,
    private val project: ProjectId?,
    private val stage: Stage,
) {
    fun isAtStart(): Boolean = project == null

    fun stage(): Stage = stage

    fun inProject(company: CompanyId, role: RoleId, project: ProjectId): Cursor =
        Cursor(company, role, project, Stage.PROJECT)

    /** The project is closed; the next thing to ask about is whether the role had others. */
    fun afterProject(): Cursor = Cursor(company, role, project, Stage.AFTER_PROJECT)

    /** The role has no more projects; the next thing to ask about is what came before it. */
    fun afterRole(): Cursor = Cursor(company, role, project, Stage.AFTER_ROLE)

    fun finished(): Cursor = Cursor(company, role, project, Stage.DONE)

    fun writeTo(record: Record) = record.cursor(company, role, project, stage)

    interface Record {
        fun cursor(company: CompanyId?, role: RoleId?, project: ProjectId?, stage: Stage)
    }

    companion object {
        fun atStart(): Cursor = Cursor(null, null, null, Stage.PROJECT)

        fun restore(company: CompanyId?, role: RoleId?, project: ProjectId?, stage: Stage): Cursor =
            Cursor(company, role, project, stage)
    }
}

/** A question put to the person, and the gap in what is known that it is meant to close. */
class Question(private val gap: Gap, private val text: String) {
    fun gap(): Gap = gap

    fun writeTo(record: Record) = record.question(gap, text)

    interface Record {
        fun question(gap: Gap, text: String)
    }
}

/**
 * The gaps in what is known about a project, in the order they are asked about, each with the question that
 * closes it. The first four and the last three are not about what is known but about where the interview is.
 */
enum class Gap(
    private val scope: Scope,
    private val template: String,
    private val opens: (ProjectFacts) -> Boolean = { false },
) {
    OPENING(Scope.CONVERSATION, "What are you working on right now?"),
    ROLE_TITLE(Scope.ROLE, "What was your role/title at {company}?", { it.lacksRoleTitle() }),
    ROLE_DATES(Scope.ROLE, "When did you start at {company}? Are you still there?", { it.lacksRoleDates() }),
    PROJECT_TITLE(Scope.PROJECT, "What was this project or system called?", { it.lacksProjectTitle() }),
    PROJECT_PROBLEM(Scope.PROJECT, "What problem were you solving?", { it.lacksProblem() }),
    NO_DECISION(
        Scope.PROJECT,
        "What alternatives did you consider, and why did you choose {approach}?",
        { !it.hasDecision() },
    ),
    NO_COLLAB(Scope.PROJECT, "Who did you work with on this: other teams, roles?", { !it.hasCollaborators() }),
    NO_METRIC(
        Scope.PROJECT,
        "Can you put a number on the result? If not, just say there's no metric.",
        { !it.hasMetric() },
    ),
    MORE_IN_PROJECT(Scope.PROJECT, "Anything else you did on {project}?", { true }),
    MORE_IN_ROLE(Scope.CONVERSATION, "Any other project at {company}?"),
    PREVIOUS_ROLE(Scope.CONVERSATION, "What did you do before {company}?"),
    DONE(Scope.CONVERSATION, "That is all I need. Paste a vacancy on the right to get a résumé."),
    ;

    fun scope(): Scope = scope

    fun opensIn(facts: ProjectFacts): Boolean = opens(facts)

    fun questionFor(facts: ProjectFacts?): Question = Question(this, facts?.fill(template) ?: template)

    /** A gap about the project or role is asked once: an answer that does not fill it does not make it open again. */
    fun isAskedOnce(): Boolean = scope != Scope.CONVERSATION && this != MORE_IN_PROJECT

    fun placementOfAnswer(): Placement =
        when (this) {
            OPENING, PREVIOUS_ROLE, DONE -> Placement.NEW_ROLE
            MORE_IN_ROLE -> Placement.NEW_PROJECT_IN_ROLE
            else -> Placement.SAME_PROJECT
        }

    /** Whether "no" is a real answer to this question. */
    fun acceptsNothingMore(): Boolean =
        when (this) {
            NO_DECISION, NO_COLLAB, NO_METRIC, MORE_IN_PROJECT, MORE_IN_ROLE, PREVIOUS_ROLE -> true
            else -> false
        }
}

/** Whether an answer is a person saying there is nothing more. Short on purpose: "no problems with it, and I also..." is not. */
class NothingMore {
    fun isSaidBy(answer: String): Boolean = answer.trim().split(Regex("""\s+""")).size <= MAX_WORDS && keyword.containsMatchIn(answer)

    private companion object {
        const val MAX_WORDS = 5
        val keyword =
            Regex(
                """(?<![\p{L}\p{N}])(нет|ничего|no|nothing|that's all|thats all)(?![\p{L}\p{N}])""",
                RegexOption.IGNORE_CASE,
            )
    }
}

/**
 * The next question: where the interview is decides what it is about, and for a project the first gap in
 * what is known, in the order of [Gap], that has not been asked about already.
 */
class Interview {
    fun opening(): Question = Gap.OPENING.questionFor(null)

    fun next(cursor: Cursor, facts: ProjectFacts?, asked: Set<Gap>): Question {
        if (cursor.isAtStart() || facts == null) return opening()
        return when (cursor.stage()) {
            Stage.DONE -> Gap.DONE.questionFor(facts)
            Stage.AFTER_ROLE -> Gap.PREVIOUS_ROLE.questionFor(facts)
            Stage.AFTER_PROJECT -> Gap.MORE_IN_ROLE.questionFor(facts)
            Stage.PROJECT -> {
                val gap = Gap.entries.first { it.scope() != Scope.CONVERSATION && it.opensIn(facts) && !(it.isAskedOnce() && it in asked) }
                gap.questionFor(facts)
            }
        }
    }
}
