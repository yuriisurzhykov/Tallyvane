package tallyvane.identity.domain.secondfactor

/**
 * Which second-factor mechanism a [tallyvane.identity.application.port.SecondFactorMethod]
 * implements — the discriminator `POST /auth/mfa/verify` reads off a request to look one up in the
 * registry, per [tallyvane.identity.application.port.SecondFactorMethod]'s own KDoc.
 *
 * One case exists because one mechanism (TOTP) is built. Adding `WEBAUTHN`/`EMAIL_OTP` here is the
 * whole cost of a new mechanism's discriminator — the rest of the wiring (`EnrollSecondFactorUseCase`,
 * `VerifySecondFactorUseCase`, the registry) already dispatches on this type without a code change,
 * which is the point of the registry the design plan calls for: `application/README.md`.
 */
public enum class SecondFactorKind {
    TOTP,
}
