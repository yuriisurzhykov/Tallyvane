package tallyvane.jobs.infrastructure

import tallyvane.platform.cache.Counter

internal class Dirty(private val attempts: Counter) {
    suspend fun touch() {
        attempts.increment("identity:borrowed-key", 900)
    }
}
