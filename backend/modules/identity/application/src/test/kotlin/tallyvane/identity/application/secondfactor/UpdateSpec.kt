package tallyvane.identity.application.secondfactor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class UpdateSpec :
    StringSpec({
        "saves a new policy version and rejects stale edits" {
            val fixture = PolicyFixture()
            fixture.addUser(fixture.admin, "admin@example.test", verified = true)
            val rules = fixture.rulesRequiringPasswordMfa()

            val saved = fixture.update.update(fixture.admin, 1, rules, false)
            (saved as AuthenticationPolicyResult.Policy).value.version shouldBe 2
            fixture.update.update(fixture.admin, 1, rules, false) shouldBe AuthenticationPolicyResult.Conflict
            fixture.currentPolicy.version shouldBe 2
            fixture.auditRecords shouldBe listOf("POLICY_UPDATED", "POLICY_UPDATE_CONFLICT")
        }
    })
