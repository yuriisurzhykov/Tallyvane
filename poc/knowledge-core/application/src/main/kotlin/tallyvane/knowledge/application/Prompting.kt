package tallyvane.knowledge.application

import tallyvane.knowledge.domain.ActionId
import tallyvane.knowledge.domain.ActionKind
import tallyvane.knowledge.domain.Collaborator
import tallyvane.knowledge.domain.Direction
import tallyvane.knowledge.domain.Outcome
import tallyvane.knowledge.domain.ProjectDetails
import tallyvane.knowledge.domain.ProjectFacts
import tallyvane.knowledge.domain.Quote
import tallyvane.knowledge.domain.RoleFacts
import tallyvane.knowledge.domain.RolePeriod
import tallyvane.knowledge.domain.Technology

/** How the facts of a project are put in front of a model: names only, never the ids of the database. */
internal object Prompting {
    /** One line with the names of the company, role and project, for the extraction step. */
    fun contextOf(facts: ProjectFacts): String {
        val text = FactsText()
        facts.writeTo(text)
        return text.context()
    }

    /** The facts as labelled lines (`A1`, `A2`, ...) for the bullets step, and which action each label is. */
    fun factsOf(facts: ProjectFacts): LabelledFacts {
        val text = FactsText()
        facts.writeTo(text)
        return text.labelled()
    }

    class LabelledFacts(val text: String, val actionsByLabel: Map<String, ActionId>)

    private class FactsText : ProjectFacts.Record {
        private var company = ""
        private var roleTitle: String? = null
        private var period = ""
        private var projectTitle: String? = null
        private var problem: String? = null
        private val actions = mutableListOf<String>()
        private val labels = linkedMapOf<String, ActionId>()
        private val collaborators = mutableListOf<String>()
        private val outcomes = mutableListOf<String>()

        override fun context(company: String, role: RoleFacts, project: ProjectDetails) {
            this.company = company
            role.writeTo(
                object : RoleFacts.Record {
                    override fun role(title: String?, period: RolePeriod) {
                        roleTitle = title
                        this@FactsText.period = period.label()
                    }
                },
            )
            project.writeTo(
                object : ProjectDetails.Record {
                    override fun project(title: String?, problem: String?) {
                        projectTitle = title
                        this@FactsText.problem = problem
                    }
                },
            )
        }

        override fun action(
            id: ActionId,
            kind: ActionKind,
            verb: String,
            what: String,
            finding: String?,
            technologies: List<Technology>,
        ) {
            val label = "A${labels.size + 1}"
            labels[label] = id
            val tech = if (technologies.isEmpty()) "" else "; technologies: " + technologies.joinToString(", ")
            val found = finding?.let { "; finding: $it" } ?: ""
            actions += "$label [${kind.wireName()}] $verb $what$found$tech"
        }

        override fun collaborator(role: String) {
            collaborators += role
        }

        override fun outcome(
            what: String,
            direction: Direction?,
            value: Double?,
            unit: String?,
            approx: Boolean,
            hasMetric: Boolean,
            quote: Quote,
        ) {
            val figure = value?.let { (if (approx) "about " else "") + (if (it == Math.floor(it)) it.toLong().toString() else it.toString()) + (unit ?: "") }
            outcomes += listOfNotNull(what, direction?.let { "(${it.wireName()})" }, figure).joinToString(" ")
        }

        fun context(): String =
            "company=\"$company\"; role=\"${roleTitle ?: "untitled"}\"; project=\"${projectTitle ?: "untitled"}\""

        fun labelled(): LabelledFacts =
            LabelledFacts(
                buildString {
                    appendLine("Company: $company")
                    appendLine("Role: ${roleTitle ?: "unknown"}${if (period.isEmpty()) "" else " ($period)"}")
                    appendLine("Project: ${projectTitle ?: "untitled"}")
                    problem?.let { appendLine("Problem: $it") }
                    appendLine("Actions:")
                    actions.forEach { appendLine(it) }
                    appendLine("Collaborators: ${collaborators.joinToString(", ").ifEmpty { "none" }}")
                    appendLine("Outcomes: ${outcomes.joinToString("; ").ifEmpty { "none" }}")
                }.trimEnd(),
                labels,
            )
    }
}
