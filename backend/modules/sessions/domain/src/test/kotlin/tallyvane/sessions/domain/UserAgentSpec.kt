package tallyvane.sessions.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

private fun reading(header: String?): String {
    val told = mutableListOf<String>()
    UserAgent(header).device().writeTo(
        object : Device.Record {
            override fun device(browser: Browser, platform: Platform, mobile: Boolean, name: String?) {
                told += "$browser on $platform${if (mobile) " (mobile)" else ""} name=$name"
            }
        },
    )
    return told.single()
}

private const val CHROME_WINDOWS =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
private const val EDGE_WINDOWS = "$CHROME_WINDOWS Edg/130.0.0.0"
private const val CHROME_MAC =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
private const val SAFARI_MAC =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15"
private const val SAFARI_IPHONE =
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) " +
        "Version/17.5 Mobile/15E148 Safari/604.1"
private const val CHROME_ANDROID =
    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
private const val FIREFOX_LINUX = "Mozilla/5.0 (X11; Linux x86_64; rv:131.0) Gecko/20100101 Firefox/131.0"
private const val OPERA_WINDOWS = "$CHROME_WINDOWS OPR/115.0.0.0"
private const val CHROME_CHROMEOS =
    "Mozilla/5.0 (X11; CrOS x86_64 14541.0.0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

class UserAgentSpec :
    StringSpec(
        {
            "reads Chrome on Windows" { reading(CHROME_WINDOWS) shouldBe "Chrome on Windows name=null" }

            "reads Edge as Edge although it also says Chrome and Safari" {
                reading(EDGE_WINDOWS) shouldBe "Edge on Windows name=null"
            }

            "reads Opera as Opera although it also says Chrome and Safari" {
                reading(OPERA_WINDOWS) shouldBe "Opera on Windows name=null"
            }

            "tells Chrome on a Mac from Chrome on Windows, which is what lets a person tell two of them apart" {
                reading(CHROME_MAC) shouldBe "Chrome on MacOs name=null"
            }

            "reads Safari on a Mac as Safari, not Chrome" { reading(SAFARI_MAC) shouldBe "Safari on MacOs name=null" }

            "reads an iPhone as iOS and mobile although it says Mac OS X" {
                reading(SAFARI_IPHONE) shouldBe "Safari on Ios (mobile) name=null"
            }

            "reads an Android phone as Android and not Linux, though it says both" {
                reading(CHROME_ANDROID) shouldBe "Chrome on Android (mobile) name=null"
            }

            "reads Firefox on Linux" { reading(FIREFOX_LINUX) shouldBe "Firefox on Linux name=null" }

            "reads Chrome on ChromeOS as ChromeOS, not Linux" {
                reading(CHROME_CHROMEOS) shouldBe "Chrome on ChromeOs name=null"
            }

            "reads a missing header as an unknown device, not an error" {
                reading(null) shouldBe "Other on Other name=null"
            }

            "reads nonsense as an unknown device, not an error" {
                reading("curl/8.0 \u0000\u0001 ???") shouldBe "Other on Other name=null"
            }

            "reads only the start of an absurdly long header" {
                reading("x".repeat(100_000) + " Firefox/131.0 Windows") shouldBe "Other on Other name=null"
            }
        },
    )
