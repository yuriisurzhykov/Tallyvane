package tallyvane.sessions.domain

/**
 * The operating system a session was started on, as far as the `User-Agent` says.
 *
 * It is the part of a device that tells two of a person's browsers apart most of the time. It is the
 * system and not the machine: two laptops on the same system look the same, and a person who needs to
 * tell them apart names them (ADR-090).
 */
public enum class Platform {
    Windows,
    MacOs,
    Linux,
    Android,
    Ios,
    ChromeOs,
    Other,
}
