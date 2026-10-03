package tallyvane.authentication.web

import kotlinx.serialization.Serializable

/**
 * Where a sign-in or a confirmation stands, for the page that shows the next step.
 *
 * [state] is `awaiting`, `paused`, `complete`, `restricted`, `exhausted` or `expired`. [factors] names the
 * answers that are wanted (`totp`, `recovery_code`) and is empty when none is. [retryAfter] is the
 * seconds a `paused` attempt must still wait.
 */
@Serializable
internal class SignInState(val state: String, val factors: List<String>, val retryAfter: Int? = null)
