package tallyvane.career.application.port

import kotlin.uuid.Uuid

/**
 * The role each person is heading for. Every call runs inside a transaction the caller opened.
 */
public interface Goals {
    /**
     * The slug of the role track [account] chose, or null.
     */
    public fun of(account: Uuid): String?

    public fun set(account: Uuid, role: String)
}
