package tallyvane.career.infrastructure

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import tallyvane.career.application.port.Goals
import kotlin.uuid.Uuid

internal class PostgresGoals : Goals {
    override fun of(account: Uuid): String? =
        GoalsTable.selectAll().where { GoalsTable.accountId eq account }.singleOrNull()?.get(GoalsTable.role)

    override fun set(account: Uuid, role: String) {
        GoalsTable.upsert {
            it[accountId] = account
            it[GoalsTable.role] = role
        }
    }

    override fun toString(): String = "PostgresGoals(schema=career)"
}
