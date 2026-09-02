package tallyvane.identity.application.port

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import tallyvane.identity.application.secondfactor.totp.Base32
import tallyvane.identity.application.secondfactor.totp.Rfc6238Totp
import tallyvane.identity.domain.secondfactor.EncryptedSecret
import tallyvane.identity.domain.user.Email
import tallyvane.identity.domain.user.User
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.ClockFake
import tallyvane.platform.kernel.Secret
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * [SecondFactorMethodConformance] covers what every [SecondFactorMethod] must do; this adds only
 * what is actually specific to [SecondFactorMethod.Rfc6238] — the shape of the enrollment payload
 * it hands back, and its clock-skew tolerance.
 */
class SecondFactorMethodRfc6238Spec : SecondFactorMethodConformance() {
    private val userId = UserId(Uuid.parse("00000000-0000-7000-8000-000000000001"))
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val user = User(userId, Email("person@example.com"), null, now, null)

    // Round-trips through an identity cipher rather than a real one — this spec is about
    // `Rfc6238`'s own enrollment/verification behaviour, already covered against real Tink by
    // `TinkSecretCipherSpec` in `infrastructure`; duplicating that here would test the same fact
    // twice under two different names.
    private val plainCipher =
        object : SecretCipher {
            override fun encrypt(plaintext: Secret) = EncryptedSecret(plaintext.revealed())

            override fun decrypt(ciphertext: EncryptedSecret) = Secret(ciphertext.value)
        }

    override suspend fun fresh(): SecondFactorMethod = SecondFactorMethod.Rfc6238(
        UserRepositoryFake().also { it.insert(user) },
        plainCipher,
        TotpEnrollmentStoreFake(),
        ClockFake(now),
        issuer = "Tallyvane",
    )

    override fun userId(): UserId = userId

    override fun correctCodeFor(startEnrollmentPayload: String): String {
        val secret = Regex("""secret=([A-Z2-7]+)""").find(startEnrollmentPayload)!!.groupValues[1]
        return Rfc6238Totp().codeAt(Base32().decode(secret), now)
    }

    init {
        "the enrollment payload follows the Key URI Format: type, label, secret, issuer" {
            val uri = fresh().startEnrollment(userId)

            uri shouldContain "otpauth://totp/"
            uri shouldContain "issuer=Tallyvane"
            uri shouldContain "person%40example.com"
        }

        "verify tolerates a code from one step of clock drift, either direction, but no more" {
            val clock = ClockFake(now)
            val enrollments = TotpEnrollmentStoreFake()
            val method = SecondFactorMethod.Rfc6238(
                UserRepositoryFake().also { it.insert(user) },
                plainCipher,
                enrollments,
                clock,
                issuer = "Tallyvane",
            )
            val payload = method.startEnrollment(userId)
            method.confirmEnrollment(userId, correctCodeFor(payload))
            val secret = Regex("""secret=([A-Z2-7]+)""").find(payload)!!.groupValues[1]
            val totp = Rfc6238Totp()
            fun codeAt(at: Instant) = totp.codeAt(Base32().decode(secret), at)

            method.verify(userId, codeAt(now - 30.seconds)) shouldBe true
            method.verify(userId, codeAt(now + 30.seconds)) shouldBe true
            method.verify(userId, codeAt(now - 60.seconds)) shouldBe false
        }
    }
}
