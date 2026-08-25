package tallyvane.platform.persistence

/**
 * How to reach a database: the module's own vocabulary for it.
 *
 * In `main` rather than in `testFixtures` even though only the test fixture builds
 * one today. The connection factory of slice 7 needs exactly these three fields, and
 * a test-only type would have guaranteed a second one beside it in production — two
 * vocabularies for one concept, free to drift. A fixture that shapes production code
 * is the failure mode this arrangement avoids.
 *
 * Says nothing about who started the database, so a container, a compose service and
 * a URL from an environment variable are all describable by it.
 */
public data class DatabaseAccess(val url: String, val user: String, val password: String)
