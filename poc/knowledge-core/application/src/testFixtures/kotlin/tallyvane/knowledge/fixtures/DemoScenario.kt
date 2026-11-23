package tallyvane.knowledge.fixtures

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import tallyvane.knowledge.application.KnowledgeCore
import tallyvane.knowledge.domain.QuestionId
import tallyvane.knowledge.domain.VacancyId
import tallyvane.knowledge.domain.port.KnowledgeGraph
import kotlin.uuid.Uuid

private fun idOf(text: String) = VacancyId(Uuid.parse(text))

/**
 * The demo of the specification, from the first question to the résumé, with every assertion it makes. It is
 * written against the use cases and the ports, so the same demo judges the in-memory store and the Postgres one.
 */
object DemoScenario {
    /** Runs the demo on [core], whose model is [llm], whose graph is [graphStore], and which has saved [rawEntries] answers. */
    suspend fun run(core: KnowledgeCore, graphStore: KnowledgeGraph, llm: ScriptedLlm, rawEntries: suspend () -> Int) {
        // 1. The interview starts with the opening question.
        core.nextQuestion.current().gap shouldBe "OPENING"

        // 2-3. The long answer builds the graph of the specification.
        val first = core.ingestAnswer.ingest(DemoScript.FIRST_ANSWER, null)
        val types = first.nodes.groupBy { it.type }.mapValues { (_, nodes) -> nodes.map { it.label } }
        types["company"] shouldBe listOf("Acme")
        types["role"] shouldBe listOf("Backend Engineer")
        types["project"]!!.size shouldBe 1
        types["action"]!!.size shouldBe 4
        types["technology"] shouldBe listOf("Redis")
        types["collaborator"] shouldBe listOf("dba")
        types["outcome"] shouldBe listOf("database load")
        types["skill"]!! shouldContainAll listOf("Caching", "Redis", "Performance profiling", "Cross-team collaboration")
        first.mergeCandidate shouldBe null

        // 4. The next questions follow the table: dates, then the project's name, then the decision.
        first.nextQuestion.gap shouldBe "ROLE_DATES"
        first.nextQuestion.text shouldBe "When did you start at Acme? Are you still there?"
        val second = core.ingestAnswer.ingest(DemoScript.DATES_ANSWER, QuestionId(Uuid.parse(first.nextQuestion.id)))
        second.nextQuestion.gap shouldBe "PROJECT_TITLE"
        val third = core.ingestAnswer.ingest(DemoScript.TITLE_ANSWER, null)
        third.nextQuestion.gap shouldBe "NO_DECISION"
        third.nextQuestion.text shouldBe "What alternatives did you consider, and why did you choose Redis?"

        // 5. The decision is answered, nothing else is missing, so the next question is the open one.
        val fourth = core.ingestAnswer.ingest(DemoScript.DECISION_ANSWER, null)
        fourth.nextQuestion.gap shouldBe "MORE_IN_PROJECT"
        fourth.nextQuestion.text shouldBe "Anything else you did on Catalog cache?"
        llm.callsTo("bullets") shouldBe 0

        // The answer is nothing more: the project closes with a bullet, and the interview asks about the role.
        val fifth = core.ingestAnswer.ingest(DemoScript.NOTHING_MORE, null)
        fifth.nextQuestion.gap shouldBe "MORE_IN_ROLE"
        fifth.nextQuestion.text shouldBe "Any other project at Acme?"
        llm.callsTo("bullets") shouldBe 1
        llm.callsTo("extract") shouldBe 4
        rawEntries() shouldBe 5

        // 6-7. The vacancy: caching and Redis are covered, PostgreSQL and Kafka are not.
        val vacancy = core.parseVacancy.parse(DemoScript.VACANCY_TEXT)
        vacancy.title shouldBe "Senior Backend Engineer"
        vacancy.reqs.map { it.skill } shouldBe listOf("caching", "redis", "postgresql", "kafka", "cross-team-collaboration")
        vacancy.unmapped shouldBe listOf("GraphQL")

        val match = core.matchAndScore.match(idOf(vacancy.vacancyId))
        val gaps = match.gaps.associateBy { it.skill }
        gaps.getValue("caching").missing shouldBe false
        gaps.getValue("redis").bestWeight shouldBe 1.0
        gaps.getValue("redis").missing shouldBe false
        gaps.getValue("postgresql").missing shouldBe true
        gaps.getValue("kafka").missing shouldBe true
        gaps.getValue("cross-team-collaboration").missing shouldBe false
        match.unmapped shouldBe listOf("GraphQL")
        match.scores.single().projects.single().score shouldBeGreaterThan 0.0

        // The résumé: the one bullet that was stored, with the person's words behind it.
        val resume = core.buildResume.build(idOf(vacancy.vacancyId))
        resume.markdown shouldContain "### Acme — Backend Engineer (2022 – present)"
        resume.markdown shouldContain "**Target:** Senior Backend Engineer"
        resume.markdown shouldContain "- Cut database load by about 40% by profiling queries, designing a Redis cache and agreeing the approach with the DBA."
        resume.markdown shouldContain "## Skills"
        resume.bullets.size shouldBe 1
        resume.bullets.single().evidence shouldContainAll
            listOf(
                "Предложил кеш на Redis",
                "согласовал с DBA",
                "Нагрузка на базу упала примерно на 40%",
            )

        // Everything the graph holds comes with quotes, and every quote is in something the person said.
        val graph = core.showGraph.show()
        graph.nodes.shouldNotBeEmpty()
        val said = DemoScript.ANSWERS
        for (node in graph.nodes.filter { it.type != "skill" }) {
            val quotes = graphStore.quotesOfNode(node.id)
            quotes.forEach { quote -> said.any { it.contains(quote) } shouldBe true }
        }
        graph.nodes.filter { it.type in setOf("company", "role", "project", "action", "outcome") }
            .forEach { graphStore.quotesOfNode(it.id).shouldNotBeEmpty() }
        graph.edges.map { it.type } shouldContain "tagged"
    }
}
