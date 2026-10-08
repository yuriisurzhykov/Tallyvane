package tallyvane.sessions.domain

import tallyvane.platform.kernel.Surface
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * What kind of client holds a session. Its lifetimes are chosen by it (ADR-079): a browser that is
 * left alone is not an extension that stays signed in for weeks, and an administrator's session is
 * shorter than a browser's.
 *
 * A browser client belongs to one door, and a session works only on the door of its own client (ADR-097):
 * a session of the console is not good on the administrators' site, and the other way round, even if its
 * secret is carried across by hand. The extension and the mobile client arrive with the devices that
 * connect through the browser (ADR-081).
 *
 * @param surface The door this kind of client uses.
 * @param starting What a session of this kind lives by until a version of its lifetimes is put in force, or
 * null when the kind must always have one. A kind that arrives in a release before its first version is
 * kept (the administrators', until the versions API of slice 7b) is judged by these, so no row has to be
 * added to storage that the previous release, still serving during a rollout, cannot read (ADR-066).
 */
public enum class ClientType(private val surface: Surface, private val starting: Lifetimes? = null) {
    Browser(Surface.App),
    Admin(Surface.Admin, Lifetimes(1.hours, 8.hours, 5.minutes)),
    ;

    internal fun lifetimesUntilVersioned(): Lifetimes? = starting

    internal fun isOn(surface: Surface): Boolean = this.surface == surface

    public companion object {
        /**
         * The kind of client of a person who came through [surface].
         */
        public fun on(surface: Surface): ClientType = entries.single { it.isOn(surface) }
    }
}
