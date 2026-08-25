package tallyvane.platform.observability

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain

class HealthCheckContainedSpec :
    StringSpec(
        {
            "passes an answer through untouched" {
                val degraded = Health.Degraded(Ailment.Refused("slow"))

                HealthCheck.Contained(HealthCheckFake("postgres", answer = degraded)).check() shouldBe degraded
            }

            "turns a failure into Down naming the exception type" {
                val check = HealthCheckFake("postgres", throws = IllegalStateException("connection refused"))

                HealthCheck.Contained(check).check() shouldBe Health.Down(Ailment.Threw("IllegalStateException"))
            }

            "keeps a driver's message out of the report, since it may carry a host or a secret" {
                val leaky = IllegalStateException("jdbc:postgresql://tallyvane:hunter2@10.0.0.4:5432/db")
                val check = HealthCheckFake("postgres", throws = leaky)

                val health = HealthCheck.Contained(check).check()

                health.toString() shouldNotContain "hunter2"
                health.toString() shouldNotContain "10.0.0.4"
            }

            "answers under the delegate's own name and readiness, not its own" {
                val contained = HealthCheck.Contained(HealthCheckFake("llm", requiredForReadiness = false))

                contained.name shouldBe "llm"
                contained.requiredForReadiness shouldBe false
            }
        },
    )
