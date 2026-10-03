package tallyvane.authentication.infrastructure

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import tallyvane.authentication.application.CheckedPolicy
import tallyvane.authentication.application.VersionStory
import tallyvane.authentication.domain.PolicyVersion
import tallyvane.authentication.domain.Purpose
import tallyvane.platform.kernel.Verdict
import tallyvane.platform.persistence.PostgresFixture
import tallyvane.platform.persistence.PostgresPersistence
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

private val NOW = Instant.parse("2026-10-01T09:00:00Z")

// A migrated database holds version 1 of every purpose, so the first of two adds is 2.
private const val FIRST_ADDED = 2
private const val SECOND_ADDED = FIRST_ADDED + 1

/**
 * Two administrators adding a version of the same purpose at the same moment, against a real
 * Postgres.
 *
 * The migrated database already holds version 1, so the two get 2 and 3, in whichever order the lock
 * lets them in. The first holds its transaction open for half a second after adding, so the second
 * meets a number that is taken but not yet committed; without the lock it would count the same
 * number and fail on the primary key, with an error where the administrator should have had a
 * version.
 */
class PolicyVersionsConcurrencyIntegrationSpec :
    StringSpec(
        {
            "two administrators adding at once each get a version of their own" {
                val persistence = PostgresPersistence(PostgresFixture.migrated())
                try {
                    val versions = storageForTests().policyVersions()
                    val policy = CheckedPolicy(Purpose.Login).policy()

                    val added = coroutineScope {
                        val first = async {
                            persistence.transactions.inTransaction {
                                val version = versions.add(policy, NOW)
                                delay(500.milliseconds)
                                Verdict.Commit(version)
                            }
                        }
                        val second = async {
                            delay(100.milliseconds)
                            persistence.transactions.inTransaction { Verdict.Commit(versions.add(policy, NOW)) }
                        }
                        first.await() to second.await()
                    }

                    VersionStory(added.first) shouldBe VersionStory(PolicyVersion(FIRST_ADDED, policy))
                    VersionStory(added.second) shouldBe VersionStory(PolicyVersion(SECOND_ADDED, policy))
                } finally {
                    persistence.close()
                }
            }
        },
    )
