package tallyvane.sessions.application

import tallyvane.identity.contract.AccountId

/**
 * Who changed how long sessions may live, and why (ADR-078, ADR-098).
 *
 * Kept next to the version it made or the activation it recorded, so one row says what, when, who and why. The
 * comment is the person's own words, trimmed, between one and [MAX_COMMENT_LENGTH] characters with no control
 * characters: it is shown on a screen and read in a log. Only [by] makes one.
 */
public class Change private constructor(private val author: AccountId, private val comment: String) {
    /**
     * Tells [record] who made the change and the words they gave.
     */
    public fun writeTo(record: Record) {
        record.change(author, comment)
    }

    override fun equals(other: Any?): Boolean = other is Change && other.author == author && other.comment == comment

    override fun hashCode(): Int = 31 * author.hashCode() + comment.hashCode()

    override fun toString(): String = "Change(author=$author, commentLength=${comment.length})"

    /**
     * What a change tells whoever keeps it.
     */
    public fun interface Record {
        public fun change(author: AccountId, comment: String)
    }

    public companion object {
        /**
         * The longest comment accepted: a sentence or two on why, not a document.
         */
        public const val MAX_COMMENT_LENGTH: Int = 500

        /**
         * The change [author] made for the reason [comment] gives, or null for a comment that is empty, longer than
         * [MAX_COMMENT_LENGTH] or holds a control character.
         */
        public fun by(author: AccountId, comment: String): Change? = comment.trim()
            .takeIf { trimmed -> trimmed.length in 1..MAX_COMMENT_LENGTH && trimmed.none(Char::isISOControl) }
            ?.let { trimmed -> Change(author, trimmed) }
    }
}
