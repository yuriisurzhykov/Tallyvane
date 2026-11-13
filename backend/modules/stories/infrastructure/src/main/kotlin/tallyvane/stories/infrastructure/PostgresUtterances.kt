package tallyvane.stories.infrastructure

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import tallyvane.platform.kernel.UntrustedText
import tallyvane.stories.application.port.Utterances
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class PostgresUtterances : Utterances {
    override fun keep(id: Uuid, account: Uuid, story: Uuid, words: UntrustedText, at: Instant) {
        UtterancesTable.insert {
            it[UtterancesTable.id] = id
            it[accountId] = account
            it[storyId] = story
            it[UtterancesTable.words] = words.cleaned()
            it[saidAt] = at
        }
    }

    override fun countOf(account: Uuid): Int =
        UtterancesTable.selectAll().where { UtterancesTable.accountId eq account }.count().toInt()

    override fun toString(): String = "PostgresUtterances(schema=stories)"
}
