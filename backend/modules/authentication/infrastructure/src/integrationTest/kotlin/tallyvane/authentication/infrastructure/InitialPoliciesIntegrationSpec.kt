package tallyvane.authentication.infrastructure

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.authentication.application.VersionStory
import tallyvane.authentication.domain.Purpose
import tallyvane.platform.kernel.Verdict
import tallyvane.platform.persistence.PostgresFixture
import tallyvane.platform.persistence.PostgresPersistence
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val GOOGLE = "step [Google] Always"
private const val SECOND_IF_ENABLED = "step [Totp, RecoveryCode] WhenEnrolled"
private const val SECOND_ALWAYS = "step [Totp, RecoveryCode] Always"
private val LIMITS = "limits ${5.minutes} 5 ${1.seconds}"

/**
 * The policies a migrated database starts with are the ones ADR-078 names, in force as version 1.
 *
 * Read back through the domain: `PolicyVersion.restore` checks each against the bounds the code
 * sets today, so a seed that had fallen outside them would fail here, not at someone's first
 * sign-in. The expected lines are written out in full, in the words of the ADR's table, on purpose:
 * a spec that derived them from the migration would agree with whatever the migration said.
 */
class InitialPoliciesIntegrationSpec :
    StringSpec(
        {
            val expected = mapOf(
                Purpose.Registration to listOf("number 1", "purpose Registration", GOOGLE, LIMITS),
                Purpose.Login to listOf("number 1", "purpose Login", GOOGLE, SECOND_IF_ENABLED, LIMITS),
                Purpose.AdminLogin to listOf("number 1", "purpose AdminLogin", GOOGLE, SECOND_ALWAYS, LIMITS),
                Purpose.StepUp to listOf("number 1", "purpose StepUp", GOOGLE, SECOND_IF_ENABLED, LIMITS),
            )

            Purpose.entries.forEach { purpose ->
                "a migrated database has version 1 of the $purpose policy in force, as ADR-078 says" {
                    val persistence = PostgresPersistence(PostgresFixture.migrated())
                    try {
                        val inForce = persistence.transactions.inTransaction {
                            Verdict.Commit(AuthenticationStorageFactory().policyVersions().active(purpose))
                        }

                        VersionStory(checkNotNull(inForce)).told() shouldBe expected[purpose]
                    } finally {
                        persistence.close()
                    }
                }
            }
        },
    )
