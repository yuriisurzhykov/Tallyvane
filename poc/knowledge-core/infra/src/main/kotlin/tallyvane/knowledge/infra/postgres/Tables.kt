package tallyvane.knowledge.infra.postgres

import org.jetbrains.exposed.v1.core.Table
import kotlin.uuid.Uuid

// The tables of V1__knowledge_core.sql, as Exposed sees them. The migration owns the schema; these
// definitions only let queries be written against it, so none of them is ever asked to create a table.

internal object RawEntriesTable : Table("raw_entry") {
    val id = uuid("id")
    val origin = text("source")
    val questionId = uuid("question_id").nullable()
    val context = jsonb("context")
    val text = text("text")

    override val primaryKey = PrimaryKey(id)
}

internal object CompaniesTable : Table("company") {
    val id = uuid("id")
    val name = text("name")
    val nameNorm = text("name_norm")

    override val primaryKey = PrimaryKey(id)
}

internal object RolesTable : Table("role") {
    val id = uuid("id")
    val seq = long("seq").autoIncrement()
    val companyId = uuid("company_id")
    val title = text("title").nullable()
    val startDate = text("start_date").nullable()
    val endDate = text("end_date").nullable()
    val isCurrent = bool("is_current").nullable()

    override val primaryKey = PrimaryKey(id)
}

internal object ProjectsTable : Table("project") {
    val id = uuid("id")
    val seq = long("seq").autoIncrement()
    val roleId = uuid("role_id")
    val title = text("title").nullable()
    val problem = text("problem").nullable()
    val canonicalBullets = jsonb("canonical_bullets").nullable()
    val closed = bool("closed")

    override val primaryKey = PrimaryKey(id)
}

internal object ActionsTable : Table("action") {
    val id = uuid("id")
    val seq = long("seq").autoIncrement()
    val projectId = uuid("project_id")
    val kind = text("kind")
    val verb = text("verb")
    val what = text("what")
    val finding = text("finding").nullable()
    val embedding = vector("embedding")

    override val primaryKey = PrimaryKey(id)
}

internal object OutcomesTable : Table("outcome") {
    val id = uuid("id")
    val seq = long("seq").autoIncrement()
    val projectId = uuid("project_id")
    val what = text("what")
    val dir = text("dir").nullable()
    val value = double("value").nullable()
    val unit = text("unit").nullable()
    val approx = bool("approx")
    val hasMetric = bool("has_metric")

    override val primaryKey = PrimaryKey(id)
}

internal object ActionOutcomesTable : Table("action_outcome") {
    val actionId = uuid("action_id")
    val outcomeId = uuid("outcome_id")

    override val primaryKey = PrimaryKey(actionId, outcomeId)
}

internal object TechnologiesTable : Table("technology") {
    val id = uuid("id")
    val name = text("name")
    val aliases = textArray("aliases")
    val seeded = bool("seeded")

    override val primaryKey = PrimaryKey(id)
}

internal object ActionTechnologiesTable : Table("action_technology") {
    val actionId = uuid("action_id")
    val technologyId = uuid("technology_id")

    override val primaryKey = PrimaryKey(actionId, technologyId)
}

internal object CollaboratorsTable : Table("collaborator") {
    val id = uuid("id")
    val roleName = text("role_name")

    override val primaryKey = PrimaryKey(id)
}

internal object ActionCollaboratorsTable : Table("action_collaborator") {
    val actionId = uuid("action_id")
    val collaboratorId = uuid("collaborator_id")

    override val primaryKey = PrimaryKey(actionId, collaboratorId)
}

internal object SkillsTable : Table("skill") {
    val id = uuid("id")
    val slug = text("slug")
    val name = text("name")
    val aliases = textArray("aliases")
    val embedding = vector("embedding").nullable()

    override val primaryKey = PrimaryKey(id)
}

internal object ActionSkillsTable : Table("action_skill") {
    val actionId = uuid("action_id")
    val skillId = uuid("skill_id")
    val weight = float("weight")

    override val primaryKey = PrimaryKey(actionId, skillId)
}

internal object EvidenceTable : Table("evidence") {
    val id = uuid("id")
    val seq = long("seq").autoIncrement()
    val entityType = text("entity_type")
    val entityId = uuid("entity_id")
    val rawEntryId = uuid("raw_entry_id")
    val quote = text("quote")

    override val primaryKey = PrimaryKey(id)
}

internal object QuestionLogTable : Table("question_log") {
    val id = uuid("id")
    val seq = long("seq").autoIncrement()
    val targetType = text("target_type").nullable()
    val targetId = uuid("target_id").nullable()
    val gapCode = text("gap_code")
    val text = text("text")
    val answered = bool("answered")

    override val primaryKey = PrimaryKey(id)
}

internal object VacanciesTable : Table("vacancy") {
    val id = uuid("id")
    val title = text("title")
    val reqs = jsonb("reqs")
    val unmapped = jsonb("unmapped")

    override val primaryKey = PrimaryKey(id)
}

internal object CursorStateTable : Table("cursor_state") {
    val id = integer("id")
    val companyId = uuid("company_id").nullable()
    val roleId = uuid("role_id").nullable()
    val projectId = uuid("project_id").nullable()
    val stage = text("stage")

    override val primaryKey = PrimaryKey(id)
}

internal fun newId(): Uuid = Uuid.random()
