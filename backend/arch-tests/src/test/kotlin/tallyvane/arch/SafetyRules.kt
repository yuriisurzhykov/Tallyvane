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

// `Uuid.generateV7` is unparenthesised so it also catches
// `generateV7NonMonotonicAt`: both v7 generators draw their suffix from the
// platform CSPRNG. Minting a UUID is listed here and deliberately not among the
// time markers, even though `generateV7()` reads the wall clock — see
// arch-tests/README.md for why the time marker was added and then withdrawn.
private val ambientRandomMarkers =
    listOf(
        "UUID.randomUUID",
        "kotlin.random.Random",
        "java.util.Random",
        "Uuid.random",
        "Uuid.generateV4",
        "Uuid.generateV7",
    )

/**
 * The ambient-random markers [source] contains, as a rule would see them.
 *
 * Exposed so a spec can pin the breadth a substring match makes easy to get
 * wrong — every way of minting a UUID must match, including the
 * `NonMonotonicAt` form — without building a Konsist scope for two lines of
 * Kotlin.
 */
internal fun ambientRandomMarkersIn(source: String): List<String> {
    val code = codeWithoutComments(source)
    return ambientRandomMarkers.filter { code.contains(it) }
}

internal fun noAmbientTime(scope: KoScope): List<String> = scope.files
    .withoutException("no-ambient-time")
    .filterNot { it.implementsSimpleName("Clock") }
    .filter { file -> ambientTimeMarkers.any { marker -> file.codeText().contains(marker) } }
    .map { it.where() }

internal fun noAmbientRandom(scope: KoScope): List<String> = scope.files
    .withoutException("no-ambient-random")
    .filterNot { it.implementsSimpleName("IdGenerator") }
    .filter { file -> ambientRandomMarkers.any { marker -> file.codeText().contains(marker) } }
    .map { it.where() }

internal fun noSqlConcat(scope: KoScope): List<String> = scope.files
    .withoutException("no-sql-concat")
    .filter { file ->
        Regex(
            """["'][^"']*(SELECT|INSERT|UPDATE|DELETE)[^"']*["']\s*\+""",
            RegexOption.IGNORE_CASE,
        ).containsMatchIn(file.codeText())
    }.map { it.where() }

internal fun ownSchemaOnly(scope: KoScope): List<String> = scope.files
    .withoutException("own-schema-only")
    .mapNotNull { file ->
        val module = moduleNameFromPath(unixPath(file)) ?: return@mapNotNull null
        val code = file.codeText()
        val schemas =
            Regex("""schema\s*=\s*"([^"]+)"""")
                .findAll(code)
                .map { it.groupValues[1] }
        val dotted =
            Regex(""""([a-z][a-z0-9_]*)\.[^"]+"""")
                .findAll(code)
                .map { it.groupValues[1] }
        if ((schemas + dotted).any { it != module }) file.where() else null
    }

/**
 * A `Counter` key belongs to the module that reads it, the same rule `own-schema-only` already
 * applies to a Postgres schema — a key with no owner-prefix convention is a channel two modules
 * could read and write without either showing up as an edge in the build graph.
 *
 * Matched the same way `own-schema-only` matches a schema name: a string literal read out of the
 * file's own source text, not a resolved call, because a `Counter` is a `platform:cache`
 * interface with no way to know at this layer which module is calling it.
 */
internal fun cacheKeyIsModulePrefixed(scope: KoScope): List<String> = scope.files
    .withoutException("cache-key-is-module-prefixed")
    .mapNotNull { file ->
        val module = moduleNameFromPath(unixPath(file)) ?: return@mapNotNull null
        val keys =
            Regex("""\.(?:increment|count)\(\s*"([^"]+)"""")
                .findAll(file.codeText())
                .map { it.groupValues[1] }
        if (keys.any { key -> !key.startsWith("$module:") }) file.where() else null
    }

internal fun noCrossSchemaJoin(scope: KoScope): List<String> = scope.files
    .withoutException("no-cross-schema-join")
    .mapNotNull { file ->
        val module = moduleNameFromPath(unixPath(file)) ?: return@mapNotNull null
        val code = file.codeText()
        val hasJoin = Regex("join", RegexOption.IGNORE_CASE).containsMatchIn(code)
        val foreign =
            Regex(""""([a-z][a-z0-9_]*)\.[^"]+"""")
                .findAll(code)
                .map { it.groupValues[1] }
                .any { it != module }
        if (hasJoin && foreign) file.where() else null
    }

internal fun noLlmWithPersonalData(scope: KoScope): List<String> = scope.files
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

/**
 * `addDataSourceProperty` may be called only by the wrapper that forces its value to a String.
 *
 * A value of any other type is accepted by HikariCP, stored, and then ignored by pgjdbc, with no
 * warning from either — this repository shipped `socketTimeout` and `connectTimeout` as `Int`
 * constants and neither was in effect. See `DriverProperties` and
 * `playground/timeout-bounds/README.md`.
 */
internal fun noRawDataSourceProperty(scope: KoScope): List<String> = scope.files
    .withoutException("no-raw-datasource-property")
    .filterNot { it.name == "DriverProperties" }
    .filter { it.codeText().contains("addDataSourceProperty") }
    .map { it.where() }

/**
 * A route never names a refusal status itself; it answers with a `Problem`.
 *
 * `call.respond(HttpStatusCode.BadRequest, "oops")` bypasses everything at once: the closed
 * vocabulary of `type` URIs, the `application/problem+json` content type, the trace id in the
 * body, the log line — all of which live in the one renderer a `Problem` goes through. The line
 * compiles and the endpoint answers, so only a gate catches it.
 *
 * Success statuses are untouched: choosing 201 over 200 is the route's business.
 *
 * Scoped to `..web..` and deliberately not to `platform:http`. A first version covered both and
 * failed on `Api` itself, which names `HttpStatusCode.NotFound` to *install* the handler that gives
 * Ktor's bodiless 404 the right shape. A rule that flags the mechanism it protects is stating the
 * wrong thing, not finding a violation.
 */
internal fun webAnswersWithProblem(scope: KoScope): List<String> = scope.files
    .withoutException("web-answers-with-problem")
    .filter { file -> file.inLayer("web") }
    .filter { file -> REFUSAL_STATUSES.any { status -> file.codeText().contains(status) } }
    .map { it.where() }

internal fun noMockLibraries(scope: KoScope): List<String> = scope.files
    .withoutException("no-mock-libraries")
    .filter { it.hasImportStartingWith(MOCK_IMPORT_PREFIXES) }
    .map { it.where() }

internal fun noDiFramework(scope: KoScope): List<String> = scope.files
    .withoutException("no-di-framework")
    .filter { it.hasImportStartingWith(DI_IMPORT_PREFIXES) }
    .map { it.where() }
