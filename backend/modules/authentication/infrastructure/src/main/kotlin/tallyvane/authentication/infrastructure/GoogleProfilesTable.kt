package tallyvane.authentication.infrastructure

import org.jetbrains.exposed.v1.core.Table

/**
 * `authentication.google_profiles`: what Google said about a person who has no account yet.
 */
internal object GoogleProfilesTable : Table("authentication.google_profiles") {
    val attemptId = uuid("attempt_id")
    val displayName = text("display_name")
    val email = text("email")

    override val primaryKey = PrimaryKey(attemptId)
}
