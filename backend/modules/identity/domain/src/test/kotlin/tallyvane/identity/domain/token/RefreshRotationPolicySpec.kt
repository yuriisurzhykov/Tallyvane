package tallyvane.identity.domain.token

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.identity.domain.session.SessionId
import kotlin.uuid.Uuid

class RefreshRotationPolicySpec :
    StringSpec({
        val sessionId = SessionId(Uuid.parse("00000000-0000-7000-8000-000000000001"))

        "a token that has not been used before is rotated" {
            val decision = RefreshRotationPolicy.Default().decide(TokenFamilyState(sessionId, used = false))

            decision shouldBe RefreshRotationDecision.Rotate
        }

        "a token already used is reported as reuse, naming the session to revoke" {
            val decision = RefreshRotationPolicy.Default().decide(TokenFamilyState(sessionId, used = true))

            decision shouldBe RefreshRotationDecision.ReuseDetected(sessionId)
        }
    })
