package tallyvane.identity.infrastructure.persistence

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.identity.domain.secondfactor.AuthenticationPolicy
import tallyvane.identity.domain.secondfactor.MfaRequirement
import tallyvane.identity.domain.secondfactor.PrimaryMethod
import tallyvane.identity.domain.secondfactor.SecondFactorKind
import tallyvane.platform.kernel.Verdict
import tallyvane.platform.persistence.PostgresFixture
import tallyvane.platform.persistence.PostgresPersistence

class AuthenticationPolicyStoreOverExposedSpec :
    StringSpec({
        "persists versioned rules and rejects a stale replacement" {
            PostgresPersistence(PostgresFixture.migrated()).use { persistence ->
                val store = AuthenticationPolicyStoreOverExposed()
                val initial = persistence.transactions.inTransaction { Verdict.Commit(store.current()) }
                initial!!.version shouldBe 1
                initial.rules shouldBe AuthenticationPolicy.defaults().rules
                initial.advancedAcknowledged shouldBe false
                val replacement = AuthenticationPolicy(
                    2,
                    initial.rules.values.map { rule ->
                        if (rule.primary == PrimaryMethod.PASSWORD) {
                            rule.copy(
                                requirement = MfaRequirement.REQUIRED,
                                allowedMethods = setOf(SecondFactorKind.TOTP, SecondFactorKind.BACKUP_CODE),
                            )
                        } else {
                            rule
                        }
                    },
                    false,
                )

                persistence.transactions.inTransaction { Verdict.Commit(store.replace(1, replacement)) } shouldBe true
                persistence.transactions.inTransaction { Verdict.Commit(store.replace(1, replacement)) } shouldBe false
                val saved = persistence.transactions.inTransaction { Verdict.Commit(store.current()) }
                saved!!.version shouldBe replacement.version
                saved.rules shouldBe replacement.rules
                saved.advancedAcknowledged shouldBe replacement.advancedAcknowledged
            }
        }
    })
