package tallyvane.arch

// Replaces a list of twenty-eight imperative prefixes that the rules used to
// recognise a use case by. That list rejected `SignIn`, `Upload`, `Open` and
// `Delete` while accepting `SaveThing`, so it contradicted the definition it was
// meant to enforce and was wrong in both directions (ADR-053).
internal const val USE_CASE_MARKER = "UseCase"

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

// `Wall` and `Uuid7` are production implementations of a kernel port that read
// ambient state but touch no technology, so nesting drags no driver into the
// port's module. Naming them here is what puts them under `nested-impl-is-pure`:
// the rule skips every nested class whose name it does not know.
internal val NESTED_IMPL_ALLOW = setOf("Cached", "Retrying", "Abstract", "Wall", "Uuid7")

internal const val PRODUCT_NAME = "Tallyvane"

internal const val MAX_EXCEPTIONS = 10

internal val ADR_PATTERN = Regex("""^ADR-\d{3}$""")

internal const val MIN_EXCEPTION_REASON = 40
