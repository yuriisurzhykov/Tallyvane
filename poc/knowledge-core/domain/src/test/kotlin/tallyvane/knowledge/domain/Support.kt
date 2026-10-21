package tallyvane.knowledge.domain

import kotlin.uuid.Uuid

/** The quote of [fragment] in [said], failing the test if it is not there. */
fun quoteOf(said: String, fragment: String): Quote =
    Utterance(said).cite(fragment).reportTo(
        object : Citation.Report<Quote> {
            override fun quoted(quote: Quote): Quote = quote

            override fun notQuoted(fragment: String): Quote = error("\"$fragment\" is not in \"$said\"")
        },
    )

fun anyQuote(): Quote = quoteOf("I did it", "I did it")

fun actionId(): ActionId = ActionId(Uuid.random())

fun action(
    kind: ActionKind = ActionKind.BUILT,
    verb: String = "built",
    what: String = "a cache",
    finding: String? = null,
): Action = Action(kind, verb, what, finding, anyQuote())

fun facts(
    company: String = "Acme",
    title: String? = "Backend Engineer",
    period: RolePeriod = RolePeriod("2022", null, true),
    projectTitle: String? = "Catalog cache",
    problem: String? = "The database was slow",
    actions: List<ActionFacts> = emptyList(),
    collaborators: List<Collaborator> = emptyList(),
    outcomes: List<Outcome> = emptyList(),
    producedOutcomes: List<OutcomeFacts> = emptyList(),
): ProjectFacts =
    ProjectFacts(
        company,
        RoleFacts(title, period),
        ProjectDetails(projectTitle, problem),
        actions,
        collaborators,
        outcomes.map { OutcomeFacts(it, emptyList()) } + producedOutcomes,
    )

fun actionFacts(
    action: Action = action(),
    technologies: List<Technology> = emptyList(),
    id: ActionId = actionId(),
): ActionFacts = ActionFacts(id, action, technologies)

fun outcome(
    said: String = "load fell about 40%",
    what: String = "database load",
    direction: Direction? = Direction.DOWN,
    value: Double? = 40.0,
    unit: String? = "%",
    approx: Boolean = true,
): Outcome = Outcome(what, direction, value, unit, approx, quoteOf(said, said))

fun slug(value: String): SkillSlug = SkillSlug(value)

/** What a question says, read back through its record. */
fun Question.text(): String {
    var said = ""
    writeTo(
        object : Question.Record {
            override fun question(gap: Gap, text: String) {
                said = text
            }
        },
    )
    return said
}

/** What a bullet says. */
fun Bullet.text(): String {
    var said = ""
    writeTo(
        object : Bullet.Record {
            override fun bullet(text: String, covers: List<ActionId>) {
                said = text
            }
        },
    )
    return said
}

val dictionary =
    TechnologyDictionary(
        listOf(
            TechnologyDictionary.Entry("Redis", listOf("редис", "redis cache")),
            TechnologyDictionary.Entry("PostgreSQL", listOf("postgres", "постгрес", "pg")),
            TechnologyDictionary.Entry("Kafka", listOf("апач кафка", "apache kafka")),
        ),
    )
