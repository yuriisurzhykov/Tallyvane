package tallyvane.authentication.infrastructure

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

/**
 * `authentication.policy_activations`: each time a version was put in force, in the order it
 * happened, with who did it and why when a person did; the version in force for a purpose is the one its
 * last row names.
 */
internal object PolicyActivationsTable : Table("authentication.policy_activations") {
    val id = long("id").autoIncrement()
    val purpose = text("purpose")
    val number = integer("number")
    val activatedAt = timestamp("activated_at")
    val activatedBy = uuid("activated_by").nullable()
    val reason = text("reason").nullable()

    override val primaryKey = PrimaryKey(id)
}
