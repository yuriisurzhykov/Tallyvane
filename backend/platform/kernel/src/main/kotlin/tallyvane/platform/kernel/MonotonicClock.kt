package tallyvane.platform.kernel

/**
 * A source for measuring elapsed time without depending on wall-clock adjustments.
 */
public fun interface MonotonicClock {

    public fun nowNanos(): Long

    /**
     * Process monotonic time, for infrastructure such as HTTP duration measurement.
     */
    public class System : MonotonicClock {
        override fun nowNanos(): Long = java.lang.System.nanoTime()
    }
}
