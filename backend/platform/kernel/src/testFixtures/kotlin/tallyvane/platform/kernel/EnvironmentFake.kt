package tallyvane.platform.kernel

/**
 * The environment a test is describing, stated rather than inherited from the machine.
 *
 * Handwritten rather than mocked (ADR-044, `no-mock-libraries`): the whole port is one function, so
 * a mock would be more setup than implementation.
 *
 * @param values every variable this environment defines. A name absent from the map reads as absent
 * from the environment, which is the case most of the configuration tests are about.
 */
public class EnvironmentFake(private val values: Map<String, String> = emptyMap()) : Environment {
    override fun read(name: String): String? = values[name]
}
