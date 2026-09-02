package tallyvane.identity.application.secondfactor

import tallyvane.identity.application.port.SecondFactorMethod
import tallyvane.identity.domain.secondfactor.SecondFactorKind
import tallyvane.identity.domain.user.UserId

/**
 * The lookup every [SecondFactorMethod] family is resolved from at runtime — one endpoint
 * (`/auth/mfa/verify`), several possible mechanisms behind it, the same Registry shape
 * `ARCHITECTURE.md` already describes for `JobSourceRegistry`.
 *
 * Empty today: nothing implements [SecondFactorMethod] yet (TOTP arrives with the next slice), so
 * [enrolledFor] always answers an empty set and no sign-in path can actually be routed to
 * `RequiresSecondFactor` in production — real code, exercised by this module's own tests against a
 * fake [SecondFactorMethod], the same "no real implementation yet" state [PendingAuthenticationStore]
 * is already in.
 */
internal interface SecondFactorMethodRegistry {
    fun find(kind: SecondFactorKind): SecondFactorMethod?

    suspend fun enrolledFor(userId: UserId): Set<SecondFactorKind>

    class Default(private val methods: List<SecondFactorMethod>) : SecondFactorMethodRegistry {
        override fun find(kind: SecondFactorKind): SecondFactorMethod? = methods.find { it.kind == kind }

        override suspend fun enrolledFor(userId: UserId): Set<SecondFactorKind> {
            val enrolled = mutableSetOf<SecondFactorKind>()
            for (method in methods) {
                if (method.isEnrolledFor(userId)) {
                    enrolled += method.kind
                }
            }
            return enrolled
        }
    }
}
