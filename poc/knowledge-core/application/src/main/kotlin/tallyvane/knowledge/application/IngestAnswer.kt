package tallyvane.knowledge.application

import tallyvane.knowledge.domain.ActionId
import tallyvane.knowledge.domain.Cursor
import tallyvane.knowledge.domain.DuplicateRule
import tallyvane.knowledge.domain.Embedding
import tallyvane.knowledge.domain.Gap
import tallyvane.knowledge.domain.NothingMore
import tallyvane.knowledge.domain.Outcome
import tallyvane.knowledge.domain.Placement
import tallyvane.knowledge.domain.QuestionId
import tallyvane.knowledge.domain.RawEntryId
import tallyvane.knowledge.domain.Utterance
import tallyvane.knowledge.domain.port.CursorStore
import tallyvane.knowledge.domain.port.EmbeddingProvider
import tallyvane.knowledge.domain.port.GraphNode
import tallyvane.knowledge.domain.port.GraphWrite
import tallyvane.knowledge.domain.port.KnowledgeGraph
import tallyvane.knowledge.domain.port.NewAction
import tallyvane.knowledge.domain.port.NewOutcome
import tallyvane.knowledge.domain.port.QuestionLog

/**
 * One answer from the person, taken all the way in: kept as they said it, read, checked, normalised, linked
 * to skills, written to the graph with the words it came from, and followed by the next question.
 *
 * Which question the answer is to decides what is done with it. "Nothing more" to a question that can be
 * answered so moves the interview on (and closes the project when it was the last one asked about); any
 * other answer is read by the model, and where its parts go is settled by the question, not by the model.
 */
class IngestAnswer(
    private val graph: KnowledgeGraph,
    private val cursors: CursorStore,
    private val log: QuestionLog,
    private val extractor: Extractor,
    private val embeddings: EmbeddingProvider,
    private val tagger: Tagger,
    private val closing: CloseProject,
    private val nextQuestion: NextQuestion,
    private val nothingMore: NothingMore,
    private val duplicates: DuplicateRule,
) {
    suspend fun ingest(text: String, questionId: QuestionId?): Ingested {
        if (text.isBlank()) throw AnswerNotUsable("The answer is empty.")
        val cursor = cursors.cursor()
        val asked = questionId?.let { log.find(it) } ?: log.pending()
        val gap = asked?.question?.gap() ?: Gap.OPENING
        val entry = graph.recordRaw(text, "answer", asked?.id ?: questionId, cursor)
        val utterance = Utterance(text)

        val created =
            if (gap.acceptsNothingMore() && nothingMore.isSaidBy(text)) {
                nothingMoreTo(gap, cursor, entry, utterance)
            } else {
                read(gap, cursor, entry, utterance, asked?.question?.let { NextQuestion.viewOf(asked.id, it).text })
            }
        asked?.let { log.answered(it.id) }
        return Ingested(created.nodes.map(::view), created.mergeCandidate, nextQuestion.current())
    }

    private class Created(val nodes: List<GraphNode>, val mergeCandidate: MergeCandidate?)

    private suspend fun nothingMoreTo(
        gap: Gap,
        cursor: Cursor,
        entry: RawEntryId,
        utterance: Utterance,
    ): Created {
        when (gap) {
            Gap.MORE_IN_PROJECT -> {
                closing.close(cursor)
                cursors.save(cursor.afterProject())
            }
            Gap.MORE_IN_ROLE -> cursors.save(cursor.afterRole())
            Gap.PREVIOUS_ROLE -> cursors.save(cursor.finished())
            Gap.NO_METRIC -> {
                val result =
                    graph.write(
                        GraphWrite(
                            entry, Placement.SAME_PROJECT, cursor, null, null, null, emptyList(),
                            listOf(NewOutcome(Outcome.unmeasured(utterance.asQuote()), emptyList())),
                        ),
                    )
                return Created(result.created, null)
            }
            else -> Unit
        }
        return Created(emptyList(), null)
    }

    private suspend fun read(
        gap: Gap,
        cursor: Cursor,
        entry: RawEntryId,
        utterance: Utterance,
        questionText: String?,
    ): Created {
        val placement = gap.placementOfAnswer()
        val facts = if (cursor.isAtStart()) null else graph.facts(cursor)
        val found = extractor.extract(utterance, placement, facts, questionText)
        if (placement == Placement.NEW_ROLE && found.company == null) {
            throw AnswerNotUsable("The answer does not say which company this was at. Please name it.")
        }
        val vectors = if (found.actions.isEmpty()) emptyList() else embeddings.embed(found.actions.map { it.action.embeddingText() })
        val newActions =
            found.actions.mapIndexed { index, action ->
                NewAction(
                    index,
                    action.action,
                    action.technologyQuotes,
                    action.collaborators,
                    tagger.tag(action.action, action.technologies, vectors[index]),
                    vectors[index],
                )
            }
        val result =
            graph.write(
                GraphWrite(
                    entry, placement, cursor, found.company, found.role, found.project,
                    newActions, found.outcomes.map { NewOutcome(it.outcome, it.producedBy) },
                ),
            )
        cursors.save(result.cursor)
        return Created(result.created, mergeCandidateFor(found, vectors, result.actionIds))
    }

    /** The first new action that is close enough to an old one to be worth asking about. */
    private suspend fun mergeCandidateFor(
        found: Found,
        vectors: List<Embedding>,
        ids: List<ActionId>,
    ): MergeCandidate? {
        found.actions.forEachIndexed { index, action ->
            val nearest = graph.similarActions(vectors[index], ids, 1).firstOrNull()
            if (nearest != null && duplicates.suspects(nearest.similarity)) {
                return MergeCandidate(
                    ids[index].uuid.toString(),
                    nearest.id.uuid.toString(),
                    action.action.embeddingText(),
                    nearest.label,
                )
            }
        }
        return null
    }

    private fun view(node: GraphNode) = NodeView(node.id, node.type.name.lowercase(), node.label)
}
