package tallyvane.identity.application.port

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.identity.domain.user.UserId
import kotlin.uuid.Uuid

/**
 * The shared contract for explicit email-factor enrollment stores.
 */
public abstract class EmailMfaEnrollmentStoreConformance : StringSpec() {
    protected abstract fun fresh(): EmailMfaEnrollmentStore

    init {
        "enrollment is explicit, idempotent, and removable" {
            val store = fresh()
            val userId = UserId(Uuid.random())
            store.isEnrolled(userId) shouldBe false
            store.enroll(userId)
            store.enroll(userId)
            store.isEnrolled(userId) shouldBe true
            store.unenroll(userId)
            store.isEnrolled(userId) shouldBe false
        }
    }
}
