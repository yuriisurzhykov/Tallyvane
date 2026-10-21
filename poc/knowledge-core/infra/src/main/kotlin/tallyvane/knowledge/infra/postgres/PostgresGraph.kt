package tallyvane.knowledge.infra.postgres

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import tallyvane.knowledge.domain.Action
import tallyvane.knowledge.domain.ActionEntry
import tallyvane.knowledge.domain.ActionFacts
import tallyvane.knowledge.domain.ActionId
import tallyvane.knowledge.domain.ActionKind
import tallyvane.knowledge.domain.Bullet
import tallyvane.knowledge.domain.Collaborator
import tallyvane.knowledge.domain.CompanyId
import tallyvane.knowledge.domain.Cursor
import tallyvane.knowledge.domain.Direction
import tallyvane.knowledge.domain.Embedding
import tallyvane.knowledge.domain.Outcome
import tallyvane.knowledge.domain.OutcomeFacts
import tallyvane.knowledge.domain.Placement
import tallyvane.knowledge.domain.Portfolio
import tallyvane.knowledge.domain.ProjectDetails
import tallyvane.knowledge.domain.ProjectEntry
import tallyvane.knowledge.domain.ProjectFacts
import tallyvane.knowledge.domain.ProjectId
import tallyvane.knowledge.domain.QuestionId
import tallyvane.knowledge.domain.Quote
import tallyvane.knowledge.domain.RawEntryId
import tallyvane.knowledge.domain.RoleEntry
import tallyvane.knowledge.domain.RoleFacts
import tallyvane.knowledge.domain.RoleId
import tallyvane.knowledge.domain.RolePeriod
import tallyvane.knowledge.domain.SkillSlug
import tallyvane.knowledge.domain.Technology
import tallyvane.knowledge.domain.CompanyName
import tallyvane.knowledge.domain.port.Evidenced
import tallyvane.knowledge.domain.port.NewAction
import tallyvane.knowledge.domain.port.NewOutcome
import tallyvane.knowledge.domain.port.ProjectPatch
import tallyvane.knowledge.domain.port.RolePatch
import tallyvane.knowledge.domain.port.GraphEdge
import tallyvane.knowledge.domain.port.GraphNode
import tallyvane.knowledge.domain.port.GraphView
import tallyvane.knowledge.domain.port.GraphWrite
import tallyvane.knowledge.domain.port.KnowledgeGraph
import tallyvane.knowledge.domain.port.NodeType
import tallyvane.knowledge.domain.port.SimilarAction
import tallyvane.knowledge.domain.port.WriteResult
import kotlin.uuid.Uuid

@Serializable
private class StoredBullet(val text: String, val covers: List<String>)

/**
 * The graph of what the person said, in Postgres. It behaves as `InMemoryKnowledge` does, which the use cases
 * were tested against, and the integration test runs the same demo over both.
 *
 * Every call is one transaction, except that what the person typed is saved on its own, first, so that it is
 * kept even when reading it fails. Evidence rows are written for every entity an answer creates.
 */
internal class PostgresGraph(private val db: Db) : KnowledgeGraph {
    override suspend fun recordRaw(text: String, source: String, question: QuestionId?, cursor: Cursor): RawEntryId =
        db.tx {
            val id = newId()
            val at = ReadCursor.of(cursor)
            RawEntriesTable.insert {
                it[RawEntriesTable.id] = id
                it[origin] = source
                it[questionId] = question?.uuid
                it[context] =
                    Json.encodeToString(
                        buildJsonObject {
                            put("companyId", at.company?.uuid?.toString()?.let(::JsonPrimitive) ?: JsonNull)
                            put("roleId", at.role?.uuid?.toString()?.let(::JsonPrimitive) ?: JsonNull)
                            put("projectId", at.project?.uuid?.toString()?.let(::JsonPrimitive) ?: JsonNull)
                        },
                    )
                it[RawEntriesTable.text] = text
            }
            RawEntryId(id)
        }

    override suspend fun facts(cursor: Cursor): ProjectFacts =
        db.tx {
            val projectId = checkNotNull(ReadCursor.of(cursor).project) { "There is no project to read the facts of." }.uuid
            val project = ProjectsTable.selectAll().where { ProjectsTable.id eq projectId }.single()
            val role = RolesTable.selectAll().where { RolesTable.id eq project[ProjectsTable.roleId] }.single()
            val company = CompaniesTable.selectAll().where { CompaniesTable.id eq role[RolesTable.companyId] }.single()
            val actionRows = ActionsTable.selectAll().where { ActionsTable.projectId eq projectId }.orderBy(ActionsTable.seq).toList()
            val actionIds = actionRows.map { it[ActionsTable.id] }
            val technologies = technologiesOf(actionIds)
            val collaborators = collaboratorsOf(actionIds)
            val quotes = firstQuotes(actionIds)

            val outcomeRows = OutcomesTable.selectAll().where { OutcomesTable.projectId eq projectId }.orderBy(OutcomesTable.seq).toList()
            val outcomeQuotes = firstQuotes(outcomeRows.map { it[OutcomesTable.id] })
            val producers = ActionOutcomesTable.selectAll()
                .where { ActionOutcomesTable.outcomeId inList outcomeRows.map { it[OutcomesTable.id] } }
                .groupBy({ it[ActionOutcomesTable.outcomeId] }, { ActionId(it[ActionOutcomesTable.actionId]) })

            ProjectFacts(
                company[CompaniesTable.name],
                RoleFacts(role[RolesTable.title], periodOf(role)),
                ProjectDetails(project[ProjectsTable.title], project[ProjectsTable.problem]),
                actionRows.map { row ->
                    val id = row[ActionsTable.id]
                    ActionFacts(
                        ActionId(id),
                        actionOf(row, quotes.getValue(id)),
                        technologies[id].orEmpty(),
                    )
                },
                collaborators.values.flatten().distinct(),
                outcomeRows.map { row ->
                    val id = row[OutcomesTable.id]
                    OutcomeFacts(outcomeOf(row, outcomeQuotes.getValue(id)), producers[id].orEmpty())
                },
            )
        }

    override suspend fun write(write: GraphWrite): WriteResult = db.tx { Applying(this, write).apply() }

    /** One [GraphWrite] being applied inside the transaction that [tx] is. */
    private class Applying(private val tx: JdbcTransaction, write: GraphWrite) {
        private val reading = Reading().also { write.writeTo(it) }
        private val created = mutableListOf<GraphNode>()

        fun apply(): WriteResult {
            val at = ReadCursor.of(reading.cursor)
            val (companyId, roleId, projectId) = place(at)
            patchRole(roleId)
            patchProject(projectId)
            val actionIds = reading.actions.map { addAction(projectId, it) }.toMap()
            reading.outcomes.forEach { addOutcome(projectId, it, actionIds) }
            val after = Cursor.atStart().inProject(CompanyId(companyId), RoleId(roleId), ProjectId(projectId))
            return WriteResult(after, created, reading.actions.map { ActionId(actionIds.getValue(ReadNewAction.of(it).local)) })
        }

        private fun place(at: ReadCursor): Triple<Uuid, Uuid, Uuid> =
            when (reading.placement) {
                Placement.NEW_ROLE -> {
                    val (name, evidence) = valueOf(checkNotNull(reading.company) { "A new role needs a company." })
                    val read = ReadCompany.of(name)
                    val existing = CompaniesTable.selectAll().where { CompaniesTable.nameNorm eq read.normalised }.singleOrNull()
                    val company = existing?.get(CompaniesTable.id) ?: newId().also { id ->
                        CompaniesTable.insert {
                            it[CompaniesTable.id] = id
                            it[CompaniesTable.name] = read.name
                            it[nameNorm] = read.normalised
                        }
                        created += GraphNode(id.toString(), NodeType.COMPANY, read.name)
                    }
                    evidence(EntityType.COMPANY, company, evidence)
                    val role = newId().also { id ->
                        RolesTable.insert {
                            it[RolesTable.id] = id
                            it[companyId] = company
                        }
                        created += GraphNode(id.toString(), NodeType.ROLE, "role")
                    }
                    Triple(company, role, newProject(role))
                }
                Placement.NEW_PROJECT_IN_ROLE -> {
                    val role = checkNotNull(at.role) { "There is no role to add a project to." }.uuid
                    Triple(checkNotNull(at.company).uuid, role, newProject(role))
                }
                Placement.SAME_PROJECT ->
                    Triple(
                        checkNotNull(at.company) { "There is no company yet." }.uuid,
                        checkNotNull(at.role).uuid,
                        checkNotNull(at.project) { "There is no project yet." }.uuid,
                    )
            }

        private fun newProject(role: Uuid): Uuid =
            newId().also { id ->
                ProjectsTable.insert {
                    it[ProjectsTable.id] = id
                    it[roleId] = role
                    it[closed] = false
                }
                created += GraphNode(id.toString(), NodeType.PROJECT, "project")
            }

        private fun patchRole(roleId: Uuid) {
            val patch = reading.role ?: return
            val read = ReadRolePatch.of(patch)
            val row = RolesTable.selectAll().where { RolesTable.id eq roleId }.single()
            val title = row[RolesTable.title] ?: read.title
            val period = ReadPeriod.of(periodOf(row).completedBy(read.period))
            RolesTable.update({ RolesTable.id eq roleId }) {
                it[RolesTable.title] = title
                it[startDate] = period.start
                it[endDate] = period.end
                it[isCurrent] = period.current
            }
            evidence(EntityType.ROLE, roleId, read.quote)
            created.removeAll { it.id == roleId.toString() }
            created += GraphNode(roleId.toString(), NodeType.ROLE, title ?: "role")
        }

        private fun patchProject(projectId: Uuid) {
            val patch = reading.project ?: return
            val read = ReadProjectPatch.of(patch)
            val row = ProjectsTable.selectAll().where { ProjectsTable.id eq projectId }.single()
            val title = row[ProjectsTable.title] ?: read.title
            ProjectsTable.update({ ProjectsTable.id eq projectId }) {
                it[ProjectsTable.title] = title
                it[problem] = row[ProjectsTable.problem] ?: read.problem
            }
            evidence(EntityType.PROJECT, projectId, read.quote)
            created.removeAll { it.id == projectId.toString() }
            created += GraphNode(projectId.toString(), NodeType.PROJECT, title ?: "project")
        }

        private fun addAction(projectId: Uuid, newAction: NewAction): Pair<Int, Uuid> {
            val read = ReadNewAction.of(newAction)
            val shape = ReadAction.of(read.action)
            val id = newId()
            ActionsTable.insert {
                it[ActionsTable.id] = id
                it[ActionsTable.projectId] = projectId
                it[kind] = shape.kind.wireName()
                it[verb] = shape.verb
                it[what] = shape.what
                it[finding] = shape.finding
                it[embedding] = valuesOf(read.embedding)
            }
            created += GraphNode(id.toString(), NodeType.ACTION, shape.label())
            evidence(EntityType.ACTION, id, shape.quote)

            read.technologies.distinctBy { nameOf(valueOf(it).first).lowercase() }.forEach { evidenced ->
                val (technology, quote) = valueOf(evidenced)
                val name = nameOf(technology)
                val existing = TechnologiesTable.selectAll().where { TechnologiesTable.name.lowerCase() eq name.lowercase() }.singleOrNull()
                val technologyId = existing?.get(TechnologiesTable.id) ?: newId().also { tid ->
                    TechnologiesTable.insert {
                        it[TechnologiesTable.id] = tid
                        it[TechnologiesTable.name] = name
                        it[aliases] = emptyList()
                        it[seeded] = false
                    }
                }
                val alreadyDrawn = ActionTechnologiesTable.selectAll().where { ActionTechnologiesTable.technologyId eq technologyId }.any()
                ActionTechnologiesTable.insert {
                    it[actionId] = id
                    it[ActionTechnologiesTable.technologyId] = technologyId
                }
                if (!alreadyDrawn) created += GraphNode(technologyId.toString(), NodeType.TECHNOLOGY, existing?.get(TechnologiesTable.name) ?: name)
                evidence(EntityType.TECHNOLOGY, technologyId, quote)
            }
            read.collaborators.distinctBy { roleNameOf(valueOf(it).first) }.forEach { evidenced ->
                val (collaborator, quote) = valueOf(evidenced)
                val role = roleNameOf(collaborator)
                val existing = CollaboratorsTable.selectAll().where { CollaboratorsTable.roleName eq role }.singleOrNull()
                val collaboratorId = existing?.get(CollaboratorsTable.id) ?: newId().also { cid ->
                    CollaboratorsTable.insert {
                        it[CollaboratorsTable.id] = cid
                        it[roleName] = role
                    }
                }
                val alreadyDrawn = ActionCollaboratorsTable.selectAll().where { ActionCollaboratorsTable.collaboratorId eq collaboratorId }.any()
                ActionCollaboratorsTable.insert {
                    it[actionId] = id
                    it[ActionCollaboratorsTable.collaboratorId] = collaboratorId
                }
                if (!alreadyDrawn) created += GraphNode(collaboratorId.toString(), NodeType.COLLABORATOR, role)
                evidence(EntityType.COLLABORATOR, collaboratorId, quote)
            }
            read.tags.forEach { tag ->
                val tagRead = ReadTag.of(tag)
                val skill = SkillsTable.selectAll().where { SkillsTable.slug eq tagRead.skill.value }.singleOrNull() ?: return@forEach
                val skillId = skill[SkillsTable.id]
                val alreadyDrawn = ActionSkillsTable.selectAll().where { ActionSkillsTable.skillId eq skillId }.any()
                ActionSkillsTable.insert {
                    it[actionId] = id
                    it[ActionSkillsTable.skillId] = skillId
                    it[weight] = tagRead.weight.toFloat()
                }
                if (!alreadyDrawn) created += GraphNode(skillId.toString(), NodeType.SKILL, skill[SkillsTable.name])
            }
            return read.local to id
        }

        private fun addOutcome(projectId: Uuid, newOutcome: NewOutcome, actions: Map<Int, Uuid>) {
            val read = ReadNewOutcome.of(newOutcome)
            val shape = ReadOutcome.of(read.outcome)
            val id = newId()
            OutcomesTable.insert {
                it[OutcomesTable.id] = id
                it[OutcomesTable.projectId] = projectId
                it[what] = shape.what
                it[dir] = shape.direction?.wireName()
                it[value] = shape.value
                it[unit] = shape.unit
                it[approx] = shape.approx
                it[hasMetric] = shape.hasMetric
            }
            read.producedBy.mapNotNull { actions[it] }.distinct().forEach { produced ->
                ActionOutcomesTable.insert {
                    it[actionId] = produced
                    it[outcomeId] = id
                }
            }
            created += GraphNode(id.toString(), NodeType.OUTCOME, shape.what)
            evidence(EntityType.OUTCOME, id, shape.quote)
        }

        private fun evidence(type: String, entity: Uuid, quote: Quote) {
            EvidenceTable.insert {
                it[id] = newId()
                it[entityType] = type
                it[entityId] = entity
                it[rawEntryId] = checkNotNull(reading.rawEntry) { "A write says which answer it comes from." }.uuid
                it[EvidenceTable.quote] = textOf(quote)
            }
        }
    }

    private class Reading : GraphWrite.Record {
        var rawEntry: RawEntryId? = null
        lateinit var placement: Placement
        lateinit var cursor: Cursor
        var company: Evidenced<CompanyName>? = null
        var role: RolePatch? = null
        var project: ProjectPatch? = null
        val actions = mutableListOf<NewAction>()
        val outcomes = mutableListOf<NewOutcome>()

        override fun where(rawEntry: RawEntryId, placement: Placement, cursor: Cursor) {
            this.rawEntry = rawEntry
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

    override suspend fun similarActions(cursor: Cursor, embedding: Embedding, excluding: List<ActionId>, limit: Int): List<SimilarAction> =
        db.tx {
            val project = checkNotNull(ReadCursor.of(cursor).project) { "There is no project to look for similar actions in." }.uuid
            val distance = CosineDistance(ActionsTable.embedding, valuesOf(embedding))
            val excluded = excluding.map { it.uuid }
            ActionsTable.select(ActionsTable.id, ActionsTable.verb, ActionsTable.what, distance)
                .where { (ActionsTable.projectId eq project) and (ActionsTable.id notInList excluded) }
                .orderBy(distance)
                .limit(limit)
                .map { SimilarAction(ActionId(it[ActionsTable.id]), "${it[ActionsTable.verb]} ${it[ActionsTable.what]}", similarityOf(it[distance])) }
        }

    override suspend fun merge(newAction: ActionId, existing: ActionId) {
        db.tx {
            val from = newAction.uuid
            val into = existing.uuid
            val have = ActionTechnologiesTable.selectAll().where { ActionTechnologiesTable.actionId eq into }.map { it[ActionTechnologiesTable.technologyId] }.toSet()
            ActionTechnologiesTable.selectAll().where { ActionTechnologiesTable.actionId eq from }.map { it[ActionTechnologiesTable.technologyId] }
                .filter { it !in have }
                .forEach { technology ->
                    ActionTechnologiesTable.insert {
                        it[actionId] = into
                        it[technologyId] = technology
                    }
                }
            val withThem = ActionCollaboratorsTable.selectAll().where { ActionCollaboratorsTable.actionId eq into }.map { it[ActionCollaboratorsTable.collaboratorId] }.toSet()
            ActionCollaboratorsTable.selectAll().where { ActionCollaboratorsTable.actionId eq from }.map { it[ActionCollaboratorsTable.collaboratorId] }
                .filter { it !in withThem }
                .forEach { collaborator ->
                    ActionCollaboratorsTable.insert {
                        it[actionId] = into
                        it[collaboratorId] = collaborator
                    }
                }
            val tagged = ActionSkillsTable.selectAll().where { ActionSkillsTable.actionId eq into }.map { it[ActionSkillsTable.skillId] }.toSet()
            ActionSkillsTable.selectAll().where { ActionSkillsTable.actionId eq from }
                .filter { it[ActionSkillsTable.skillId] !in tagged }
                .forEach { tag ->
                    ActionSkillsTable.insert {
                        it[actionId] = into
                        it[skillId] = tag[ActionSkillsTable.skillId]
                        it[weight] = tag[ActionSkillsTable.weight]
                    }
                }
            val produced = ActionOutcomesTable.selectAll().where { ActionOutcomesTable.actionId eq into }.map { it[ActionOutcomesTable.outcomeId] }.toSet()
            ActionOutcomesTable.selectAll().where { ActionOutcomesTable.actionId eq from }.map { it[ActionOutcomesTable.outcomeId] }
                .filter { it !in produced }
                .forEach { outcome ->
                    ActionOutcomesTable.insert {
                        it[actionId] = into
                        it[outcomeId] = outcome
                    }
                }
            EvidenceTable.update({ EvidenceTable.entityId eq from }) { it[entityId] = into }
            ActionsTable.deleteWhere { id eq from }
        }
    }

    override suspend fun closeProject(cursor: Cursor, bullets: List<Bullet>) {
        db.tx {
            val project = checkNotNull(ReadCursor.of(cursor).project) { "There is no project to close." }.uuid
            val stored = bullets.map { bullet ->
                val read = ReadBullet.of(bullet)
                StoredBullet(read.text, read.covers.map { it.uuid.toString() })
            }
            ProjectsTable.update({ ProjectsTable.id eq project }) {
                it[canonicalBullets] = Json.encodeToString(stored)
                it[closed] = true
            }
        }
    }

    override suspend fun portfolio(): Portfolio =
        db.tx {
            val companies = CompaniesTable.selectAll().associate { it[CompaniesTable.id] to it[CompaniesTable.name] }
            val actions = ActionsTable.select(ActionsTable.id, ActionsTable.projectId).orderBy(ActionsTable.seq).toList()
            val slugs = SkillsTable.selectAll().associate { it[SkillsTable.id] to it[SkillsTable.slug] }
            val weights = ActionSkillsTable.selectAll()
                .groupBy({ it[ActionSkillsTable.actionId] }, { slugs.getValue(it[ActionSkillsTable.skillId]) to it[ActionSkillsTable.weight].toDouble() })
            val measured = OutcomesTable.select(OutcomesTable.id).where { OutcomesTable.hasMetric eq true }.map { it[OutcomesTable.id] }.toSet()
            val withMetric = ActionOutcomesTable.selectAll().filter { it[ActionOutcomesTable.outcomeId] in measured }.map { it[ActionOutcomesTable.actionId] }.toSet()
            val projects = ProjectsTable.selectAll().orderBy(ProjectsTable.seq).toList()

            Portfolio(
                RolesTable.selectAll().orderBy(RolesTable.seq).map { role ->
                    RoleEntry(
                        companies.getValue(role[RolesTable.companyId]),
                        role[RolesTable.title],
                        periodOf(role),
                        projects.filter { it[ProjectsTable.roleId] == role[RolesTable.id] }.map { project ->
                            ProjectEntry(
                                project[ProjectsTable.title],
                                bulletsOf(project[ProjectsTable.canonicalBullets]),
                                actions.filter { it[ActionsTable.projectId] == project[ProjectsTable.id] }.map { action ->
                                    val id = action[ActionsTable.id]
                                    ActionEntry(
                                        ActionId(id),
                                        weights[id].orEmpty().associate { (slug, weight) -> SkillSlug(slug) to weight },
                                        id in withMetric,
                                    )
                                },
                            )
                        },
                    )
                },
            )
        }

    override suspend fun view(): GraphView =
        db.tx {
            val nodes = mutableListOf<GraphNode>()
            val edges = mutableListOf<GraphEdge>()
            CompaniesTable.selectAll().forEach { nodes += GraphNode(it[CompaniesTable.id].toString(), NodeType.COMPANY, it[CompaniesTable.name]) }
            RolesTable.selectAll().orderBy(RolesTable.seq).forEach {
                nodes += GraphNode(it[RolesTable.id].toString(), NodeType.ROLE, it[RolesTable.title] ?: "role")
                edges += GraphEdge(it[RolesTable.companyId].toString(), it[RolesTable.id].toString(), "has_role")
            }
            ProjectsTable.selectAll().orderBy(ProjectsTable.seq).forEach {
                nodes += GraphNode(it[ProjectsTable.id].toString(), NodeType.PROJECT, it[ProjectsTable.title] ?: "project")
                edges += GraphEdge(it[ProjectsTable.roleId].toString(), it[ProjectsTable.id].toString(), "has_project")
            }
            ActionsTable.selectAll().orderBy(ActionsTable.seq).forEach {
                nodes += GraphNode(it[ActionsTable.id].toString(), NodeType.ACTION, "${it[ActionsTable.verb]} ${it[ActionsTable.what]}")
                edges += GraphEdge(it[ActionsTable.projectId].toString(), it[ActionsTable.id].toString(), "has_action")
            }
            val technologies = TechnologiesTable.selectAll().associate { it[TechnologiesTable.id] to it[TechnologiesTable.name] }
            val usedTechnologies = ActionTechnologiesTable.selectAll().toList()
            usedTechnologies.map { it[ActionTechnologiesTable.technologyId] }.distinct().forEach {
                nodes += GraphNode(it.toString(), NodeType.TECHNOLOGY, technologies.getValue(it))
            }
            usedTechnologies.forEach { edges += GraphEdge(it[ActionTechnologiesTable.actionId].toString(), it[ActionTechnologiesTable.technologyId].toString(), "uses") }
            val collaborators = CollaboratorsTable.selectAll().associate { it[CollaboratorsTable.id] to it[CollaboratorsTable.roleName] }
            val workedWith = ActionCollaboratorsTable.selectAll().toList()
            workedWith.map { it[ActionCollaboratorsTable.collaboratorId] }.distinct().forEach {
                nodes += GraphNode(it.toString(), NodeType.COLLABORATOR, collaborators.getValue(it))
            }
            workedWith.forEach { edges += GraphEdge(it[ActionCollaboratorsTable.actionId].toString(), it[ActionCollaboratorsTable.collaboratorId].toString(), "with") }
            val skills = SkillsTable.select(SkillsTable.id, SkillsTable.name).associate { it[SkillsTable.id] to it[SkillsTable.name] }
            val tagged = ActionSkillsTable.selectAll().toList()
            tagged.map { it[ActionSkillsTable.skillId] }.distinct().forEach {
                nodes += GraphNode(it.toString(), NodeType.SKILL, skills.getValue(it))
            }
            tagged.forEach {
                edges += GraphEdge(it[ActionSkillsTable.actionId].toString(), it[ActionSkillsTable.skillId].toString(), "tagged", it[ActionSkillsTable.weight].toDouble())
            }
            OutcomesTable.selectAll().orderBy(OutcomesTable.seq).forEach {
                nodes += GraphNode(it[OutcomesTable.id].toString(), NodeType.OUTCOME, it[OutcomesTable.what])
            }
            ActionOutcomesTable.selectAll().forEach { edges += GraphEdge(it[ActionOutcomesTable.actionId].toString(), it[ActionOutcomesTable.outcomeId].toString(), "led_to") }
            GraphView(nodes, edges)
        }

    override suspend fun quotesOfNode(id: String): List<String> =
        db.tx {
            val entity = runCatching { Uuid.parse(id) }.getOrNull() ?: return@tx emptyList()
            EvidenceTable.selectAll().where { EvidenceTable.entityId eq entity }.orderBy(EvidenceTable.seq).map { it[EvidenceTable.quote] }
        }

    override suspend fun quotesOfActions(actions: List<ActionId>): List<String> =
        db.tx {
            val ids = actions.map { it.uuid }
            val outcomes = ActionOutcomesTable.selectAll().where { ActionOutcomesTable.actionId inList ids }.map { it[ActionOutcomesTable.outcomeId] }
            EvidenceTable.selectAll().where { EvidenceTable.entityId inList (ids + outcomes) }.orderBy(EvidenceTable.seq).map { it[EvidenceTable.quote] }.distinct()
        }

    override suspend fun wipe() {
        db.tx {
            EvidenceTable.deleteAll()
            QuestionLogTable.deleteAll()
            VacanciesTable.deleteAll()
            CursorStateTable.deleteAll()
            CompaniesTable.deleteAll()
            CollaboratorsTable.deleteAll()
            TechnologiesTable.deleteWhere { seeded eq false }
            RawEntriesTable.deleteAll()
        }
    }

    private object EntityType {
        const val COMPANY = "company"
        const val ROLE = "role"
        const val PROJECT = "project"
        const val ACTION = "action"
        const val OUTCOME = "outcome"
        const val TECHNOLOGY = "technology"
        const val COLLABORATOR = "collaborator"
    }

    private fun technologiesOf(actions: List<Uuid>): Map<Uuid, List<Technology>> {
        val names = TechnologiesTable.selectAll().associate { it[TechnologiesTable.id] to it[TechnologiesTable.name] }
        return ActionTechnologiesTable.selectAll().where { ActionTechnologiesTable.actionId inList actions }
            .groupBy({ it[ActionTechnologiesTable.actionId] }, { Technology(names.getValue(it[ActionTechnologiesTable.technologyId])) })
    }

    private fun collaboratorsOf(actions: List<Uuid>): Map<Uuid, List<Collaborator>> {
        val roles = CollaboratorsTable.selectAll().associate { it[CollaboratorsTable.id] to it[CollaboratorsTable.roleName] }
        return ActionCollaboratorsTable.selectAll().where { ActionCollaboratorsTable.actionId inList actions }
            .groupBy({ it[ActionCollaboratorsTable.actionId] }, { Collaborator(roles.getValue(it[ActionCollaboratorsTable.collaboratorId])) })
    }

    /** For each entity, the first thing the person said that it was made from. */
    private fun firstQuotes(entities: List<Uuid>): Map<Uuid, String> =
        EvidenceTable.selectAll().where { EvidenceTable.entityId inList entities }.orderBy(EvidenceTable.seq).toList()
            .groupBy { it[EvidenceTable.entityId] }
            .mapValues { it.value.first()[EvidenceTable.quote] }
}

private fun periodOf(row: ResultRow): RolePeriod =
    RolePeriod(row[RolesTable.startDate], row[RolesTable.endDate], row[RolesTable.isCurrent])

private fun actionOf(row: ResultRow, quote: String): Action =
    Action(
        ActionKind.named(row[ActionsTable.kind]),
        row[ActionsTable.verb],
        row[ActionsTable.what],
        row[ActionsTable.finding],
        Quote.restore(quote),
    )

private fun outcomeOf(row: ResultRow, quote: String): Outcome =
    Outcome(
        row[OutcomesTable.what],
        row[OutcomesTable.dir]?.let(Direction::named),
        row[OutcomesTable.value],
        row[OutcomesTable.unit],
        row[OutcomesTable.approx],
        Quote.restore(quote),
    )

private fun bulletsOf(stored: String?): List<Bullet> =
    stored?.let { Json.decodeFromString<List<StoredBullet>>(it) }.orEmpty()
        .map { bullet -> Bullet(bullet.text, bullet.covers.map { ActionId(Uuid.parse(it)) }) }
