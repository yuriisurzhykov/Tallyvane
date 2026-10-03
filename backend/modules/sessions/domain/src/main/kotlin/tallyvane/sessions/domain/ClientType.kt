package tallyvane.sessions.domain

/**
 * What kind of client holds a session. Its lifetimes are chosen by it (ADR-079): a browser that is
 * left alone is not an extension that stays signed in for weeks.
 *
 * Only the browser exists today. The extension and the mobile client arrive with the devices that
 * connect through the browser (ADR-081).
 */
public enum class ClientType {
    Browser,
}
