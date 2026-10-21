package tallyvane.knowledge.application

import tallyvane.knowledge.domain.port.KnowledgeGraph

/** The graph as the page draws it. */
class ShowGraph(private val graph: KnowledgeGraph) {
    suspend fun show(): GraphResponse {
        val view = graph.view()
        return GraphResponse(
            view.nodes.map { NodeView(it.id, it.type.name.lowercase(), it.label) },
            view.edges.map { EdgeView(it.from, it.to, it.type, it.weight) },
        )
    }
}

/** Throws away everything the person said and what was made of it, and keeps the seeds. */
class ResetData(private val graph: KnowledgeGraph) {
    suspend fun reset() = graph.wipe()
}
