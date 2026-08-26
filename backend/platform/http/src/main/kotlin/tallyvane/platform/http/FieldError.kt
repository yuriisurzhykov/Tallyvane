package tallyvane.platform.http

import kotlinx.serialization.Serializable

/**
 * One field a request got wrong, as §11.6's `errors` array carries it.
 *
 * @property field the name as the *client* sent it — `salary_min_cents`, not `salaryMinCents`.
 * The client cannot act on a name it never used.
 * @property code what is wrong, in a form a frontend can branch on: `range.invalid`,
 * `required`, `too-long`. A free string on purpose — closing this set would mean a vocabulary
 * of every module's validation rules living in the platform.
 */
@Serializable
public data class FieldError(public val field: String, public val code: String)
