package tallyvane.platform.observability.health

import kotlin.time.Duration

/**
 * Why something is not [Health.Up].
 *
 * A free-text reason was the first shape, and it made §17 a matter of everyone
 * remembering it: a driver's message carries hosts, ports and sometimes
 * credentials, so every producer had to be trusted not to pass one through. A
 * named case per cause moves that from a convention to a property of the type —
 * [Threw] has nowhere to put a message, so no message can arrive by accident.
 *
 * The second gain is at the edge: §11 can render each case as its own JSON
 * object with a discriminator, so the shape of a probe's answer stops depending
 * on how someone worded a string.
 */
public sealed interface Ailment {
    /**
     * The check decided this itself, in its own words. The one case whose text
     * is written by us rather than by a library, which is why it may carry any.
     */
    public data class Refused(val says: String) : Ailment

    /**
     * Did not answer inside the bound `HealthCheck.Bounded` applied.
     */
    public data class Overran(val bound: Duration) : Ailment

    /**
     * Failed with this exception type. The type only — see the note above.
     */
    public data class Threw(val type: String) : Ailment

    /**
     * An aggregate over dependencies that are not [Health.Up], named rather than
     * joined, so a reader is not parsing a sentence back into a list.
     */
    public data class Dependencies(val names: List<String>) : Ailment

    /**
     * A schema behind the code that expects it, by these migration versions.
     *
     * Its own case rather than [Refused] text because the remedy depends on the
     * list: one pending version is a deploy that started the application before
     * its migration command, several is a deploy that never ran it at all. A
     * sentence would make a reader parse that back out.
     *
     * Like [Dependencies], it has no public representation — schema versions say
     * what the system is made of, and ADR-055 keeps that for an authorized reader.
     */
    public data class Behind(val versions: List<String>) : Ailment
}
