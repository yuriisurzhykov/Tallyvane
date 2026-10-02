package tallyvane.authentication.application

/**
 * What Google says about a person besides who they are: the name they go by there and the address
 * Google has verified.
 *
 * Only a new person needs it, and only to fill in the welcome screen; their account keeps what they
 * confirm there (slice 3, fork 3). It is personal data of somebody who has agreed to nothing yet, so
 * it never reaches a log: [toString] says nothing of it.
 */
public class GoogleProfile(private val name: String, private val email: String) {
    /**
     * Tells [record] the name and the address.
     */
    public fun writeTo(record: Record) {
        record.profile(name, email)
    }

    override fun equals(other: Any?): Boolean = other is GoogleProfile && other.name == name && other.email == email

    override fun hashCode(): Int = name.hashCode() * 31 + email.hashCode()

    override fun toString(): String = "GoogleProfile(***)"

    /**
     * Whoever needs the profile itself: the welcome screen, and the storage.
     */
    public fun interface Record {
        public fun profile(name: String, email: String)
    }
}
