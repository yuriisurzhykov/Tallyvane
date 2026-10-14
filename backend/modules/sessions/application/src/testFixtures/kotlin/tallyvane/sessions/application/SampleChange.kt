package tallyvane.sessions.application

import tallyvane.identity.contract.AccountId
import kotlin.uuid.Uuid

/**
 * A [Change] for suites that need one and have no opinion about who made it or why.
 */
class SampleChange(
    private val author: Uuid = Uuid.parse("00000000-0000-4000-8000-0000000000ad"),
    private val comment: String = "A reason given in a test",
) {
    /**
     * The author's identifier as a history tells it.
     */
    fun author(): String = author.toString()

    /**
     * The words given, as a history tells them.
     */
    fun comment(): String = comment

    /**
     * The change these words describe.
     */
    fun change(): Change =
        checkNotNull(Change.by(AccountId(author), comment)) { "The comment '$comment' is not one a change accepts." }
}
