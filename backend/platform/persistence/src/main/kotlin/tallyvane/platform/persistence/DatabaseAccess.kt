package tallyvane.platform.persistence

import tallyvane.platform.kernel.Secret

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
 *
 * ### Why the password is a [Secret] and not a `String`
 *
 * This is a `data class`, so its generated `toString()` prints every field. With a plain
 * `String` password, one interpolation of the whole object into a log line — or one
 * exception message that included it — would have written a database password to disk.
 * Nothing had leaked; nothing prevented it either. Changed 2026-08-26, when a second
 * secret (the health service token) made "how a secret behaves" worth one home rather
 * than a `toString()` override here.
 */
public data class DatabaseAccess(val url: String, val user: String, val password: Secret)
