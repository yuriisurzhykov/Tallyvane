package tallyvane.platform.http

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

/**
 * The one JSON configuration the API speaks, per §11.1.
 *
 * `snake_case` comes from a naming strategy rather than a `@SerialName` on every property, and
 * the difference is whether it can be forgotten. Annotations are per property: the first new
 * DTO that omits one ships `salaryMinCents` into a contract that promises `salary_min_cents`,
 * and nothing fails. A strategy is set once and applies to everything.
 *
 * The strategy is an experimental API, so the opt-in is here — narrowly, on the one file that
 * needs it, rather than as a compiler flag for the whole module.
 *
 * `explicitNulls = false` because §11.1's `PATCH` semantics make an absent field and an explicit
 * `null` mean different things: absent leaves a value alone, `null` clears it. Emitting `null`
 * for every unset property would make the two indistinguishable on the way out.
 *
 * `ignoreUnknownKeys = true` so a newer client sending a field this version does not know is a
 * request that works, not a 400 — the compatibility direction slice 14 promises to keep.
 */
public object ApiJson {
    @OptIn(ExperimentalSerializationApi::class)
    public val format: Json = Json {
        namingStrategy = JsonNamingStrategy.SnakeCase
        explicitNulls = false
        ignoreUnknownKeys = true
        prettyPrint = false
    }
}
