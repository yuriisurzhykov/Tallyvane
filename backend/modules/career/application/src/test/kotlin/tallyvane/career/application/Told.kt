package tallyvane.career.application

import tallyvane.stories.contract.ToldFact
import tallyvane.stories.contract.ToldStory
import kotlin.uuid.Uuid

/**
 * Stories to build tests from.
 */
internal class Told {
    fun story(n: Int, missing: String?, vararg facts: ToldFact): ToldStory = ToldStory(
        Uuid.fromLongs(0, n.toLong()),
        "Story $n",
        "Acme",
        "Engineer",
        "2023-01 – now",
        missing,
        facts.toList(),
    )

    fun fact(kind: String, text: String, mine: Boolean = true, vararg skills: String): ToldFact =
        ToldFact(kind, text, skills.toList(), mine)

    override fun toString(): String = "Told"
}
