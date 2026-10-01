package tallyvane.authentication.infrastructure

import org.jetbrains.exposed.v1.core.Table

/**
 * `authentication.policy_version_step_kinds`: the kinds of factor any one of which satisfies a step.
 */
internal object PolicyVersionStepKindsTable : Table("authentication.policy_version_step_kinds") {
    val purpose = text("purpose")
    val number = integer("number")
    val position = integer("position")
    val kind = text("kind")

    override val primaryKey = PrimaryKey(purpose, number, position, kind)
}
