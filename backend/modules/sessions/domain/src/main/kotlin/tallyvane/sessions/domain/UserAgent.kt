package tallyvane.sessions.domain

/**
 * What a browser said about itself in its `User-Agent` header, and the [Device] that makes of it.
 *
 * The header is whatever the client chose to send, so nothing here is trusted: it picks out a few
 * well-known words, takes the first that fits, and names the rest `Other`. A header that is missing,
 * absurdly long, or nonsense gives a device of `Other` on `Other`, never an error, because a person must
 * be able to sign in however their browser introduces itself.
 *
 * The order of the words is the rule: Edge, Opera and Chrome all say "Chrome" and "Safari" as well, so
 * the more specific is asked first. Reading is by words and not by version numbers, so a new release of
 * a known browser is still recognised.
 */
public class UserAgent(text: String?) {
    private val text: String = text.orEmpty().take(LONGEST_READ)

    /**
     * The device this header describes.
     */
    public fun device(): Device = Device(browser(), platform(), mobile(), null)

    private fun browser(): Browser = BROWSER_WORDS.firstOrNull { (words, _) -> words.any(text::contains) }?.second
        ?: Browser.Other

    private fun platform(): Platform = PLATFORM_WORDS.firstOrNull { (words, _) -> words.any(text::contains) }?.second
        ?: Platform.Other

    private fun mobile(): Boolean = MOBILE_WORDS.any(text::contains)

    override fun toString(): String = "UserAgent"

    private companion object {
        /**
         * More than a real header ever is; the rest is not read.
         */
        const val LONGEST_READ = 512

        val BROWSER_WORDS: List<Pair<List<String>, Browser>> = listOf(
            listOf("Edg/", "EdgA/", "EdgiOS/") to Browser.Edge,
            listOf("OPR/", "Opera") to Browser.Opera,
            listOf("Firefox/", "FxiOS/") to Browser.Firefox,
            listOf("Chrome/", "CriOS/") to Browser.Chrome,
            listOf("Safari/") to Browser.Safari,
        )

        /**
         * Android says "Linux" too, and an iPhone says "Mac OS X", so those are asked first.
         */
        val PLATFORM_WORDS: List<Pair<List<String>, Platform>> = listOf(
            listOf("Android") to Platform.Android,
            listOf("iPhone", "iPad", "iPod") to Platform.Ios,
            listOf("CrOS") to Platform.ChromeOs,
            listOf("Windows") to Platform.Windows,
            listOf("Macintosh", "Mac OS X") to Platform.MacOs,
            listOf("Linux", "X11") to Platform.Linux,
        )

        val MOBILE_WORDS: List<String> = listOf("Mobi", "iPad")
    }
}
