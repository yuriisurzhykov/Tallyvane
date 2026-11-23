package tallyvane.knowledge.application

import tallyvane.knowledge.domain.ActionId
import tallyvane.knowledge.domain.port.KnowledgeGraph

/** The person's answer to "is this the same as...": yes folds the new action into the old one; no leaves both. */
class ResolveMerge(private val graph: KnowledgeGraph) {
    suspend fun resolve(newAction: ActionId, existing: ActionId, accept: Boolean) {
        if (accept) graph.merge(newAction, existing)
    }
}
