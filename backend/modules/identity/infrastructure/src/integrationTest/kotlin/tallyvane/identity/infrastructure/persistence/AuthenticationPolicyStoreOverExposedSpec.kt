package tallyvane.identity.infrastructure.persistence

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.identity.domain.secondfactor.AuthenticationPolicy
import tallyvane.platform.kernel.Verdict
import tallyvane.platform.persistence.PostgresFixture
import tallyvane.platform.persistence.PostgresPersistence

class AuthenticationPolicyStoreOverExposedSpec :
    StringSpec({
        "persists versioned rules and rejects a stale replacement" {
            PostgresPersistence(PostgresFixture.migrated()).use { persistence ->
                val store = AuthenticationPolicyStoreOverExposed()
                val initial = persistence.transactions.inTransaction { Verdict.Commit(store.current()) }
                initial!!.version shouldBe 2
                initial.rules shouldBe AuthenticationPolicy.defaults().rules
                initial.advancedAcknowledged shouldBe false
                val replacement = AuthenticationPolicy.fromSchemes(
                    3,
                    initial.schemes.map { scheme -> scheme.copy(assuranceRank = scheme.assuranceRank + 1) },
                    false,
                )

                persistence.transactions.inTransaction { Verdict.Commit(store.replace(2, replacement)) } shouldBe true
                persistence.transactions.inTransaction { Verdict.Commit(store.replace(2, replacement)) } shouldBe false
                val saved = persistence.transactions.inTransaction { Verdict.Commit(store.current()) }
                saved!!.version shouldBe replacement.version
                saved.schemes shouldBe replacement.schemes
                saved.advancedAcknowledged shouldBe replacement.advancedAcknowledged
            }
        }
    })
