package tallyvane.platform.kernel

/**
 * Marks a deliberate, reviewed break from a named architecture rule.
 *
 * Konsist skips the annotated declaration only when [reason] is at least forty
 * characters, [adr] names a file that exists under `docs/adr/`, and [rule] is a
 * known rule code. The project-wide count is capped at ten; raising the cap is
 * itself a visible change to the architecture tests.
 *
 * How the checker wires this, and why a silent comment is not enough:
 * `backend/platform/kernel/README.md`.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.FILE,
    AnnotationTarget.PROPERTY,
)
public annotation class ArchitectureException(
    /**
     * Konsist rule code being broken, for example `no-top-level-functions`.
     */
    val rule: String,
    /**
     * Why the rule cannot be followed here. Must be at least forty characters.
     */
    val reason: String,
    /**
     * Identifier of an existing file under `docs/adr/`, for example `ADR-044`
     * .*/
    val adr: String,
)
