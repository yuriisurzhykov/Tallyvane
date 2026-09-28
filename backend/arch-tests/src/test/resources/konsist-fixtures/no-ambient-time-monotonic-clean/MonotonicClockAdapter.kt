package tallyvane.platform.kernel

class ProcessMonotonicClock : MonotonicClock {
    override fun nowNanos(): Long = System.nanoTime()
}
