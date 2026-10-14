package tallyvane.sessions.application

import tallyvane.sessions.domain.ClientType

/**
 * The words the API uses for kinds of client, and back.
 *
 * Spelled out rather than taken from the enum's names, so renaming a constant cannot change the contract. They are
 * the words of the migrations as well (`browser`, `admin`), which a person reading the database sees too.
 */
public class LifetimeWords {
    /**
     * The kind of client [word] names, or null for a word that names none.
     */
    public fun clientNamed(word: String): ClientType? = ClientType.entries.firstOrNull { wordOf(it) == word }

    public fun wordOf(client: ClientType): String = when (client) {
        ClientType.Browser -> "browser"
        ClientType.Admin -> "admin"
    }

    /**
     * The fault of a kind of client [word] that names none.
     */
    public fun unknownClient(word: String): Fault =
        Fault("client", "unknown", "There is no kind of client called '$word'.")

    /**
     * The fault of a comment that is empty, too long or holds a control character.
     */
    public fun invalidComment(): Fault = Fault(
        "comment",
        "comment-invalid",
        "Say why in one to ${Change.MAX_COMMENT_LENGTH} characters, without control characters.",
    )

    override fun toString(): String = "LifetimeWords"
}
