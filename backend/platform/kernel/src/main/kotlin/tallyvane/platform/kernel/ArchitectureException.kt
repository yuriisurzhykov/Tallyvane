package tallyvane.platform.kernel

/**
 * Marks a deliberate, reviewed break from a named architecture rule.
 *
 * Konsist skips the annotated declaration only when [reason] is at least forty
 * characters, [adr] names a file that exists under `docs/adr/`, and [rule] is a
 * known rule code. The project-wide count is capped; raising the cap is itself
 * a visible change to the architecture tests.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.FILE,
    AnnotationTarget.PROPERTY,
)
public annotation class ArchitectureException(
    val rule: String,
    val reason: String,
    val adr: String,
)
