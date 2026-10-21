package tallyvane.knowledge.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.uuid.Uuid

private fun gapOf(question: Question): Gap {
    var found: Gap? = null
    question.writeTo(
        object : Question.Record {
            override fun question(gap: Gap, text: String) {
                found = gap
            }
        },
    )
    return found!!
}

private val onProject = Cursor.atStart().inProject(CompanyId(Uuid.random()), RoleId(Uuid.random()), ProjectId(Uuid.random()))
private val interview = Interview()

private fun next(facts: ProjectFacts, asked: Set<Gap> = emptySet(), cursor: Cursor = onProject): Gap =
    gapOf(interview.next(cursor, facts, asked))

class InterviewSpec :
    StringSpec(
        {
            "the first question, with nothing known, is what the person is working on" {
                gapOf(interview.opening()) shouldBe Gap.OPENING
                gapOf(interview.next(Cursor.atStart(), null, emptySet())) shouldBe Gap.OPENING
            }

            "a role without a title is asked its title first" {
                next(facts(title = null, period = RolePeriod(null, null, null), projectTitle = null, problem = null)) shouldBe
                    Gap.ROLE_TITLE
            }

            "the dates are asked when the start is unknown" {
                next(facts(period = RolePeriod(null, null, null))) shouldBe Gap.ROLE_DATES
            }

            "the dates are asked when the start is known but not whether the role is over" {
                next(facts(period = RolePeriod("2022", null, null))) shouldBe Gap.ROLE_DATES
            }

            "the dates are not asked once the start and the end are known" {
                next(facts(period = RolePeriod("2019", "2022", null))) shouldBe Gap.NO_DECISION
            }

            "the dates are not asked once the start is known and the role is current" {
                next(facts(period = RolePeriod("2022", null, true))) shouldBe Gap.NO_DECISION
            }

            "the gaps come in the order of the table" {
                val empty = facts(title = null, period = RolePeriod(null, null, null), projectTitle = null, problem = null)
                val asked = mutableSetOf<Gap>()
                val order = mutableListOf<Gap>()
                repeat(8) {
                    val gap = next(empty, asked)
                    order += gap
                    asked += gap
                }
                order.take(7) shouldBe
                    listOf(
                        Gap.ROLE_TITLE,
                        Gap.ROLE_DATES,
                        Gap.PROJECT_TITLE,
                        Gap.PROJECT_PROBLEM,
                        Gap.NO_DECISION,
                        Gap.NO_COLLAB,
                        Gap.NO_METRIC,
                    )
                order.last() shouldBe Gap.MORE_IN_PROJECT
            }

            "a project without a title is asked what it was called, then the problem" {
                next(facts(projectTitle = null, problem = null)) shouldBe Gap.PROJECT_TITLE
                next(facts(projectTitle = "Cache", problem = null)) shouldBe Gap.PROJECT_PROBLEM
            }

            "a decision is asked about unless some action was a decision" {
                next(facts()) shouldBe Gap.NO_DECISION
                next(facts(actions = listOf(actionFacts(action(kind = ActionKind.DECIDED))))) shouldBe Gap.NO_COLLAB
            }

            "colleagues are asked about unless some are known" {
                val decided = listOf(actionFacts(action(kind = ActionKind.DECIDED)))
                next(facts(actions = decided)) shouldBe Gap.NO_COLLAB
                next(facts(actions = decided, collaborators = listOf(Collaborator("DBA")))) shouldBe Gap.NO_METRIC
            }

            "a result without a number is asked for a number, and a result with one is not" {
                val decided = listOf(actionFacts(action(kind = ActionKind.DECIDED)))
                val dba = listOf(Collaborator("DBA"))
                next(facts(actions = decided, collaborators = dba, outcomes = listOf(outcome(value = null, unit = null)))) shouldBe
                    Gap.NO_METRIC
                next(facts(actions = decided, collaborators = dba, outcomes = listOf(outcome()))) shouldBe Gap.MORE_IN_PROJECT
            }

            "a gap that was asked is not asked again for the same target" {
                next(facts(), setOf(Gap.NO_DECISION)) shouldBe Gap.NO_COLLAB
            }

            "anything else in the project is asked again however often it was asked" {
                val complete =
                    facts(
                        actions = listOf(actionFacts(action(kind = ActionKind.DECIDED))),
                        collaborators = listOf(Collaborator("DBA")),
                        outcomes = listOf(outcome()),
                    )
                next(complete, setOf(Gap.MORE_IN_PROJECT)) shouldBe Gap.MORE_IN_PROJECT
            }

            "after a project the question is whether the role had others" {
                gapOf(interview.next(onProject.afterProject(), facts(), emptySet())) shouldBe Gap.MORE_IN_ROLE
            }

            "after a role the question is what came before it" {
                gapOf(interview.next(onProject.afterRole(), facts(), emptySet())) shouldBe Gap.PREVIOUS_ROLE
            }

            "when it is finished there is nothing to ask" {
                gapOf(interview.next(onProject.finished(), facts(), emptySet())) shouldBe Gap.DONE
            }

            "questions name the company, the project and the approach" {
                val withRedis = listOf(actionFacts(technologies = listOf(Technology("Redis"))))
                interview.next(onProject, facts(company = "Acme", actions = withRedis), emptySet()).text() shouldBe
                    "What alternatives did you consider, and why did you choose Redis?"
                interview.next(onProject.afterProject(), facts(company = "Acme"), emptySet()).text() shouldBe
                    "Any other project at Acme?"
                interview.next(onProject.afterRole(), facts(company = "Acme"), emptySet()).text() shouldBe
                    "What did you do before Acme?"
            }

            "the approach falls back to the design, then to plain words" {
                val designed = listOf(actionFacts(action(kind = ActionKind.DESIGNED, what = "a read-through cache")))
                interview.next(onProject, facts(actions = designed), emptySet()).text() shouldBe
                    "What alternatives did you consider, and why did you choose a read-through cache?"
                interview.next(onProject, facts(), emptySet()).text() shouldBe
                    "What alternatives did you consider, and why did you choose this approach?"
            }

            "where the answer goes follows from the question that was asked" {
                Gap.OPENING.placementOfAnswer() shouldBe Placement.NEW_ROLE
                Gap.PREVIOUS_ROLE.placementOfAnswer() shouldBe Placement.NEW_ROLE
                Gap.MORE_IN_ROLE.placementOfAnswer() shouldBe Placement.NEW_PROJECT_IN_ROLE
                Gap.MORE_IN_PROJECT.placementOfAnswer() shouldBe Placement.SAME_PROJECT
                Gap.ROLE_DATES.placementOfAnswer() shouldBe Placement.SAME_PROJECT
            }

            "only some questions can be answered with nothing more" {
                Gap.MORE_IN_PROJECT.acceptsNothingMore() shouldBe true
                Gap.NO_METRIC.acceptsNothingMore() shouldBe true
                Gap.ROLE_TITLE.acceptsNothingMore() shouldBe false
                Gap.OPENING.acceptsNothingMore() shouldBe false
            }
        },
    )
