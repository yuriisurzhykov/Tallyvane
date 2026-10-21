package tallyvane.knowledge.fixtures

import tallyvane.knowledge.domain.Action
import tallyvane.knowledge.domain.ActionId
import tallyvane.knowledge.domain.ActionKind
import tallyvane.knowledge.domain.Bullet
import tallyvane.knowledge.domain.Collaborator
import tallyvane.knowledge.domain.CompanyId
import tallyvane.knowledge.domain.CompanyName
import tallyvane.knowledge.domain.Cursor
import tallyvane.knowledge.domain.Direction
import tallyvane.knowledge.domain.Embedding
import tallyvane.knowledge.domain.Gap
import tallyvane.knowledge.domain.Outcome
import tallyvane.knowledge.domain.ProjectId
import tallyvane.knowledge.domain.Question
import tallyvane.knowledge.domain.Quote
import tallyvane.knowledge.domain.RoleId
import tallyvane.knowledge.domain.RolePeriod
import tallyvane.knowledge.domain.Skill
import tallyvane.knowledge.domain.SkillMatch
import tallyvane.knowledge.domain.SkillSlug
import tallyvane.knowledge.domain.Stage
import tallyvane.knowledge.domain.Tag
import tallyvane.knowledge.domain.Technology
import tallyvane.knowledge.domain.port.Evidenced

/** The fakes live on the far side of the domain's records, so they read domain objects the way an adapter does. */
internal class ReadAction : Action.Record {
    lateinit var kind: ActionKind
    lateinit var verb: String
    lateinit var what: String
    var finding: String? = null
    lateinit var quote: Quote

    override fun action(kind: ActionKind, verb: String, what: String, finding: String?, quote: Quote) {
        this.kind = kind
        this.verb = verb
        this.what = what
        this.finding = finding
        this.quote = quote
    }

    companion object {
        fun of(action: Action): ReadAction = ReadAction().also { action.writeTo(it) }
    }
}

internal class ReadOutcome : Outcome.Record {
    lateinit var what: String
    var hasMetric = false
    lateinit var quote: Quote

    override fun outcome(what: String, direction: Direction?, value: Double?, unit: String?, approx: Boolean, hasMetric: Boolean, quote: Quote) {
        this.what = what
        this.hasMetric = hasMetric
        this.quote = quote
    }

    companion object {
        fun of(outcome: Outcome): ReadOutcome = ReadOutcome().also { outcome.writeTo(it) }
    }
}

internal fun textOf(quote: Quote): String {
    var found = ""
    quote.writeTo(
        object : Quote.Record {
            override fun quote(text: String) {
                found = text
            }
        },
    )
    return found
}

internal fun <T> valueOf(evidenced: Evidenced<T>): Pair<T, Quote> {
    lateinit var found: Pair<T, Quote>
    evidenced.writeTo { value, quote -> found = value to quote }
    return found
}

internal fun nameOf(technology: Technology): String = technology.toString()

internal fun nameOf(collaborator: Collaborator): String = collaborator.toString()

internal fun normalisedAndName(name: CompanyName): Pair<String, String> {
    lateinit var found: Pair<String, String>
    name.writeTo(
        object : CompanyName.Record {
            override fun company(name: String, normalised: String) {
                found = name to normalised
            }
        },
    )
    return found
}

internal class ReadCursor : Cursor.Record {
    var company: CompanyId? = null
    var role: RoleId? = null
    var project: ProjectId? = null
    var stage: Stage = Stage.PROJECT

    override fun cursor(company: CompanyId?, role: RoleId?, project: ProjectId?, stage: Stage) {
        this.company = company
        this.role = role
        this.project = project
        this.stage = stage
    }

    companion object {
        fun of(cursor: Cursor): ReadCursor = ReadCursor().also { cursor.writeTo(it) }
    }
}

internal class ReadQuestion : Question.Record {
    lateinit var gap: Gap
    lateinit var text: String

    override fun question(gap: Gap, text: String) {
        this.gap = gap
        this.text = text
    }

    companion object {
        fun of(question: Question): ReadQuestion = ReadQuestion().also { question.writeTo(it) }
    }
}

internal class ReadSkill : Skill.Record {
    var slug: SkillSlug = SkillSlug("")
    lateinit var name: String
    var aliases: List<String> = emptyList()

    override fun skill(slug: SkillSlug, name: String, aliases: List<String>) {
        this.slug = slug
        this.name = name
        this.aliases = aliases
    }

    companion object {
        fun of(skill: Skill): ReadSkill = ReadSkill().also { skill.writeTo(it) }
    }
}

internal class ReadTag : Tag.Record {
    var skill: SkillSlug = SkillSlug("")
    var weight = 0.0

    override fun tag(skill: SkillSlug, weight: Double) {
        this.skill = skill
        this.weight = weight
    }

    companion object {
        fun of(tag: Tag): ReadTag = ReadTag().also { tag.writeTo(it) }
    }
}

internal class ReadBullet : Bullet.Record {
    lateinit var text: String
    var covers: List<ActionId> = emptyList()

    override fun bullet(text: String, covers: List<ActionId>) {
        this.text = text
        this.covers = covers
    }

    companion object {
        fun of(bullet: Bullet): ReadBullet = ReadBullet().also { bullet.writeTo(it) }
    }
}

internal class ReadPeriod : RolePeriod.Record {
    var start: String? = null
    var end: String? = null
    var current: Boolean? = null

    override fun period(start: String?, end: String?, current: Boolean?) {
        this.start = start
        this.end = end
        this.current = current
    }

    companion object {
        fun of(period: RolePeriod): ReadPeriod = ReadPeriod().also { period.writeTo(it) }
    }
}

internal class ReadEmbedding : Embedding.Record {
    lateinit var values: FloatArray

    override fun embedding(values: FloatArray) {
        this.values = values
    }

    companion object {
        fun of(embedding: Embedding): ReadEmbedding = ReadEmbedding().also { embedding.writeTo(it) }
    }
}

internal class ReadRolePatch : tallyvane.knowledge.domain.port.RolePatch.Record {
    var title: String? = null
    lateinit var period: RolePeriod
    lateinit var quote: Quote

    override fun role(title: String?, period: RolePeriod, quote: Quote) {
        this.title = title
        this.period = period
        this.quote = quote
    }

    companion object {
        fun of(patch: tallyvane.knowledge.domain.port.RolePatch): ReadRolePatch = ReadRolePatch().also { patch.writeTo(it) }
    }
}

internal class ReadProjectPatch : tallyvane.knowledge.domain.port.ProjectPatch.Record {
    var title: String? = null
    var problem: String? = null
    lateinit var quote: Quote

    override fun project(title: String?, problem: String?, quote: Quote) {
        this.title = title
        this.problem = problem
        this.quote = quote
    }

    companion object {
        fun of(patch: tallyvane.knowledge.domain.port.ProjectPatch): ReadProjectPatch = ReadProjectPatch().also { patch.writeTo(it) }
    }
}

internal class ReadNewAction : tallyvane.knowledge.domain.port.NewAction.Record {
    var local = 0
    lateinit var action: Action
    var technologies: List<Evidenced<Technology>> = emptyList()
    var collaborators: List<Evidenced<Collaborator>> = emptyList()
    var tags: List<Tag> = emptyList()
    lateinit var embedding: Embedding

    override fun newAction(
        local: Int,
        action: Action,
        technologies: List<Evidenced<Technology>>,
        collaborators: List<Evidenced<Collaborator>>,
        tags: List<Tag>,
        embedding: Embedding,
    ) {
        this.local = local
        this.action = action
        this.technologies = technologies
        this.collaborators = collaborators
        this.tags = tags
        this.embedding = embedding
    }

    companion object {
        fun of(action: tallyvane.knowledge.domain.port.NewAction): ReadNewAction = ReadNewAction().also { action.writeTo(it) }
    }
}

internal class ReadNewOutcome : tallyvane.knowledge.domain.port.NewOutcome.Record {
    lateinit var outcome: Outcome
    var producedBy: List<Int> = emptyList()

    override fun newOutcome(outcome: Outcome, producedBy: List<Int>) {
        this.outcome = outcome
        this.producedBy = producedBy
    }

    companion object {
        fun of(outcome: tallyvane.knowledge.domain.port.NewOutcome): ReadNewOutcome = ReadNewOutcome().also { outcome.writeTo(it) }
    }
}

internal class ReadMatch : SkillMatch.Record {
    var skill: SkillSlug = SkillSlug("")
    var similarity = 0.0

    override fun match(skill: SkillSlug, similarity: Double) {
        this.skill = skill
        this.similarity = similarity
    }

    companion object {
        fun of(match: SkillMatch): ReadMatch = ReadMatch().also { match.writeTo(it) }
    }
}
