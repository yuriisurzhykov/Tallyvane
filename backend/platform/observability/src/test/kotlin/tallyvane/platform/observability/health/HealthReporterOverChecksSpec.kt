package tallyvane.platform.observability.health

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

class HealthReporterOverChecksSpec :
    StringSpec(
        {
            "is Up and ready when every check is Up" {
                val report = HealthReporter.OverChecks(listOf(HealthCheckFake("postgres"))).report()

                report.status shouldBe Health.Up
                report.ready shouldBe true
            }

            "is Down and unready when a required check is Down" {
                val refused = Health.Down(Ailment.Refused("no connection"))
                val checks = listOf(HealthCheckFake("postgres", answer = refused))

                val report = HealthReporter.OverChecks(checks).report()

                report.status shouldBe Health.Down(Ailment.Dependencies(listOf("postgres")))
                report.ready shouldBe false
            }

            "stays ready when an optional check is Down, but says so" {
                val checks =
                    listOf(
                        HealthCheckFake("postgres"),
                        HealthCheckFake(
                            "llm",
                            requiredForReadiness = false,
                            answer = Health.Down(Ailment.Refused("no route")),
                        ),
                    )

                val report = HealthReporter.OverChecks(checks).report()

                report.ready shouldBe true
                report.status shouldBe Health.Degraded(Ailment.Dependencies(listOf("llm")))
            }

            "names in the aggregate only the checks that are not Up" {
                val checks =
                    listOf(
                        HealthCheckFake("postgres"),
                        HealthCheckFake(
                            "llm",
                            requiredForReadiness = false,
                            answer = Health.Degraded(Ailment.Refused("slow")),
                        ),
                    )

                val report = HealthReporter.OverChecks(checks).report()

                report.status
                    .shouldBeInstanceOf<Health.Degraded>()
                    .cause
                    .shouldBeInstanceOf<Ailment.Dependencies>()
                    .names shouldContainExactly listOf("llm")
            }

            "keeps each check's own account of itself, not only the aggregate" {
                val refused = Health.Down(Ailment.Refused("no connection"))
                val checks = listOf(HealthCheckFake("postgres", answer = refused))

                val report = HealthReporter.OverChecks(checks).report()

                report.checks.single().health shouldBe refused
            }

            "lets a check's failure through, since containing it is Contained's job" {
                val checks = listOf(HealthCheckFake("postgres", throws = IllegalStateException("connection refused")))

                shouldThrow<IllegalStateException> {
                    HealthReporter.OverChecks(checks).report()
                }
            }

            "runs checks at once, so the whole report costs the longest one rather than the sum" {
                val checks = List(4) { HealthCheckFake("dependency-$it", takes = 200.milliseconds) }
                val started = TimeSource.Monotonic.markNow()

                HealthReporter.OverChecks(checks).report()

                started.elapsedNow() shouldBeLessThan 600.milliseconds
            }

            "keeps the checks in the order they were given" {
                val checks = listOf(HealthCheckFake("postgres"), HealthCheckFake("llm"), HealthCheckFake("storage"))

                val report = HealthReporter.OverChecks(checks).report()

                report.checks.map { it.name } shouldContainExactly listOf("postgres", "llm", "storage")
            }

            "refuses two checks under one name, which no alert could tell apart" {
                shouldThrow<IllegalArgumentException> {
                    HealthReporter.OverChecks(listOf(HealthCheckFake("postgres"), HealthCheckFake("postgres")))
                }
            }
        },
    )
