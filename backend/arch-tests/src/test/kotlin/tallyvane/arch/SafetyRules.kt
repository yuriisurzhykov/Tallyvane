package tallyvane.arch

import com.lemonappdev.konsist.api.container.KoScope

private val ambientTimeMarkers =
    listOf(
        "Instant.now",
        "LocalDate.now",
        "LocalDateTime.now",
        "OffsetDateTime.now",
        "System.currentTimeMillis",
        "System.nanoTime",
        "kotlin.time.Clock.System",
    )

private val ambientRandomMarkers =
    listOf(
        "UUID.randomUUID",
        "kotlin.random.Random",
        "java.util.Random",
    )

internal fun noAmbientTime(scope: KoScope): List<String> =
    scope.files
        .withoutException("no-ambient-time")
        .filterNot { it.implementsSimpleName("Clock") }
        .filter { file -> ambientTimeMarkers.any { marker -> file.text.contains(marker) } }
        .map { it.where() }

internal fun noAmbientRandom(scope: KoScope): List<String> =
    scope.files
        .withoutException("no-ambient-random")
        .filterNot { it.implementsSimpleName("IdGenerator") }
        .filter { file -> ambientRandomMarkers.any { marker -> file.text.contains(marker) } }
        .map { it.where() }

internal fun noSqlConcat(scope: KoScope): List<String> =
    scope.files
        .withoutException("no-sql-concat")
        .filter { file ->
            Regex(
                """["'][^"']*(SELECT|INSERT|UPDATE|DELETE)[^"']*["']\s*\+""",
                RegexOption.IGNORE_CASE,
            ).containsMatchIn(file.text)
        }.map { it.where() }

internal fun ownSchemaOnly(scope: KoScope): List<String> =
    scope.files
        .withoutException("own-schema-only")
        .mapNotNull { file ->
            val module = moduleNameFromPath(unixPath(file)) ?: return@mapNotNull null
            val schemas =
                Regex("""schema\s*=\s*"([^"]+)"""")
                    .findAll(file.text)
                    .map { it.groupValues[1] }
            val dotted =
                Regex(""""([a-z][a-z0-9_]*)\.[^"]+"""")
                    .findAll(file.text)
                    .map { it.groupValues[1] }
            if ((schemas + dotted).any { it != module }) file.where() else null
        }

internal fun noCrossSchemaJoin(scope: KoScope): List<String> =
    scope.files
        .withoutException("no-cross-schema-join")
        .mapNotNull { file ->
            val module = moduleNameFromPath(unixPath(file)) ?: return@mapNotNull null
            val hasJoin = Regex("join", RegexOption.IGNORE_CASE).containsMatchIn(file.text)
            val foreign =
                Regex(""""([a-z][a-z0-9_]*)\.[^"]+"""")
                    .findAll(file.text)
                    .map { it.groupValues[1] }
                    .any { it != module }
            if (hasJoin && foreign) file.where() else null
        }

internal fun noLlmWithPersonalData(scope: KoScope): List<String> =
    scope.files
        .withoutException("no-llm-with-personal-data")
        .filter { file ->
            val buildsPrompt =
                file.inLayer("llm") ||
                    file
                        .classes(includeNested = true, includeLocal = false)
                        .any { it.name.contains("Prompt") || it.name.contains("Llm") }
            val contacts = file.imports.any { it.name.startsWith("tallyvane.contacts") }
            buildsPrompt && contacts
        }.map { it.where() }

internal fun noMockLibraries(scope: KoScope): List<String> =
    scope.files
        .withoutException("no-mock-libraries")
        .filter { it.hasImportStartingWith(MOCK_IMPORT_PREFIXES) }
        .map { it.where() }

internal fun noDiFramework(scope: KoScope): List<String> =
    scope.files
        .withoutException("no-di-framework")
        .filter { it.hasImportStartingWith(DI_IMPORT_PREFIXES) }
        .map { it.where() }
