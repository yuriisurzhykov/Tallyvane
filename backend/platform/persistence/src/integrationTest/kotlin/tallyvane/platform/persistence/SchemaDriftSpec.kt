package tallyvane.platform.persistence

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContainIgnoringCase
import org.jetbrains.exposed.v1.core.Table
import java.sql.DriverManager

private object Aligned : Table("aligned") {
    val id = integer("id")
    val label = text("label")
}

private object WantsColumn : Table("wants_column") {
    val id = integer("id")
    val label = text("label")
}

private object HasSpare : Table("has_spare") {
    val id = integer("id")
}

private object Absent : Table("absent") {
    val id = integer("id")
}

private fun create(access: DatabaseAccess, ddl: String) {
    DriverManager.getConnection(access.url, access.user, access.password.revealed()).use { connection ->
        connection.createStatement().use { it.execute(ddl) }
    }
}

/**
 * The gate itself, proven in three directions.
 *
 * A gate that only reported what the database lacks would pass half the mistakes, so the
 * cases below cover both column directions: a column Kotlin wants and the database has not
 * got, and a column the database has that no Kotlin table declares. The second is the one
 * `SchemaUtils.statementsRequiredToActualizeScheme` never reported, and the reason `from`
 * is built on `MigrationUtils`.
 *
 * The third direction is a whole table the database still has after its Kotlin declaration
 * is gone. `MigrationUtils` does not see that — measured 2026-08-25, same schema, empty
 * result — which is why `unmappedTables` reads the catalog itself.
 *
 * Tables here are declared in the test source set and created by hand. None of them belongs
 * in `db/migration`: a table that exists only to prove a check would otherwise ship to
 * production.
 */
class SchemaDriftSpec :
    StringSpec(
        {
            "says nothing when the schema and the Kotlin declaration agree" {
                val access = PostgresFixture.empty()
                create(access, "create table aligned (id integer not null, label text not null)")

                val drift = SchemaDrift(access)
                drift.from(Aligned).shouldBeEmpty()
                drift.unmappedTables(Aligned).shouldBeEmpty()
            }

            "reports a column Kotlin declares and the database has not got" {
                val access = PostgresFixture.empty()
                create(access, "create table wants_column (id integer not null)")

                val drift = SchemaDrift(access).from(WantsColumn)

                drift.joinToString("\n") shouldContainIgnoringCase "add"
            }

            "reports a column the database has and no Kotlin table declares" {
                val access = PostgresFixture.empty()
                create(access, "create table has_spare (id integer not null, spare text)")

                val drift = SchemaDrift(access).from(HasSpare)

                // The direction the deprecated SchemaUtils never reported.
                drift.joinToString("\n") shouldContainIgnoringCase "drop"
            }

            "reports a table the database does not have at all" {
                val access = PostgresFixture.empty()

                val drift = SchemaDrift(access).from(Absent)

                drift.joinToString("\n") shouldContainIgnoringCase "create table"
            }

            "reports a table the database has and no Kotlin table declares at all" {
                val access = PostgresFixture.empty()
                create(access, "create table aligned (id integer not null, label text not null)")
                create(access, "create table orphan_leftover (id integer not null)")

                SchemaDrift(access).unmappedTables(Aligned) shouldBe listOf("public.orphan_leftover")
            }

            "reports an undeclared table that lives in another schema" {
                val access = PostgresFixture.empty()
                create(access, "create schema leftover")
                create(access, "create table leftover.gone (id integer not null)")
                create(access, "create table aligned (id integer not null, label text not null)")

                SchemaDrift(access).unmappedTables(Aligned) shouldBe listOf("leftover.gone")
            }

            "reports an undeclared table in platform that is not Flyway's history" {
                val access = PostgresFixture.empty()
                create(access, "create schema platform")
                create(access, "create table platform.orphan_in_platform (id integer not null)")
                create(access, "create table aligned (id integer not null, label text not null)")

                SchemaDrift(access).unmappedTables(Aligned) shouldBe listOf("platform.orphan_in_platform")
            }

            "does not mistake Flyway's own history table for drift" {
                // Matters for the run over every table in slice 13: that database has
                // `platform.flyway_schema_history`, which no Kotlin table declares.
                val access = PostgresFixture.migrated()
                create(access, "create table aligned (id integer not null, label text not null)")

                val drift = SchemaDrift(access)
                drift.from(Aligned).shouldBeEmpty()
                drift.unmappedTables(Aligned).shouldBeEmpty()
            }
        },
    )
