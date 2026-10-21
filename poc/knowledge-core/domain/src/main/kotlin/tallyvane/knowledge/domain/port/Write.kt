package tallyvane.knowledge.domain.port

import tallyvane.knowledge.domain.Action
import tallyvane.knowledge.domain.Collaborator
import tallyvane.knowledge.domain.CompanyName
import tallyvane.knowledge.domain.Cursor
import tallyvane.knowledge.domain.Embedding
import tallyvane.knowledge.domain.Outcome
import tallyvane.knowledge.domain.Placement
import tallyvane.knowledge.domain.Quote
import tallyvane.knowledge.domain.RawEntryId
import tallyvane.knowledge.domain.RolePeriod
import tallyvane.knowledge.domain.Tag
import tallyvane.knowledge.domain.Technology

/** A thing together with the words of the person that it comes from. */
class Evidenced<T>(private val value: T, private val quote: Quote) {
    fun writeTo(record: Record<T>) = record.evidenced(value, quote)

    fun interface Record<T> {
        fun evidenced(value: T, quote: Quote)
    }
}

/** What a role is now said to be. Only what the graph does not know yet is filled in. */
class RolePatch(private val title: String?, private val period: RolePeriod, private val quote: Quote) {
    fun writeTo(record: Record) = record.role(title, period, quote)

    interface Record {
        fun role(title: String?, period: RolePeriod, quote: Quote)
    }
}

class ProjectPatch(private val title: String?, private val problem: String?, private val quote: Quote) {
    fun writeTo(record: Record) = record.project(title, problem, quote)

    interface Record {
        fun project(title: String?, problem: String?, quote: Quote)
    }
}

/** An action about to enter the graph, with what it used, whom it was done with, what it shows and where it lies in meaning. */
class NewAction(
    private val local: Int,
    private val action: Action,
    private val technologies: List<Evidenced<Technology>>,
    private val collaborators: List<Evidenced<Collaborator>>,
    private val tags: List<Tag>,
    private val embedding: Embedding,
) {
    fun writeTo(record: Record) = record.newAction(local, action, technologies, collaborators, tags, embedding)

    interface Record {
        fun newAction(
            local: Int,
            action: Action,
            technologies: List<Evidenced<Technology>>,
            collaborators: List<Evidenced<Collaborator>>,
            tags: List<Tag>,
            embedding: Embedding,
        )
    }
}

/** An outcome about to enter the graph, and which of the new actions (by their [NewAction] number) led to it. */
class NewOutcome(private val outcome: Outcome, private val producedBy: List<Int>) {
    fun writeTo(record: Record) = record.newOutcome(outcome, producedBy)

    interface Record {
        fun newOutcome(outcome: Outcome, producedBy: List<Int>)
    }
}

/**
 * Everything one answer adds to the graph, checked and normalised, to be written in one transaction.
 * Evidence rows are written for every part of it.
 */
class GraphWrite(
    private val rawEntry: RawEntryId,
    private val placement: Placement,
    private val cursor: Cursor,
    private val company: Evidenced<CompanyName>?,
    private val role: RolePatch?,
    private val project: ProjectPatch?,
    private val actions: List<NewAction>,
    private val outcomes: List<NewOutcome>,
) {
    fun writeTo(record: Record) {
        record.where(rawEntry, placement, cursor)
        company?.let { record.company(it) }
        role?.let { record.role(it) }
        project?.let { record.project(it) }
        actions.forEach { record.action(it) }
        outcomes.forEach { record.outcome(it) }
    }

    interface Record {
        fun where(rawEntry: RawEntryId, placement: Placement, cursor: Cursor)

        fun company(company: Evidenced<CompanyName>)

        fun role(role: RolePatch)

        fun project(project: ProjectPatch)

        fun action(action: NewAction)

        fun outcome(outcome: NewOutcome)
    }
}
