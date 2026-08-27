package tallyvane.platform.kernel

/**
 * Configuration as a collaborator, not as a static call.
 *
 * Read settings through this rather than through `System.getenv`, which a test cannot set: the JVM
 * exposes the process environment read-only. A test supplies an [EnvironmentFake] and so can
 * describe the environment its case is about.
 *
 * Same shape as [Clock] and [IdGenerator] — a port over ambient state, implementation nested — and,
 * unlike those two, not yet enforced by a rule: nothing stops a file from calling `System.getenv`
 * directly.
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
     * indistinguishable from the `System.getenv` this port exists to keep out of everything else.
     */
    public class Process : Environment {
        override fun read(name: String): String? = System.getenv(name)
    }
}
