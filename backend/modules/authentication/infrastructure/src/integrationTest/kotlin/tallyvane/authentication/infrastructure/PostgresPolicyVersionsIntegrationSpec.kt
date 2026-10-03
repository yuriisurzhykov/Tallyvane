package tallyvane.authentication.infrastructure

import tallyvane.authentication.application.PolicyVersionsConformance
import tallyvane.authentication.application.port.PolicyVersions
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.persistence.DatabaseAccess
import tallyvane.platform.persistence.PostgresFixture
import tallyvane.platform.persistence.PostgresPersistence
import java.sql.DriverManager

/**
 * The adapter over Postgres, judged by the suite the fake already passes (ADR-046).
 *
 * A migrated database ships with the initial policies, in force, and the suite starts from nothing
 * made, so each case begins by emptying the four tables. `TRUNCATE` is the only way: the triggers
 * that keep history append-only refuse every `DELETE`, which is the point of them, and do not fire
 * for `TRUNCATE`. What the shipped rows are is checked by [InitialPoliciesIntegrationSpec] instead.
 */
class PostgresPolicyVersionsIntegrationSpec : PolicyVersionsConformance() {
    private val opened = mutableListOf<PostgresPersistence>()

    init {
        afterTest {
            opened.forEach { it.close() }
            opened.clear()
        }
    }

    override suspend fun fresh(): Subject {
        val access = PostgresFixture.migrated()
        emptyThePolicyTables(access)
        val persistence = PostgresPersistence(access).also { opened += it }
        return object : Subject {
            override val versions: PolicyVersions = storageForTests().policyVersions()
            override val transactions: TransactionRunner = persistence.transactions
        }
    }

    private fun emptyThePolicyTables(access: DatabaseAccess) {
        DriverManager.getConnection(access.url, access.user, access.password.revealed()).use { connection ->
            connection.createStatement().use {
                it.execute(
                    "truncate authentication.policy_activations, authentication.policy_version_step_kinds, " +
                        "authentication.policy_version_steps, authentication.policy_versions",
                )
            }
        }
    }
}
