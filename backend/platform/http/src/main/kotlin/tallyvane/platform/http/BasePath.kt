package tallyvane.platform.http

/**
 * The path segment a [RouteModule] mounts under, checked when it is made rather than when a
 * request misses.
 *
 * §11.1 used to type this as `String`, which allowed `"jobs"`, `"/Jobs/"` and `"/api/v1/jobs"`
 * equally — three spellings of one intent, two of which mount somewhere nobody expects. The
 * mistake is silent: routes register, requests 404, and nothing says why.
 *
 * One leading slash, lowercase, words joined by single hyphens. The version prefix is not part
 * of it: `app` mounts every module under `/api/v1`, so a module that wrote the prefix itself
 * would land on `/api/v1/api/v1/jobs`.
 *
 * Uniqueness is not checked here — a value cannot see its siblings. `app` refuses to start on
 * a duplicate, the same way the health aggregator refuses two checks with one name.
 */
@JvmInline
public value class BasePath(public val value: String) {
    init {
        require(SHAPE.matches(value)) {
            "A base path is one lowercase kebab-case segment after a slash, like /jobs; got '$value'"
        }
    }

    private companion object {
        val SHAPE = Regex("^/[a-z0-9]+(-[a-z0-9]+)*$")
    }
}
