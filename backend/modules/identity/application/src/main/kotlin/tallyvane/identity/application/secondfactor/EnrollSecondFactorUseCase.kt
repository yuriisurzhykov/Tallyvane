package tallyvane.identity.application.secondfactor

import tallyvane.platform.kernel.UseCase

/**
 * Starts enrolling the already-signed-in caller in one second factor — one action, per ADR-053,
 * distinct from [ConfirmSecondFactorEnrollmentUseCase]: this one hands back whatever the chosen
 * mechanism's own client-side flow needs (an `otpauth://` URI for TOTP), and confirming a code
 * against it is a second action with its own request shape, not a second branch of this one.
 *
 * A design plan sketch listing one `EnrollSecondFactorUseCase` for the whole flow did not survive
 * contact with ADR-053 once "start" and "confirm" turned out to need different requests and
 * different answers — the same kind of correction already recorded for `SessionIssuer.issue`'s
 * return type: `application/README.md`.
 */
public interface EnrollSecondFactorUseCase : UseCase {
    /**
     * Null only if [EnrollSecondFactorRequest.kind] names a mechanism the registry has nothing
     * registered for — a deployment misconfiguration, not a normal outcome a real client should
     * ever trigger, since the client only ever offers kinds this deployment actually advertises.
     */
    public suspend fun enroll(request: EnrollSecondFactorRequest): String?

    public class Enroll internal constructor(private val registry: SecondFactorMethodRegistry) :
        EnrollSecondFactorUseCase {
        override suspend fun enroll(request: EnrollSecondFactorRequest): String? =
            registry.find(request.kind)?.startEnrollment(request.userId)
    }
}
