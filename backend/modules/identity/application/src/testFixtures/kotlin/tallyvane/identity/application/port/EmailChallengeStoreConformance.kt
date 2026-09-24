package tallyvane.identity.application.port

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import tallyvane.identity.domain.email.EmailChallenge
import tallyvane.identity.domain.email.EmailChallengePurpose
import tallyvane.identity.domain.user.Email
import tallyvane.platform.kernel.Secret
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Shared one-time-use behaviour required of email challenge stores. */
public abstract class EmailChallengeStoreConformance : StringSpec() {
    protected abstract fun fresh(): EmailChallengeStore

    init {
        "a valid challenge can be read and consumed once" {
            val store = fresh()
            val id = Uuid.random()
            val hash = Secret("hashed-value")
            val now = Instant.parse("2026-09-24T00:00:00Z")
            val challenge = EmailChallenge(
                id, Email("recipient@example.test"), EmailChallengePurpose.REGISTRATION,
                "account-1", now + kotlin.time.Duration.parse("10m"),
            )
            store.issue(challenge, hash, now, now, 5).shouldBeTrue()
            store.find(id) shouldBe challenge
            store.consume(id, hash, now).shouldBeTrue()
            store.consume(id, hash, now) shouldBe false
            store.find(id).shouldNotBeNull()
        }
    }
}
