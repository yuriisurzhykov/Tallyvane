package tallyvane.platform.http

import kotlinx.serialization.Serializable

/**
 * One error answer, in the shape §11.6 fixed and RFC 9457 defines.
 *
 * There is no way to construct one outside `platform:http`: the constructor is `internal` and the
 * factories live on [Answers], which a module only ever receives as a receiver inside
 * [Problems.of] or [FailureTranslator.translate]. So a route cannot answer with a problem of its
 * own devising — it can only hand back a [Refused], which pairs a failure with the table that
 * knows what it means.
 *
 * @property type stable identifier of the *kind* of failure, and the field a client branches on.
 * It survives rewording of [title] and [detail].
 * @property title the kind, in human words; identical for every occurrence of one [type].
 * @property status HTTP status, repeated in the body so the document stands alone when forwarded
 * or logged.
 * @property detail this occurrence, in human words. Whatever a module puts here reaches the client
 * verbatim, so it is never a driver's message.
 * @property errors per-field detail for a validation failure, empty otherwise.
 */
@Serializable
public class Problem internal constructor(
    public val type: String,
    public val title: String,
    public val status: Int,
    public val detail: String? = null,
    public val errors: List<FieldError> = emptyList(),
)
