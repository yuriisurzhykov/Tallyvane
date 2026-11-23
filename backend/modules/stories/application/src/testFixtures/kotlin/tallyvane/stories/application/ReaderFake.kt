package tallyvane.stories.application

import tallyvane.platform.kernel.UntrustedText
import tallyvane.stories.application.port.Reader
import tallyvane.stories.domain.Fact
import tallyvane.stories.domain.Place
import tallyvane.stories.domain.Slot
import kotlin.uuid.Uuid

/**
 * [Reader] that answers what it was told to, the same for any words.
 */
class ReaderFake(private val reading: Reading = Reading.Heard(emptyList(), Place(null, null, null, null), null)) :
    Reader {
    override suspend fun read(words: UntrustedText, account: Uuid, asked: Slot?): Reading = reading

    override suspend fun correct(fact: Fact, words: UntrustedText, account: Uuid): Reading = reading

    override fun toString(): String = "ReaderFake($reading)"
}
