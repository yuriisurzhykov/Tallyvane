package tallyvane.platform.llm

/**
 * Which provider serves the model calls now, as an administrator chose it.
 */
public interface Providers {
    /**
     * The chosen provider, or null when none is.
     */
    public suspend fun active(): Provider?
}
