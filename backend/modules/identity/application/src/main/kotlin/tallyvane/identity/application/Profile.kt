package tallyvane.identity.application

import kotlin.uuid.Uuid

/**
 * A person as the application shows them to themselves: which account, and what they are called.
 *
 * Told to whoever shows it through [writeTo], and through nothing else: the fields are private, so a
 * screen cannot reach into one for what it was not offered.
 */
public class Profile(private val id: Uuid, private val name: String) {
    /**
     * Tells [record] who this is.
     */
    public fun writeTo(record: Record) {
        record.profile(id, name)
    }

    override fun toString(): String = "Profile(id=$id)"

    /**
     * Whoever shows a profile.
     */
    public fun interface Record {
        public fun profile(id: Uuid, name: String)
    }
}
