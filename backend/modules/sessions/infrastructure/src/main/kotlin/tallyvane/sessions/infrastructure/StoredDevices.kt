package tallyvane.sessions.infrastructure

import tallyvane.sessions.domain.Browser
import tallyvane.sessions.domain.ClientType
import tallyvane.sessions.domain.Platform

/**
 * The words the migration's `check` constraints accept for each kind of client, browser and system, and
 * back.
 *
 * Spelled out rather than taken from the enums' names, so renaming a constant cannot change what is
 * stored.
 */
internal class StoredDevices {
    fun of(client: ClientType): String = when (client) {
        ClientType.Browser -> "browser"
        ClientType.Admin -> "admin"
    }

    fun clientFrom(word: String): ClientType = ClientType.entries.firstOrNull { of(it) == word }
        ?: error("A session was kept for a '$word' client, which the sessions schema does not accept.")

    fun of(browser: Browser): String = when (browser) {
        Browser.Chrome -> "chrome"
        Browser.Edge -> "edge"
        Browser.Firefox -> "firefox"
        Browser.Opera -> "opera"
        Browser.Safari -> "safari"
        Browser.Other -> "other"
    }

    fun browserFrom(word: String): Browser = Browser.entries.firstOrNull { of(it) == word }
        ?: error("A session was kept on a '$word' browser, which the sessions schema does not accept.")

    fun of(platform: Platform): String = when (platform) {
        Platform.Windows -> "windows"
        Platform.MacOs -> "macos"
        Platform.Linux -> "linux"
        Platform.Android -> "android"
        Platform.Ios -> "ios"
        Platform.ChromeOs -> "chromeos"
        Platform.Other -> "other"
    }

    fun platformFrom(word: String): Platform = Platform.entries.firstOrNull { of(it) == word }
        ?: error("A session was kept on a '$word' system, which the sessions schema does not accept.")

    override fun toString(): String = "StoredDevices"
}
