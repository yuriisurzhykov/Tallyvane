package tallyvane.authentication.web

import kotlinx.serialization.Serializable

/**
 * What putting a version in force would do: [people] is how many people its purpose applies to, and [cannotPass]
 * how many of them could not get to the end of a sign-in under it.
 */
@Serializable
internal class PolicyPreview(val people: Int, val cannotPass: Int)
