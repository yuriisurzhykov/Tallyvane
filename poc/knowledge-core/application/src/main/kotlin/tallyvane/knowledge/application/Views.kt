package tallyvane.knowledge.application

import kotlinx.serialization.Serializable

/**
 * What the use cases answer with. These are the one place in the application that is plain data on purpose:
 * they are the shape of the API's responses, built from the domain objects by the use case that answers.
 */
@Serializable
class AskedQuestion(val id: String, val gap: String, val text: String)

@Serializable
class NodeView(val id: String, val type: String, val label: String)

@Serializable
class EdgeView(val from: String, val to: String, val type: String, val weight: Double? = null)

@Serializable
class GraphResponse(val nodes: List<NodeView>, val edges: List<EdgeView>)

@Serializable
class EvidenceView(val quotes: List<String>)

@Serializable
class MergeCandidate(
    val newActionId: String,
    val existingActionId: String,
    val newLabel: String,
    val existingLabel: String,
)

@Serializable
class Ingested(val nodes: List<NodeView>, val mergeCandidate: MergeCandidate?, val nextQuestion: AskedQuestion)

@Serializable
class RequirementView(val skill: String, val name: String, val importance: Int, val must: Boolean)

@Serializable
class VacancyView(
    val vacancyId: String,
    val title: String,
    val reqs: List<RequirementView>,
    val unmapped: List<String>,
)

@Serializable
class GapView(
    val skill: String,
    val name: String,
    val must: Boolean,
    val importance: Int,
    val bestWeight: Double,
    val missing: Boolean,
)

@Serializable
class ProjectScoreView(val title: String?, val score: Double)

@Serializable
class RoleScoreView(
    val company: String,
    val title: String?,
    val period: String,
    val score: Double,
    val projects: List<ProjectScoreView>,
)

@Serializable
class MatchView(val scores: List<RoleScoreView>, val gaps: List<GapView>, val unmapped: List<String>)

@Serializable
class BulletView(val text: String, val evidence: List<String>)

@Serializable
class ResumeView(val markdown: String, val bullets: List<BulletView>)
