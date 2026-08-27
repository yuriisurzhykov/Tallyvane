package tallyvane.platform.kernel

/**
 * Configuration as a collaborator, not as a static call.
 *
 * The composition root reads its settings through this port for a reason that is not stylistic:
 * `System.getenv` cannot be set from a test. The JVM exposes the process environment read-only, so
 * code that calls it directly can only be exercised against whatever the machine happens to have.
 * A port lets a test state the environment it is describing, which is what makes "three variables
 * are missing and the failure names all three" testable at all.
 *
 * Same shape and same argument as [Clock] and [IdGenerator]: a port over ambient state, with the
 * production implementation nested on it because it reaches no technology and so drags no driver
 * into a module whose whole purpose is to have none.
 *
 * Unlike time and identifiers, this one has no gate behind it yet — there is no `no-ambient-env`
 * rule, so nothing stops a future file from calling `System.getenv` directly. Worth having; not
 * introduced here, because adding a gate is its own decision.
 */
public interface Environment {
    /**
     * @return the value of that variable, or `null` when the environment does not define it.
     */
    public fun read(name: String): String?

    /**
     * The environment of the process this code is running in.
     *
     * Named for what it reads rather than `System`, which at a call site would be
     * indistinguishable from the `System.getenv` this port exists to keep out of everything else —
     * the same naming argument as [Clock.Wall].
     */
    public class Process : Environment {
        override fun read(name: String): String? = System.getenv(name)
    }
}
