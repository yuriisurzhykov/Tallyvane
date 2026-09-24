package tallyvane.identity.domain.credential

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class PasswordPolicySpec : StringSpec({
    val policy = PasswordPolicy(minimumLength = 15, maximumLength = 128)

    "accepts a long passphrase without mandatory character classes" {
        policy.accepts("a quiet evening at home") shouldBe true
    }

    "rejects a password below the configured minimum" {
        policy.accepts("a".repeat(14)) shouldBe false
    }

    "accepts the boundaries and rejects excessive length" {
        policy.accepts("a".repeat(15)) shouldBe true
        policy.accepts("a".repeat(128)) shouldBe true
        policy.accepts("a".repeat(129)) shouldBe false
    }

    "counts Unicode code points rather than UTF-16 units" {
        policy.accepts("\uD83D\uDE00".repeat(8)) shouldBe false
        policy.accepts("\uD83D\uDE00".repeat(15)) shouldBe true
    }
})
