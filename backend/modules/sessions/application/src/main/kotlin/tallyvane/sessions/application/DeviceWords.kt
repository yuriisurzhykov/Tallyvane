package tallyvane.sessions.application

import tallyvane.sessions.domain.Browser
import tallyvane.sessions.domain.Platform

/**
 * The words the API uses for a browser and a system, which a client branches on.
 *
 * Spelled out rather than taken from the enums' names, so renaming a constant cannot change the API. The web
 * layer answers with them and the journal is told them (ADR-095), so both say the same word.
 */
public class DeviceWords {
    public fun of(browser: Browser): String = when (browser) {
        Browser.Chrome -> "chrome"
        Browser.Edge -> "edge"
        Browser.Firefox -> "firefox"
        Browser.Opera -> "opera"
        Browser.Safari -> "safari"
        Browser.Other -> "other"
    }

    public fun of(platform: Platform): String = when (platform) {
        Platform.Windows -> "windows"
        Platform.MacOs -> "macos"
        Platform.Linux -> "linux"
        Platform.Android -> "android"
        Platform.Ios -> "ios"
        Platform.ChromeOs -> "chromeos"
        Platform.Other -> "other"
    }

    override fun toString(): String = "DeviceWords"
}
