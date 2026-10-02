package tallyvane.authentication.application

import tallyvane.identity.contract.Registrant
import kotlin.time.Instant

/**
 * A registration waiting for its welcome form: whose it is to Google, and what Google said about them.
 */
internal class Pending(private val subject: String, val profile: GoogleProfile) {
    /**
     * The person described by what Google vouched for and the [name] they chose, who agreed at [at].
     */
    fun registrantNamed(name: String, at: Instant): Registrant {
        val addresses = mutableListOf<String>()
        profile.writeTo { _, email -> addresses += email }
        return Registrant(subject, name, addresses.single(), at)
    }

    override fun toString(): String = "Pending(***)"
}
