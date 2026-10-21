package tallyvane.knowledge.application

import tallyvane.knowledge.domain.BulletCheck
import tallyvane.knowledge.domain.DuplicateRule
import tallyvane.knowledge.domain.Interview
import tallyvane.knowledge.domain.NothingMore
import tallyvane.knowledge.domain.SkillTags
import tallyvane.knowledge.domain.TechnologyDictionary
import tallyvane.knowledge.domain.port.CursorStore
import tallyvane.knowledge.domain.port.Diagnostics
import tallyvane.knowledge.domain.port.EmbeddingProvider
import tallyvane.knowledge.domain.port.KnowledgeGraph
import tallyvane.knowledge.domain.port.LlmClient
import tallyvane.knowledge.domain.port.Prompts
import tallyvane.knowledge.domain.port.QuestionLog
import tallyvane.knowledge.domain.port.Skills
import tallyvane.knowledge.domain.port.Vacancies
import java.time.LocalDate

/** The numbers that tune how answers are linked: how alike two actions must be to ask about, and how near a skill. */
class Settings(
    val dedupeThreshold: Double = 0.85,
    val tagThreshold: Double = 0.55,
    val tagLimit: Int = 3,
)

/**
 * The use cases, built once from the ports they stand on. Whoever runs the application (the server, a test)
 * supplies the ports and gets the same use cases wired the same way; nothing in them builds another object.
 */
class KnowledgeCore(
    graph: KnowledgeGraph,
    cursors: CursorStore,
    log: QuestionLog,
    skills: Skills,
    vacancies: Vacancies,
    llm: LlmClient,
    embeddings: EmbeddingProvider,
    prompts: Prompts,
    dictionary: TechnologyDictionary,
    diagnostics: Diagnostics,
    settings: Settings,
    today: () -> LocalDate,
) {
    private val call = StructuredCall(llm, prompts)
    private val interview = Interview()

    val nextQuestion = NextQuestion(cursors, graph, log, interview)
    val ingestAnswer =
        IngestAnswer(
            graph, cursors, log,
            Extractor(call, dictionary, diagnostics),
            embeddings,
            Tagger(skills, call, SkillTags(settings.tagThreshold, settings.tagLimit), diagnostics),
            CloseProject(graph, call, BulletCheck(dictionary), diagnostics),
            nextQuestion,
            NothingMore(),
            DuplicateRule(settings.dedupeThreshold),
        )
    val resolveMerge = ResolveMerge(graph)
    val showGraph = ShowGraph(graph)
    val resetData = ResetData(graph)
    val parseVacancy = ParseVacancy(call, skills, vacancies, diagnostics)
    val matchAndScore = MatchAndScore(graph, vacancies, skills, today)
    val buildResume = BuildResume(graph, vacancies, skills, today)
}
