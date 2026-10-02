package tallyvane.authentication.infrastructure

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import tallyvane.authentication.application.GoogleProfile
import tallyvane.authentication.application.port.GoogleProfiles
import tallyvane.platform.kernel.Digest

/**
 * [GoogleProfiles] over the `authentication` schema in Postgres.
 *
 * Runs inside the caller's transaction (ADR-052).
 */
internal class PostgresGoogleProfiles(private val rows: AttemptRows) : GoogleProfiles {
    override fun keep(attempt: Digest, profile: GoogleProfile) {
        val id = checkNotNull(rows.idOf(attempt)) {
            "A profile is kept for an attempt that is kept first; none is kept under this key."
        }
        profile.writeTo { name, email ->
            val changed = GoogleProfilesTable.update({ GoogleProfilesTable.attemptId eq id }) {
                it[displayName] = name
                it[GoogleProfilesTable.email] = email
            }
            if (changed == 0) {
                GoogleProfilesTable.insert {
                    it[attemptId] = id
                    it[displayName] = name
                    it[GoogleProfilesTable.email] = email
                }
            }
        }
    }

    override fun of(attempt: Digest): GoogleProfile? {
        val id = rows.idOf(attempt) ?: return null
        return GoogleProfilesTable.selectAll()
            .where { GoogleProfilesTable.attemptId eq id }
            .singleOrNull()
            ?.let { GoogleProfile(it[GoogleProfilesTable.displayName], it[GoogleProfilesTable.email]) }
    }

    override fun toString(): String = "PostgresGoogleProfiles(schema=authentication)"
}
