package tallyvane.platform.http

import io.ktor.server.application.ApplicationCall

/**
 * A route says, with this, that the answer it is about to give carries a secret and must not be stored
 * for a repeat.
 *
 * ```kotlin
 * SecretAnswer(call).withheldFromReplay()
 * call.respond(HttpStatusCode.Created, DeviceTokenIssued(secret))
 * ```
 *
 * A response that sets a cookie is withheld without being asked. This is for the answer that puts the
 * secret in its body, the device-token exchange of ADR-081 above all: the database holds only hashes of
 * secrets (ADR-079), and an answer stored for replay would hold one in the clear for a day.
 *
 * A repeat of a withheld answer is told `409`: the work was carried out and cannot be repeated.
 */
public class SecretAnswer(private val call: ApplicationCall) {
    public fun withheldFromReplay() {
        call.attributes.put(Repeats.WITHHELD, true)
    }
}
