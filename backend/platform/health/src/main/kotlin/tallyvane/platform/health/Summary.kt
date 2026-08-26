package tallyvane.platform.health

import kotlinx.serialization.Serializable

/**
 * What an unauthorised caller gets: one field (ADR-055).
 *
 * A separate type from [Detail] rather than the same one with fields left out, and that is the
 * whole reason it exists. Filtering a rich object down to a poor one is a decision taken at every
 * call site, and one forgotten filter publishes the list of everything this system depends on and
 * which part of it is currently broken — reconnaissance for anyone looking for somewhere to push.
 * A type with one field cannot leak a second.
 */
@Serializable
internal data class Summary(val status: String)
