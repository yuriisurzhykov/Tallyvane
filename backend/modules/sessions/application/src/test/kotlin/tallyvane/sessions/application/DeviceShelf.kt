package tallyvane.sessions.application

import tallyvane.platform.kernel.Secret
import tallyvane.sessions.domain.Browser
import tallyvane.sessions.domain.Platform
import tallyvane.sessions.domain.SessionId
import tallyvane.sessions.domain.UserAgent
import kotlin.time.Instant

internal val MAC_CHROME = UserAgent(
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/130.0.0.0 Safari/537.36",
)

internal class Shown(
    val id: SessionId,
    val browser: Browser,
    val platform: Platform,
    val name: String?,
    val lastActiveAt: Instant,
    val current: Boolean,
)

internal suspend fun Harness.shown(session: Secret?): List<Shown> {
    val outcome = listDevices.list(session)
    val shown = mutableListOf<Shown>()
    (outcome as DeviceOutcome.Listed).writeTo { id, browser, platform, _, name, _, lastActiveAt, current ->
        shown += Shown(id, browser, platform, name, lastActiveAt, current)
    }
    return shown
}

internal suspend fun Harness.idOfOther(session: Secret): SessionId = shown(session).single { !it.current }.id
