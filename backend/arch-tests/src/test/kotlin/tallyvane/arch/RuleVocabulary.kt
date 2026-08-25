package tallyvane.arch

internal val USE_CASE_PREFIXES =
    listOf(
        "Capture",
        "Save",
        "Submit",
        "Advance",
        "Schedule",
        "Send",
        "Render",
        "Register",
        "Update",
        "Archive",
        "Publish",
        "Record",
        "Evaluate",
        "Compose",
        "Notify",
        "Ingest",
        "Extract",
        "Normalize",
        "Score",
        "Match",
        "Recommend",
        "Accept",
        "Dismiss",
        "Complete",
        "Start",
        "Conclude",
        "Observe",
        "Log",
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

// `Wall` and `Uuid7` are production implementations of a kernel port that read
// ambient state but touch no technology, so nesting drags no driver into the
// port's module. Naming them here is what puts them under `nested-impl-is-pure`:
// the rule skips every nested class whose name it does not know.
internal val NESTED_IMPL_ALLOW = setOf("Cached", "Retrying", "Abstract", "Wall", "Uuid7")

internal const val PRODUCT_NAME = "Tallyvane"

internal const val MAX_EXCEPTIONS = 10

internal val ADR_PATTERN = Regex("""^ADR-\d{3}$""")

internal const val MIN_EXCEPTION_REASON = 40
