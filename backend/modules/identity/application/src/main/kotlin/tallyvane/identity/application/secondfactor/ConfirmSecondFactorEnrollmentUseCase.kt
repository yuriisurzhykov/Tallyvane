package tallyvane.identity.application.secondfactor

import tallyvane.platform.kernel.UseCase

/**
 * Confirms a [EnrollSecondFactorUseCase] already in progress for the caller — the second half of
 * enrollment, distinct from the first per this package's own `EnrollSecondFactorUseCase` KDoc.
 */
public interface ConfirmSecondFactorEnrollmentUseCase : UseCase {
    /**
     * False both for a wrong code and for a [ConfirmSecondFactorEnrollmentRequest.kind] the
     * registry has nothing registered for — this use case does not distinguish the two, the same
     * "don't leak why a credential was refused" choice
     * [tallyvane.identity.application.password.SignInWithPasswordUseCase] already makes for a
     * password.
     */
    public suspend fun confirm(request: ConfirmSecondFactorEnrollmentRequest): Boolean

    public class Confirm internal constructor(private val registry: SecondFactorMethodRegistry) :
        ConfirmSecondFactorEnrollmentUseCase {
        override suspend fun confirm(request: ConfirmSecondFactorEnrollmentRequest): Boolean =
            registry.find(request.kind)?.confirmEnrollment(request.userId, request.code) ?: false
    }
}
