package tallyvane.knowledge.application

import tallyvane.knowledge.domain.ActionId
import tallyvane.knowledge.domain.Bullet
import tallyvane.knowledge.domain.BulletCheck
import tallyvane.knowledge.domain.Cursor
import tallyvane.knowledge.domain.ProjectFacts
import tallyvane.knowledge.domain.port.Diagnostics
import tallyvane.knowledge.domain.port.KnowledgeGraph

/**
 * Closing a project writes its résumé bullets from the facts of the project and from nothing else. The model
 * proposes; the checks of [BulletCheck] decide; a proposal that fails twice is replaced by a bullet built
 * from the facts by a template, so a project always closes with bullets that are true.
 */
class CloseProject(
    private val graph: KnowledgeGraph,
    private val call: StructuredCall,
    private val check: BulletCheck,
    private val diagnostics: Diagnostics,
) {
    suspend fun close(cursor: Cursor) {
        val facts = graph.facts(cursor)
        val bullets =
            if (!facts.hasActions()) {
                emptyList()
            } else {
                try {
                    proposedFor(facts)
                } catch (e: LlmStepFailed) {
                    diagnostics.dropped("bullets", "${e.reason}; the template is used instead")
                    facts.fallbackBullets()
                }
            }
        graph.closeProject(cursor, bullets)
    }

    private suspend fun proposedFor(facts: ProjectFacts): List<Bullet> {
        val labelled = Prompting.factsOf(facts)
        return call.run("bullets", labelled.text) { reply ->
            val wire = Wire.decode<BulletsWire>(reply)
            if (wire.bullets.isEmpty() || wire.bullets.size > MAX_BULLETS) {
                throw InvalidLlmOutput("Give one or two bullets, not ${wire.bullets.size}.")
            }
            wire.bullets.map { bulletOf(it, labelled.actionsByLabel, facts) }
        }
    }

    private fun bulletOf(wire: BulletWire, actions: Map<String, ActionId>, facts: ProjectFacts): Bullet {
        val verdict = check.verify(wire.text, facts)
        if (!verdict.isAcceptable()) throw InvalidLlmOutput("Bullet \"${wire.text}\": ${verdict.why()}")
        if (wire.a.isEmpty()) throw InvalidLlmOutput("Bullet \"${wire.text}\" says which actions it covers in \"a\".")
        val covered =
            wire.a.map { label ->
                actions[label.trim()] ?: throw InvalidLlmOutput("Bullet \"${wire.text}\" covers $label, which is not an action label.")
            }
        return Bullet(wire.text.trim(), covered.distinct())
    }

    private companion object {
        const val MAX_BULLETS = 2
    }
}
