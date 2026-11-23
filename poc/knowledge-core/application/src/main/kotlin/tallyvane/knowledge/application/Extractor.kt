package tallyvane.knowledge.application

import tallyvane.knowledge.domain.Action
import tallyvane.knowledge.domain.ActionKind
import tallyvane.knowledge.domain.Citation
import tallyvane.knowledge.domain.Collaborator
import tallyvane.knowledge.domain.CompanyName
import tallyvane.knowledge.domain.Direction
import tallyvane.knowledge.domain.Outcome
import tallyvane.knowledge.domain.Placement
import tallyvane.knowledge.domain.ProjectFacts
import tallyvane.knowledge.domain.Quote
import tallyvane.knowledge.domain.RolePeriod
import tallyvane.knowledge.domain.Technology
import tallyvane.knowledge.domain.TechnologyDictionary
import tallyvane.knowledge.domain.Utterance
import tallyvane.knowledge.domain.port.Diagnostics
import tallyvane.knowledge.domain.port.Evidenced
import tallyvane.knowledge.domain.port.ProjectPatch
import tallyvane.knowledge.domain.port.RolePatch

/** An action the model found, checked against the answer, before it is given an embedding and tags. */
internal class FoundAction(
    val action: Action,
    val technologies: List<Technology>,
    val technologyQuotes: List<Evidenced<Technology>>,
    val collaborators: List<Evidenced<Collaborator>>,
)

internal class FoundOutcome(val outcome: Outcome, val producedBy: List<Int>)

/** What survived the checks of an answer: only things the person's words support. */
internal class Found(
    val company: Evidenced<CompanyName>?,
    val role: RolePatch?,
    val project: ProjectPatch?,
    val actions: List<FoundAction>,
    val outcomes: List<FoundOutcome>,
)

/**
 * Reads an answer with the model and keeps only what the answer supports. The model's reply is parsed as
 * JSON (one retry with the reason if it is not), and then each thing in it is kept or dropped on its own:
 * the quote must be in the answer, the kind must be a known one, a figure must be in its quote. What is
 * dropped is said to [diagnostics], not lost.
 */
class Extractor(
    private val call: StructuredCall,
    private val dictionary: TechnologyDictionary,
    private val diagnostics: Diagnostics,
) {
    internal suspend fun extract(utterance: Utterance, placement: Placement, facts: ProjectFacts?, question: String?): Found {
        val wire = call.run("extract", suffix(utterance, placement, facts, question)) { Wire.decode<ExtractionWire>(it) }
        return Checks(utterance, placement).verify(wire)
    }

    private fun suffix(utterance: Utterance, placement: Placement, facts: ProjectFacts?, question: String?): String {
        var said = ""
        utterance.writeTo(
            object : Utterance.Record {
                override fun utterance(text: String) {
                    said = text
                }
            },
        )
        return buildString {
            appendLine("Position: " + instruction(placement))
            appendLine("Context: " + (facts?.let { Prompting.contextOf(it) } ?: "nothing yet, this is the first answer"))
            question?.let { appendLine("Question asked: $it") }
            appendLine("Answer:")
            appendLine("<<<")
            appendLine(said)
            append(">>>")
        }
    }

    private fun instruction(placement: Placement): String =
        when (placement) {
            Placement.NEW_ROLE ->
                "The answer is about a job the context does not have: fill company, role and project."
            Placement.NEW_PROJECT_IN_ROLE ->
                "The answer is about another project at the same company: describe it in project; leave company and role empty."
            Placement.SAME_PROJECT ->
                "The answer adds to the current project: fill role or project only with what the answer states about them."
        }

    private inner class Checks(private val utterance: Utterance, private val placement: Placement) {
        fun verify(wire: ExtractionWire): Found {
            val keptActions = mutableListOf<FoundAction>()
            val localOf = mutableMapOf<Int, Int>()
            wire.actions.forEachIndexed { index, action ->
                action(action)?.let {
                    localOf[index] = keptActions.size
                    keptActions += it
                }
            }
            return Found(
                company = if (placement == Placement.NEW_ROLE) company(wire.company) else null,
                role = if (placement == Placement.NEW_PROJECT_IN_ROLE) null else role(wire.role),
                project = project(wire.project),
                actions = keptActions,
                outcomes = wire.outcomes.mapNotNull { outcome(it, localOf) },
            )
        }

        private fun quote(entity: String, fragment: String): Quote? =
            utterance.cite(fragment).reportTo(
                object : Citation.Report<Quote?> {
                    override fun quoted(quote: Quote): Quote = quote

                    override fun notQuoted(fragment: String): Quote? {
                        diagnostics.dropped(entity, "the quote \"$fragment\" is not in the answer")
                        return null
                    }
                },
            )

        private fun company(wire: NamedWire?): Evidenced<CompanyName>? {
            if (wire == null || wire.n.isBlank()) return null
            return quote("company", wire.q)?.let { Evidenced(CompanyName(wire.n), it) }
        }

        private fun role(wire: RoleWire?): RolePatch? {
            if (wire == null) return null
            val quote = quote("role", wire.q) ?: return null
            return RolePatch(wire.title?.trim()?.takeIf { it.isNotEmpty() }, RolePeriod(wire.start, wire.end, wire.current), quote)
        }

        private fun project(wire: ProjectWire?): ProjectPatch? {
            if (wire == null) return null
            val quote = quote("project", wire.q) ?: return null
            return ProjectPatch(wire.title?.trim()?.takeIf { it.isNotEmpty() }, wire.problem?.trim()?.takeIf { it.isNotEmpty() }, quote)
        }

        private fun action(wire: ActionWire): FoundAction? {
            if (!ActionKind.isKnown(wire.kind)) {
                diagnostics.dropped("action", "the kind \"${wire.kind}\" is not one of the known kinds")
                return null
            }
            if (wire.verb.isBlank() || wire.what.isBlank()) {
                diagnostics.dropped("action", "it has no verb or no object")
                return null
            }
            val quote = quote("action", wire.q) ?: return null
            val technologies =
                wire.techs.filter { it.n.isNotBlank() }.mapNotNull { named ->
                    quote("technology", named.q)?.let { dictionary.resolve(named.n) to it }
                }
            return FoundAction(
                Action(ActionKind.named(wire.kind), wire.verb.trim(), wire.what.trim(), wire.finding?.trim()?.takeIf { it.isNotEmpty() }, quote),
                technologies.map { it.first },
                technologies.map { (technology, quote) -> Evidenced(technology, quote) },
                wire.collabs.filter { it.n.isNotBlank() }.mapNotNull { named ->
                    quote("collaborator", named.q)?.let { Evidenced(Collaborator(named.n), it) }
                },
            )
        }

        private fun outcome(wire: OutcomeWire, localOf: Map<Int, Int>): FoundOutcome? {
            val quote = quote("outcome", wire.q) ?: return null
            val direction = wire.dir?.takeIf { Direction.isKnown(it) }?.let { Direction.named(it) }
            return try {
                FoundOutcome(
                    Outcome(wire.what.trim(), direction, wire.value, wire.unit?.trim()?.takeIf { it.isNotEmpty() }, wire.approx, quote),
                    wire.of.mapNotNull { localOf[it] },
                )
            } catch (e: IllegalArgumentException) {
                diagnostics.dropped("outcome", e.message ?: "it is not supported by the answer")
                null
            }
        }
    }
}
