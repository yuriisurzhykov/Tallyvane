package tallyvane.identity.infrastructure

import tallyvane.identity.application.port.LoginAttempts
import tallyvane.identity.application.port.LoginAttemptsConformance
import tallyvane.platform.cache.Counter
import tallyvane.platform.kernel.ClockFake
import kotlin.time.Instant

class LoginAttemptsOverCounterSpec : LoginAttemptsConformance() {
    override fun fresh(): LoginAttempts =
        LoginAttemptsOverCounter(Counter.InMemory(ClockFake(Instant.parse("2026-01-01T00:00:00Z"))))
}
