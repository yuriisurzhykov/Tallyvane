package tallyvane.identity.application.secondfactor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ReadSpec :
    StringSpec({
        "refuses an unverified non-admin and records the denial" {
            val fixture = PolicyFixture()
            fixture.addUser(fixture.outsider, "admin@example.test", verified = false)

            fixture.read.read(fixture.outsider) shouldBe AuthenticationPolicyResult.Forbidden
            fixture.auditRecords shouldBe listOf("POLICY_READ_DENIED")
        }
    })
