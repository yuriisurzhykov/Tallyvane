package tallyvane.knowledge.domain

import kotlin.uuid.Uuid

/** Identifiers are distinct types so that a role id can never be passed where a project id is wanted. */
@JvmInline
value class CompanyId(val uuid: Uuid)

@JvmInline
value class RoleId(val uuid: Uuid)

@JvmInline
value class ProjectId(val uuid: Uuid)

@JvmInline
value class ActionId(val uuid: Uuid)

@JvmInline
value class OutcomeId(val uuid: Uuid)

@JvmInline
value class RawEntryId(val uuid: Uuid)

@JvmInline
value class QuestionId(val uuid: Uuid)

@JvmInline
value class VacancyId(val uuid: Uuid)

/** The stable name of a skill from the seed list, such as `redis` or `performance-profiling`. */
@JvmInline
value class SkillSlug(val value: String)
