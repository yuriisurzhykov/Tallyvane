package tallyvane.platform.persistence

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.platform.observability.health.Ailment
import tallyvane.platform.observability.health.Health

class MigrationsAppliedSpec :
    StringSpec(
        {
            "is up when nothing is pending" {
                MigrationsApplied(MigrationsFake()).check() shouldBe Health.Up
            }

            // The versions, not a count and not a sentence: one pending version is a deploy
            // that started the application before its migration step, several is a deploy
            // that skipped the step altogether, and the remedies differ.
            "is down and names the versions the schema is behind by" {
                val check = MigrationsApplied(MigrationsFake(listOf("20260825020000", "20260826090000")))

                check.check() shouldBe
                    Health.Down(Ailment.Behind(listOf("20260825020000", "20260826090000")))
            }

            // ADR-051: the deploy applies, readiness verifies. A probe that migrates is a
            // probe that always passes, and this is the assertion that would catch it.
            "verifies the schema without applying anything" {
                val migrations = MigrationsFake(listOf("20260825020000"))

                MigrationsApplied(migrations).check()

                migrations.applications shouldBe 0
            }

            "is required for readiness, because a stale schema serves wrong answers rather than none" {
                MigrationsApplied(MigrationsFake()).requiredForReadiness shouldBe true
            }
        },
    )
