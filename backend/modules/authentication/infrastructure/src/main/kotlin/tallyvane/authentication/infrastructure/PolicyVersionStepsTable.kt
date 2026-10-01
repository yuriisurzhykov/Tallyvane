package tallyvane.authentication.infrastructure

import org.jetbrains.exposed.v1.core.Table

/**
 * `authentication.policy_version_steps`: the steps of a version, in the order they apply.
 */
internal object PolicyVersionStepsTable : Table("authentication.policy_version_steps") {
    val purpose = text("purpose")
    val number = integer("number")
    val position = integer("position")
    val necessity = text("necessity")

    override val primaryKey = PrimaryKey(purpose, number, position)
}
