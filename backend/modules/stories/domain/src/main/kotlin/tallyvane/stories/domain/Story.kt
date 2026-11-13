package tallyvane.stories.domain

import tallyvane.platform.kernel.IdGenerator
import kotlin.uuid.Uuid

/**
 * One piece of a person's work, told in their words: where it was, and the facts heard so far.
 *
 * A story changes only by hearing more. Each change gives a new story; the facts it had stay as they were.
 */
public class Story private constructor(
    private val id: Uuid,
    private val account: Uuid,
    private val place: Place,
    private val title: String?,
    private val facts: List<Fact>,
    private val finished: Boolean,
) {
    public fun isOf(account: Uuid): Boolean = this.account == account

    public fun has(id: Uuid): Boolean = this.id == id

    public fun holds(fact: Uuid): Boolean = facts.any { it.has(fact) && it.isLive() }

    /**
     * This story after hearing [claims] in [hearing]. Claims that do not hold are dropped, and so is one that
     * repeats a fact already here. A person who says they have nothing more finishes the story.
     */
    public fun hear(hearing: Hearing, claims: List<Claim>, where: Place, named: String?, ids: IdGenerator): Story {
        val heard = claims.take(MOST).fold(facts) { known, claim ->
            val fact = hearing.factOf(claim, ids.next())
            if (fact == null || known.any { it.isLive() && it.says(claim.text.trim()) }) known else known + fact
        }
        return Story(
            id,
            account,
            place.filledFrom(where),
            title ?: named?.trim()?.take(LONGEST)?.takeIf { it.isNotEmpty() },
            heard,
            finished || (heard.size == facts.size && hearing.saysNothingMore()),
        )
    }

    /**
     * This story with the fact [corrected] replaced by what [claim] says in [hearing], or null when the story
     * has no such live fact or the claim does not hold.
     */
    public fun correct(corrected: Uuid, hearing: Hearing, claim: Claim, ids: IdGenerator): Story? {
        val newId = ids.next()
        val old = facts.firstOrNull { it.has(corrected) && it.isLive() }
        val new = old?.let { hearing.factOf(claim, newId, it) }
        return new?.let { fact ->
            Story(
                id,
                account,
                place,
                title,
                facts.map {
                    if (it ===
                        old
                    ) {
                        it.replacedBy(newId)
                    } else {
                        it
                    }
                } + fact,
                finished,
            )
        }
    }

    /**
     * What is still to be asked, or null when nothing is.
     */
    public fun missing(): Slot? = if (finished) null else Slot.entries.firstOrNull { it !in filled }

    public fun state(): StoryState = when {
        Slot.Role in filled && Slot.Outcome in filled && missing() == null -> StoryState.Strong
        missing() == null -> StoryState.Closed
        else -> StoryState.Open
    }

    private val filled: Set<Slot>
        get() = facts.filter { it.isLive() }.mapNotNull { it.fills() }.toSet()

    /**
     * Tells [record] the story, then its place, then each fact with its quotes.
     */
    public fun writeTo(record: Record) {
        record.story(id, account, title, finished, state(), missing())
        place.writeTo(record)
        facts.forEach { it.writeTo(record) }
    }

    override fun equals(other: Any?): Boolean = other is Story &&
        other.id == id &&
        other.account == account &&
        other.place == place &&
        other.title == title &&
        other.facts == facts &&
        other.finished == finished

    override fun hashCode(): Int = listOf(id, account, place, title, facts, finished).hashCode()

    override fun toString(): String = "Story(${state()}, facts=${facts.size})"

    /**
     * Whoever keeps or shows a story.
     */
    public interface Record :
        Place.Record,
        Fact.Record {
        @Suppress("LongParameterList") // What a story is, and the two things it computes about itself.
        public fun story(id: Uuid, account: Uuid, title: String?, finished: Boolean, state: StoryState, missing: Slot?)
    }

    public companion object {
        private const val MOST = 12
        private const val LONGEST = 120

        /**
         * A story nobody has told anything about yet.
         */
        public fun begun(id: Uuid, account: Uuid): Story =
            Story(id, account, Place(null, null, null, null), null, emptyList(), false)

        /**
         * A story storage kept.
         */
        @Suppress("LongParameterList") // The parts of a story, as storage kept them.
        public fun restore(
            id: Uuid,
            account: Uuid,
            place: Place,
            title: String?,
            facts: List<Fact>,
            finished: Boolean,
        ): Story =
            Story(id, account, place, title, facts, finished)
    }
}
