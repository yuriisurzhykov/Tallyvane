package tallyvane.knowledge.application

import tallyvane.knowledge.domain.Interview
import tallyvane.knowledge.domain.Question
import tallyvane.knowledge.domain.QuestionId
import tallyvane.knowledge.domain.Gap
import tallyvane.knowledge.domain.port.CursorStore
import tallyvane.knowledge.domain.port.KnowledgeGraph
import tallyvane.knowledge.domain.port.LoggedQuestion
import tallyvane.knowledge.domain.port.QuestionLog

/** The question the person is to answer now. No model is involved: it follows from what is known. */
class NextQuestion(
    private val cursors: CursorStore,
    private val graph: KnowledgeGraph,
    private val log: QuestionLog,
    private val interview: Interview,
) {
    suspend fun current(): AskedQuestion {
        log.pending()?.let { return viewOf(it.id, it.question) }
        val cursor = cursors.cursor()
        val facts = if (cursor.isAtStart()) null else graph.facts(cursor)
        val question = interview.next(cursor, facts, log.askedAbout(cursor))
        return viewOf(log.log(question, cursor), question)
    }

    companion object {
        fun viewOf(id: QuestionId, question: Question): AskedQuestion {
            var view: AskedQuestion? = null
            question.writeTo(
                object : Question.Record {
                    override fun question(gap: Gap, text: String) {
                        view = AskedQuestion(id.uuid.toString(), gap.name, text)
                    }
                },
            )
            return view!!
        }

        fun viewOf(logged: LoggedQuestion): AskedQuestion = viewOf(logged.id, logged.question)
    }
}
