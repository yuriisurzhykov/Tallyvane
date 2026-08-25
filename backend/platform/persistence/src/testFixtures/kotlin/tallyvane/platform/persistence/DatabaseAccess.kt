package tallyvane.platform.persistence

/**
 * How a test reaches a database, without saying who started it.
 *
 * Deliberately opaque about the mechanism: today a container, tomorrow possibly
 * a database already running and named by an environment variable. A test that
 * held a `PostgreSQLContainer` would have to be rewritten for that change; one
 * that holds this does not.
 */
public data class DatabaseAccess(val url: String, val user: String, val password: String)
