package tallyvane.identity.application.port

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.identity.domain.secondfactor.AuthenticationPolicy

/**
 * Shared behavioural contract for policy persistence adapters.
 */
public abstract class AuthenticationPolicyStoreConformance : StringSpec() {
    protected abstract fun fresh(): AuthenticationPolicyStore

    init {
        "a current policy can be read" {
            val current = fresh().current()!!
            current.version shouldBe 1
            current.rules shouldBe AuthenticationPolicy.defaults().rules
            current.advancedAcknowledged shouldBe false
        }

        "replacement increments the version and rejects a stale writer" {
            val store = fresh()
            val changed = AuthenticationPolicy.defaults(2)
            store.replace(1, changed) shouldBe true
            store.current()!!.let {
                it.version shouldBe changed.version
                it.rules shouldBe changed.rules
                it.advancedAcknowledged shouldBe changed.advancedAcknowledged
            }
            store.replace(1, AuthenticationPolicy.defaults(3)) shouldBe false
            store.current()!!.version shouldBe changed.version
        }
    }
}
