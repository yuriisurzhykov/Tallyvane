package tallyvane.knowledge.fixtures

import tallyvane.knowledge.domain.Action
import tallyvane.knowledge.domain.ActionEntry
import tallyvane.knowledge.domain.ActionFacts
import tallyvane.knowledge.domain.ActionId
import tallyvane.knowledge.domain.Bullet
import tallyvane.knowledge.domain.Collaborator
import tallyvane.knowledge.domain.CompanyId
import tallyvane.knowledge.domain.CompanyName
import tallyvane.knowledge.domain.Cursor
import tallyvane.knowledge.domain.Embedding
import tallyvane.knowledge.domain.Gap
import tallyvane.knowledge.domain.Outcome
import tallyvane.knowledge.domain.OutcomeId
import tallyvane.knowledge.domain.Placement
import tallyvane.knowledge.domain.Portfolio
import tallyvane.knowledge.domain.ProjectDetails
import tallyvane.knowledge.domain.ProjectEntry
import tallyvane.knowledge.domain.ProjectFacts
import tallyvane.knowledge.domain.ProjectId
import tallyvane.knowledge.domain.Question
import tallyvane.knowledge.domain.QuestionId
import tallyvane.knowledge.domain.RawEntryId
import tallyvane.knowledge.domain.RoleEntry
import tallyvane.knowledge.domain.RoleFacts
import tallyvane.knowledge.domain.RoleId
import tallyvane.knowledge.domain.RolePeriod
import tallyvane.knowledge.domain.Scope
import tallyvane.knowledge.domain.Skill
import tallyvane.knowledge.domain.SkillMatch
import tallyvane.knowledge.domain.SkillSlug
import tallyvane.knowledge.domain.Stage
import tallyvane.knowledge.domain.Tag
import tallyvane.knowledge.domain.Technology
import tallyvane.knowledge.domain.Vacancy
import tallyvane.knowledge.domain.VacancyId
import tallyvane.knowledge.domain.port.CursorStore
import tallyvane.knowledge.domain.port.Evidenced
import tallyvane.knowledge.domain.port.GraphEdge
import tallyvane.knowledge.domain.port.GraphNode
import tallyvane.knowledge.domain.port.GraphView
import tallyvane.knowledge.domain.port.GraphWrite
import tallyvane.knowledge.domain.port.KnowledgeGraph
import tallyvane.knowledge.domain.port.LoggedQuestion
import tallyvane.knowledge.domain.port.NewAction
import tallyvane.knowledge.domain.port.NewOutcome
import tallyvane.knowledge.domain.port.NodeType
import tallyvane.knowledge.domain.port.ProjectPatch
import tallyvane.knowledge.domain.port.QuestionLog
import tallyvane.knowledge.domain.port.RolePatch
import tallyvane.knowledge.domain.port.SimilarAction
import tallyvane.knowledge.domain.port.Skills
import tallyvane.knowledge.domain.port.Vacancies
import tallyvane.knowledge.domain.port.WriteResult
import kotlin.uuid.Uuid

/**
 * The whole store in memory, behind every port the application stands on, in the way the Postgres adapter
 * is meant to behave: the use cases are tested against this and the adapter is held to the same contract.
 * It is a fake for tests and for nothing else.
 */
class InMemoryKnowledge(
    skills: List<Skill>,
    private val skillEmbeddings: Map<SkillSlug, Embedding>,
) : KnowledgeGraph, Skills, Vacancies, CursorStore, QuestionLog {
    private class Company(val id: CompanyId, val name: String, val norm: String)

    private class Role(val id: RoleId, val company: CompanyId, var title: String?, var period: RolePeriod)

    private class Project(val id: ProjectId, val role: RoleId, var title: String?, var problem: String?) {
        var bullets: List<Bullet> = emptyList()
        var closed = false
    }

    private class Act(
        var id: ActionId,
        val project: ProjectId,
        val action: Action,
        val technologies: MutableList<Technology>,
        val collaborators: MutableList<Collaborator>,
        val tags: MutableList<Tag>,
        val embedding: Embedding,
    )

    private class Out(val id: OutcomeId, val project: ProjectId, val outcome: Outcome, val actions: MutableList<ActionId>)

    private class Asked(val id: QuestionId, val question: Question, val target: String?) {
        var answered = false
    }

    private val catalog = skills
    private var rawEntries = 0
    private val companies = mutableListOf<Company>()
    private val roles = mutableListOf<Role>()
    private val projects = mutableListOf<Project>()
    private val actions = mutableListOf<Act>()
    private val outcomes = mutableListOf<Out>()
    private val evidence = mutableMapOf<String, MutableList<String>>()
    private val technologies = mutableMapOf<String, Technology>()
    private val collaborators = mutableMapOf<String, Collaborator>()
    private val taggedSkills = mutableSetOf<SkillSlug>()
    private var position: Cursor = Cursor.atStart()
    private val questions = mutableListOf<Asked>()
    private val vacancies = mutableMapOf<VacancyId, Vacancy>()

    /** How many answers were saved, including those whose reading failed. */
    fun rawEntries(): Int = rawEntries

    // KnowledgeGraph

    override suspend fun recordRaw(text: String, source: String, question: QuestionId?, cursor: Cursor): RawEntryId {
        rawEntries++
        return RawEntryId(Uuid.random())
    }

    override suspend fun facts(cursor: Cursor): ProjectFacts {
        val project = projects.first { it.id == ReadCursor.of(cursor).project }
        val role = roles.first { it.id == project.role }
        val company = companies.first { it.id == role.company }
        val mine = actions.filter { it.project == project.id }
        return ProjectFacts(
            company.name,
            RoleFacts(role.title, role.period),
            ProjectDetails(project.title, project.problem),
            mine.map { ActionFacts(it.id, it.action, it.technologies.toList()) },
            mine.flatMap { it.collaborators }.distinct(),
            outcomes.filter { it.project == project.id }.map { it.outcome },
        )
    }

    override suspend fun write(write: GraphWrite): WriteResult {
        val reading = Reading()
        write.writeTo(reading)
        val created = mutableListOf<GraphNode>()
        val cursor = ReadCursor.of(reading.cursor)
        val companyId: CompanyId
        val roleId: RoleId
        val projectId: ProjectId

        when (reading.placement) {
            Placement.NEW_ROLE -> {
                val (name, norm) = normalisedAndName(valueOf(reading.company!!).first)
                val existing = companies.firstOrNull { it.norm == norm }
                val company = existing ?: Company(CompanyId(Uuid.random()), name, norm).also {
                    companies += it
                    created += GraphNode(it.id.uuid.toString(), NodeType.COMPANY, it.name)
                }
                companyId = company.id
                evidenceFor(company.id.uuid.toString(), valueOf(reading.company!!).second)
                val role = Role(RoleId(Uuid.random()), companyId, null, RolePeriod(null, null, null)).also { roles += it }
                roleId = role.id
                created += GraphNode(role.id.uuid.toString(), NodeType.ROLE, "role")
                projectId = newProject(roleId, created)
            }
            Placement.NEW_PROJECT_IN_ROLE -> {
                companyId = cursor.company!!
                roleId = cursor.role!!
                projectId = newProject(roleId, created)
            }
            Placement.SAME_PROJECT -> {
                companyId = cursor.company!!
                roleId = cursor.role!!
                projectId = cursor.project!!
            }
        }

        reading.role?.let { patch ->
            val role = roles.first { it.id == roleId }
            val read = ReadRolePatch.of(patch)
            role.title = role.title ?: read.title
            role.period = role.period.completedBy(read.period)
            evidenceFor(role.id.uuid.toString(), read.quote)
            created.removeAll { it.id == role.id.uuid.toString() }
            created += GraphNode(role.id.uuid.toString(), NodeType.ROLE, role.title ?: "role")
        }
        reading.project?.let { patch ->
            val project = projects.first { it.id == projectId }
            val read = ReadProjectPatch.of(patch)
            project.title = project.title ?: read.title
            project.problem = project.problem ?: read.problem
            evidenceFor(project.id.uuid.toString(), read.quote)
            created.removeAll { it.id == project.id.uuid.toString() }
            created += GraphNode(project.id.uuid.toString(), NodeType.PROJECT, project.title ?: "project")
        }

        val ids = mutableMapOf<Int, ActionId>()
        for (newAction in reading.actions) {
            val read = ReadNewAction.of(newAction)
            val act =
                Act(
                    ActionId(Uuid.random()), projectId, read.action,
                    mutableListOf(), mutableListOf(), read.tags.toMutableList(), read.embedding,
                )
            actions += act
            ids[read.local] = act.id
            val shape = ReadAction.of(read.action)
            created += GraphNode(act.id.uuid.toString(), NodeType.ACTION, "${shape.verb} ${shape.what}")
            evidenceFor(act.id.uuid.toString(), shape.quote)
            read.technologies.forEach { evidenced ->
                val (technology, quote) = valueOf(evidenced)
                act.technologies += technology
                val key = "tech:" + nameOf(technology).lowercase()
                if (technologies.putIfAbsent(key, technology) == null) created += GraphNode(key, NodeType.TECHNOLOGY, nameOf(technology))
                evidenceFor(key, quote)
            }
            read.collaborators.forEach { evidenced ->
                val (collaborator, quote) = valueOf(evidenced)
                act.collaborators += collaborator
                val key = "collab:" + nameOf(collaborator)
                if (collaborators.putIfAbsent(key, collaborator) == null) created += GraphNode(key, NodeType.COLLABORATOR, nameOf(collaborator))
                evidenceFor(key, quote)
            }
            read.tags.forEach { tag ->
                val slug = ReadTag.of(tag).skill
                if (taggedSkills.add(slug)) created += GraphNode("skill:${slug.value}", NodeType.SKILL, nameOfSkill(slug))
            }
        }
        for (newOutcome in reading.outcomes) {
            val read = ReadNewOutcome.of(newOutcome)
            val out = Out(OutcomeId(Uuid.random()), projectId, read.outcome, read.producedBy.mapNotNull { ids[it] }.toMutableList())
            outcomes += out
            val shape = ReadOutcome.of(read.outcome)
            created += GraphNode(out.id.uuid.toString(), NodeType.OUTCOME, shape.what)
            evidenceFor(out.id.uuid.toString(), shape.quote)
        }

        val after = Cursor.atStart().inProject(companyId, roleId, projectId)
        return WriteResult(after, created, reading.actions.indices.map { ids.getValue(it) })
    }

    private fun newProject(role: RoleId, created: MutableList<GraphNode>): ProjectId {
        val project = Project(ProjectId(Uuid.random()), role, null, null).also { projects += it }
        created += GraphNode(project.id.uuid.toString(), NodeType.PROJECT, "project")
        return project.id
    }

    private fun evidenceFor(id: String, quote: tallyvane.knowledge.domain.Quote) {
        evidence.getOrPut(id) { mutableListOf() } += textOf(quote)
    }

    private fun nameOfSkill(slug: SkillSlug): String = ReadSkill.of(catalog.first { it.isSlug(slug) }).name

    override suspend fun similarActions(embedding: Embedding, excluding: List<ActionId>, limit: Int): List<SimilarAction> =
        actions
            .filter { it.id !in excluding }
            .map { SimilarAction(it.id, ReadAction.of(it.action).let { shape -> "${shape.verb} ${shape.what}" }, it.embedding.similarityTo(embedding)) }
            .sortedByDescending { it.similarity }
            .take(limit)

    override suspend fun merge(newAction: ActionId, existing: ActionId) {
        val from = actions.first { it.id == newAction }
        val into = actions.first { it.id == existing }
        from.technologies.filter { it !in into.technologies }.forEach { into.technologies += it }
        from.collaborators.filter { it !in into.collaborators }.forEach { into.collaborators += it }
        from.tags.forEach { tag -> if (into.tags.none { ReadTag.of(it).skill == ReadTag.of(tag).skill }) into.tags += tag }
        evidence.remove(from.id.uuid.toString())?.let { evidence.getOrPut(into.id.uuid.toString()) { mutableListOf() } += it }
        outcomes.forEach { out -> if (out.actions.remove(from.id)) out.actions += into.id }
        actions.remove(from)
    }

    override suspend fun closeProject(cursor: Cursor, bullets: List<Bullet>) {
        val project = projects.first { it.id == ReadCursor.of(cursor).project }
        project.bullets = bullets
        project.closed = true
    }

    override suspend fun portfolio(): Portfolio =
        Portfolio(
            roles.map { role ->
                val company = companies.first { it.id == role.company }
                RoleEntry(
                    company.name,
                    role.title,
                    role.period,
                    projects.filter { it.role == role.id }.map { project ->
                        ProjectEntry(
                            project.title,
                            project.bullets,
                            actions.filter { it.project == project.id }.map { act ->
                                ActionEntry(
                                    act.id,
                                    act.tags.associate { ReadTag.of(it).let { tag -> tag.skill to tag.weight } },
                                    outcomes.any { out -> act.id in out.actions && ReadOutcome.of(out.outcome).hasMetric },
                                )
                            },
                        )
                    },
                )
            },
        )

    override suspend fun view(): GraphView {
        val nodes = mutableListOf<GraphNode>()
        val edges = mutableListOf<GraphEdge>()
        companies.forEach { nodes += GraphNode(it.id.uuid.toString(), NodeType.COMPANY, it.name) }
        roles.forEach {
            nodes += GraphNode(it.id.uuid.toString(), NodeType.ROLE, it.title ?: "role")
            edges += GraphEdge(it.company.uuid.toString(), it.id.uuid.toString(), "has_role")
        }
        projects.forEach {
            nodes += GraphNode(it.id.uuid.toString(), NodeType.PROJECT, it.title ?: "project")
            edges += GraphEdge(it.role.uuid.toString(), it.id.uuid.toString(), "has_project")
        }
        actions.forEach { act ->
            val shape = ReadAction.of(act.action)
            nodes += GraphNode(act.id.uuid.toString(), NodeType.ACTION, "${shape.verb} ${shape.what}")
            edges += GraphEdge(act.project.uuid.toString(), act.id.uuid.toString(), "has_action")
            act.technologies.forEach { edges += GraphEdge(act.id.uuid.toString(), "tech:" + nameOf(it).lowercase(), "uses") }
            act.collaborators.forEach { edges += GraphEdge(act.id.uuid.toString(), "collab:" + nameOf(it), "with") }
            act.tags.forEach { tag ->
                val read = ReadTag.of(tag)
                edges += GraphEdge(act.id.uuid.toString(), "skill:${read.skill.value}", "tagged", read.weight)
            }
        }
        technologies.forEach { (key, technology) -> nodes += GraphNode(key, NodeType.TECHNOLOGY, nameOf(technology)) }
        collaborators.forEach { (key, collaborator) -> nodes += GraphNode(key, NodeType.COLLABORATOR, nameOf(collaborator)) }
        taggedSkills.forEach { nodes += GraphNode("skill:${it.value}", NodeType.SKILL, nameOfSkill(it)) }
        outcomes.forEach { out ->
            nodes += GraphNode(out.id.uuid.toString(), NodeType.OUTCOME, ReadOutcome.of(out.outcome).what)
            out.actions.forEach { edges += GraphEdge(it.uuid.toString(), out.id.uuid.toString(), "led_to") }
        }
        return GraphView(nodes, edges)
    }

    override suspend fun quotesOfNode(id: String): List<String> = evidence[id].orEmpty()

    override suspend fun quotesOfActions(actions: List<ActionId>): List<String> =
        (
            actions.flatMap { evidence[it.uuid.toString()].orEmpty() } +
                outcomes.filter { out -> out.actions.any { it in actions } }.flatMap { evidence[it.id.uuid.toString()].orEmpty() }
        ).distinct()

    override suspend fun wipe() {
        rawEntries = 0
        companies.clear()
        roles.clear()
        projects.clear()
        actions.clear()
        outcomes.clear()
        evidence.clear()
        technologies.clear()
        collaborators.clear()
        taggedSkills.clear()
        position = Cursor.atStart()
        questions.clear()
        vacancies.clear()
    }

    // Skills

    override suspend fun all(): List<Skill> = catalog

    override suspend fun nearest(embedding: Embedding, limit: Int): List<SkillMatch> =
        skillEmbeddings.entries
            .map { (slug, vector) -> SkillMatch(slug, vector.similarityTo(embedding)) }
            .sortedByDescending { ReadMatch.of(it).similarity }
            .take(limit)

    // Vacancies

    override suspend fun save(vacancy: Vacancy): VacancyId = VacancyId(Uuid.random()).also { vacancies[it] = vacancy }

    override suspend fun find(id: VacancyId): Vacancy = vacancies.getValue(id)

    // CursorStore

    override suspend fun cursor(): Cursor = position

    override suspend fun save(cursor: Cursor) {
        position = cursor
    }

    // QuestionLog

    override suspend fun pending(): LoggedQuestion? =
        questions.lastOrNull { !it.answered }?.let { LoggedQuestion(it.id, it.question) }

    override suspend fun find(id: QuestionId): LoggedQuestion? =
        questions.firstOrNull { it.id == id }?.let { LoggedQuestion(it.id, it.question) }

    override suspend fun log(question: Question, cursor: Cursor): QuestionId {
        val read = ReadCursor.of(cursor)
        val gap = ReadQuestion.of(question).gap
        val target =
            when (gap.scope()) {
                Scope.ROLE -> "role:${read.role?.uuid}"
                Scope.PROJECT -> "project:${read.project?.uuid}"
                Scope.CONVERSATION -> null
            }
        return QuestionId(Uuid.random()).also { questions += Asked(it, question, target) }
    }

    override suspend fun answered(id: QuestionId) {
        questions.first { it.id == id }.answered = true
    }

    override suspend fun askedAbout(cursor: Cursor): Set<Gap> {
        val read = ReadCursor.of(cursor)
        val keys = setOf("role:${read.role?.uuid}", "project:${read.project?.uuid}")
        return questions.filter { it.target in keys }.map { ReadQuestion.of(it.question).gap }.toSet()
    }

    @Suppress("unused")
    private fun stageOf(cursor: Cursor): Stage = ReadCursor.of(cursor).stage

    private class Reading : GraphWrite.Record {
        lateinit var placement: Placement
        lateinit var cursor: Cursor
        var company: Evidenced<CompanyName>? = null
        var role: RolePatch? = null
        var project: ProjectPatch? = null
        val actions = mutableListOf<NewAction>()
        val outcomes = mutableListOf<NewOutcome>()

        override fun where(rawEntry: RawEntryId, placement: Placement, cursor: Cursor) {
            this.placement = placement
            this.cursor = cursor
        }

        override fun company(company: Evidenced<CompanyName>) {
            this.company = company
        }

        override fun role(role: RolePatch) {
            this.role = role
        }

        override fun project(project: ProjectPatch) {
            this.project = project
        }

        override fun action(action: NewAction) {
            actions += action
        }

        override fun outcome(outcome: NewOutcome) {
            outcomes += outcome
        }
    }
}
