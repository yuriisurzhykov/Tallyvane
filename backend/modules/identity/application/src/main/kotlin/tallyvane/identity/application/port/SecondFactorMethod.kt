package tallyvane.identity.application.port

import tallyvane.identity.application.secondfactor.totp.Base32
import tallyvane.identity.application.secondfactor.totp.Rfc6238Totp
import tallyvane.identity.domain.secondfactor.EncryptedSecret
import tallyvane.identity.domain.secondfactor.SecondFactorKind
import tallyvane.identity.domain.secondfactor.totp.TotpEnrollment
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.Secret
import java.security.SecureRandom
import kotlin.time.Duration.Companion.seconds

/**
 * One interchangeable second-factor mechanism — TOTP today, WebAuthn and Email OTP named in the
 * design as the next two. [tallyvane.identity.application.secondfactor.SecondFactorMethodRegistry]
 * is what a sign-in path, [tallyvane.identity.application.EnrollSecondFactorUseCase] or
 * [tallyvane.identity.application.VerifySecondFactorUseCase] actually holds; none of the three
 * ever depends on one [SecondFactorMethod] by name.
 *
 * Enrollment earns a place on this shared port for the same reason [verify] already has one: both
 * are reached through exactly one endpoint (`/auth/mfa/enroll`, `/auth/mfa/verify`) whose caller
 * does not know in advance which mechanism answers — a registry-dispatched action, not a
 * prediction about mechanisms that do not exist yet. `application/README.md` has the comparison
 * against `AuthenticationMethod`, which needed no such dispatch and was reverted for exactly that
 * reason.
 *
 * [startEnrollment]'s and [confirmEnrollment]'s recovery-code question is still open, though —
 * `application/README.md` names it.
 */
public interface SecondFactorMethod {
    public val kind: SecondFactorKind

    public suspend fun isEnrolledFor(userId: UserId): Boolean

    /**
     * Checks [code] for [userId] against whatever this method already has enrolled — never called
     * for a [userId] this method is not enrolled for, per
     * [tallyvane.identity.application.secondfactor.SecondFactorMethodRegistry]'s own lookup by
     * [tallyvane.identity.domain.secondfactor.PendingAuthentication.availableMethods].
     */
    public suspend fun verify(userId: UserId, code: String): Boolean

    /**
     * Starts enrolling [userId] in this mechanism and returns whatever its own client-side flow
     * needs to finish the job — an `otpauth://` URI for TOTP, say. The format is a private
     * contract between one [SecondFactorMethod] and the one route that calls it; neither the
     * registry nor [tallyvane.identity.application.EnrollSecondFactorUseCase] reads this string,
     * only passes it through.
     *
     * Not active yet: [confirmEnrollment] is what proves the account holder actually captured it
     * correctly (scanned the right QR code, registered the right authenticator) before
     * [isEnrolledFor] can ever answer `true`.
     */
    public suspend fun startEnrollment(userId: UserId): String

    /**
     * Confirms a [startEnrollment] already in progress for [userId] by checking [code] against the
     * not-yet-active enrollment, activating it only on a match.
     */
    public suspend fun confirmEnrollment(userId: UserId, code: String): Boolean

    /**
     * TOTP (RFC 6238) over HMAC-SHA1, 6 digits, a 30-second step — the defaults every
     * `otpauth://` URI leaves optional, so any authenticator app agrees with this class without
     * either side naming them. Reaches no technology beyond the JDK's own `javax.crypto.Mac`
     * (inside [Rfc6238Totp]) and [SecureRandom] for a fresh seed, composing [SecretCipher] and
     * [TotpEnrollmentStore] rather than owning either technology itself — the same reason
     * [SessionIssuer][tallyvane.identity.application.SessionIssuer] nests despite composing ports
     * whose real implementations will do I/O (ADR-047).
     */
    public class Rfc6238(
        private val users: UserRepository,
        private val cipher: SecretCipher,
        private val enrollments: TotpEnrollmentStore,
        private val clock: Clock,
        private val issuer: String,
    ) : SecondFactorMethod {
        override val kind: SecondFactorKind = SecondFactorKind.TOTP
        private val random = SecureRandom()
        private val base32 = Base32()
        private val totp = Rfc6238Totp()

        override suspend fun isEnrolledFor(userId: UserId): Boolean = enrollments.find(userId)?.active == true

        /**
         * Checks [code] against the step containing [tallyvane.platform.kernel.Clock.now] and the
         * one immediately before and after it — one step of tolerance either way for a clock that
         * has drifted, the gap RFC 6238 itself expects a verifier to allow.
         */
        override suspend fun verify(userId: UserId, code: String): Boolean {
            val enrollment = enrollments.find(userId)?.takeIf { it.active } ?: return false
            return matchesWithSkew(enrollment.secret, code)
        }

        override suspend fun startEnrollment(userId: UserId): String {
            val user = users.findById(userId) ?: error("startEnrollment called for a user that does not exist")
            val secretBytes = ByteArray(SECRET_BYTES).also { random.nextBytes(it) }
            val base32Secret = base32.encode(secretBytes)
            enrollments.save(
                TotpEnrollment(userId, cipher.encrypt(Secret(base32Secret)), active = false, createdAt = clock.now()),
            )
            return provisioningUri(user.email.value, base32Secret)
        }

        override suspend fun confirmEnrollment(userId: UserId, code: String): Boolean {
            val enrollment = enrollments.find(userId)?.takeIf { !it.active } ?: return false
            return matchesWithSkew(enrollment.secret, code).also { matched ->
                if (matched) enrollments.save(enrollment.copy(active = true))
            }
        }

        private fun matchesWithSkew(encrypted: EncryptedSecret, code: String): Boolean {
            val secretBytes = base32.decode(cipher.decrypt(encrypted).revealed())
            val now = clock.now()
            return SKEW_STEPS.any { totp.codeAt(secretBytes, now + STEP * it) == code }
        }

        /**
         * The Key URI Format `google/google-authenticator`'s own wiki documents, followed rather
         * than invented — every authenticator app already assumes it.
         */
        private fun provisioningUri(email: String, base32Secret: String): String {
            val label = "${percentEncode(issuer)}:${percentEncode(email)}"
            return "otpauth://totp/$label?secret=$base32Secret&issuer=${percentEncode(issuer)}"
        }

        /**
         * RFC 3986's unreserved characters pass through; everything else becomes `%XX` over its
         * UTF-8 bytes. Not [java.net.URLEncoder]: it encodes a space as `+`, the
         * `application/x-www-form-urlencoded` rule, not RFC 3986's — a `+` inside this URI's own
         * query values would reach an authenticator app as a literal plus sign, not a space.
         */
        private fun percentEncode(value: String): String {
            val encoded = StringBuilder()
            for (byte in value.toByteArray(Charsets.UTF_8)) {
                val unsigned = byte.toInt() and BYTE_MASK
                if (unsigned.toChar() in UNRESERVED) {
                    encoded.append(unsigned.toChar())
                } else {
                    encoded
                        .append('%')
                        .append(HEX_DIGITS[unsigned shr HEX_SHIFT])
                        .append(HEX_DIGITS[unsigned and HEX_MASK])
                }
            }
            return encoded.toString()
        }

        private companion object {
            const val SECRET_BYTES = 20
            val STEP = 30.seconds
            val SKEW_STEPS = listOf(-1, 0, 1)
            const val UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
            const val HEX_DIGITS = "0123456789ABCDEF"
            const val BYTE_MASK = 0xFF
            const val HEX_SHIFT = 4
            const val HEX_MASK = 0xF
        }
    }
}
