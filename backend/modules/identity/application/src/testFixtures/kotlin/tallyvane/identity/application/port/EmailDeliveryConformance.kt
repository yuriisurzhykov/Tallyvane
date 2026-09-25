package tallyvane.identity.application.port

import io.kotest.core.spec.style.StringSpec
import tallyvane.identity.domain.email.EmailChallengePurpose
import tallyvane.identity.domain.user.Email
import tallyvane.platform.kernel.Secret

/**
 * Shared contract for delivery adapters: a valid code for a purpose is accepted for its recipient.
 */
public abstract class EmailDeliveryConformance : StringSpec() {
    protected abstract fun fresh(): EmailDelivery

    init {
        "delivers a six-digit code for its requested purpose" {
            fresh().sendCode(Email("person@example.test"), EmailChallengePurpose.REGISTRATION, Secret("123456"))
        }
    }
}
