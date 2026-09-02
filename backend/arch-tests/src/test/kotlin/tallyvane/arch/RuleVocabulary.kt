package tallyvane.arch

// Replaces a list of twenty-eight imperative prefixes that the rules used to
// recognise a use case by. That list rejected `SignIn`, `Upload`, `Open` and
// `Delete` while accepting `SaveThing`, so it contradicted the definition it was
// meant to enforce and was wrong in both directions (ADR-053).
internal const val USE_CASE_MARKER = "UseCase"

/**
 * The kernel marker on the branch of an outcome that says the operation did not happen.
 */
internal const val FAILURE_MARKER = "Failure"

/**
 * The port in `platform:http` that maps one module's failures to an HTTP answer.
 */
internal const val PROBLEMS_PORT = "Problems"

/**
 * The RFC 9457 document, which must keep having no public source (ADR-062).
 */
internal const val PROBLEM_TYPE = "Problem"

/**
 * Statuses a route must not name directly: choosing one is `Problem`'s job, and a route that
 * writes its own has bypassed the type, the URI vocabulary and the renderer in one line.
 */
internal val REFUSAL_STATUSES =
    listOf(
        "HttpStatusCode.BadRequest",
        "HttpStatusCode.Unauthorized",
        "HttpStatusCode.Forbidden",
        "HttpStatusCode.NotFound",
        "HttpStatusCode.Conflict",
        "HttpStatusCode.UnprocessableEntity",
        "HttpStatusCode.TooManyRequests",
        "HttpStatusCode.InternalServerError",
        "HttpStatusCode.ServiceUnavailable",
    )

internal val BANNED_SUFFIXES =
    listOf(
        "Utils",
        "Util",
        "Helpers",
        "Helper",
        "Manager",
        "Tools",
        "Common",
        "Misc",
        "Shared",
        "Constants",
        "Processor",
        "Data",
        "Info",
        "Base",
    )

internal val FRAMEWORK_IMPORT_PREFIXES =
    listOf(
        "io.ktor",
        "org.jetbrains.exposed",
        "java.sql",
        "javax.sql",
        "kotlinx.serialization",
        "java.io",
        "java.net",
    )

internal val MOCK_IMPORT_PREFIXES = listOf("io.mockk", "org.mockito")

internal val DI_IMPORT_PREFIXES =
    listOf(
        "org.koin",
        "dagger",
        "org.springframework",
        "com.google.inject",
        "javax.inject",
        "jakarta.inject",
    )

// `Wall`, `Uuid7`, `Process`, `InMemory`, `Csprng`, `Hmac` and `Default` are production
// implementations of a port that read or hold ambient state, or reach only the JDK's own
// crypto/randomness primitives, but touch no external technology — so nesting drags no driver into
// the port's module. Naming them here is what puts them under `nested-impl-is-pure`: the rule
// skips every nested class whose name it does not know, so an addition here widens the check
// rather than relaxing it.
internal val NESTED_IMPL_ALLOW =
    setOf(
        "Cached", "Retrying", "Abstract", "Wall", "Uuid7", "Process", "InMemory", "Csprng", "Hmac", "Default",
    )

internal const val PRODUCT_NAME = "Tallyvane"

internal const val MAX_EXCEPTIONS = 10

internal val ADR_PATTERN = Regex("""^ADR-\d{3}$""")

internal const val MIN_EXCEPTION_REASON = 40
