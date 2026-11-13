package tallyvane.stories.application.port

import tallyvane.platform.kernel.UntrustedText
import tallyvane.stories.application.Reading
import tallyvane.stories.domain.Fact
import tallyvane.stories.domain.Slot
import kotlin.uuid.Uuid

/**
 * Turns words into claims (specs `extract_facts@1` and `correct_fact@1`). The model translates; the domain
 * decides what holds.
 */
public interface Reader {
    /**
     * Claims in [words]; [asked] is the slot the person was asked about, if any.
     */
    public suspend fun read(words: UntrustedText, account: Uuid, asked: Slot?): Reading

    /**
     * The one claim that replaces [fact], as [words] correct it.
     */
    public suspend fun correct(fact: Fact, words: UntrustedText, account: Uuid): Reading
}
