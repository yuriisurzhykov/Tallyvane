package tallyvane.knowledge.infra.postgres

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import tallyvane.knowledge.domain.CompanyId
import tallyvane.knowledge.domain.Cursor
import tallyvane.knowledge.domain.Embedding
import tallyvane.knowledge.domain.Gap
import tallyvane.knowledge.domain.ProjectId
import tallyvane.knowledge.domain.Question
import tallyvane.knowledge.domain.QuestionId
import tallyvane.knowledge.domain.RoleId
import tallyvane.knowledge.domain.Scope
import tallyvane.knowledge.domain.Skill
import tallyvane.knowledge.domain.SkillMatch
import tallyvane.knowledge.domain.SkillSlug
import tallyvane.knowledge.domain.Stage
import tallyvane.knowledge.domain.Vacancy
import tallyvane.knowledge.domain.VacancyId
import tallyvane.knowledge.domain.port.CursorStore
import tallyvane.knowledge.domain.port.KnowledgeGraph
import tallyvane.knowledge.domain.port.LoggedQuestion
import tallyvane.knowledge.domain.port.QuestionLog
import tallyvane.knowledge.domain.port.Skills
import tallyvane.knowledge.domain.port.Vacancies
import kotlin.uuid.Uuid

/**
 * Every store the application stands on, in one Postgres. The graph, the skills, the vacancies, the position of
 * the interview and the log of questions are separate ports, each with a small adapter of its own; this is the
 * one object the composition root holds, and the tests of the adapters judge it as one.
 */
class PostgresKnowledge private constructor(
    graph: KnowledgeGraph,
    skills: Skills,
    vacancies: Vacancies,
    cursors: CursorStore,
    log: QuestionLog,
) : KnowledgeGraph by graph,
    Skills by skills,
    Vacancies by vacancies,
    CursorStore by cursors,
    QuestionLog by log {
    constructor(database: Database) : this(Db(database))

    private constructor(db: Db) : this(
        PostgresGraph(db),
        PostgresSkills(db),
        PostgresVacancies(db),
        PostgresCursor(db),
        PostgresQuestions(db),
    )
}

internal class PostgresSkills(private val db: Db) : Skills {
    override suspend fun all(): List<Skill> =
        db.tx {
            SkillsTable.selectAll().map { Skill(SkillSlug(it[SkillsTable.slug]), it[SkillsTable.name], it[SkillsTable.aliases]) }
        }

    override suspend fun nearest(embedding: Embedding, limit: Int): List<SkillMatch> =
        db.tx {
            val distance = CosineDistance(SkillsTable.embedding, valuesOf(embedding))
            SkillsTable.select(SkillsTable.slug, distance)
                .where { SkillsTable.embedding.isNotNull() }
                .orderBy(distance)
                .limit(limit)
                .map { SkillMatch(SkillSlug(it[SkillsTable.slug]), similarityOf(it[distance])) }
        }
}

@Serializable
private class StoredRequirement(val skill: String, val importance: Int, val must: Boolean)

internal class PostgresVacancies(private val db: Db) : Vacancies {
    override suspend fun save(vacancy: Vacancy): VacancyId =
        db.tx {
            val read = ReadVacancy.of(vacancy)
            val id = newId()
            VacanciesTable.insert {
                it[VacanciesTable.id] = id
                it[title] = read.title
                it[reqs] = Json.encodeToString(read.requirements.map { (skill, importance, must) -> StoredRequirement(skill, importance, must) })
                it[unmapped] = Json.encodeToString(read.unmapped)
            }
            VacancyId(id)
        }

    override suspend fun find(id: VacancyId): Vacancy =
        db.tx {
            val row = VacanciesTable.selectAll().where { VacanciesTable.id eq id.uuid }.singleOrNull()
                ?: throw NoSuchElementException("There is no vacancy ${id.uuid}.")
            Vacancy(
                row[VacanciesTable.title],
                Json.decodeFromString<List<StoredRequirement>>(row[VacanciesTable.reqs]).map { requirementOf(it.skill, it.importance, it.must) },
                Json.decodeFromString<List<String>>(row[VacanciesTable.unmapped]),
            )
        }
}

internal class PostgresCursor(private val db: Db) : CursorStore {
    override suspend fun cursor(): Cursor =
        db.tx {
            CursorStateTable.selectAll().where { CursorStateTable.id eq ROW }.singleOrNull()?.let {
                Cursor.restore(
                    it[CursorStateTable.companyId]?.let(::CompanyId),
                    it[CursorStateTable.roleId]?.let(::RoleId),
                    it[CursorStateTable.projectId]?.let(::ProjectId),
                    Stage.valueOf(it[CursorStateTable.stage]),
                )
            } ?: Cursor.atStart()
        }

    override suspend fun save(cursor: Cursor) {
        val at = ReadCursor.of(cursor)
        db.tx {
            CursorStateTable.upsert {
                it[id] = ROW
                it[companyId] = at.company?.uuid
                it[roleId] = at.role?.uuid
                it[projectId] = at.project?.uuid
                it[stage] = at.stage.name
            }
        }
    }

    private companion object {
        const val ROW = 1
    }
}

internal class PostgresQuestions(private val db: Db) : QuestionLog {
    override suspend fun pending(): LoggedQuestion? =
        db.tx {
            QuestionLogTable.selectAll().where { QuestionLogTable.answered eq false }
                .orderBy(QuestionLogTable.seq, SortOrder.DESC)
                .limit(1)
                .singleOrNull()
                ?.let(::loggedOf)
        }

    override suspend fun find(id: QuestionId): LoggedQuestion? =
        db.tx { QuestionLogTable.selectAll().where { QuestionLogTable.id eq id.uuid }.singleOrNull()?.let(::loggedOf) }

    override suspend fun log(question: Question, cursor: Cursor): QuestionId =
        db.tx {
            val read = ReadQuestion.of(question)
            val at = ReadCursor.of(cursor)
            val (type, target) =
                when (read.gap.scope()) {
                    Scope.ROLE -> TARGET_ROLE to at.role?.uuid
                    Scope.PROJECT -> TARGET_PROJECT to at.project?.uuid
                    Scope.CONVERSATION -> null to null
                }
            val id = newId()
            QuestionLogTable.insert {
                it[QuestionLogTable.id] = id
                it[targetType] = if (target == null) null else type
                it[targetId] = target
                it[gapCode] = read.gap.name
                it[text] = read.text
                it[answered] = false
            }
            QuestionId(id)
        }

    override suspend fun answered(id: QuestionId) {
        db.tx { QuestionLogTable.update({ QuestionLogTable.id eq id.uuid }) { it[answered] = true } }
    }

    override suspend fun askedAbout(cursor: Cursor): Set<Gap> =
        db.tx {
            val at = ReadCursor.of(cursor)
            val role = at.role?.uuid ?: NONE
            val project = at.project?.uuid ?: NONE
            QuestionLogTable.selectAll()
                .where {
                    ((QuestionLogTable.targetType eq TARGET_ROLE) and (QuestionLogTable.targetId eq role)) or
                        ((QuestionLogTable.targetType eq TARGET_PROJECT) and (QuestionLogTable.targetId eq project))
                }
                .map { Gap.valueOf(it[QuestionLogTable.gapCode]) }
                .toSet()
        }

    private fun loggedOf(row: ResultRow): LoggedQuestion =
        LoggedQuestion(
            QuestionId(row[QuestionLogTable.id]),
            Question(Gap.valueOf(row[QuestionLogTable.gapCode]), row[QuestionLogTable.text]),
        )

    private companion object {
        const val TARGET_ROLE = "role"
        const val TARGET_PROJECT = "project"
        val NONE: Uuid = Uuid.NIL
    }
}
