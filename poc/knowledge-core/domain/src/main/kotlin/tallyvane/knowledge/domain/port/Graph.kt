package tallyvane.knowledge.domain.port

import tallyvane.knowledge.domain.ActionId
import tallyvane.knowledge.domain.Bullet
import tallyvane.knowledge.domain.Cursor
import tallyvane.knowledge.domain.Embedding
import tallyvane.knowledge.domain.Gap
import tallyvane.knowledge.domain.Portfolio
import tallyvane.knowledge.domain.ProjectFacts
import tallyvane.knowledge.domain.Question
import tallyvane.knowledge.domain.QuestionId
import tallyvane.knowledge.domain.RawEntryId
import tallyvane.knowledge.domain.Skill
import tallyvane.knowledge.domain.SkillMatch
import tallyvane.knowledge.domain.Vacancy
import tallyvane.knowledge.domain.VacancyId

enum class NodeType { COMPANY, ROLE, PROJECT, ACTION, TECHNOLOGY, OUTCOME, SKILL, COLLABORATOR }

/** A node as the graph panel draws it. The id is the id of the thing in the graph, as text. */
class GraphNode(val id: String, val type: NodeType, val label: String)

class GraphEdge(val from: String, val to: String, val type: String, val weight: Double? = null)

class GraphView(val nodes: List<GraphNode>, val edges: List<GraphEdge>)

/** What one write created, and where the interview stands after it. */
class WriteResult(val cursor: Cursor, val created: List<GraphNode>, val actionIds: List<ActionId>)

/** An existing action that is close in meaning to another one, and how close. */
class SimilarAction(val id: ActionId, val label: String, val similarity: Double)

/** The graph of what the person has said. */
interface KnowledgeGraph {
    /** Saves what the person typed, before anything is made of it, and says where in the interview it was said. */
    suspend fun recordRaw(text: String, source: String, question: QuestionId?, cursor: Cursor): RawEntryId

    /** What is known of the project the [cursor] is on. */
    suspend fun facts(cursor: Cursor): ProjectFacts

    /** Applies [write] in one transaction. Fields of a role or project that already have a value keep it. */
    suspend fun write(write: GraphWrite): WriteResult

    /**
     * The [limit] actions of the project the [cursor] is on that are nearest [embedding], nearest first, leaving
     * out those in [excluding]. An action of another project is never a candidate: folding it into one of this
     * project would take the action out of the project it was said about.
     */
    suspend fun similarActions(cursor: Cursor, embedding: Embedding, excluding: List<ActionId>, limit: Int): List<SimilarAction>

    /** Folds [newAction] into [existing]: its links, tags and evidence move to the existing one, and it is removed. */
    suspend fun merge(newAction: ActionId, existing: ActionId)

    /** Stores the bullets of the project the [cursor] is on and marks it closed. */
    suspend fun closeProject(cursor: Cursor, bullets: List<Bullet>)

    suspend fun portfolio(): Portfolio

    suspend fun view(): GraphView

    /** What the person said that a node was made from. */
    suspend fun quotesOfNode(id: String): List<String>

    /** What the person said that the actions, and the results that came of them, were made from. */
    suspend fun quotesOfActions(actions: List<ActionId>): List<String>

    /** Removes everything of the person's: graph, answers, questions, vacancies, position. Keeps the seeds. */
    suspend fun wipe()
}

interface Skills {
    suspend fun all(): List<Skill>

    suspend fun nearest(embedding: Embedding, limit: Int): List<SkillMatch>
}

interface Vacancies {
    suspend fun save(vacancy: Vacancy): VacancyId

    suspend fun find(id: VacancyId): Vacancy
}

interface CursorStore {
    suspend fun cursor(): Cursor

    suspend fun save(cursor: Cursor)
}

/** A question that was put to the person, and the id it can be answered by. */
class LoggedQuestion(val id: QuestionId, val question: Question)

interface QuestionLog {
    /** The last question that has not been answered. */
    suspend fun pending(): LoggedQuestion?

    suspend fun find(id: QuestionId): LoggedQuestion?

    /** Records [question] as asked about whatever of the [cursor] it is about (the role or the project). */
    suspend fun log(question: Question, cursor: Cursor): QuestionId

    suspend fun answered(id: QuestionId)

    /** The gaps already asked about for the role and the project the [cursor] is on. */
    suspend fun askedAbout(cursor: Cursor): Set<Gap>
}
