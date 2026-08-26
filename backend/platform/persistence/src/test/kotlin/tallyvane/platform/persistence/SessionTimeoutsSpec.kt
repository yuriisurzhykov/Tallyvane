package tallyvane.platform.persistence

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class SessionTimeoutsSpec :
    StringSpec(
        {
            "renders every set bound as a -c setting the server understands" {
                SessionTimeouts(
                    statement = 15.seconds,
                    lock = 3.seconds,
                    idleInTransaction = 1.minutes,
                ).asConnectionOption() shouldBe
                    "-c statement_timeout=15000ms -c lock_timeout=3000ms " +
                    "-c idle_in_transaction_session_timeout=60000ms"
            }

            "omits what is not set, so a migration gets a lock bound and no statement bound" {
                SessionTimeouts(lock = 3.seconds).asConnectionOption() shouldBe "-c lock_timeout=3000ms"
            }

            "renders sub-second bounds without rounding them to zero" {
                SessionTimeouts(lock = 250.milliseconds).asConnectionOption() shouldBe "-c lock_timeout=250ms"
            }

            // An empty option string would be accepted by the driver and by the server, so
            // nothing downstream can tell it apart from a bound that was set.
            "refuses to be constructed with nothing set" {
                shouldThrow<IllegalArgumentException> { SessionTimeouts() }
            }
        },
    )
