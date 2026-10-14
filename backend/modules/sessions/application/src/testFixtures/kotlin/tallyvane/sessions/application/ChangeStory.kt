package tallyvane.sessions.application

import tallyvane.identity.contract.AccountId

/**
 * What a [Change] says when asked to [Change.writeTo]: who made it and the words they gave.
 */
class ChangeStory(change: Change) {
    private val told = mutableListOf<Pair<AccountId, String>>().also {
        change.writeTo { author, comment -> it += author to comment }
    }

    fun author(): AccountId = told.single().first

    fun comment(): String = told.single().second
}
