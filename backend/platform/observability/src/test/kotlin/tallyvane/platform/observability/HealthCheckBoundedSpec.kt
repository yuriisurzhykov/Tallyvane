package tallyvane.platform.observability

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.util.concurrent.CountDownLatch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Blocks its thread instead of suspending, the way a JDBC socket read does. A
 * check written with `delay` proves nothing about this case: cancellation is
 * cooperative, so a body that never reaches a suspension point never observes it.
 */
private class BlockingCheck(override val name: String, private val blocksFor: Duration) : HealthCheck {
    override val requiredForReadiness: Boolean = true

    /**
     * Falls as the blocking call is entered, so a test need not sleep to know it started.
     */
    val entered: CountDownLatch = CountDownLatch(1)

    override suspend fun check(): Health {
        entered.countDown()
        Thread.sleep(blocksFor.inWholeMilliseconds)
        return Health.Up
    }
}

class HealthCheckBoundedSpec :
    StringSpec(
        {
            val abandoned = CoroutineScope(SupervisorJob() + Dispatchers.IO)

            afterSpec { abandoned.cancel() }

            "passes an answer that arrived in time straight through" {
                val degraded = Health.Degraded(Ailment.Refused("slow"))
                val check = HealthCheckFake("postgres", answer = degraded)

                HealthCheck.Bounded(check, 1.seconds, abandoned).check() shouldBe degraded
            }

            "reports a suspending check that outran the bound as Down, naming the bound" {
                val check = HealthCheckFake("postgres", takes = 5.seconds)

                val health = HealthCheck.Bounded(check, 30.milliseconds, abandoned).check()

                health shouldBe Health.Down(Ailment.Overran(30.milliseconds))
            }

            "bounds a check that blocks its thread, which cancellation alone cannot" {
                val check = BlockingCheck("postgres", blocksFor = 2.seconds)
                val started = TimeSource.Monotonic.markNow()

                val health = HealthCheck.Bounded(check, 30.milliseconds, abandoned).check()

                started.elapsedNow() shouldBeLessThan 1.seconds
                health shouldBe Health.Down(Ailment.Overran(30.milliseconds))
            }

            "answers again while an abandoned check is still blocking" {
                val check = BlockingCheck("postgres", blocksFor = 2.seconds)
                val bounded = HealthCheck.Bounded(check, 30.milliseconds, abandoned)
                bounded.check()
                check.entered.await()
                val started = TimeSource.Monotonic.markNow()

                bounded.check()

                started.elapsedNow() shouldBeLessThan 1.seconds
            }

            "answers under the delegate's own name and readiness, not its own" {
                val check = HealthCheckFake("llm", requiredForReadiness = false)

                val bounded = HealthCheck.Bounded(check, 1.seconds, abandoned)

                bounded.name shouldBe "llm"
                bounded.requiredForReadiness shouldBe false
            }
        },
    )
