package tallyvane.sessions.domain

import tallyvane.platform.kernel.Surface

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
 */
public enum class ClientType(private val surface: Surface) {
    Browser(Surface.App),
    Admin(Surface.Admin),
    ;

    internal fun isOn(surface: Surface): Boolean = this.surface == surface

    public companion object {
        /**
         * The kind of client of a person who came through [surface].
         */
        public fun on(surface: Surface): ClientType = entries.single { it.isOn(surface) }
    }
}
